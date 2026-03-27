package goshkow.cords;

import com.tchristofferson.configupdater.ConfigUpdater;
import goshkow.cords.command.CordCommand;
import goshkow.cords.integration.CombatLogBridge;
import goshkow.cords.integration.MapIntegrationService;
import goshkow.cords.integration.VaultBridge;
import goshkow.cords.service.CordRepository;
import goshkow.cords.service.ConfirmationService;
import goshkow.cords.service.LanguagePack;
import goshkow.cords.service.TeleportService;
import goshkow.cords.service.UpdateService;
import goshkow.cords.util.ColorUtil;
import goshkow.cords.util.CordLogger;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CordsPlugin extends JavaPlugin implements Listener {
    public static final String PRIMARY_COMMAND = "pcords";
    private static CordsPlugin instance;

    private final CordLogger logger = new CordLogger("PointCoordinates");
    private long startupTime;
    private CordCommand commandExecutor;
    private final Set<String> registeredAliases = new HashSet<>();

    private static String prefix = "";
    private static String accentColor = "";
    private static boolean debugEnabled;
    private static boolean soundsEnabled;
    private static boolean vaultHooked;
    private static boolean combatHooked;

    @Override
    public void onEnable() {
        instance = this;
        startupTime = System.currentTimeMillis();

        prepareFiles();
        refreshRuntimeState();
        CordRepository.loadAll();
        reloadMapIntegrations();

        PluginCommand command = getCommand(PRIMARY_COMMAND);
        if (command != null) {
            commandExecutor = new CordCommand();
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }
        refreshAliasCommands();

        Bukkit.getPluginManager().registerEvents(this, this);
        refreshIntegrations();
        UpdateService.checkAsync();

        logger.info(LanguagePack.translate("messages.ready") + " (" + (System.currentTimeMillis() - startupTime) + "ms)");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(this, () -> UpdateService.notifyIfNeeded(event.getPlayer()), 40L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getTo() == null) {
            return;
        }

        UUID playerId = event.getPlayer().getUniqueId();
        if (TeleportService.consumeIgnoreFlag(playerId)) {
            return;
        }
        if (!TeleportService.isTeleporting(playerId) || TeleportService.hasRecentlyTeleported(playerId)) {
            return;
        }

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        TeleportService.sendFeedback(event.getPlayer(), LanguagePack.translate("messages.moved"));
        TeleportService.cancelTeleport(playerId);
        if (soundsEnabled) {
            event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        TeleportService.cancelTeleport(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (ConfirmationService.handleChat(event.getPlayer(), event.getMessage())) {
            event.setCancelled(true);
        }
    }

    @Override
    public void onDisable() {
        logger.info(LanguagePack.translate("messages.shutdown_start"));
        ConfirmationService.clearAll();
        unregisterAliasCommands();
        shutdownMapIntegrations();
        CordRepository.saveAll();
        logger.info(LanguagePack.translate("messages.shutdown_complete"));
    }

    public void reloadPluginState() {
        updateBundledFiles();
        reloadConfig();
        refreshRuntimeState();
        LanguagePack.reload();
        ConfirmationService.clearAll();
        CordRepository.reload();
        reloadMapIntegrations();
        refreshAliasCommands();
        refreshIntegrations();
        UpdateService.checkAsync();
    }

    private void prepareFiles() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        saveDefaultConfig();
        saveResource("languages/en_us.yml", false);

        updateBundledFiles();

        CordStorage.init(this);
        CordStorage.backup();
        reloadConfig();
    }

    private void updateBundledFiles() {
        try {
            ConfigUpdater.update(this, "config.yml", new File(getDataFolder(), "config.yml"));
            ConfigUpdater.update(this, "languages/en_us.yml", new File(getDataFolder(), "languages/en_us.yml"));
        } catch (IOException exception) {
            getLogger().severe("Failed to update bundled configuration files.");
            exception.printStackTrace();
        }
    }

    private void refreshRuntimeState() {
        debugEnabled = getConfig().getBoolean("debug", false);
        soundsEnabled = getConfig().getBoolean("sounds", true);
        accentColor = ColorUtil.colorize(getConfig().getString("style.accent_color", "&#4B86B5"));
        String configuredPrefix = getConfig().getString("prefix", "%accent%PCords &8> &f");
        if ("&9PCords &8> &f".equals(configuredPrefix) || "&9PointCoordinates &8> &f".equals(configuredPrefix)) {
            configuredPrefix = "%accent%PCords &8> &f";
        }
        prefix = ColorUtil.colorize(configuredPrefix.replace("%accent%", accentColor));
        LanguagePack.load(getConfig().getString("language", "en_us"));
    }

    private void refreshIntegrations() {
        vaultHooked = false;
        combatHooked = false;

        if (getConfig().getBoolean("integrations.vault.enabled", false)) {
            new VaultBridge().hook();
        }
        if (getConfig().getBoolean("integrations.combatlogx.enabled", false)) {
            new CombatLogBridge().hook();
        }
    }

    private void refreshAliasCommands() {
        unregisterAliasCommands();
        registerAliasCommands();
    }

    private void registerAliasCommands() {
        if (commandExecutor == null) {
            return;
        }

        PluginCommand baseCommand = getCommand(PRIMARY_COMMAND);
        if (baseCommand == null) {
            return;
        }

        if (isAliasEnabled("cords")) {
            registerAliasCommand("cords", baseCommand);
        }
        if (isAliasEnabled("pc")) {
            registerAliasCommand("pc", baseCommand);
        }
        if (isAliasEnabled("pt")) {
            registerAliasCommand("pt", baseCommand);
        }
    }

    private boolean isAliasEnabled(String alias) {
        String flatPath = "commands.aliases." + alias;
        if (getConfig().isBoolean(flatPath)) {
            return getConfig().getBoolean(flatPath);
        }
        return getConfig().getBoolean(flatPath + ".enabled", true);
    }

    private void registerAliasCommand(String alias, PluginCommand baseCommand) {
        CommandMap commandMap = getCommandMap();
        if (commandMap == null) {
            return;
        }

        String normalizedAlias = alias.toLowerCase(Locale.ROOT);
        if (registeredAliases.contains(normalizedAlias)) {
            return;
        }

        Command aliasCommand = new Command(normalizedAlias) {
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                return commandExecutor.onCommand(sender, baseCommand, label, args);
            }

            @Override
            public List<String> tabComplete(CommandSender sender, String aliasName, String[] args) {
                List<String> completions = commandExecutor.onTabComplete(sender, baseCommand, aliasName, args);
                return completions == null ? Collections.emptyList() : completions;
            }
        };
        aliasCommand.setDescription("Alias for /cords");
        aliasCommand.setUsage("/" + alias);
        if (baseCommand.getPermission() != null) {
            aliasCommand.setPermission(baseCommand.getPermission());
        }
        commandMap.register(getName().toLowerCase(Locale.ROOT), aliasCommand);
        registeredAliases.add(normalizedAlias);
    }

    private void unregisterAliasCommands() {
        if (registeredAliases.isEmpty()) {
            return;
        }

        CommandMap commandMap = getCommandMap();
        Map<String, Command> knownCommands = getKnownCommands(commandMap);
        if (knownCommands == null) {
            registeredAliases.clear();
            return;
        }

        String prefix = getName().toLowerCase(Locale.ROOT) + ":";
        for (String alias : new ArrayList<>(registeredAliases)) {
            Command command = knownCommands.remove(alias);
            if (command != null) {
                command.unregister(commandMap);
            }
            Command namespaced = knownCommands.remove(prefix + alias);
            if (namespaced != null) {
                namespaced.unregister(commandMap);
            }
            registeredAliases.remove(alias);
        }
    }

    private CommandMap getCommandMap() {
        try {
            Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(Bukkit.getServer());
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Unable to access the server command map.");
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap commandMap) {
        if (commandMap == null) {
            return null;
        }

        try {
            Field field = commandMap.getClass().getDeclaredField("knownCommands");
            field.setAccessible(true);
            return (Map<String, Command>) field.get(commandMap);
        } catch (ReflectiveOperationException exception) {
            getLogger().warning("Unable to access registered commands.");
            return null;
        }
    }

    public static CordsPlugin getInstance() {
        return instance;
    }

    public static String getPrefix() {
        return prefix;
    }

    public static String getAccentColor() {
        return accentColor;
    }

    public static boolean isDebugEnabled() {
        return debugEnabled;
    }

    public static boolean isSoundsEnabled() {
        return soundsEnabled;
    }

    public static boolean isVaultHooked() {
        return vaultHooked;
    }

    public static void setVaultHooked(boolean value) {
        vaultHooked = value;
    }

    public static boolean isCombatHooked() {
        return combatHooked;
    }

    public static void setCombatHooked(boolean value) {
        combatHooked = value;
    }

    public static void syncMapIntegrations() {
        if (!isMapIntegrationEnabled()) {
            return;
        }
        try {
            MapIntegrationService.sync();
        } catch (Throwable ignored) {
        }
    }

    public static void reloadMapIntegrations() {
        try {
            MapIntegrationService.shutdown();
        } catch (Throwable ignored) {
        }
        if (!isMapIntegrationEnabled()) {
            return;
        }
        try {
            MapIntegrationService.reload();
        } catch (Throwable ignored) {
        }
    }

    public static void shutdownMapIntegrations() {
        try {
            MapIntegrationService.shutdown();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isMapIntegrationEnabled() {
        return instance != null && (
                instance.getConfig().getBoolean("integrations.maps.dynmap.enabled", false)
                        || instance.getConfig().getBoolean("integrations.maps.bluemap.enabled", false)
        );
    }
}
