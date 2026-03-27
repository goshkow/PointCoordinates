package goshkow.cords.integration;

import goshkow.cords.CordsPlugin;
import goshkow.cords.model.CordEntry;
import goshkow.cords.service.CordRepository;
import org.bukkit.Bukkit;
import org.bukkit.Location;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MapIntegrationService {
    private static final Map<String, Object> blueMapSets = new LinkedHashMap<>();
    private static final Map<String, Object> dynmapSets = new LinkedHashMap<>();

    private MapIntegrationService() {
    }

    public static void reload() {
        sync();
    }

    public static void shutdown() {
        try {
            clearDynmap();
        } catch (Throwable ignored) {
        }
        try {
            clearBlueMap();
        } catch (Throwable ignored) {
        }
        blueMapSets.clear();
        dynmapSets.clear();
    }

    public static void sync() {
        if (CordsPlugin.getInstance() == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(CordsPlugin.getInstance(), MapIntegrationService::syncNow);
            return;
        }

        syncNow();
    }

    private static void syncNow() {
        syncDynmap();
        syncBlueMap();
    }

    private static void syncDynmap() {
        if (!CordsPlugin.getInstance().getConfig().getBoolean("integrations.maps.dynmap.enabled", false)) {
            return;
        }

        Object markerApi = getDynmapMarkerApi();
        if (markerApi == null) {
            return;
        }

        syncDynmapScope(markerApi, false, "integrations.maps.dynmap.personal");
        syncDynmapScope(markerApi, true, "integrations.maps.dynmap.public");
    }

    private static void syncDynmapScope(Object markerApi, boolean publicEntry, String configPath) {
        if (!CordsPlugin.getInstance().getConfig().getBoolean(configPath + ".enabled", false)) {
            return;
        }

        String setId = buildSetId("dynmap", publicEntry);
        String label = buildSetLabel(publicEntry, CordsPlugin.getInstance().getConfig().getString(configPath + ".label", "PointCoordinates"));
        Object markerSet = dynmapSets.get(setId);
        if (markerSet == null) {
            markerSet = getOrCreateDynmapMarkerSet(markerApi, setId, label);
            if (markerSet == null) {
                return;
            }
            dynmapSets.put(setId, markerSet);
        }

        invoke(markerSet, "setLabel", new Class<?>[]{String.class}, label);
        Object markers = invoke(markerSet, "getMarkers", null);
        Map<?, ?> markerMap = asMap(markers);
        if (markerMap != null) {
            markerMap.clear();
        }

        Object icon = getDynmapMarkerIcon(markerApi, CordsPlugin.getInstance().getConfig().getString(configPath + ".icon", "default"));
        Collection<CordEntry> entries = publicEntry
                ? CordRepository.listPublicEntries()
                : CordRepository.listPersonalEntriesForMaps();

        for (CordEntry entry : entries) {
            Location location = entry.location();
            if (location.getWorld() == null) {
                continue;
            }
            invoke(markerSet, "createMarker",
                    new Class<?>[]{
                            String.class, String.class, boolean.class, String.class,
                            double.class, double.class, double.class,
                            icon == null ? Object.class : icon.getClass(), boolean.class
                    },
                    markerId(entry),
                    escapeHtml(entry.name()),
                    true,
                    location.getWorld().getName(),
                    location.getX(),
                    location.getY(),
                    location.getZ(),
                    icon,
                    false
            );
        }
    }

    private static void syncBlueMap() {
        if (!CordsPlugin.getInstance().getConfig().getBoolean("integrations.maps.bluemap.enabled", false)) {
            return;
        }

        Optional<?> optional = getBlueMapInstance();
        if (optional.isEmpty()) {
            return;
        }

        Object api = optional.get();
        syncBlueMapScope(api, false, "integrations.maps.bluemap.personal", "PointCoordinates - Personal");
        syncBlueMapScope(api, true, "integrations.maps.bluemap.public", "PointCoordinates - Public");
    }

    private static void syncBlueMapScope(Object api, boolean publicEntry, String configPath, String defaultLabel) {
        if (!CordsPlugin.getInstance().getConfig().getBoolean(configPath + ".enabled", false)) {
            return;
        }

        Collection<CordEntry> entries = publicEntry
                ? CordRepository.listPublicEntries()
                : CordRepository.listPersonalEntriesForMaps();

        Collection<?> worlds = asCollection(invoke(api, "getWorlds", null));
        if (worlds == null) {
            return;
        }

        for (Object world : worlds) {
            String worldId = String.valueOf(invoke(world, "getId", null));
            String setId = buildSetId("bluemap", publicEntry) + "_" + normalizeId(worldId);
            String label = buildSetLabel(publicEntry, CordsPlugin.getInstance().getConfig().getString(configPath + ".label", defaultLabel));

            Object markerSet = blueMapSets.get(setId);
            if (markerSet == null) {
                markerSet = createBlueMapMarkerSet(label);
                if (markerSet == null) {
                    return;
                }
                blueMapSets.put(setId, markerSet);
            }
            invoke(markerSet, "setLabel", new Class<?>[]{String.class}, label);

            Collection<?> maps = asCollection(invoke(world, "getMaps", null));
            if (maps == null) {
                continue;
            }
            for (Object map : maps) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> mapSets = (Map<Object, Object>) asMap(invoke(map, "getMarkerSets", null));
                if (mapSets != null) {
                    mapSets.put(setId, markerSet);
                }
            }

            for (CordEntry entry : entries) {
                Location location = entry.location();
                if (location.getWorld() == null) {
                    continue;
                }

                Optional<?> markerWorld = getBlueMapWorld(api, location.getWorld());
                if (markerWorld.isEmpty()) {
                    continue;
                }
                Object resolvedWorld = markerWorld.get();
                String resolvedWorldId = String.valueOf(invoke(resolvedWorld, "getId", null));
                if (!resolvedWorldId.equalsIgnoreCase(worldId)) {
                    continue;
                }

                Object position = createBlueMapVector(location.getX(), location.getY(), location.getZ());
                if (position == null) {
                    continue;
                }

                Object marker = createBlueMapMarker(escapeHtml(entry.name()), position);
                if (marker == null) {
                    continue;
                }
                invoke(marker, "setListed", new Class<?>[]{boolean.class}, true);
                invoke(marker, "setDetail", new Class<?>[]{String.class}, buildBlueMapDetail(entry));
                invoke(markerSet, "put", new Class<?>[]{String.class, Object.class}, markerId(entry), marker);
            }
        }
    }

    private static void clearDynmap() {
        Object markerApi = getDynmapMarkerApi();
        if (markerApi == null) {
            return;
        }

        for (String setId : dynmapSets.keySet()) {
            Object markerSet = invoke(markerApi, "getMarkerSet", new Class<?>[]{String.class}, setId);
            if (markerSet != null) {
                Object markers = invoke(markerSet, "getMarkers", null);
                Map<?, ?> markerMap = asMap(markers);
                if (markerMap != null) {
                    markerMap.clear();
                }
            }
        }
    }

    private static void clearBlueMap() {
        Optional<?> optional = getBlueMapInstance();
        if (optional.isEmpty()) {
            return;
        }

        Object api = optional.get();
        Collection<?> worlds = asCollection(invoke(api, "getWorlds", null));
        if (worlds == null) {
            return;
        }

        for (Object world : worlds) {
            Collection<?> maps = asCollection(invoke(world, "getMaps", null));
            if (maps == null) {
                continue;
            }
            for (Object map : maps) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> mapSets = (Map<Object, Object>) asMap(invoke(map, "getMarkerSets", null));
                if (mapSets != null) {
                    for (String setId : blueMapSets.keySet()) {
                        mapSets.remove(setId);
                    }
                }
            }
        }
    }

    private static Object getDynmapMarkerApi() {
        Object plugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (plugin == null) {
            return null;
        }
        return invoke(plugin, "getMarkerAPI", null);
    }

    private static Object getDynmapMarkerIcon(Object markerApi, String iconId) {
        if (iconId == null || iconId.isBlank()) {
            return null;
        }
        return invoke(markerApi, "getMarkerIcon", new Class<?>[]{String.class}, iconId);
    }

    private static Object getOrCreateDynmapMarkerSet(Object markerApi, String setId, String label) {
        Object markerSet = invoke(markerApi, "getMarkerSet", new Class<?>[]{String.class}, setId);
        if (markerSet != null) {
            return markerSet;
        }

        Object created = invoke(markerApi, "createMarkerSet",
                new Class<?>[]{String.class, String.class, java.util.Set.class, boolean.class},
                setId, label, null, false);
        if (created != null) {
            return created;
        }
        return invoke(markerApi, "createMarkerSet",
                new Class<?>[]{String.class, String.class, Object.class, boolean.class},
                setId, label, null, false);
    }

    private static Optional<?> getBlueMapInstance() {
        try {
            Class<?> apiClass = Class.forName("de.bluecolored.bluemap.api.BlueMapAPI");
            Method getInstance = apiClass.getMethod("getInstance");
            Object value = getInstance.invoke(null);
            return value instanceof Optional<?> optional ? optional : Optional.empty();
        } catch (ReflectiveOperationException exception) {
            return Optional.empty();
        }
    }

    private static Optional<?> getBlueMapWorld(Object api, Object worldObject) {
        Object value = invoke(api, "getWorld", new Class<?>[]{Object.class}, worldObject);
        return value instanceof Optional<?> optional ? optional : Optional.empty();
    }

    private static Object createBlueMapMarkerSet(String label) {
        try {
            Class<?> markerSetClass = Class.forName("de.bluecolored.bluemap.api.markers.MarkerSet");
            Constructor<?> constructor = markerSetClass.getConstructor(String.class, boolean.class, boolean.class);
            return constructor.newInstance(label, true, false);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object createBlueMapVector(double x, double y, double z) {
        try {
            Class<?> vectorClass = Class.forName("de.bluecolored.bluemap.api.math.Vector3d");
            Constructor<?> constructor = vectorClass.getConstructor(double.class, double.class, double.class);
            return constructor.newInstance(x, y, z);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object createBlueMapMarker(String label, Object position) {
        try {
            Class<?> markerClass = Class.forName("de.bluecolored.bluemap.api.markers.POIMarker");
            Constructor<?> constructor = markerClass.getConstructor(String.class, Class.forName("de.bluecolored.bluemap.api.math.Vector3d"));
            return constructor.newInstance(label, position);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        if (target == null) {
            return null;
        }

        try {
            Method method;
            if (parameterTypes == null) {
                method = target.getClass().getMethod(methodName);
                return method.invoke(target);
            }
            method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (NoSuchMethodException exception) {
            if (parameterTypes != null) {
                for (Method candidate : target.getClass().getMethods()) {
                    if (!candidate.getName().equals(methodName) || candidate.getParameterCount() != args.length) {
                        continue;
                    }
                    try {
                        return candidate.invoke(target, args);
                    } catch (IllegalAccessException | InvocationTargetException ignored) {
                        // Try the next compatible overload.
                    }
                }
            }
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> asCollection(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static Map<?, ?> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map;
        }
        return null;
    }

    private static String buildBlueMapDetail(CordEntry entry) {
        StringBuilder detail = new StringBuilder();
        detail.append("<b>").append(escapeHtml(entry.name())).append("</b><br>");
        detail.append(escapeHtml(entry.publicEntry() ? "Public marker" : "Personal marker"));
        detail.append("<br>");
        detail.append("Owner: ").append(escapeHtml(ownerName(entry.ownerId())));
        if (entry.publicEntry() && !entry.tags().isEmpty()) {
            detail.append("<br>Tags: ").append(escapeHtml(String.join(", ", entry.tags())));
        }
        return detail.toString();
    }

    private static String buildSetId(String plugin, boolean publicEntry) {
        return "pointcoordinates_" + plugin + "_" + (publicEntry ? "public" : "personal");
    }

    private static String buildSetLabel(boolean publicEntry, String baseLabel) {
        return baseLabel + (publicEntry ? " - Public" : " - Personal");
    }

    private static String markerId(CordEntry entry) {
        return entry.ownerId() + ":" + entry.name().toLowerCase(Locale.ROOT) + ":" + (entry.publicEntry() ? "public" : "personal");
    }

    private static String normalizeId(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    private static String ownerName(java.util.UUID ownerId) {
        String name = Bukkit.getOfflinePlayer(ownerId).getName();
        return name == null ? ownerId.toString() : name;
    }

    private static String escapeHtml(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
