package goshkow.cords.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ColorUtil {
    private static final Pattern HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private ColorUtil() {
    }

    public static String colorize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(input);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, toLegacyHex(matcher.group(1)));
        }
        matcher.appendTail(buffer);

        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    private static String toLegacyHex(String hex) {
        StringBuilder builder = new StringBuilder("\u00A7x");
        for (char character : hex.toCharArray()) {
            builder.append('\u00A7').append(character);
        }
        return builder.toString();
    }
}
