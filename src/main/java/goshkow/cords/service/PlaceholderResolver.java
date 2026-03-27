package goshkow.cords.service;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class PlaceholderResolver {
    private PlaceholderResolver() {
    }

    public static String apply(String text, Player player) {
        return text
                .replace("${player}", player.getName())
                .replace("${playerId}", player.getUniqueId().toString());
    }

    public static String applyMarker(String text, String name) {
        if (text == null) {
            return "";
        }

        String value = name == null ? "" : name;
        return text
                .replace("%marker_name%", value)
                .replace("%new_name%", value)
                .replace("${name}", value)
                .replace("%name%", value)
                .replace("{name}", value)
                .replace("${marker}", value)
                .replace("%marker%", value);
    }

    public static String applyMarkerRename(String text, String oldName, String newName) {
        if (text == null) {
            return "";
        }

        String oldValue = oldName == null ? "" : oldName;
        String newValue = newName == null ? "" : newName;
        return text
                .replace("%marker_name%", oldValue)
                .replace("${name}", oldValue)
                .replace("%name%", oldValue)
                .replace("{name}", oldValue)
                .replace("${marker}", oldValue)
                .replace("%marker%", oldValue)
                .replace("%new_name%", newValue)
                .replace("${new_name}", newValue)
                .replace("{new_name}", newValue);
    }

    public static String applyMarkerMove(String text, String name, Location from, Location to) {
        if (text == null) {
            return "";
        }

        return text
                .replace("%marker_name%", name == null ? "" : name)
                .replace("${name}", name == null ? "" : name)
                .replace("%name%", name == null ? "" : name)
                .replace("{name}", name == null ? "" : name)
                .replace("${marker}", name == null ? "" : name)
                .replace("%marker%", name == null ? "" : name)
                .replace("%from%", formatLocation(from))
                .replace("%to%", formatLocation(to));
    }

    private static String formatLocation(Location location) {
        if (location == null) {
            return "";
        }

        return formatCoordinate(location.getX()) + ", " + formatCoordinate(location.getY()) + ", " + formatCoordinate(location.getZ());
    }

    public static String formatCoordinate(double value) {
        return String.valueOf((int) Math.floor(value));
    }
}
