package goshkow.cords.command;

import goshkow.cords.CordsPlugin;
import goshkow.cords.model.CordEntry;
import goshkow.cords.service.CordRepository;
import goshkow.cords.service.ConfirmationService;
import goshkow.cords.service.InfoPresenter;
import goshkow.cords.service.LanguagePack;
import goshkow.cords.service.ListPresenter;
import goshkow.cords.service.TeleportService;
import goshkow.cords.util.PermissionGate;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CordCommand implements TabExecutor {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LanguagePack.translate("messages.player_only"));
            return true;
        }

        if (args.length == 0) {
            handleRoot(player, label);
            return true;
        }

        String rootAction = resolveCommandWord(args[0],
                "list", "reload", "add", "open", "tp", "remove", "edit", "search", "info", "view");
        if (rootAction == null) {
            sendRootHelp(player, label);
            return true;
        }

        switch (rootAction) {
            case "list" -> {
                int page = 1;
                String mode = args.length >= 2 ? resolveListModeToken(args[1]) : resolveListModeForPlayer(player);
                if (mode == null) {
                    sendListUsage(player, label);
                    return true;
                }
                if (!canListMode(player, mode)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                    return true;
                }
                int pageArgIndex = args.length >= 3 ? 2 : -1;
                if (pageArgIndex != -1) {
                    Integer parsedPage = parsePage(args[pageArgIndex]);
                    if (parsedPage == null) {
                        sendListUsage(player, label);
                        return true;
                    }
                    page = parsedPage;
                }

                dispatchList(player, label, mode, page);
            }
            case "reload" -> {
                if (!canReload(player)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                    return true;
                }
                CordsPlugin.getInstance().reloadPluginState();
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.reloaded"));
            }
            case "add" -> {
                if (args.length < 2) {
                    if (!canCreatePersonal(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    sendUsage(player, label, false, "add", usageName());
                    return true;
                }
                CordRepository.create(player, player.getLocation(), args[1]);
            }
            case "open" -> {
                if (args.length < 2) {
                    if (!canCreatePublic(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    sendUsage(player, label, false, "open", usageName());
                    return true;
                }
                CordEntry ownedPersonal = CordRepository.findOwnedMarker(player.getUniqueId(), args[1]);
                if (ownedPersonal != null && !ownedPersonal.publicEntry()) {
                    ConfirmationService.request(player,
                            "open_promote",
                            LanguagePack.translate("messages.action_promote_public"),
                            () -> CordRepository.createPublic(player, player.getLocation(), args[1]));
                    return true;
                }
                CordRepository.createPublic(player, player.getLocation(), args[1]);
            }
            case "tp" -> {
                if (args.length < 2) {
                    if (!canTeleportAny(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    sendUsage(player, label, false, "tp", usageName());
                    return true;
                }
                CordEntry entry = CordRepository.findVisible(player.getUniqueId(), args[1]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return true;
                }
                TeleportService.teleport(player, entry);
            }
            case "remove" -> {
                if (args.length < 2) {
                    if (!canRemoveOwn(player) && !canRemoveOthers(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    if (canRemoveOthers(player)) {
                        sendUsage(player, label, false, "remove", usagePlayer(), usageName());
                    } else {
                        sendUsage(player, label, false, "remove", usageName());
                    }
                    return true;
                }
                CordEntry entry;
                if (args.length >= 3 && canRemoveOthers(player)) {
                    UUID ownerId = resolvePlayerId(args[1]);
                    if (ownerId == null) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                        return true;
                    }
                    entry = CordRepository.findOwnedMarker(ownerId, args[2]);
                } else if (canRemoveOthers(player) && resolvePlayerId(args[1]) != null) {
                    sendUsage(player, label, false, "remove", usagePlayer(), usageName());
                    return true;
                } else {
                    entry = CordRepository.findVisible(player.getUniqueId(), args[1]);
                }
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return true;
                }
                CordEntry target = entry;
                ConfirmationService.request(player,
                        "remove",
                        LanguagePack.translate("messages.action_remove"),
                        () -> CordRepository.remove(player, target));
            }
            case "edit" -> {
                handleEdit(player, label, args);
            }
            case "search" -> {
                handleSearch(player, label, args);
            }
            case "info" -> {
                if (args.length < 2) {
                    if (!canUseInfo(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    sendUsage(player, label, true, "info", usageName());
                    return true;
                }
                CordEntry entry = CordRepository.findVisible(player.getUniqueId(), args[1]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return true;
                }
                new InfoPresenter().show(player, entry);
            }
            case "view" -> {
                if (args.length < 2) {
                    if (!canUseInfo(player)) {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                        return true;
                    }
                    sendUsage(player, label, false, "info", usageName());
                    return true;
                }
                CordEntry entry = CordRepository.findVisible(player.getUniqueId(), args[1]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return true;
                }
                new InfoPresenter().showFromList(player, entry);
            }
            default -> player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }

        if (args.length == 1) {
            ArrayList<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], rootArgumentsFor(player), completions);
            return completions;
        }

        String rootAction = resolveCommandWord(args[0],
                "list", "reload", "add", "open", "tp", "remove", "edit", "search", "info", "view");
        if (rootAction == null) {
            return List.of();
        }

        if (args.length == 2 && "list".equals(rootAction)) {
            List<String> availableModes = availableListModes(player);
            if (availableModes.size() <= 1) {
                return List.of();
            }
            ArrayList<String> completions = new ArrayList<>();
            ArrayList<String> displayModes = new ArrayList<>();
            for (String mode : availableModes) {
                displayModes.add(displayCommandWord(mode));
            }
            StringUtil.copyPartialMatches(args[1], displayModes, completions);
            return completions;
        }

        if (args.length == 2 && "search".equals(rootAction)) {
            ArrayList<String> completions = new ArrayList<>();
            ArrayList<String> modes = new ArrayList<>();
            if (canSearchName(player)) {
                modes.add(displayCommandWord("name"));
            }
            if (canSearchTag(player)) {
                modes.add(displayCommandWord("tag"));
            }
            StringUtil.copyPartialMatches(args[1], modes, completions);
            return completions;
        }

        if (args.length == 2 && ("tp".equals(rootAction) || "remove".equals(rootAction) || "info".equals(rootAction))) {
            ArrayList<String> completions = new ArrayList<>();
            if ("tp".equals(rootAction)) {
                boolean teleportAllowed = canTeleportAny(player);
                if (!teleportAllowed) {
                    return List.of();
                }
            }
            if ("remove".equals(rootAction) && canRemoveOthers(player)) {
                StringUtil.copyPartialMatches(args[1], getKnownPlayerNames(), completions);
            } else {
                StringUtil.copyPartialMatches(args[1], CordRepository.listOwned(player.getUniqueId()), completions);
            }
            return completions;
        }

        if (args.length == 3 && "remove".equals(rootAction) && canRemoveOthers(player)) {
            UUID ownerId = resolvePlayerId(args[1]);
            if (ownerId == null) {
                return List.of();
            }
            ArrayList<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[2], CordRepository.listOwned(ownerId), completions);
            return completions;
        }

        String searchMode = args.length >= 2 ? resolveCommandWord(args[1], "name", "tag") : null;
        if (args.length >= 3 && "search".equals(rootAction) && "tag".equals(searchMode)) {
            if (!CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true)) {
                return List.of();
            }

            int completionIndex = args.length - 1;
            if (args.length > 3 && parsePage(args[completionIndex]) != null) {
                completionIndex--;
            }
            if (completionIndex < 2) {
                return List.of();
            }

            ArrayList<String> usedTags = new ArrayList<>();
            for (int index = 2; index < completionIndex; index++) {
                usedTags.add(args[index].toLowerCase(java.util.Locale.ROOT));
            }

            ArrayList<String> completions = new ArrayList<>();
            ArrayList<String> availableTags = new ArrayList<>();
            for (String tag : CordRepository.allowedPublicTags()) {
                if (!usedTags.contains(tag.toLowerCase(java.util.Locale.ROOT))) {
                    availableTags.add(tag);
                }
            }
            StringUtil.copyPartialMatches(args[completionIndex], availableTags, completions);
            return completions;
        }

        if ("edit".equals(rootAction)) {
            boolean tagsEnabled = CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true);
            boolean adminMode = args.length >= 2 && resolvePlayerId(args[1]) != null && canEditAnythingOnOthers(player);
            String localAction = args.length >= 2 ? resolveCommandWord(args[1], "name", "move", "tag") : null;

            if (args.length == 2) {
                ArrayList<String> completions = new ArrayList<>();
                ArrayList<String> actions = new ArrayList<>();
                if (canEditName(player, false)) {
                    actions.add(displayCommandWord("name"));
                }
                if (canEditMove(player, false)) {
                    actions.add(displayCommandWord("move"));
                }
                if (tagsEnabled && canEditTags(player, false)) {
                    actions.add(displayCommandWord("tag"));
                }
                if (canEditAnythingOnOthers(player)) {
                    actions.addAll(getKnownPlayerNames());
                }
                StringUtil.copyPartialMatches(args[1], actions, completions);
                return completions;
            }

            if (adminMode) {
                String adminAction = args.length >= 3 ? resolveCommandWord(args[2], "name", "move", "tag") : null;
                if (args.length == 3) {
                    ArrayList<String> completions = new ArrayList<>();
                    ArrayList<String> actions = new ArrayList<>();
                    if (canEditName(player, true)) {
                        actions.add(displayCommandWord("name"));
                    }
                    if (canEditMove(player, true)) {
                        actions.add(displayCommandWord("move"));
                    }
                    if (tagsEnabled && canEditTags(player, true)) {
                        actions.add(displayCommandWord("tag"));
                    }
                    StringUtil.copyPartialMatches(args[2], actions, completions);
                    return completions;
                }

                if (args.length == 4 && ("name".equals(adminAction) || "move".equals(adminAction) || "tag".equals(adminAction))) {
                    ArrayList<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[3], CordRepository.listOwned(resolvePlayerId(args[1])), completions);
                    return completions;
                }

                String adminTagAction = args.length >= 4 ? resolveCommandWord(args[3], "add", "remove") : null;
                if (args.length == 5 && "tag".equals(adminAction)) {
                    ArrayList<String> completions = new ArrayList<>();
                    ArrayList<String> actions = new ArrayList<>();
                    actions.add(displayCommandWord("add"));
                    actions.add(displayCommandWord("remove"));
                    StringUtil.copyPartialMatches(args[4], actions, completions);
                    return completions;
                }
                if (args.length >= 6 && "tag".equals(adminAction) && ("add".equals(adminTagAction) || "remove".equals(adminTagAction))) {
                    if (!tagsEnabled) {
                        return List.of();
                    }
                    UUID ownerId = resolvePlayerId(args[1]);
                    if (ownerId == null) {
                        return List.of();
                    }
                    CordEntry entry = CordRepository.findOwnedMarker(ownerId, args[3]);
                    if (entry == null) {
                        return List.of();
                    }
                    ArrayList<String> completions = new ArrayList<>();
                    List<String> source = "remove".equals(adminTagAction) ? entry.tags() : CordRepository.allowedPublicTags();
                    StringUtil.copyPartialMatches(args[args.length - 1], source, completions);
                    return completions;
                }
            } else {
                if (args.length == 3 && ("name".equals(localAction) || "move".equals(localAction) || "tag".equals(localAction))) {
                    ArrayList<String> completions = new ArrayList<>();
                    StringUtil.copyPartialMatches(args[2], CordRepository.listOwned(player.getUniqueId()), completions);
                    return completions;
                }

                String localTagAction = args.length >= 3 ? resolveCommandWord(args[2], "add", "remove") : null;
                if (args.length == 4 && "tag".equals(localAction)) {
                    ArrayList<String> completions = new ArrayList<>();
                    ArrayList<String> actions = new ArrayList<>();
                    actions.add(displayCommandWord("add"));
                    actions.add(displayCommandWord("remove"));
                    StringUtil.copyPartialMatches(args[3], actions, completions);
                    return completions;
                }
                if (args.length >= 5 && "tag".equals(localAction) && ("add".equals(localTagAction) || "remove".equals(localTagAction))) {
                    if (!tagsEnabled) {
                        return List.of();
                    }
                    CordEntry entry = CordRepository.findOwnedMarker(player.getUniqueId(), args[2]);
                    if (entry == null) {
                        return List.of();
                    }
                    ArrayList<String> completions = new ArrayList<>();
                    List<String> source = "remove".equals(localTagAction) ? entry.tags() : CordRepository.allowedPublicTags();
                    StringUtil.copyPartialMatches(args[args.length - 1], source, completions);
                    return completions;
                }
            }
        }

        return List.of();
    }

    private List<String> rootArgumentsFor(Player player) {
        ArrayList<String> roots = new ArrayList<>();
        if (canCreatePersonal(player)) {
            roots.add(displayCommandWord("add"));
        }
        if (canCreatePublic(player)) {
            roots.add(displayCommandWord("open"));
        }
        if (canListAny(player)) {
            roots.add(displayCommandWord("list"));
        }
        if (canTeleportAny(player)) {
            roots.add(displayCommandWord("tp"));
        }
        if (canRemoveOwn(player) || canRemoveOthers(player)) {
            roots.add(displayCommandWord("remove"));
        }
        if (canEditAny(player)) {
            roots.add(displayCommandWord("edit"));
        }
        if (canSearchAny(player)) {
            roots.add(displayCommandWord("search"));
        }
        if (canUseInfo(player)) {
            roots.add(displayCommandWord("info"));
        }
        if (canReload(player)) {
            roots.add(displayCommandWord("reload"));
        }
        return roots;
    }

    private void sendUsage(Player player, String commandLabel, boolean includeAuthor, String command, String... args) {
        TextComponent root = new TextComponent(CordsPlugin.getPrefix());
        String label = LanguagePack.translate("messages.usage_label");
        if (label.isBlank()) {
            label = "Usage";
        }

        root.addExtra(new TextComponent(ChatColor.WHITE + label + ChatColor.GRAY + ": "));
        String rootCommand = commandLabel == null || commandLabel.isBlank() ? "cords" : commandLabel;
        root.addExtra(new TextComponent(CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand)));
        if (command != null && !command.isBlank()) {
            root.addExtra(new TextComponent(CordsPlugin.getAccentColor() + " " + displayCommandWord(command)));
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            root.addExtra(new TextComponent(" " + ChatColor.GRAY + arg));
        }

        player.spigot().sendMessage(root);
        if (includeAuthor) {
            player.sendMessage(ChatColor.GRAY + "Author: goshkow");
        }
    }

    private String usageName() {
        String value = LanguagePack.translate("messages.usage_name");
        return value.isBlank() ? "<name>" : value;
    }

    private String usagePlayer() {
        String value = LanguagePack.translate("messages.usage_player");
        return value.isBlank() ? "<player>" : value;
    }

    private String usagePage() {
        return "[page]";
    }

    private boolean localizedCommandsEnabled() {
        if (CordsPlugin.getInstance().getConfig().isBoolean("commands.localization")) {
            return CordsPlugin.getInstance().getConfig().getBoolean("commands.localization");
        }
        return CordsPlugin.getInstance().getConfig().getBoolean("commands.localization.enabled", false);
    }

    private String displayCommandWord(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        if (!localizedCommandsEnabled()) {
            return raw;
        }

        String translated = LanguagePack.translate("commands." + raw.toLowerCase(java.util.Locale.ROOT));
        return translated.isBlank() ? raw : translated;
    }

    private boolean commandWordMatches(String input, String raw) {
        if (input == null || raw == null) {
            return false;
        }

        String normalized = input.trim().toLowerCase(java.util.Locale.ROOT);
        String canonical = raw.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.equals(canonical)) {
            return true;
        }

        if (!localizedCommandsEnabled()) {
            return false;
        }

        String translated = displayCommandWord(raw);
        return !translated.isBlank() && normalized.equals(translated.trim().toLowerCase(java.util.Locale.ROOT));
    }

    private String resolveCommandWord(String input, String... options) {
        for (String option : options) {
            if (commandWordMatches(input, option)) {
                return option;
            }
        }
        return null;
    }

    private String resolveRootDefaultAction() {
        String configured = CordsPlugin.getInstance().getConfig().getString("commands.root_default_action", "help");
        if (configured == null) {
            return "help";
        }

        String normalized = configured.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "help", "list" -> normalized;
            default -> "help";
        };
    }

    private void handleRoot(Player player, String label) {
        String rootDefaultAction = resolveRootDefaultAction();
        if ("list".equals(rootDefaultAction)) {
            String defaultListMode = resolveListModeForPlayer(player);
            if (defaultListMode != null) {
                dispatchList(player, label, defaultListMode, 1);
                return;
            }
        }

        sendRootHelp(player, label);
    }

    private String usageEditName() {
        return displayCommandWord("name");
    }

    private String usageEditTag() {
        return displayCommandWord("tag");
    }

    private String usageEditMove() {
        return displayCommandWord("move");
    }

    private String usageNewName() {
        String value = LanguagePack.translate("messages.usage_new_name");
        return value.isBlank() ? "<new_name>" : value;
    }

    private void dispatchList(Player player, String label, String mode, int page) {
        if (mode == null) {
            sendListUsage(player, label);
            return;
        }

        switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "private" -> new ListPresenter().showPersonal(player, page);
            case "owned" -> new ListPresenter().showOwned(player, page);
            case "open" -> new ListPresenter().showPublic(player, page);
            case "all" -> new ListPresenter().showVisible(player, page);
            default -> sendListUsage(player, label);
        }
    }

    private String resolveListModeForPlayer(Player player) {
        List<String> modes = availableListModes(player);
        if (modes.isEmpty()) {
            return null;
        }
        String configured = CordsPlugin.getInstance().getConfig().getString("lists.default_mode", "owned");
        if (configured == null) {
            return modes.size() == 1 ? modes.get(0) : null;
        }

        String normalized = configured.trim().toLowerCase(java.util.Locale.ROOT);
        if ("usage".equals(normalized)) {
            return modes.size() == 1 ? modes.get(0) : null;
        }

        if (modes.contains(normalized)) {
            return normalized;
        }

        if ("private".equals(normalized) && modes.contains("owned")) {
            return "owned";
        }
        if ("public".equals(normalized) && modes.contains("open")) {
            return "open";
        }
        if ("open".equals(normalized) && modes.contains("open")) {
            return "open";
        }
        if ("all".equals(normalized) && modes.contains("all")) {
            return "all";
        }

        return modes.size() == 1 ? modes.get(0) : null;
    }

    private String resolveListModeToken(String input) {
        return resolveCommandWord(input, "private", "owned", "open", "all");
    }

    private String usageSearchMode() {
        String value = LanguagePack.translate("messages.usage_search_mode");
        return value.isBlank() ? "<name/tag>" : value;
    }

    private String usageTags() {
        String value = LanguagePack.translate("messages.usage_tags");
        return value.isBlank() ? "<tag1> <tag2> ..." : value;
    }

    private void handleEdit(Player player, String label, String[] args) {
        boolean adminMode = args.length >= 2 && resolvePlayerId(args[1]) != null && canEditAnythingOnOthers(player);
        int actionIndex = adminMode ? 2 : 1;
        List<String> availableActions = availableEditActions(player, adminMode);

        if (args.length <= actionIndex) {
            if (!canEditAny(player)) {
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                return;
            }
            if (availableActions.size() == 1) {
                String[] implicitArgs = injectArgument(args, actionIndex, availableActions.get(0));
                handleEdit(player, label, implicitArgs);
                return;
            }
            sendEditUsage(player, label, adminMode);
            return;
        }

        UUID ownerId = adminMode ? resolvePlayerId(args[1]) : player.getUniqueId();
        if (ownerId == null) {
            sendEditUsage(player, label, adminMode);
            return;
        }

        String sub = resolveCommandWord(args[actionIndex], "name", "move", "tag");
        if (sub == null && availableActions.size() == 1) {
            String[] implicitArgs = injectArgument(args, actionIndex, availableActions.get(0));
            handleEdit(player, label, implicitArgs);
            return;
        }
        switch (sub) {
            case "name" -> {
                if (adminMode ? !canEditName(player, true) : !canEditName(player, false)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                    return;
                }
                int oldNameIndex = actionIndex + 1;
                int newNameIndex = actionIndex + 2;
                if (args.length <= newNameIndex) {
                    sendEditNameUsage(player, label, adminMode);
                    return;
                }

                CordEntry entry = adminMode
                        ? CordRepository.findOwnedMarker(ownerId, args[oldNameIndex])
                        : CordRepository.findVisible(player.getUniqueId(), args[oldNameIndex]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return;
                }

                String newName = args[newNameIndex];
                ConfirmationService.request(player,
                        "rename",
                        LanguagePack.translate("messages.action_rename"),
                        () -> CordRepository.rename(player, entry, newName));
            }
            case "move" -> {
                if (adminMode ? !canEditMove(player, true) : !canEditMove(player, false)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                    return;
                }
                int markerNameIndex = actionIndex + 1;
                if (args.length <= markerNameIndex) {
                    if (adminMode) {
                        sendUsage(player, label, false, "edit", usagePlayer(), usageEditMove(), usageName());
                    } else {
                        sendUsage(player, label, false, "edit", usageEditMove(), usageName());
                    }
                    return;
                }

                CordEntry entry = adminMode
                        ? CordRepository.findOwnedMarker(ownerId, args[markerNameIndex])
                        : CordRepository.findVisible(player.getUniqueId(), args[markerNameIndex]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return;
                }

                ConfirmationService.request(player,
                        "move",
                        LanguagePack.translate("messages.action_move"),
                        () -> CordRepository.move(player, entry, player.getLocation()));
            }
            case "tag" -> {
                if (!CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.tags_disabled"));
                    return;
                }
                if (adminMode ? !canEditTags(player, true) : !canEditTags(player, false)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                    return;
                }
                int markerNameIndex = actionIndex + 1;
                int tagActionIndex = actionIndex + 2;
                if (args.length <= tagActionIndex) {
                    sendEditTagUsage(player, label, adminMode);
                    return;
                }

                String tagAction = resolveCommandWord(args[tagActionIndex], "add", "remove");
                CordEntry entry = adminMode
                        ? CordRepository.findOwnedMarker(ownerId, args[markerNameIndex])
                        : CordRepository.findVisible(player.getUniqueId(), args[markerNameIndex]);
                if (entry == null) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.not_found"));
                    return;
                }

                List<String> values = new ArrayList<>();
                for (int index = tagActionIndex + 1; index < args.length; index++) {
                    values.add(args[index]);
                }
                if ("add".equals(tagAction)) {
                    ConfirmationService.request(player,
                            "tag_add",
                            LanguagePack.translate("messages.action_tag_add"),
                            () -> CordRepository.addTags(player, entry, values));
                } else if ("remove".equals(tagAction)) {
                    ConfirmationService.request(player,
                            "tag_remove",
                            LanguagePack.translate("messages.action_tag_remove"),
                            () -> CordRepository.removeTags(player, entry, values));
                } else {
                    sendEditTagUsage(player, label, adminMode);
                }
            }
            default -> sendEditUsage(player, label, adminMode);
        }
    }

    private void sendEditUsage(Player player, String label, boolean adminMode) {
        sendUsage(player, label, false, "edit", usageEditModeSummary(player, adminMode));
        if (availableEditActions(player, adminMode).contains("name")) {
            sendCommandLine(player, label, "edit", adminMode ? new String[]{usagePlayer(), usageEditName(), usageName(), usageNewName()} : new String[]{usageEditName(), usageName(), usageNewName()},
                    helpText("messages.help_edit_name", "messages.help_edit_name_own"));
        }
        if (availableEditActions(player, adminMode).contains("move")) {
            sendCommandLine(player, label, "edit", adminMode ? new String[]{usagePlayer(), usageEditMove(), usageName()} : new String[]{usageEditMove(), usageName()},
                    helpText("messages.help_edit_move", "messages.help_edit_move_own"));
        }
        if (availableEditActions(player, adminMode).contains("tag")) {
            sendCommandLine(player, label, "edit", adminMode ? new String[]{usagePlayer(), usageEditTag(), usageName(), "<" + displayCommandWord("add") + "/" + displayCommandWord("remove") + ">", usageTags()}
                            : new String[]{usageEditTag(), usageName(), "<" + displayCommandWord("add") + "/" + displayCommandWord("remove") + ">", usageTags()},
                    helpText("messages.help_edit_tag", "messages.help_edit_tag_own"));
        }
    }

    private void sendEditNameUsage(Player player, String label, boolean adminMode) {
        if (adminMode) {
            sendUsage(player, label, false, "edit", usagePlayer(), usageEditName(), usageName(), usageNewName());
        } else {
            sendUsage(player, label, false, "edit", usageEditName(), usageName(), usageNewName());
        }
    }

    private void sendEditTagUsage(Player player, String label, boolean adminMode) {
        if (adminMode) {
            sendUsage(player, label, false, "edit", usagePlayer(), usageEditTag(), usageName(), "<" + displayCommandWord("add") + "/" + displayCommandWord("remove") + ">", usageTags());
        } else {
            sendUsage(player, label, false, "edit", usageEditTag(), usageName(), "<" + displayCommandWord("add") + "/" + displayCommandWord("remove") + ">", usageTags());
        }
    }

    private void sendEditMoveUsage(Player player, String label, boolean adminMode) {
        if (adminMode) {
            sendUsage(player, label, false, "edit", usagePlayer(), usageEditMove(), usageName());
        } else {
            sendUsage(player, label, false, "edit", usageEditMove(), usageName());
        }
    }

    private void sendRootHelp(Player player, String label) {
        String rootCommand = label == null || label.isBlank() ? "cords" : label;
        String prefix = CordsPlugin.getPrefix();
        String title = LanguagePack.translate("messages.help_title");
        if (title.isBlank()) {
            title = "Available commands:";
        }

        player.sendMessage(prefix + ChatColor.WHITE + title);
        if (canCreatePersonal(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("add") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_add"));
        }
        if (canCreatePublic(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("open") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_open"));
        }
        if (canListAny(player)) {
            String listModes = usageListModes(player);
            String listLine = prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("list");
            if (!listModes.isBlank()) {
                listLine += " " + ChatColor.WHITE + listModes;
            }
            player.sendMessage(listLine + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_list"));
        }
        if (canTeleportAny(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("tp") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_tp"));
        }
        if (canRemoveOwn(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("remove") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + helpText("messages.help_remove", "messages.help_remove_own"));
        } else if (canRemoveOthers(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("remove") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + helpText("messages.help_remove", "messages.help_remove_others"));
        }
        if (canEditAny(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("edit")
                    + ChatColor.GRAY + " - " + helpText("messages.help_edit", "messages.help_edit_name"));
        }
        if (canSearchAny(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("search")
                    + ChatColor.GRAY + " - " + helpText("messages.help_search", "messages.help_search_name"));
        }
        if (canUseInfo(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("info") + " " + ChatColor.WHITE + usageName()
                    + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_info"));
        }
        if (canReload(player)) {
            player.sendMessage(prefix + CordsPlugin.getAccentColor() + "/" + displayCommandWord(rootCommand) + " " + displayCommandWord("reload")
                    + ChatColor.GRAY + " - " + LanguagePack.translate("messages.help_reload"));
        }
    }

    private void sendCommandLine(Player player, String commandLabel, String command, String[] args, String description) {
        String rootCommand = commandLabel == null || commandLabel.isBlank() ? "cords" : commandLabel;
        StringBuilder line = new StringBuilder();
        line.append(CordsPlugin.getPrefix())
                .append(CordsPlugin.getAccentColor())
                .append("/")
                .append(displayCommandWord(rootCommand));
        if (command != null && !command.isBlank()) {
            line.append(" ").append(displayCommandWord(command));
        }
        for (String arg : args) {
            if (arg == null || arg.isBlank()) {
                continue;
            }
            line.append(" ").append(ChatColor.WHITE).append(arg);
        }
        if (description != null && !description.isBlank()) {
            line.append(ChatColor.GRAY).append(" - ").append(description);
        }
        player.sendMessage(line.toString());
    }

    private void sendListUsage(Player player, String label) {
        List<String> modes = availableListModes(player);
        sendUsage(player, label, false, "list", usageListModes(player), usagePage());
        for (String mode : modes) {
            sendCommandLine(player, label, "list", new String[]{displayCommandWord(mode), usagePage()}, listDescription(mode));
        }
    }

    private String usageListModes(Player player) {
        ArrayList<String> modes = new ArrayList<>();
        if (canListMode(player, "private")) {
            modes.add("private");
        }
        if (canListMode(player, "owned")) {
            modes.add("owned");
        }
        if (canListMode(player, "open")) {
            modes.add("open");
        }
        if (canListMode(player, "all")) {
            modes.add("all");
        }

        if (modes.size() <= 1) {
            return "";
        }

        ArrayList<String> displayModes = new ArrayList<>();
        for (String mode : modes) {
            displayModes.add(displayCommandWord(mode));
        }
        return "[" + String.join("/", displayModes) + "]";
    }

    private String usageListModes() {
        return "[private/owned/open/all]";
    }

    private List<String> availableListModes(Player player) {
        ArrayList<String> modes = new ArrayList<>();
        boolean personalEnabled = CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.enabled", true);
        boolean publicEnabled = CordsPlugin.getInstance().getConfig().getBoolean("labels.public.enabled", true);

        if (personalEnabled && publicEnabled) {
            if (canListMode(player, "private")) {
                modes.add("private");
            }
            if (canListMode(player, "owned")) {
                modes.add("owned");
            }
            if (canListMode(player, "open")) {
                modes.add("open");
            }
            if (canListMode(player, "all")) {
                modes.add("all");
            }
            return modes;
        }

        if (personalEnabled) {
            if (canListMode(player, "owned")) {
                modes.add("owned");
            } else if (canListMode(player, "private")) {
                modes.add("private");
            }
            return modes;
        }

        if (publicEnabled) {
            if (canListMode(player, "open")) {
                modes.add("open");
            } else if (canListMode(player, "all")) {
                modes.add("all");
            }
        }

        return modes;
    }

    private String usageEditArgs() {
        String value = LanguagePack.translate("messages.usage_edit_args");
        return value.isBlank() ? "[name/tag]" : value;
    }

    private String usageSearchArgs() {
        String value = LanguagePack.translate("messages.usage_search_args");
        return value.isBlank() ? "<name/tag> <query>" : value;
    }

    private String usageSearchMode(Player player) {
        ArrayList<String> modes = new ArrayList<>();
        if (canSearchName(player)) {
            modes.add(displayCommandWord("name"));
        }
        if (canSearchTag(player)) {
            modes.add(displayCommandWord("tag"));
        }

        if (modes.isEmpty()) {
            String value = LanguagePack.translate("messages.usage_search_mode");
            return value.isBlank() ? "<name/tag>" : value;
        }

        if (modes.size() == 1) {
            return modes.get(0);
        }

        return String.join("/", modes);
    }

    private String usageEditModeSummary(Player player, boolean adminMode) {
        ArrayList<String> modes = new ArrayList<>();
        for (String action : availableEditActions(player, adminMode)) {
            modes.add(displayCommandWord(action));
        }
        if (modes.isEmpty()) {
            return displayCommandWord("name") + "/" + displayCommandWord("move") + "/" + displayCommandWord("tag");
        }
        if (modes.size() == 1) {
            return modes.get(0);
        }
        return String.join("/", modes);
    }

    private String usageSearchName() {
        return displayCommandWord("name");
    }

    private String usageSearchTag() {
        return displayCommandWord("tag");
    }

    private List<String> availableEditActions(Player player, boolean adminMode) {
        ArrayList<String> actions = new ArrayList<>();
        if (adminMode ? canEditName(player, true) : canEditName(player, false)) {
            actions.add("name");
        }
        if (adminMode ? canEditMove(player, true) : canEditMove(player, false)) {
            actions.add("move");
        }
        if (adminMode ? canEditTags(player, true) : canEditTags(player, false)) {
            actions.add("tag");
        }
        return actions;
    }

    private String[] injectArgument(String[] args, int index, String value) {
        String[] result = new String[args.length + 1];
        System.arraycopy(args, 0, result, 0, index);
        result[index] = value;
        System.arraycopy(args, index, result, index + 1, args.length - index);
        return result;
    }

    private String helpText(String primaryKey, String fallbackKey) {
        String primary = LanguagePack.translate(primaryKey);
        if (!primary.isBlank()) {
            return primary;
        }
        return LanguagePack.translate(fallbackKey);
    }

    private String listDescription(String mode) {
        return switch (mode) {
            case "private" -> LanguagePack.translate("messages.personal_section");
            case "owned" -> LanguagePack.translate("messages.owned_section");
            case "open" -> LanguagePack.translate("messages.public_section");
            case "all" -> LanguagePack.translate("messages.visible_section");
            default -> "";
        };
    }

    private boolean canCreatePersonal(Player player) {
        return CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.enabled", true)
                && PermissionGate.has(player, "cords.add");
    }

    private boolean canCreatePublic(Player player) {
        return CordsPlugin.getInstance().getConfig().getBoolean("labels.public.enabled", true)
                && PermissionGate.has(player, "cords.open");
    }

    private boolean canUseList(Player player) {
        return PermissionGate.has(player, "cords.list");
    }

    private boolean canListAny(Player player) {
        return !availableListModes(player).isEmpty();
    }

    private boolean canListMode(Player player, String mode) {
        if (!canUseList(player)) {
            return false;
        }

        return switch (mode.toLowerCase(java.util.Locale.ROOT)) {
            case "private" -> CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.enabled", true)
                    && PermissionGate.has(player, "cords.list.private", "cords.list.owned");
            case "owned" -> CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.enabled", true)
                    && PermissionGate.has(player, "cords.list.owned");
            case "open" -> CordsPlugin.getInstance().getConfig().getBoolean("labels.public.enabled", true)
                    && PermissionGate.has(player, "cords.list.open");
            case "all" -> (CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.enabled", true)
                    || CordsPlugin.getInstance().getConfig().getBoolean("labels.public.enabled", true))
                    && PermissionGate.has(player, "cords.list.all");
            default -> false;
        };
    }

    private boolean canTeleportAny(Player player) {
        boolean personalEnabled = CordsPlugin.getInstance().getConfig().getBoolean("labels.personal.teleport_enabled", true);
        boolean publicEnabled = CordsPlugin.getInstance().getConfig().getBoolean("labels.public.teleport_enabled", true);
        return (personalEnabled && PermissionGate.has(player, "cords.teleport.personal"))
                || (publicEnabled && PermissionGate.has(player, "cords.teleport.public"))
                || PermissionGate.has(player, "cords.teleport.owned");
    }

    private boolean canRemoveOwn(Player player) {
        return PermissionGate.has(player, "cords.remove");
    }

    private boolean canRemoveOthers(Player player) {
        return player.isOp() || PermissionGate.has(player, "cords.remove.others");
    }

    private boolean canEditName(Player player, boolean others) {
        return others
                ? player.isOp() || PermissionGate.has(player, "cords.edit.name.others")
                : PermissionGate.has(player, "cords.edit.name");
    }

    private boolean canEditMove(Player player, boolean others) {
        return others
                ? player.isOp() || PermissionGate.has(player, "cords.edit.move.others")
                : PermissionGate.has(player, "cords.edit.move");
    }

    private boolean canEditTags(Player player, boolean others) {
        return CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true)
                && (others
                ? player.isOp() || PermissionGate.has(player, "cords.edit.tag.others")
                : PermissionGate.has(player, "cords.edit.tag"));
    }

    private boolean canEditAnythingOnOthers(Player player) {
        return canEditName(player, true) || canEditMove(player, true) || canEditTags(player, true);
    }

    private boolean canEditAny(Player player) {
        return canEditName(player, false) || canEditMove(player, false) || canEditTags(player, false)
                || canEditAnythingOnOthers(player);
    }

    private boolean canSearchName(Player player) {
        return PermissionGate.has(player, "cords.search.name");
    }

    private boolean canSearchTag(Player player) {
        return CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true)
                && PermissionGate.has(player, "cords.search.tag");
    }

    private boolean canSearchAny(Player player) {
        return canSearchName(player) || canSearchTag(player);
    }

    private boolean canUseInfo(Player player) {
        return PermissionGate.has(player, "cords.info");
    }

    private boolean canReload(Player player) {
        return player.isOp() || PermissionGate.has(player, "cords.reload");
    }

    private void handleSearch(Player player, String label, String[] args) {
        List<String> availableModes = availableSearchModes(player);
        if (args.length < 2) {
            if (!canSearchAny(player)) {
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                return;
            }
            if (availableModes.size() == 1) {
                sendUsage(player, label, false, "search", displayCommandWord(availableModes.get(0)), "name".equals(availableModes.get(0)) ? usageName() : usageTags());
                player.sendMessage(CordsPlugin.getPrefix() + ChatColor.GRAY + ("name".equals(availableModes.get(0))
                        ? LanguagePack.translate("messages.help_search_name")
                        : LanguagePack.translate("messages.help_search_tag")));
                return;
            }
            sendSearchUsage(player, label);
            return;
        }

        String mode = resolveCommandWord(args[1], "name", "tag");
        if (mode == null && availableModes.size() == 1) {
            String[] implicitArgs = injectArgument(args, 1, availableModes.get(0));
            handleSearch(player, label, implicitArgs);
            return;
        }

        if (args.length < 3) {
            if (!canSearchAny(player)) {
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                return;
            }
            sendSearchUsage(player, label);
            return;
        }

        if ("name".equals(mode) && !canSearchName(player)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }
        if ("tag".equals(mode) && !canSearchTag(player)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }
        int page = 1;
        int queryEnd = args.length;
        if ("tag".equals(mode) && args.length >= 4) {
            Integer parsedPage = parsePage(args[args.length - 1]);
            if (parsedPage != null) {
                page = parsedPage;
                queryEnd--;
            }
        } else if (args.length >= 4) {
            Integer parsedPage = parsePage(args[3]);
            if (parsedPage == null) {
                sendUsage(player, label, false, "search", usageSearchMode(player), usageName(), usagePage());
                return;
            }
            page = parsedPage;
        }

        List<CordEntry> results;
        String baseCommand;
        String title;
        if ("name".equals(mode)) {
            String query = args[2];
            results = new ArrayList<>(CordRepository.searchVisibleByName(player.getUniqueId(), query));
            title = LanguagePack.translate("messages.search_name_results").replace("%query%", query);
            baseCommand = "/" + CordsPlugin.PRIMARY_COMMAND + " search name " + query;
        } else if ("tag".equals(mode)) {
            ArrayList<String> queryTags = new ArrayList<>();
            for (int index = 2; index < queryEnd; index++) {
                queryTags.add(args[index]);
            }
            if (queryTags.isEmpty()) {
                sendUsage(player, label, false, "search", usageSearchMode(player), usageTags(), usagePage());
                return;
            }
            for (String tag : queryTags) {
                if (!CordRepository.allowedPublicTags().contains(tag.toLowerCase(java.util.Locale.ROOT))) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.invalid_tag"));
                    return;
                }
            }
            results = new ArrayList<>(CordRepository.searchVisibleByTags(player.getUniqueId(), queryTags));
            String joinedQuery = String.join(" ", queryTags);
            title = LanguagePack.translate("messages.search_tag_results").replace("%query%", joinedQuery);
            baseCommand = "/" + CordsPlugin.PRIMARY_COMMAND + " search tag " + joinedQuery;
        } else {
            sendSearchUsage(player, label);
            return;
        }

        if (results.isEmpty()) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.search_no_results"));
            return;
        }

        new ListPresenter().showEntries(player, results, title, page, baseCommand);
    }

    private Integer parsePage(String value) {
        try {
            int page = Integer.parseInt(value);
            return page > 0 ? page : null;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private void sendSearchUsage(Player player, String label) {
        sendUsage(player, label, false, "search", usageSearchMode(player));
        if (canSearchName(player)) {
            sendCommandLine(player, label, "search", new String[]{usageSearchName(), usageName()}, LanguagePack.translate("messages.help_search_name"));
        }
        if (canSearchTag(player)) {
            sendCommandLine(player, label, "search", new String[]{usageSearchTag(), usageTags()}, LanguagePack.translate("messages.help_search_tag"));
        }
    }

    private List<String> availableSearchModes(Player player) {
        ArrayList<String> modes = new ArrayList<>();
        if (canSearchName(player)) {
            modes.add("name");
        }
        if (canSearchTag(player)) {
            modes.add("tag");
        }
        return modes;
    }

    private UUID resolvePlayerId(String inputName) {
        if (inputName == null || inputName.isBlank()) {
            return null;
        }

        Player exactOnline = Bukkit.getPlayerExact(inputName);
        if (exactOnline != null) {
            return exactOnline.getUniqueId();
        }

        String lookup = inputName.trim();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && name.equalsIgnoreCase(lookup)) {
                return offlinePlayer.getUniqueId();
            }
        }

        return null;
    }

    private List<String> getKnownPlayerNames() {
        ArrayList<String> names = new ArrayList<>();
        for (OfflinePlayer offlinePlayer : Bukkit.getOfflinePlayers()) {
            String name = offlinePlayer.getName();
            if (name != null && !name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }
}
