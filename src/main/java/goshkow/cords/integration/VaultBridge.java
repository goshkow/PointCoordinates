package goshkow.cords.integration;

import goshkow.cords.CordsPlugin;
import goshkow.cords.util.CordLogger;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

public final class VaultBridge {
    private static Economy economy;
    private final CordLogger logger = new CordLogger("PointCoordinates / Vault");

    public void hook() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return;
        }

        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) {
            return;
        }

        economy = registration.getProvider();
        if (economy != null) {
            CordsPlugin.setVaultHooked(true);
            logger.info("Vault detected. Economy charges are enabled.");
        }
    }

    public static Economy economy() {
        return economy;
    }

    public static boolean hasRequiredBalance(Player player) {
        if (economy == null) {
            return false;
        }
        double charge = CordsPlugin.getInstance().getConfig().getDouble("integrations.vault.charge_amount", 5.0);
        return economy.getBalance(player) >= charge;
    }
}
