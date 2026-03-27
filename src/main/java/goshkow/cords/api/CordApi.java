package goshkow.cords.api;

import goshkow.cords.model.CordEntry;
import goshkow.cords.service.CordRepository;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.UUID;

public final class CordApi {
    public CordEntry createCord(Player player, Location location, String name) {
        return CordRepository.create(player, location, name);
    }

    public CordEntry createPublicCord(Player player, Location location, String name) {
        return CordRepository.createPublic(player, location, name);
    }

    public void removeCord(Player player, CordEntry cord) {
        CordRepository.remove(player, cord);
    }

    public CordEntry renameCord(Player player, CordEntry cord, String newName) {
        return CordRepository.rename(player, cord, newName);
    }

    public CordEntry findCord(UUID ownerId, String name) {
        return CordRepository.find(ownerId, name);
    }

    public CordEntry findVisibleCord(UUID viewerId, String name) {
        return CordRepository.findVisible(viewerId, name);
    }

    public ArrayList<String> listCords(OfflinePlayer player) {
        return CordRepository.list(player.getUniqueId());
    }

    public ArrayList<String> listCords(UUID playerId) {
        return CordRepository.list(playerId);
    }

    public ArrayList<String> listVisibleCords(UUID viewerId) {
        return CordRepository.listVisible(viewerId);
    }

    public int countCords(UUID playerId) {
        return CordRepository.count(playerId);
    }

    public int countPersonalCords(UUID playerId) {
        return CordRepository.countPersonal(playerId);
    }

    public int countPublicCords(UUID playerId) {
        return CordRepository.countPublic(playerId);
    }
}
