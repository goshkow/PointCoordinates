package goshkow.cords.service;

import goshkow.cords.CordsPlugin;
import goshkow.cords.util.PermissionGate;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateService {
    private static final String MODRINTH_VERSIONS_API = "https://api.modrinth.com/v2/project/pointcoordinates/version";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/goshkow/PointCoordinates/releases";
    private static final String MODRINTH_RELEASES_PAGE = "https://modrinth.com/plugin/pointcoordinates/versions";
    private static final String GITHUB_RELEASES_PAGE = "https://github.com/goshkow/PointCoordinates/releases";

    private static final Pattern GITHUB_RELEASE_PATTERN = Pattern.compile(
            "\"tag_name\"\\s*:\\s*\"([^\"]+)\".*?\"html_url\"\\s*:\\s*\"([^\"]+)\".*?\"draft\"\\s*:\\s*(true|false).*?\"prerelease\"\\s*:\\s*(true|false)",
            Pattern.DOTALL
    );
    private static final Pattern MODRINTH_RELEASE_PATTERN = Pattern.compile(
            "\"version_number\"\\s*:\\s*\"([^\"]+)\".*?\"version_type\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );

    private static volatile UpdateInfo latestStableUpdate;

    private UpdateService() {
    }

    public static void checkAsync() {
        if (!CordsPlugin.getInstance().getConfig().getBoolean("updates.notification", true)) {
            latestStableUpdate = null;
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(CordsPlugin.getInstance(), () -> {
            try {
                latestStableUpdate = fetchLatestStableUpdate();
            } catch (Exception exception) {
                if (CordsPlugin.isDebugEnabled()) {
                    CordsPlugin.getInstance().getLogger().warning("Failed to check for PointCoordinates updates: " + exception.getMessage());
                }
            }
        });
    }

    public static void notifyIfNeeded(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }
        if (!CordsPlugin.getInstance().getConfig().getBoolean("updates.notification", true)) {
            return;
        }
        if (!canReceiveNotifications(player)) {
            return;
        }

        UpdateInfo update = latestStableUpdate;
        if (update == null) {
            return;
        }

        player.sendMessage(ChatColor.AQUA + "[PointCoordinates] " + ChatColor.WHITE
                + "A new version is available: " + ChatColor.AQUA + update.version().raw());

        TextComponent root = new TextComponent(ChatColor.GRAY + "Download: ");
        root.addExtra(linkButton("Modrinth", MODRINTH_RELEASES_PAGE));
        root.addExtra(new TextComponent(ChatColor.DARK_GRAY + " | "));
        root.addExtra(linkButton("GitHub", GITHUB_RELEASES_PAGE));
        player.spigot().sendMessage(root);
    }

    private static TextComponent linkButton(String label, String url) {
        TextComponent component = new TextComponent(ChatColor.AQUA + label);
        component.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url));
        component.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.WHITE + "Open " + label + " releases").create()
        ));
        return component;
    }

    private static boolean canReceiveNotifications(Player player) {
        return player.isOp() || PermissionGate.has(player, "cords.reload");
    }

    private static UpdateInfo fetchLatestStableUpdate() throws IOException, InterruptedException {
        SemanticVersion current = SemanticVersion.parse(CordsPlugin.getInstance().getDescription().getVersion());
        List<UpdateInfo> candidates = new ArrayList<>();
        candidates.addAll(fetchGitHubStableReleases());
        candidates.addAll(fetchModrinthStableReleases());

        return candidates.stream()
                .filter(Objects::nonNull)
                .filter(update -> update.version().compareTo(current) > 0)
                .max(Comparator.comparing(UpdateInfo::version))
                .orElse(null);
    }

    private static List<UpdateInfo> fetchGitHubStableReleases() throws IOException, InterruptedException {
        String body = fetch(GITHUB_RELEASES_API);
        ArrayList<UpdateInfo> updates = new ArrayList<>();
        Matcher matcher = GITHUB_RELEASE_PATTERN.matcher(body);
        while (matcher.find()) {
            String rawVersion = matcher.group(1);
            String htmlUrl = matcher.group(2);
            boolean draft = Boolean.parseBoolean(matcher.group(3));
            boolean prerelease = Boolean.parseBoolean(matcher.group(4));
            SemanticVersion parsed = SemanticVersion.parse(rawVersion);
            if (draft || prerelease || !parsed.stable()) {
                continue;
            }
            updates.add(new UpdateInfo(parsed, htmlUrl, "GitHub"));
        }
        return updates;
    }

    private static List<UpdateInfo> fetchModrinthStableReleases() throws IOException, InterruptedException {
        String body = fetch(MODRINTH_VERSIONS_API);
        ArrayList<UpdateInfo> updates = new ArrayList<>();
        Matcher matcher = MODRINTH_RELEASE_PATTERN.matcher(body);
        while (matcher.find()) {
            String rawVersion = matcher.group(1);
            String versionType = matcher.group(2);
            SemanticVersion parsed = SemanticVersion.parse(rawVersion);
            if (!"release".equalsIgnoreCase(versionType) || !parsed.stable()) {
                continue;
            }
            updates.add(new UpdateInfo(parsed, MODRINTH_RELEASES_PAGE, "Modrinth"));
        }
        return updates;
    }

    private static String fetch(String url) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(8))
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "PointCoordinates-UpdateChecker")
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    private record UpdateInfo(SemanticVersion version, String url, String source) {
    }

    static final class SemanticVersion implements Comparable<SemanticVersion> {
        private final String raw;
        private final List<Integer> numbers;
        private final String qualifier;
        private final int qualifierNumber;

        private SemanticVersion(String raw, List<Integer> numbers, String qualifier, int qualifierNumber) {
            this.raw = raw;
            this.numbers = numbers;
            this.qualifier = qualifier;
            this.qualifierNumber = qualifierNumber;
        }

        static SemanticVersion parse(String rawVersion) {
            String raw = Optional.ofNullable(rawVersion).orElse("0.0.0").trim();
            String normalized = raw.toLowerCase(Locale.ROOT)
                    .replaceFirst("^v", "")
                    .replace('_', '.');

            String withoutBuild = normalized.split("\\+", 2)[0];
            String numericPart = withoutBuild;
            String qualifierPart = "";

            int hyphenIndex = withoutBuild.indexOf('-');
            if (hyphenIndex >= 0) {
                numericPart = withoutBuild.substring(0, hyphenIndex);
                qualifierPart = withoutBuild.substring(hyphenIndex + 1);
            } else {
                int letterIndex = firstLetterIndex(withoutBuild);
                if (letterIndex >= 0) {
                    numericPart = withoutBuild.substring(0, letterIndex);
                    qualifierPart = withoutBuild.substring(letterIndex);
                }
            }

            ArrayList<Integer> numbers = new ArrayList<>();
            for (String token : numericPart.split("\\.")) {
                if (token.isBlank()) {
                    continue;
                }
                try {
                    numbers.add(Integer.parseInt(token));
                } catch (NumberFormatException ignored) {
                    numbers.add(0);
                }
            }
            while (numbers.size() < 3) {
                numbers.add(0);
            }

            String qualifier = normalizeQualifier(qualifierPart);
            int qualifierNumber = extractQualifierNumber(qualifierPart);
            return new SemanticVersion(raw, numbers, qualifier, qualifierNumber);
        }

        boolean stable() {
            return "release".equals(qualifier);
        }

        String raw() {
            return raw;
        }

        @Override
        public int compareTo(SemanticVersion other) {
            int max = Math.max(numbers.size(), other.numbers.size());
            for (int index = 0; index < max; index++) {
                int left = index < numbers.size() ? numbers.get(index) : 0;
                int right = index < other.numbers.size() ? other.numbers.get(index) : 0;
                if (left != right) {
                    return Integer.compare(left, right);
                }
            }

            int qualifierCompare = Integer.compare(qualifierRank(qualifier), qualifierRank(other.qualifier));
            if (qualifierCompare != 0) {
                return qualifierCompare;
            }

            return Integer.compare(qualifierNumber, other.qualifierNumber);
        }

        private static int firstLetterIndex(String value) {
            for (int index = 0; index < value.length(); index++) {
                if (Character.isLetter(value.charAt(index))) {
                    return index;
                }
            }
            return -1;
        }

        private static String normalizeQualifier(String qualifierPart) {
            String compact = qualifierPart == null ? "" : qualifierPart.toLowerCase(Locale.ROOT).trim();
            if (compact.isBlank()) {
                return "release";
            }
            if (compact.contains("snapshot")) {
                return "snapshot";
            }
            if (compact.contains("alpha")) {
                return "alpha";
            }
            if (compact.contains("beta")) {
                return "beta";
            }
            if (compact.contains("pre") || compact.contains("preview")) {
                return "pre";
            }
            if (compact.contains("rc")) {
                return "rc";
            }
            return "pre";
        }

        private static int extractQualifierNumber(String qualifierPart) {
            if (qualifierPart == null) {
                return 0;
            }
            Matcher matcher = Pattern.compile("(\\d+)").matcher(qualifierPart);
            if (matcher.find()) {
                try {
                    return Integer.parseInt(matcher.group(1));
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        }

        private static int qualifierRank(String qualifier) {
            return switch (qualifier) {
                case "snapshot" -> 0;
                case "alpha" -> 1;
                case "beta" -> 2;
                case "pre" -> 3;
                case "rc" -> 4;
                case "release" -> 5;
                default -> 0;
            };
        }
    }
}
