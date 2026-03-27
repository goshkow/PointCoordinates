package goshkow.cords.model;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.UUID;

public record CordEntry(UUID ownerId, Location location, String name, long createdAt, boolean publicEntry, List<String> tags) {
    public CordEntry(UUID ownerId, Location location, String name) {
        this(ownerId, location, name, System.currentTimeMillis() / 1000L, false, List.of());
    }

    public CordEntry(UUID ownerId, Location location, String name, long createdAt) {
        this(ownerId, location, name, createdAt, false, List.of());
    }

    public CordEntry(UUID ownerId, Location location, String name, long createdAt, boolean publicEntry) {
        this(ownerId, location, name, createdAt, publicEntry, List.of());
    }

    public CordEntry {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public CordEntry withPublicEntry(boolean publicEntry) {
        return new CordEntry(ownerId, location, name, createdAt, publicEntry, tags);
    }

    public CordEntry withName(String newName) {
        return new CordEntry(ownerId, location, newName, createdAt, publicEntry, tags);
    }

    public CordEntry withLocation(Location newLocation) {
        return new CordEntry(ownerId, newLocation, name, createdAt, publicEntry, tags);
    }

    public CordEntry withTags(List<String> newTags) {
        return new CordEntry(ownerId, location, name, createdAt, publicEntry, newTags);
    }

    public CordEntry withTagAdded(String tag) {
        ArrayList<String> updated = new ArrayList<>(tags);
        if (!updated.contains(tag)) {
            updated.add(tag);
        }
        return withTags(updated);
    }

    public CordEntry withTagRemoved(String tag) {
        ArrayList<String> updated = new ArrayList<>(tags);
        updated.remove(tag);
        return withTags(updated);
    }

    public String storageKey() {
        return name.toLowerCase(Locale.ROOT);
    }
}
