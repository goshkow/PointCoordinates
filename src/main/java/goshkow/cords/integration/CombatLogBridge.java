package goshkow.cords.integration;

import com.github.sirblobman.combatlogx.api.ICombatLogX;
import com.github.sirblobman.combatlogx.api.manager.ICombatManager;
import goshkow.cords.CordsPlugin;
import goshkow.cords.util.CordLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class CombatLogBridge {
    private static final CordLogger logger = new CordLogger("PointCoordinates / Combat");

    public void hook() {
        if (Bukkit.getPluginManager().isPluginEnabled("CombatLogX")) {
            CordsPlugin.setCombatHooked(true);
            logger.info("CombatLogX detected. Combat restrictions are enabled.");
        }
    }

    public static boolean isActive() {
        return CordsPlugin.isCombatHooked();
    }

    public static boolean isInCombat(Player player) {
        ICombatLogX api = api();
        if (api == null) {
            return false;
        }

        ICombatManager manager = api.getCombatManager();
        return manager != null && manager.isInCombat(player);
    }

    private static ICombatLogX api() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CombatLogX");
        if (!(plugin instanceof ICombatLogX combatLogX)) {
            return null;
        }
        return combatLogX;
    }
}
