package goshkow.cords.util;

import org.bukkit.entity.Player;

public final class PermissionGate {
    private PermissionGate() {
    }

    public static boolean has(Player player, String... nodes) {
        for (String node : nodes) {
            if (node != null && !node.isBlank() && player.hasPermission(node)) {
                return true;
            }
        }
        return false;
    }
}
