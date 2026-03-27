package goshkow.cords.service;

import goshkow.cords.CordsPlugin;
import goshkow.cords.model.CordEntry;
import goshkow.cords.util.PermissionGate;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Date;

public final class InfoPresenter {
    public void show(Player player, CordEntry entry) {
        show(player, entry, false);
    }

    public void showFromList(Player player, CordEntry entry) {
        show(player, entry, true);
    }

    private void show(Player player, CordEntry entry, boolean fromList) {
        if (!PermissionGate.has(player, "cords.info")) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }

        String nameColor = entry.publicEntry() ? ChatColor.GREEN.toString() : CordsPlugin.getAccentColor();
        TextComponent root = new TextComponent((fromList ? "\n" : "") + CordsPlugin.getPrefix() + LanguagePack.translate("messages.info_title") + " " + nameColor + entry.name() + ChatColor.RESET + ":");

        Location location = entry.location();
        String coordinates = Math.floor(location.getX()) + ", " + Math.floor(location.getY()) + ", " + Math.floor(location.getZ());

        TextComponent coordinateLine = new TextComponent("\n" + LanguagePack.translate("messages.coordinates") + " " + ChatColor.UNDERLINE + coordinates + ChatColor.RESET);
        coordinateLine.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, coordinates));
        coordinateLine.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.AQUA + LanguagePack.translate("messages.location_hover")).create()
        ));

        String worldName = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        String displayWorld = LanguagePack.translate("worlds." + worldName);
        if (displayWorld.isBlank()) {
            displayWorld = worldName;
        }

        TextComponent worldLine = new TextComponent("\n" + LanguagePack.translate("messages.world") + " " + displayWorld);
        TextComponent createdLine = new TextComponent("\n" + LanguagePack.translate("messages.created") + " " + new Date(entry.createdAt() * 1000L));
        TextComponent typeLine = new TextComponent("\n" + LanguagePack.translate("messages.scope") + " " + (entry.publicEntry()
                ? LanguagePack.translate("messages.scope_public")
                : LanguagePack.translate("messages.scope_personal")));
        TextComponent pitchLine = new TextComponent("\n" + LanguagePack.translate("messages.pitch") + " " + location.getPitch());
        TextComponent yawLine = new TextComponent("\n" + LanguagePack.translate("messages.yaw") + " " + location.getYaw());

        root.addExtra(coordinateLine);
        root.addExtra(worldLine);
        root.addExtra(typeLine);

        if (entry.publicEntry() && !entry.tags().isEmpty()) {
            root.addExtra(new TextComponent("\n" + LanguagePack.translate("messages.tags") + " " + ChatColor.WHITE + String.join(", ", entry.tags())));
        }

        if (entry.publicEntry()) {
            root.addExtra(new TextComponent("\n" + LanguagePack.translate("messages.owner") + " " + ownerName(entry.ownerId())));
        }

        FileConfiguration config = CordsPlugin.getInstance().getConfig();
        if (config.getBoolean("all_info", false) || config.getBoolean(scopePath(entry.publicEntry()) + ".show_extra_info", false)) {
            root.addExtra(createdLine);
            root.addExtra(pitchLine);
            root.addExtra(yawLine);
        }

        if (teleportAllowed(player, entry)) {
            TextComponent teleportLine = new TextComponent("\n" + ChatColor.AQUA + LanguagePack.translate("messages.click_teleport") + ChatColor.RESET);
            teleportLine.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + CordsPlugin.PRIMARY_COMMAND + " tp " + entry.name()));
            teleportLine.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new ComponentBuilder(ChatColor.AQUA + LanguagePack.translate("messages.click_teleport_hover")).create()
            ));
            root.addExtra(teleportLine);
        }

        player.spigot().sendMessage(root);
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    private boolean teleportAllowed(Player player, CordEntry entry) {
        return TeleportService.canTeleport(player, entry);
    }

    private String scopePath(boolean publicEntry) {
        return publicEntry ? "labels.public" : "labels.personal";
    }

    private String ownerName(java.util.UUID ownerId) {
        String name = org.bukkit.Bukkit.getOfflinePlayer(ownerId).getName();
        return name == null ? ownerId.toString() : name;
    }
}
