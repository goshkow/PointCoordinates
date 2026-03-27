package goshkow.cords.service;

import goshkow.cords.CordStorage;
import goshkow.cords.CordsPlugin;
import goshkow.cords.integration.CombatLogBridge;
import goshkow.cords.model.CordEntry;
import goshkow.cords.util.CordLogger;
import goshkow.cords.util.PermissionGate;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CordRepository {
    private static final Map<UUID, Map<String, CordEntry>> cords = new LinkedHashMap<>();
    private static final CordLogger logger = new CordLogger("PointCoordinates / Storage");

    private CordRepository() {
    }

    public static CordEntry create(Player player, Location location, String inputName) {
        return createInternal(player, location, inputName, false);
    }

    public static CordEntry createPublic(Player player, Location location, String inputName) {
        return createOrPromotePublic(player, location, inputName);
    }

    private static CordEntry createOrPromotePublic(Player player, Location location, String inputName) {
        String scopePath = "labels.public";

        if (!PermissionGate.has(player, "cords.open")) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return null;
        }

        if (!CordsPlugin.getInstance().getConfig().getBoolean(scopePath + ".enabled", true)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.public_disabled"));
            return null;
        }

        if (CombatLogBridge.isActive() && CombatLogBridge.isInCombat(player)
                && !CordsPlugin.getInstance().getConfig().getBoolean("integrations.combatlogx.combat.set", false)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.combatlogx.cannot_add_cord"));
            return null;
        }

        String cleanedName = sanitizeName(inputName);
        if (cleanedName.isBlank()) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.invalid_argument"));
            return null;
        }

        int maxLength = CordsPlugin.getInstance().getConfig().getInt(
                scopePath + ".max_name_length",
                CordsPlugin.getInstance().getConfig().getInt("max_characters", 10)
        );
        if (maxLength > 0 && cleanedName.length() > maxLength) {
            cleanedName = cleanedName.substring(0, maxLength);
            player.sendMessage(CordsPlugin.getPrefix()
                    + LanguagePack.translate("messages.max_length").replace("${limit}", String.valueOf(maxLength)));
        }

        CordEntry existingPersonal = findOwned(player.getUniqueId(), cleanedName, false);
        if (existingPersonal != null) {
            int maxCount = CordsPlugin.getInstance().getConfig().getInt(
                    scopePath + ".max_per_player",
                    CordsPlugin.getInstance().getConfig().getInt("max_cap", 1)
            );
            if (maxCount > 0 && countPublic(player.getUniqueId()) >= maxCount) {
                player.sendMessage(CordsPlugin.getPrefix()
                        + LanguagePack.translate("messages.public_limit")
                        .replace("${limit}", String.valueOf(maxCount)));
                return null;
            }

            Map<String, CordEntry> playerCords = cords.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
            CordEntry promoted = existingPersonal.withPublicEntry(true);
            playerCords.remove(buildKey(existingPersonal.name(), false));
            playerCords.put(buildKey(promoted.name(), true), promoted);
            player.sendMessage(CordsPlugin.getPrefix()
                    + PlaceholderResolver.applyMarker(LanguagePack.translate("messages.public_promoted"), promoted.name()));
            if (CordsPlugin.isSoundsEnabled()) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
            }
            CordsPlugin.syncMapIntegrations();
            saveAll();
            return promoted;
        }

        if (findAnyMarker(cleanedName) != null) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.name_taken"));
            return null;
        }

        return createInternal(player, location, cleanedName, true);
    }

    private static CordEntry createInternal(Player player, Location location, String inputName, boolean publicEntry) {
        String scopePath = publicEntry ? "labels.public" : "labels.personal";

        if (publicEntry) {
            if (!PermissionGate.has(player, "cords.open")) {
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
                return null;
            }
        } else if (!PermissionGate.has(player, "cords.add")) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.no_permission"));
            return null;
        }

        if (!CordsPlugin.getInstance().getConfig().getBoolean(scopePath + ".enabled", true)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate(
                    publicEntry ? "messages.public_disabled" : "messages.personal_disabled"));
            return null;
        }

        if (CombatLogBridge.isActive() && CombatLogBridge.isInCombat(player)
                && !CordsPlugin.getInstance().getConfig().getBoolean("integrations.combatlogx.combat.set", false)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("integrations.combatlogx.cannot_add_cord"));
            return null;
        }

        String cleanedName = sanitizeName(inputName);
        if (cleanedName.isBlank()) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.invalid_argument"));
            return null;
        }

        int maxLength = CordsPlugin.getInstance().getConfig().getInt(
                scopePath + ".max_name_length",
                CordsPlugin.getInstance().getConfig().getInt("max_characters", 10)
        );
        if (maxLength > 0 && cleanedName.length() > maxLength) {
            cleanedName = cleanedName.substring(0, maxLength);
            player.sendMessage(CordsPlugin.getPrefix()
                    + LanguagePack.translate("messages.max_length").replace("${limit}", String.valueOf(maxLength)));
        }

        if (publicEntry) {
            if (findAnyMarker(cleanedName) != null) {
                player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.name_taken"));
                return null;
            }
        } else if (findOwned(player.getUniqueId(), cleanedName, false) != null
                || findOwned(player.getUniqueId(), cleanedName, true) != null
                || findAnyPublic(cleanedName) != null) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.name_taken"));
            return null;
        }

        Map<String, CordEntry> playerCords = cords.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        String key = buildKey(cleanedName, publicEntry);

        int maxCount = CordsPlugin.getInstance().getConfig().getInt(
                scopePath + ".max_per_player",
                CordsPlugin.getInstance().getConfig().getInt("max_cap", publicEntry ? 1 : 5)
        );
        int ownedCount = publicEntry ? countPublic(player.getUniqueId()) : countPersonal(player.getUniqueId());
        if (maxCount > 0 && ownedCount >= maxCount) {
            player.sendMessage(CordsPlugin.getPrefix()
                    + LanguagePack.translate(publicEntry ? "messages.public_limit" : "messages.personal_limit")
                    .replace("${limit}", String.valueOf(maxCount)));
            return null;
        }

        CordEntry entry = new CordEntry(player.getUniqueId(), location.clone(), cleanedName, System.currentTimeMillis() / 1000L, publicEntry, List.of());
        playerCords.put(key, entry);

        String createdKey = publicEntry ? "messages.public_created" : "messages.personal_created";
        player.sendMessage(CordsPlugin.getPrefix()
                + PlaceholderResolver.applyMarker(LanguagePack.translate(createdKey), cleanedName)
                .replace("${x}", PlaceholderResolver.formatCoordinate(location.getX()))
                .replace("${y}", PlaceholderResolver.formatCoordinate(location.getY()))
                .replace("${z}", PlaceholderResolver.formatCoordinate(location.getZ())));

        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }

        CordsPlugin.syncMapIntegrations();
        saveAll();

        return entry;
    }

    public static void remove(Player player, CordEntry entry) {
        if (entry == null) {
            return;
        }

        boolean isOwner = entry.ownerId().equals(player.getUniqueId());
        boolean canRemoveOwn = PermissionGate.has(player, "cords.remove");
        boolean canRemoveOthers = player.isOp() || PermissionGate.has(player, "cords.remove.others");
        if ((isOwner && !canRemoveOwn) || (!isOwner && !canRemoveOthers)) {
            player.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.remove_denied"));
            return;
        }

        Map<String, CordEntry> playerCords = cords.get(entry.ownerId());
        if (playerCords != null) {
            playerCords.remove(buildKey(entry.name(), entry.publicEntry()));
            if (playerCords.isEmpty()) {
                cords.remove(entry.ownerId());
            }
        }

        String removedKey = entry.publicEntry() ? "messages.public_removed" : "messages.personal_removed";
        player.sendMessage(CordsPlugin.getPrefix()
                + PlaceholderResolver.applyMarker(LanguagePack.translate(removedKey), entry.name()));
        if (CordsPlugin.isSoundsEnabled()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
        CordsPlugin.syncMapIntegrations();
        saveAll();
    }

    public static CordEntry rename(Player actor, CordEntry entry, String newName) {
        if (entry == null) {
            return null;
        }

        boolean isOwner = entry.ownerId().equals(actor.getUniqueId());
        boolean canRenameOwn = PermissionGate.has(actor, "cords.edit.name");
        boolean canRenameOthers = actor.isOp() || PermissionGate.has(actor, "cords.edit.name.others");
        if ((isOwner && !canRenameOwn) || (!isOwner && !canRenameOthers)) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.rename_denied"));
            return null;
        }

        String cleanedName = sanitizeName(newName);
        if (cleanedName.isBlank()) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.invalid_argument"));
            return null;
        }

        if (cleanedName.equalsIgnoreCase(entry.name())) {
            return entry;
        }

        if (entry.publicEntry()) {
            CordEntry conflict = findAnyMarker(cleanedName);
            if (conflict != null && !sameIdentity(conflict, entry)) {
                actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.name_taken"));
                return null;
            }
        } else {
            CordEntry sameOwnerPersonal = findOwned(entry.ownerId(), cleanedName, false);
            CordEntry sameOwnerPublic = findOwned(entry.ownerId(), cleanedName, true);
            CordEntry publicConflict = findAnyPublic(cleanedName);
            if ((sameOwnerPersonal != null && !sameIdentity(sameOwnerPersonal, entry))
                    || (sameOwnerPublic != null && !sameIdentity(sameOwnerPublic, entry))
                    || (publicConflict != null && !sameIdentity(publicConflict, entry))) {
                actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.name_taken"));
                return null;
            }
        }

        Map<String, CordEntry> ownerCords = cords.computeIfAbsent(entry.ownerId(), ignored -> new LinkedHashMap<>());
        ownerCords.remove(buildKey(entry.name(), entry.publicEntry()));

        CordEntry renamed = entry.withName(cleanedName);
        ownerCords.put(buildKey(renamed.name(), renamed.publicEntry()), renamed);

        String renamedKey = renamed.publicEntry() ? "messages.public_renamed" : "messages.personal_renamed";
        actor.sendMessage(CordsPlugin.getPrefix()
                + PlaceholderResolver.applyMarkerRename(LanguagePack.translate(renamedKey), entry.name(), renamed.name()));
        if (CordsPlugin.isSoundsEnabled()) {
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
        CordsPlugin.syncMapIntegrations();
        saveAll();

        return renamed;
    }

    public static CordEntry move(Player actor, CordEntry entry, Location location) {
        if (entry == null || location == null || location.getWorld() == null) {
            return null;
        }

        boolean isOwner = entry.ownerId().equals(actor.getUniqueId());
        boolean canMoveOwn = PermissionGate.has(actor, "cords.edit.move");
        boolean canMoveOthers = actor.isOp() || PermissionGate.has(actor, "cords.edit.move.others");
        if ((isOwner && !canMoveOwn) || (!isOwner && !canMoveOthers)) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.rename_denied"));
            return null;
        }

        Location fromLocation = entry.location().clone();
        Map<String, CordEntry> ownerCords = cords.computeIfAbsent(entry.ownerId(), ignored -> new LinkedHashMap<>());
        ownerCords.remove(buildKey(entry.name(), entry.publicEntry()));
        CordEntry moved = new CordEntry(entry.ownerId(), location.clone(), entry.name(), entry.createdAt(), entry.publicEntry(), entry.tags());
        ownerCords.put(buildKey(moved.name(), moved.publicEntry()), moved);
        actor.sendMessage(CordsPlugin.getPrefix()
                + PlaceholderResolver.applyMarkerMove(LanguagePack.translate("messages.marker_moved"), entry.name(), fromLocation, location));
        if (CordsPlugin.isSoundsEnabled()) {
            actor.playSound(actor.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.0f);
        }
        CordsPlugin.syncMapIntegrations();
        saveAll();
        return moved;
    }

    public static CordEntry addTags(Player actor, CordEntry entry, List<String> tags) {
        if (entry == null || tags == null || tags.isEmpty()) {
            return entry;
        }
        if (!canEditTags(actor, entry)) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.rename_denied"));
            return null;
        }

        List<String> allowed = allowedPublicTags();
        if (allowed.isEmpty()) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.tags_disabled"));
            return null;
        }

        ArrayList<String> updated = new ArrayList<>(entry.tags());
        for (String tag : tags) {
            String normalized = normalizeTag(tag);
            if (normalized.isBlank() || !allowed.contains(normalized)) {
                continue;
            }
            if (!updated.contains(normalized)) {
                updated.add(normalized);
            }
        }
        CordEntry changed = replaceEntry(entry, entry.withTags(updated));
        CordsPlugin.syncMapIntegrations();
        saveAll();
        return changed;
    }

    public static CordEntry removeTags(Player actor, CordEntry entry, List<String> tags) {
        if (entry == null || tags == null || tags.isEmpty()) {
            return entry;
        }
        if (!canEditTags(actor, entry)) {
            actor.sendMessage(CordsPlugin.getPrefix() + LanguagePack.translate("messages.rename_denied"));
            return null;
        }

        ArrayList<String> updated = new ArrayList<>(entry.tags());
        for (String tag : tags) {
            updated.remove(normalizeTag(tag));
        }
        CordEntry changed = replaceEntry(entry, entry.withTags(updated));
        CordsPlugin.syncMapIntegrations();
        saveAll();
        return changed;
    }

    public static List<CordEntry> searchVisibleByName(UUID viewerId, String query) {
        String needle = sanitizeName(query);
        ArrayList<CordEntry> results = new ArrayList<>();
        for (CordEntry entry : listVisibleEntries(viewerId)) {
            if (entry.name().contains(needle)) {
                results.add(entry);
            }
        }
        return results;
    }

    public static List<CordEntry> searchVisibleByTag(UUID viewerId, String tag) {
        return searchVisibleByTags(viewerId, List.of(tag));
    }

    public static List<CordEntry> searchVisibleByTags(UUID viewerId, List<String> tags) {
        ArrayList<String> needles = new ArrayList<>();
        for (String tag : tags) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank()) {
                needles.add(normalized);
            }
        }

        ArrayList<CordEntry> results = new ArrayList<>();
        for (CordEntry entry : listVisibleEntries(viewerId)) {
            boolean matches = true;
            for (String needle : needles) {
                if (!entry.tags().contains(needle)) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                results.add(entry);
            }
        }
        return results;
    }

    public static List<String> allowedPublicTags() {
        if (!CordsPlugin.getInstance().getConfig().getBoolean("labels.public.tags.enabled", true)) {
            return List.of();
        }
        ArrayList<String> tags = new ArrayList<>();
        for (String tag : CordsPlugin.getInstance().getConfig().getStringList("labels.public.tags.allowed")) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank() && !tags.contains(normalized)) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    public static CordEntry findVisible(UUID viewerId, String inputName) {
        CordEntry personal = findOwned(viewerId, inputName, false);
        if (personal != null) {
            return personal;
        }
        return findAnyPublic(inputName);
    }

    public static CordEntry findOwnedMarker(UUID ownerId, String inputName) {
        CordEntry personal = findOwned(ownerId, inputName, false);
        if (personal != null) {
            return personal;
        }
        return findOwned(ownerId, inputName, true);
    }

    /**
     * Backward-compatible lookup for the old API surface.
     * Personal markers remain the default because the original plugin only exposed them.
     */
    public static CordEntry find(UUID ownerId, String inputName) {
        return findPersonal(ownerId, inputName);
    }

    public static CordEntry findPersonal(UUID ownerId, String inputName) {
        return findOwned(ownerId, inputName, false);
    }

    public static CordEntry findOwned(UUID ownerId, String inputName, boolean publicEntry) {
        Map<String, CordEntry> playerCords = cords.get(ownerId);
        if (playerCords == null) {
            return null;
        }
        return playerCords.get(buildKey(sanitizeName(inputName), publicEntry));
    }

    public static CordEntry findAnyPublic(String inputName) {
        String lookup = sanitizeName(inputName);
        for (Map<String, CordEntry> playerCords : cords.values()) {
            for (CordEntry entry : playerCords.values()) {
                if (entry.publicEntry() && entry.name().equalsIgnoreCase(lookup)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static CordEntry findAnyMarker(String inputName) {
        String lookup = sanitizeName(inputName);
        for (Map<String, CordEntry> playerCords : cords.values()) {
            for (CordEntry entry : playerCords.values()) {
                if (entry.name().equalsIgnoreCase(lookup)) {
                    return entry;
                }
            }
        }
        return null;
    }

    public static ArrayList<String> listPersonal(UUID ownerId) {
        ArrayList<String> names = new ArrayList<>();
        for (CordEntry entry : listPersonalEntries(ownerId)) {
            names.add(entry.name());
        }
        return names;
    }

    public static ArrayList<String> listVisible(UUID viewerId) {
        ArrayList<String> names = new ArrayList<>();
        for (CordEntry entry : listVisibleEntries(viewerId)) {
            names.add(entry.name());
        }
        return names;
    }

    public static ArrayList<String> listOwned(UUID ownerId) {
        ArrayList<String> names = new ArrayList<>();
        for (CordEntry entry : listOwnedEntries(ownerId)) {
            names.add(entry.name());
        }
        return names;
    }

    /**
     * Backward-compatible list for older integrations.
     * Returns only personal markers, matching the original behavior.
     */
    public static ArrayList<String> list(UUID ownerId) {
        return listPersonal(ownerId);
    }

    public static List<CordEntry> listPersonalEntries(UUID ownerId) {
        ArrayList<CordEntry> entries = new ArrayList<>();
        Map<String, CordEntry> playerCords = cords.get(ownerId);
        if (playerCords == null) {
            return entries;
        }

        for (CordEntry entry : playerCords.values()) {
            if (!entry.publicEntry()) {
                entries.add(entry);
            }
        }
        return entries;
    }

    public static List<CordEntry> listOwnedEntries(UUID ownerId) {
        ArrayList<CordEntry> entries = new ArrayList<>();
        Map<String, CordEntry> playerCords = cords.get(ownerId);
        if (playerCords == null) {
            return entries;
        }

        entries.addAll(playerCords.values());
        return entries;
    }

    public static List<CordEntry> listPublicEntries() {
        ArrayList<CordEntry> entries = new ArrayList<>();
        for (Map<String, CordEntry> playerCords : cords.values()) {
            for (CordEntry entry : playerCords.values()) {
                if (entry.publicEntry()) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public static List<CordEntry> listPersonalEntriesForMaps() {
        ArrayList<CordEntry> entries = new ArrayList<>();
        for (Map<String, CordEntry> playerCords : cords.values()) {
            for (CordEntry entry : playerCords.values()) {
                if (!entry.publicEntry()) {
                    entries.add(entry);
                }
            }
        }
        return entries;
    }

    public static List<CordEntry> listVisibleEntries(UUID viewerId) {
        ArrayList<CordEntry> entries = new ArrayList<>();
        entries.addAll(listPersonalEntries(viewerId));
        entries.addAll(listPublicEntries());
        return entries;
    }

    public static int countPersonal(UUID ownerId) {
        return listPersonalEntries(ownerId).size();
    }

    public static int countPublic(UUID ownerId) {
        int count = 0;
        Map<String, CordEntry> playerCords = cords.get(ownerId);
        if (playerCords == null) {
            return 0;
        }

        for (CordEntry entry : playerCords.values()) {
            if (entry.publicEntry()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Backward-compatible counter for older integrations.
     * Counts only personal markers, matching the original behavior.
     */
    public static int count(UUID ownerId) {
        return countPersonal(ownerId);
    }

    public static void loadAll() {
        cords.clear();
        FileConfiguration storage = CordStorage.get();
        if (storage == null) {
            CordsPlugin.syncMapIntegrations();
            return;
        }

        ConfigurationSection labelsRoot = storage.getConfigurationSection("labels");
        if (labelsRoot != null) {
            loadScope(labelsRoot.getConfigurationSection("personal"), false);
            loadScope(labelsRoot.getConfigurationSection("public"), true);
            CordsPlugin.syncMapIntegrations();
            return;
        }

        ConfigurationSection legacyRoot = storage.getConfigurationSection("cords");
        if (legacyRoot == null || legacyRoot.getKeys(false).isEmpty()) {
            legacyRoot = storage.getConfigurationSection("pcords");
        }
        loadLegacyScope(legacyRoot);
        CordsPlugin.syncMapIntegrations();
    }

    public static void saveAll() {
        FileConfiguration storage = CordStorage.get();
        if (storage == null) {
            return;
        }

        CordStorage.backup();
        storage.set("labels", null);
        storage.set("cords", null);
        storage.set("pcords", null);

        for (Map.Entry<UUID, Map<String, CordEntry>> ownerEntry : cords.entrySet()) {
            UUID ownerId = ownerEntry.getKey();
            for (CordEntry entry : ownerEntry.getValue().values()) {
                String scope = entry.publicEntry() ? "labels.public" : "labels.personal";
                String base = scope + "." + ownerId + "." + entry.name();
                Location location = entry.location();
                storage.set(base + ".timestamp", entry.createdAt());
                storage.set(base + ".world", location.getWorld() == null ? null : location.getWorld().getName());
                storage.set(base + ".x", location.getX());
                storage.set(base + ".y", location.getY());
                storage.set(base + ".z", location.getZ());
                storage.set(base + ".pitch", location.getPitch());
                storage.set(base + ".yaw", location.getYaw());
                if (!entry.tags().isEmpty()) {
                    storage.set(base + ".tags", new ArrayList<>(entry.tags()));
                } else {
                    storage.set(base + ".tags", null);
                }
            }
        }

        CordStorage.save();
    }

    public static CordEntry promoteToPublic(Player player, String inputName) {
        String cleanedName = sanitizeName(inputName);
        if (cleanedName.isBlank()) {
            return null;
        }

        CordEntry existingPersonal = findOwned(player.getUniqueId(), cleanedName, false);
        if (existingPersonal == null) {
            return null;
        }

        Map<String, CordEntry> playerCords = cords.computeIfAbsent(player.getUniqueId(), ignored -> new LinkedHashMap<>());
        CordEntry promoted = existingPersonal.withPublicEntry(true);
        playerCords.remove(buildKey(existingPersonal.name(), false));
        playerCords.put(buildKey(promoted.name(), true), promoted);
        CordsPlugin.syncMapIntegrations();
        saveAll();
        return promoted;
    }

    public static void reload() {
        CordStorage.reload();
        loadAll();
    }

    private static void loadScope(ConfigurationSection scope, boolean publicEntry) {
        if (scope == null || scope.getKeys(false).isEmpty()) {
            return;
        }

        for (String ownerKey : scope.getKeys(false)) {
            UUID ownerId = parseUuid(ownerKey);
            if (ownerId == null) {
                logger.debug("Skipping invalid owner id: " + ownerKey);
                continue;
            }

            ConfigurationSection playerSection = scope.getConfigurationSection(ownerKey);
            if (playerSection == null) {
                continue;
            }

            Map<String, CordEntry> playerCords = cords.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
            for (String cordName : playerSection.getKeys(false)) {
                CordEntry entry = readEntry(ownerId, playerSection.getConfigurationSection(cordName), cordName, publicEntry);
                if (entry != null) {
                    playerCords.put(buildKey(entry.name(), publicEntry), entry);
                }
            }
        }
    }

    private static void loadLegacyScope(ConfigurationSection root) {
        if (root == null || root.getKeys(false).isEmpty()) {
            return;
        }

        for (String ownerKey : root.getKeys(false)) {
            UUID ownerId = parseUuid(ownerKey);
            if (ownerId == null) {
                logger.debug("Skipping invalid owner id: " + ownerKey);
                continue;
            }

            ConfigurationSection playerSection = root.getConfigurationSection(ownerKey);
            if (playerSection == null) {
                continue;
            }

            Map<String, CordEntry> playerCords = cords.computeIfAbsent(ownerId, ignored -> new LinkedHashMap<>());
            for (String cordName : playerSection.getKeys(false)) {
                CordEntry entry = readEntry(ownerId, playerSection.getConfigurationSection(cordName), cordName, false);
                if (entry != null) {
                    playerCords.put(buildKey(entry.name(), false), entry);
                }
            }
        }
    }

    private static CordEntry readEntry(UUID ownerId, ConfigurationSection section, String cordName, boolean publicEntry) {
        if (section == null) {
            return null;
        }

        String worldName = section.getString("world");
        World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) {
            logger.debug("Skipping cord '" + cordName + "' because the world is missing.");
            return null;
        }

        Location location = new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
        long createdAt = section.getLong("timestamp", System.currentTimeMillis() / 1000L);
        return new CordEntry(ownerId, location, cordName, createdAt, publicEntry, readTags(section));
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String sanitizeName(String inputName) {
        if (inputName == null) {
            return "";
        }
        return inputName.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String buildKey(String name, boolean publicEntry) {
        return (publicEntry ? "public:" : "personal:") + sanitizeName(name);
    }

    private static boolean sameIdentity(CordEntry first, CordEntry second) {
        return first.ownerId().equals(second.ownerId())
                && first.publicEntry() == second.publicEntry()
                && first.name().equalsIgnoreCase(second.name());
    }

    private static boolean canEditTags(Player actor, CordEntry entry) {
        return entry.ownerId().equals(actor.getUniqueId())
                ? PermissionGate.has(actor, "cords.edit.tag")
                : actor.isOp() || PermissionGate.has(actor, "cords.edit.tag.others");
    }

    private static CordEntry replaceEntry(CordEntry oldEntry, CordEntry newEntry) {
        Map<String, CordEntry> ownerCords = cords.computeIfAbsent(oldEntry.ownerId(), ignored -> new LinkedHashMap<>());
        ownerCords.remove(buildKey(oldEntry.name(), oldEntry.publicEntry()));
        ownerCords.put(buildKey(newEntry.name(), newEntry.publicEntry()), newEntry);
        return newEntry;
    }

    private static List<String> readTags(ConfigurationSection section) {
        ArrayList<String> tags = new ArrayList<>();
        if (section == null) {
            return tags;
        }
        for (String tag : section.getStringList("tags")) {
            String normalized = normalizeTag(tag);
            if (!normalized.isBlank() && !tags.contains(normalized)) {
                tags.add(normalized);
            }
        }
        return tags;
    }

    private static String normalizeTag(String tag) {
        if (tag == null) {
            return "";
        }
        return tag.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
