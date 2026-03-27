package goshkow.cords.util;

import goshkow.cords.CordsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public final class CordLogger {
    private final String label;

    public CordLogger(String label) {
        this.label = ChatColor.translateAlternateColorCodes('&', label);
    }

    public void info(String message) {
        send(CordsPlugin.getAccentColor() + "[" + label + "]&r " + message);
    }

    public void debug(String message) {
        if (CordsPlugin.isDebugEnabled()) {
            send("&7[" + label + " / debug]&r " + message);
        }
    }

    private void send(String rawMessage) {
        Bukkit.getConsoleSender().sendMessage(ChatColor.translateAlternateColorCodes('&', rawMessage));
    }
}
