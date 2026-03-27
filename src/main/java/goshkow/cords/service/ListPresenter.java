package goshkow.cords.service;

import goshkow.cords.CordsPlugin;
import goshkow.cords.model.CordEntry;
import goshkow.cords.util.PermissionGate;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class ListPresenter {
    private static final int COLUMNS = 3;
    private static final int ITEMS_PER_PAGE = 9;

    public void showPersonal(Player player) {
        showPersonal(player, 1);
    }

    public void showPersonal(Player player, int page) {
        show(player,
                CordRepository.listPersonalEntries(player.getUniqueId()),
                LanguagePack.translate("messages.personal_section"),
                CordsPlugin.getAccentColor(),
                "/" + CordsPlugin.PRIMARY_COMMAND + " list private",
                page);
    }

    public void showOwned(Player player) {
        showOwned(player, 1);
    }

    public void showOwned(Player player, int page) {
        showVisibleMixed(player,
                CordRepository.listOwnedEntries(player.getUniqueId()),
                LanguagePack.translate("messages.owned_section"),
                "/" + CordsPlugin.PRIMARY_COMMAND + " list owned",
                page);
    }

    public void showPublic(Player player) {
        showPublic(player, 1);
    }

    public void showPublic(Player player, int page) {
        show(player,
                CordRepository.listPublicEntries(),
                LanguagePack.translate("messages.public_section"),
                ChatColor.GREEN.toString(),
                "/" + CordsPlugin.PRIMARY_COMMAND + " list open",
                page);
    }

    public void showVisible(Player player) {
        showVisible(player, 1);
    }

    public void showVisible(Player player, int page) {
        showVisibleMixed(player,
                CordRepository.listVisibleEntries(player.getUniqueId()),
                LanguagePack.translate("messages.visible_section"),
                "/" + CordsPlugin.PRIMARY_COMMAND + " list all",
                page);
    }

    public void showEntries(Player player, List<CordEntry> entries, String title, int page, String baseCommand) {
        show(player, entries, title, ChatColor.WHITE.toString(), baseCommand, page);
    }

    private void showVisibleMixed(Player player, List<CordEntry> entries, String title, String baseCommand, int requestedPage) {
        if (!PermissionGate.has(player, "cords.list")) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }

        if (entries.isEmpty()) {
            player.spigot().sendMessage(new TextComponent(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_cords")));
            if (CordsPlugin.isSoundsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            }
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIndex = (page - 1) * ITEMS_PER_PAGE;
        int toIndex = Math.min(entries.size(), fromIndex + ITEMS_PER_PAGE);
        List<CordEntry> pageEntries = new ArrayList<>(entries.subList(fromIndex, toIndex));

        TextComponent root = new TextComponent(CordsPlugin.getPrefix());
        appendMixedSection(root, title, pageEntries);
        root.addExtra(createPageControls(baseCommand, page, totalPages));

        player.spigot().sendMessage(root);
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    private void show(Player player, List<CordEntry> entries, String title, String entryColor, String baseCommand, int requestedPage) {
        if (!PermissionGate.has(player, "cords.list")) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }

        if (entries.isEmpty()) {
            player.spigot().sendMessage(new TextComponent(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_cords")));
            if (CordsPlugin.isSoundsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            }
            return;
        }

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) ITEMS_PER_PAGE));
        int page = Math.max(1, Math.min(requestedPage, totalPages));
        int fromIndex = (page - 1) * ITEMS_PER_PAGE;
        int toIndex = Math.min(entries.size(), fromIndex + ITEMS_PER_PAGE);
        List<CordEntry> pageEntries = new ArrayList<>(entries.subList(fromIndex, toIndex));

        TextComponent root = new TextComponent(CordsPlugin.getPrefix());
        appendSection(root, title, pageEntries, entryColor);
        root.addExtra(createPageControls(baseCommand, page, totalPages));

        player.spigot().sendMessage(root);
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
    }

    private void appendSection(TextComponent root, String title, List<CordEntry> entries, String entryColor) {
        if (entries.isEmpty()) {
            return;
        }

        root.addExtra(new TextComponent(entryColor + title + ChatColor.RESET));
        for (int index = 0; index < entries.size(); index++) {
            if (index % COLUMNS == 0) {
                root.addExtra(new TextComponent("\n  "));
            } else {
                root.addExtra(new TextComponent("   "));
            }

            root.addExtra(createItem(entries.get(index)));
        }
    }

    private void appendMixedSection(TextComponent root, String title, List<CordEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        root.addExtra(new TextComponent(CordsPlugin.getAccentColor() + title + ChatColor.RESET));
        for (int index = 0; index < entries.size(); index++) {
            if (index % COLUMNS == 0) {
                root.addExtra(new TextComponent("\n  "));
            } else {
                root.addExtra(new TextComponent("   "));
            }

            CordEntry entry = entries.get(index);
            String color = entry.publicEntry() ? ChatColor.GREEN.toString() : ChatColor.AQUA.toString();
            root.addExtra(createItem(entry, color));
        }
    }

    private TextComponent createItem(CordEntry entry) {
        return createItem(entry, ChatColor.WHITE.toString());
    }

    private TextComponent createItem(CordEntry entry, String color) {
        TextComponent item = new TextComponent(color + entry.name() + ChatColor.RESET);
        item.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/" + CordsPlugin.PRIMARY_COMMAND + " view " + entry.name()));
        item.setHoverEvent(new HoverEvent(
                HoverEvent.Action.SHOW_TEXT,
                new ComponentBuilder(ChatColor.WHITE + LanguagePack.translate("messages.hover_info")).create()
        ));
        return item;
    }

    private TextComponent createPageControls(String baseCommand, int page, int totalPages) {
        if (totalPages <= 1) {
            return new TextComponent();
        }

        TextComponent controls = new TextComponent();
        controls.addExtra(new TextComponent("\n"));
        controls.addExtra(createPageButton(
                page > 1,
                LanguagePack.translate("messages.previous_page"),
                baseCommand + " " + (page - 1),
                ChatColor.GRAY,
                ChatColor.DARK_GRAY
        ));
        controls.addExtra(new TextComponent(ChatColor.GRAY + " " + page + "/" + totalPages + " "));
        controls.addExtra(createPageButton(
                page < totalPages,
                LanguagePack.translate("messages.next_page"),
                baseCommand + " " + (page + 1),
                ChatColor.GRAY,
                ChatColor.DARK_GRAY
        ));
        return controls;
    }

    private TextComponent createPageButton(boolean active, String label, String command, ChatColor activeColor, ChatColor inactiveColor) {
        TextComponent button = new TextComponent((active ? activeColor : inactiveColor) + "[" + label + "]" + ChatColor.RESET);
        if (active) {
            button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
            button.setHoverEvent(new HoverEvent(
                    HoverEvent.Action.SHOW_TEXT,
                    new net.md_5.bungee.api.chat.ComponentBuilder(ChatColor.GRAY + label).create()
            ));
        }
        return button;
    }
}
