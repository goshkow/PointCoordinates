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
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateService {
    private static final String MODRINTH_VERSIONS_API = "https://api.modrinth.com/v2/project/pointcoordinates/version";
    private static final String GITHUB_RELEASES_API = "https://api.github.com/repos/goshkow/PointCoordinates/releases";
    private static final String MODRINTH_RELEASES_PAGE = "https://modrinth.com/plugin/pointcoordinates/versions";
    private static final String GITHUB_RELEASES_PAGE = "https://github.com/goshkow/PointCoordinates/releases";

    private static final Pattern GITHUB_STRING_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern GITHUB_BOOLEAN_FIELD = Pattern.compile("\"([^\"]+)\"\\s*:\\s*(true|false)");
    private static final Pattern MODRINTH_RELEASE_PATTERN = Pattern.compile(
            "\"version_number\"\\s*:\\s*\"([^\"]+)\".*?\"version_type\"\\s*:\\s*\"([^\"]+)\"",
            Pattern.DOTALL
    );

    private static volatile UpdateSnapshot latestSnapshot;

    private UpdateService() {
    }

    public static void checkAsync() {
        if (!CordsPlugin.getInstance().getConfig().getBoolean("updates.notification", true)) {
            latestSnapshot = null;
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(CordsPlugin.getInstance(), () -> {
            try {
                latestSnapshot = fetchLatestSnapshot();
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

        UpdateSnapshot snapshot = latestSnapshot;
        if (snapshot == null) {
            return;
        }

        List<UpdateInfo> relevant = snapshot.relevantUpdates();
        if (relevant.isEmpty()) {
            return;
        }

        SemanticVersion version = relevant.get(0).version();
        player.sendMessage(ChatColor.AQUA + "[PointCoordinates] " + ChatColor.WHITE
                + "A new version is available: " + ChatColor.AQUA + version.raw());

        TextComponent root = new TextComponent(ChatColor.GRAY + "Download: ");
        for (int index = 0; index < relevant.size(); index++) {
            UpdateInfo update = relevant.get(index);
            root.addExtra(linkButton(update.source(), update.url()));
            if (index + 1 < relevant.size()) {
                root.addExtra(new TextComponent(ChatColor.DARK_GRAY + " | "));
            }
        }
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

    private static UpdateSnapshot fetchLatestSnapshot() throws IOException, InterruptedException {
        SemanticVersion current = SemanticVersion.parse(CordsPlugin.getInstance().getDescription().getVersion());
        UpdateInfo github = fetchGitHubStableRelease();
        UpdateInfo modrinth = fetchModrinthStableRelease();
        return new UpdateSnapshot(current, github, modrinth);
    }

    private static UpdateInfo fetchGitHubStableRelease() throws IOException, InterruptedException {
        String body = fetch(GITHUB_RELEASES_API);
        ArrayList<String> objects = splitTopLevelObjects(body);
        UpdateInfo latest = null;
        for (String object : objects) {
            String releaseName = extractStringField(object, "name");
            String tagName = extractStringField(object, "tag_name");
            boolean draft = extractBooleanField(object, "draft");
            boolean prerelease = extractBooleanField(object, "prerelease");
            if (draft || prerelease) {
                continue;
            }

            SemanticVersion parsedFromName = SemanticVersion.parse(releaseName);
            SemanticVersion parsedFromTag = SemanticVersion.parse(tagName);
            SemanticVersion parsed = parsedFromName.stable() ? parsedFromName : parsedFromTag;
            if (!parsed.stable()) {
                continue;
            }

            UpdateInfo candidate = new UpdateInfo(parsed, GITHUB_RELEASES_PAGE, "GitHub");
            if (latest == null || candidate.version().compareTo(latest.version()) > 0) {
                latest = candidate;
            }
        }
        return latest;
    }

    private static UpdateInfo fetchModrinthStableRelease() throws IOException, InterruptedException {
        String body = fetch(MODRINTH_VERSIONS_API);
        UpdateInfo latest = null;
        Matcher matcher = MODRINTH_RELEASE_PATTERN.matcher(body);
        while (matcher.find()) {
            String rawVersion = matcher.group(1);
            String versionType = matcher.group(2);
            SemanticVersion parsed = SemanticVersion.parse(rawVersion);
            if (!"release".equalsIgnoreCase(versionType) || !parsed.stable()) {
                continue;
            }
            UpdateInfo candidate = new UpdateInfo(parsed, MODRINTH_RELEASES_PAGE, "Modrinth");
            if (latest == null || candidate.version().compareTo(latest.version()) > 0) {
                latest = candidate;
            }
        }
        return latest;
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

    private record UpdateSnapshot(SemanticVersion current, UpdateInfo github, UpdateInfo modrinth) {
        List<UpdateInfo> relevantUpdates() {
            boolean githubNewer = github != null && github.version().compareTo(current) > 0;
            boolean modrinthNewer = modrinth != null && modrinth.version().compareTo(current) > 0;

            if (!githubNewer && !modrinthNewer) {
                return List.of();
            }
            if (githubNewer && !modrinthNewer) {
                return List.of(github);
            }
            if (!githubNewer) {
                return List.of(modrinth);
            }

            int compare = github.version().compareTo(modrinth.version());
            if (compare > 0) {
                return List.of(github);
            }
            if (compare < 0) {
                return List.of(modrinth);
            }
            return List.of(modrinth, github);
        }
    }

    private static ArrayList<String> splitTopLevelObjects(String jsonArray) {
        ArrayList<String> objects = new ArrayList<>();
        if (jsonArray == null || jsonArray.isBlank()) {
            return objects;
        }

        int depth = 0;
        int start = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int index = 0; index < jsonArray.length(); index++) {
            char current = jsonArray.charAt(index);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == '"') {
                    inString = false;
                }
                continue;
            }

            if (current == '"') {
                inString = true;
                continue;
            }
            if (current == '{') {
                if (depth == 0) {
                    start = index;
                }
                depth++;
                continue;
            }
            if (current == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    objects.add(jsonArray.substring(start, index + 1));
                    start = -1;
                }
            }
        }
        return objects;
    }

    private static String extractStringField(String jsonObject, String field) {
        Matcher matcher = GITHUB_STRING_FIELD.matcher(jsonObject);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return matcher.group(2);
            }
        }
        return "";
    }

    private static boolean extractBooleanField(String jsonObject, String field) {
        Matcher matcher = GITHUB_BOOLEAN_FIELD.matcher(jsonObject);
        while (matcher.find()) {
            if (field.equals(matcher.group(1))) {
                return Boolean.parseBoolean(matcher.group(2));
            }
        }
        return false;
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
