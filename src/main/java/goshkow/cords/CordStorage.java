package goshkow.cords;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class CordStorage {
    private static File file;
    private static FileConfiguration configuration;

    private CordStorage() {
    }

    public static void init(CordsPlugin plugin) {
        file = new File(plugin.getDataFolder(), "cords.yml");
        if (!file.exists()) {
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                file.createNewFile();
            } catch (IOException exception) {
                plugin.getLogger().severe("Unable to create cords.yml");
                exception.printStackTrace();
            }
        }
        configuration = YamlConfiguration.loadConfiguration(file);
    }

    public static FileConfiguration get() {
        return configuration;
    }

    public static void reload() {
        if (file != null) {
            configuration = YamlConfiguration.loadConfiguration(file);
        }
    }

    public static void save() {
        if (configuration == null || file == null) {
            return;
        }

        try {
            configuration.save(file);
        } catch (IOException exception) {
            CordsPlugin.getInstance().getLogger().severe("Unable to save cords.yml");
            exception.printStackTrace();
        }
    }

    public static void backup() {
        if (file == null || !file.exists()) {
            return;
        }

        File parent = file.getParentFile();
        if (parent == null) {
            return;
        }

        File backupDir = new File(parent, "backups");
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return;
        }

        String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        File backupFile = new File(backupDir, "cords-" + timestamp + ".yml");
        try {
            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            pruneBackups(backupDir.toPath(), 2);
        } catch (IOException exception) {
            CordsPlugin.getInstance().getLogger().warning("Unable to create cords.yml backup.");
            exception.printStackTrace();
        }
    }

    private static void pruneBackups(Path backupDir, int keepLatest) {
        try {
            List<Path> backups = new ArrayList<>();
            try (var stream = Files.list(backupDir)) {
                stream.filter(path -> {
                            String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
                            return name.startsWith("cords-") && name.endsWith(".yml");
                        })
                        .sorted(Comparator.comparingLong((Path path) -> {
                            try {
                                return Files.getLastModifiedTime(path).toMillis();
                            } catch (IOException exception) {
                                return 0L;
                            }
                        }).reversed())
                        .forEach(backups::add);
            }

            for (int index = keepLatest; index < backups.size(); index++) {
                Files.deleteIfExists(backups.get(index));
            }
        } catch (IOException exception) {
            CordsPlugin.getInstance().getLogger().warning("Unable to prune cords.yml backups.");
            exception.printStackTrace();
        }
    }
}
