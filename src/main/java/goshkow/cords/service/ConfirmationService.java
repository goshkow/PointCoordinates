package goshkow.cords.service;

import goshkow.cords.CordsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfirmationService {
    private static final Map<UUID, PendingConfirmation> pending = new ConcurrentHashMap<>();

    private ConfirmationService() {
    }

    public static void request(Player player, String actionKey, Runnable confirmedAction) {
        request(player, actionKey, actionKey, confirmedAction);
    }

    public static void request(Player player, String actionKey, String actionLabel, Runnable confirmedAction) {
        pending.put(player.getUniqueId(), new PendingConfirmation(actionKey, actionLabel, confirmedAction));

        String title = translateOrDefault("messages.confirm_title", "Action cannot be undone");
        String subtitle = translateOrDefault("messages.confirm_subtitle", "Type \"${confirm}\" in chat. Any other message will cancel it.");
        subtitle = subtitle.replace("${action}", actionLabel)
                .replace("${confirm}", quotedConfirmWord());

        player.sendTitle(ChatColor.RED + title, ChatColor.GRAY + subtitle, 10, 60, 10);
        String warning = translateOrDefault("messages.confirm_chat", "This action cannot be undone: ${action}")
                .replace("${action}", actionLabel);
        player.sendMessage(CordsPlugin.getPrefix() + ChatColor.RED + warning);
        player.sendMessage(CordsPlugin.getPrefix()
                + ChatColor.GRAY
                + translateOrDefault("messages.confirm_hint", "Type \"${confirm}\" in chat. Any other message will cancel it.")
                .replace("${confirm}", quotedConfirmWord()));
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.0f);
        }
    }

    public static boolean handleChat(Player player, String message) {
        PendingConfirmation confirmation = pending.get(player.getUniqueId());
        if (confirmation == null) {
            return false;
        }

        String normalized = normalize(message);
        if (containsConfirmWord(normalized)) {
            pending.remove(player.getUniqueId());
            player.sendTitle(ChatColor.GREEN + translateOrDefault("messages.confirm_success_title", "Confirmed"),
                    ChatColor.GRAY + translateOrDefault("messages.confirm_success_subtitle", "Changes saved"),
                    10, 40, 10);
            CordsPlugin.getInstance().getServer().getScheduler().runTask(CordsPlugin.getInstance(), confirmation.action());
            return true;
        }

        pending.remove(player.getUniqueId());
        player.sendTitle(ChatColor.GRAY + translateOrDefault("messages.confirm_cancel_title", "Cancelled"),
                ChatColor.GRAY + translateOrDefault("messages.confirm_cancel_subtitle", "No changes were made"),
                10, 40, 10);
        player.sendMessage(CordsPlugin.getPrefix() + ChatColor.YELLOW + translateOrDefault("messages.confirm_cancelled", "Cancelled."));
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        }
        return true;
    }

    public static void clearAll() {
        pending.clear();
    }

    private static boolean containsConfirmWord(String value) {
        String localized = normalize(translateOrDefault("messages.confirm_word", "ok"));
        for (String token : tokenize(value)) {
            if (token.equals("confirm")
                    || token.equals("yes")
                    || token.equals("y")
                    || token.equals("accept")
                    || token.equals("ok")
                    || token.equals(localized)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim().toLowerCase();
        normalized = normalized.replace("\"", "");
        while (normalized.startsWith("(") && normalized.endsWith(")") && normalized.length() > 1) {
            normalized = normalized.substring(1, normalized.length() - 1).trim();
        }
        return normalized.replace("(", "").replace(")", "");
    }

    private static String[] tokenize(String value) {
        String normalized = normalize(value).replaceAll("[^\\p{L}\\p{N}\\s]", " ");
        if (normalized.isBlank()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }

    private static String quotedConfirmWord() {
        return "\"" + translateOrDefault("messages.confirm_word", "ok") + "\"";
    }

    private static String translateOrDefault(String key, String fallback) {
        String value = LanguagePack.translate(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private record PendingConfirmation(String actionKey, String actionLabel, Runnable action) {
    }
}
