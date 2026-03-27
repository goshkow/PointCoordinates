package goshkow.cords.service;

import goshkow.cords.CordsPlugin;
import goshkow.cords.integration.CombatLogBridge;
import goshkow.cords.integration.VaultBridge;
import goshkow.cords.model.CordEntry;
import goshkow.cords.util.PermissionGate;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TeleportService {
    private static final Set<UUID> teleporting = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ignoreMoveOnce = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, String> teleportScopes = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> recentTeleports = new ConcurrentHashMap<>();

    private static final long ignoreWindowMs = 1000L;

    private TeleportService() {
    }

    public static void teleport(Player player, CordEntry entry) {
        if (!canTeleport(player, entry)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return;
        }

        String scopePath = entry.publicEntry() ? "labels.public" : "labels.personal";
        if (!CordsPlugin.getInstance().getConfig().getBoolean(scopePath + ".teleport_enabled", !entry.publicEntry())) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate(
                    entry.publicEntry() ? "messages.teleport_disabled_public" : "messages.teleport_disabled_personal"));
            return;
        }

        UUID playerId = player.getUniqueId();
        if (teleporting.contains(playerId)) {
            return;
        }

        long now = System.currentTimeMillis();
        if (!PermissionGate.has(player, "cords.teleport.bypass_cooldown")) {
            long cooldownMs = Math.max(0L, CordsPlugin.getInstance().getConfig().getLong(
                    cooldownScopePath(entry.publicEntry()) + ".teleport_cooldown",
                    CordsPlugin.getInstance().getConfig().getLong("teleport_cooldown", 10L)
            ) * 1000L);
            Long lastTeleport = cooldowns.get(playerId);
            if (lastTeleport != null && cooldownMs > 0L) {
                long remaining = lastTeleport + cooldownMs - now;
                if (remaining > 0L) {
                    sendFeedback(player, LanguagePack.translate("messages.cooldown").replace("${cooldown}", String.valueOf(Math.max(1L, remaining / 1000L))), scopePath);
                    if (CordsPlugin.isSoundsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                    }
                    return;
                }
            }
        }

        if (CombatLogBridge.isActive() && CombatLogBridge.isInCombat(player)
                && !CordsPlugin.getInstance().getConfig().getBoolean("integrations.combatlogx.combat.teleport", false)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.combatlogx.in_combat"));
            if (CordsPlugin.isSoundsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            }
            return;
        }

        if (CordsPlugin.isVaultHooked() && !VaultBridge.hasRequiredBalance(player)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.vault.not_enough_money")
                    .replace("${amount}", String.valueOf(CordsPlugin.getInstance().getConfig().getDouble("integrations.vault.charge_amount", 5.0))));
            if (CordsPlugin.isSoundsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
            }
            return;
        }

        teleporting.add(playerId);
        teleportScopes.put(playerId, scopePath);

        new BukkitRunnable() {
            private int secondsLeft = 5;

            @Override
            public void run() {
                if (!teleporting.contains(playerId)) {
                    cancel();
                    return;
                }

                if (CombatLogBridge.isActive() && CombatLogBridge.isInCombat(player)
                        && !CordsPlugin.getInstance().getConfig().getBoolean("integrations.combatlogx.combat.teleport", false)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.combatlogx.in_combat"));
                    cancelTeleport(playerId);
                    if (CordsPlugin.isSoundsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                    }
                    cancel();
                    return;
                }

                if (CordsPlugin.isVaultHooked() && !VaultBridge.hasRequiredBalance(player)) {
                    player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.vault.not_enough_money")
                            .replace("${amount}", String.valueOf(CordsPlugin.getInstance().getConfig().getDouble("integrations.vault.charge_amount", 5.0))));
                    cancelTeleport(playerId);
                    if (CordsPlugin.isSoundsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                    }
                    cancel();
                    return;
                }

                if (secondsLeft <= 0) {
                    if (CordsPlugin.isSoundsEnabled()) {
                        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                    }

                    if (CordsPlugin.isVaultHooked()) {
                        Economy economy = VaultBridge.economy();
                        double charge = CordsPlugin.getInstance().getConfig().getDouble("integrations.vault.charge_amount", 5.0);
                        EconomyResponse response = economy == null ? null : economy.withdrawPlayer(player, charge);
                        if (response != null && response.transactionSuccess()) {
                            player.sendMessage(CordsPlugin.getPrefix()
                                    + LanguagePack.translate("messages.teleported")
                                    + " "
                                    + LanguagePack.translate("integrations.vault.charged").replace("${amount}", String.valueOf(response.amount)));
                            teleportNow(player, entry);
                        } else if (response != null) {
                            player.sendMessage(CordsPlugin.getPrefix()
                                    + LanguagePack.translate("integrations.vault.transaction_failed")
                                    + response.errorMessage);
                        }
                    } else {
                        player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.teleported"));
                        teleportNow(player, entry);
                    }

                    cancel();
                    return;
                }

                if (secondsLeft == 1) {
                    sendFeedback(player, LanguagePack.translate("messages.wait_one"));
                } else {
                    sendFeedback(player, LanguagePack.translate("messages.wait_many").replace("${seconds}", String.valueOf(secondsLeft)));
                }

                if (CordsPlugin.isSoundsEnabled()) {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
                }

                secondsLeft--;
            }
        }.runTaskTimer(CordsPlugin.getInstance(), 0L, 20L);
    }

    public static void teleportNow(Player player, CordEntry entry) {
        Location source = entry.location();
        if (source.getWorld() == null) {
            return;
        }

        Location target = new Location(
                source.getWorld(),
                source.getX(),
                source.getY(),
                source.getZ(),
                source.getYaw(),
                source.getPitch()
        );

        player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
        player.setRotation(target.getYaw(), target.getPitch());
        Bukkit.getScheduler().runTaskLater(CordsPlugin.getInstance(), () -> {
            if (player.isOnline() && player.getWorld().equals(target.getWorld())) {
                player.setRotation(target.getYaw(), target.getPitch());
            }
        }, 1L);
        boolean heardByOthers = playArrivalSound(player, target, entry.publicEntry());
        ignoreMoveOnce.add(player.getUniqueId());
        recentTeleports.put(player.getUniqueId(), System.currentTimeMillis());
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
        teleporting.remove(player.getUniqueId());
        teleportScopes.remove(player.getUniqueId());

        if (heardByOthers) {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(
                    CordsPlugin.getPrefix() + ChatColor.YELLOW + LanguagePack.translate("messages.teleport_sound_heard")
            ));
        }

        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f, 1.0f);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
    }

    private static boolean playArrivalSound(Player player, Location location, boolean publicEntry) {
        if (!CordsPlugin.isSoundsEnabled()) {
            return false;
        }

        String scopePath = publicEntry ? "labels.public.teleport_sound" : "labels.personal.teleport_sound";
        if (!CordsPlugin.getInstance().getConfig().getBoolean(scopePath + ".enabled", false)) {
            return false;
        }

        Sound sound = parseSound(CordsPlugin.getInstance().getConfig().getString(scopePath + ".sound", "ENTITY_ENDERMAN_TELEPORT"));
        float volume = (float) Math.max(0.0, CordsPlugin.getInstance().getConfig().getDouble(scopePath + ".volume", 1.0D));
        float pitch = (float) Math.max(0.0, CordsPlugin.getInstance().getConfig().getDouble(scopePath + ".pitch", 1.0D));
        double radius = CordsPlugin.getInstance().getConfig().getDouble(scopePath + ".radius", 32.0D);
        if (sound == null || radius <= 0.0D || location.getWorld() == null) {
            return false;
        }

        float effectiveVolume = Math.max(volume, (float) (radius / 16.0D));
        double maxDistanceSquared = radius * radius;
        boolean heardByOthers = false;
        for (Player listener : location.getWorld().getPlayers()) {
            if (!listener.isOnline() || listener.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }
            if (listener.getLocation().distanceSquared(location) > maxDistanceSquared) {
                continue;
            }
            listener.playSound(location, sound, SoundCategory.PLAYERS, effectiveVolume, pitch);
            heardByOthers = true;
        }
        return heardByOthers;
    }

    public static boolean canTeleport(Player player, CordEntry entry) {
        if (player == null || entry == null) {
            return false;
        }

        boolean ownsMarker = entry.ownerId() != null && entry.ownerId().equals(player.getUniqueId());
        if (ownsMarker && PermissionGate.has(player, "cords.teleport.owned")) {
            return true;
        }

        String scopePath = entry.publicEntry() ? "labels.public" : "labels.personal";
        if (!CordsPlugin.getInstance().getConfig().getBoolean(scopePath + ".teleport_enabled", !entry.publicEntry())) {
            return false;
        }

        return PermissionGate.has(
                player,
                entry.publicEntry() ? "cords.teleport.public" : "cords.teleport.personal"
        );
    }

    private static Sound parseSound(String rawSound) {
        if (rawSound == null || rawSound.isBlank()) {
            return Sound.ENTITY_ENDERMAN_TELEPORT;
        }

        try {
            return Sound.valueOf(rawSound.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return Sound.ENTITY_ENDERMAN_TELEPORT;
        }
    }

    public static boolean isTeleporting(UUID playerId) {
        return teleporting.contains(playerId);
    }

    public static void cancelTeleport(UUID playerId) {
        teleporting.remove(playerId);
        teleportScopes.remove(playerId);
    }

    public static boolean consumeIgnoreFlag(UUID playerId) {
        return ignoreMoveOnce.remove(playerId);
    }

    public static boolean hasRecentlyTeleported(UUID playerId) {
        Long timestamp = recentTeleports.get(playerId);
        if (timestamp == null) {
            return false;
        }

        long age = System.currentTimeMillis() - timestamp;
        if (age < ignoreWindowMs) {
            return true;
        }

        recentTeleports.remove(playerId);
        return false;
    }

    public static void sendFeedback(Player player, String message) {
        sendFeedback(player, message, teleportScopes.getOrDefault(player.getUniqueId(), "labels.personal"));
    }

    public static void sendFeedback(Player player, String message, String scopePath) {
        String translated = PlaceholderResolver.apply(message, player);
        String mode = CordsPlugin.getInstance().getConfig().getString(
                scopePath + ".teleport_action_type",
                CordsPlugin.getInstance().getConfig().getString("teleport.action_type", "action")
        );
        String withPrefix = CordsPlugin.getPrefix() + translated;

        if ("message".equalsIgnoreCase(mode)) {
            player.sendMessage(ChatColor.translateAlternateColorCodes('&', withPrefix));
        } else {
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(withPrefix));
        }
    }

    private static String cooldownScopePath(boolean publicEntry) {
        if (publicEntry && CordsPlugin.getInstance().getConfig().getBoolean("labels.public.same_timeout_as_personal", false)) {
            return "labels.personal";
        }
        return publicEntry ? "labels.public" : "labels.personal";
    }
}
