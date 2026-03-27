package goshkow.cords.service;

import com.tchristofferson.configupdater.ConfigUpdater;
import goshkow.cords.CordsPlugin;
import goshkow.cords.util.ColorUtil;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class LanguagePack {
    private static File languageFile;
    private static FileConfiguration language;
    private static FileConfiguration fallbackLanguage;
    private static FileConfiguration bundledLanguage;
    private static boolean languageFileWritable;
    private static String selected = "en_us";

    private LanguagePack() {
    }

    public static void load(String requestedLanguage) {
        CordsPlugin plugin = CordsPlugin.getInstance();
        selected = normalizeLanguageCode(requestedLanguage);
        languageFile = ensureLanguageFile(plugin, selected);
        language = YamlConfiguration.loadConfiguration(languageFile);
        bundledLanguage = loadBundledLanguage(plugin, selected);
        fallbackLanguage = loadBundledLanguage(plugin, "en_us");
    }

    public static void reload() {
        if (languageFile != null) {
            ensureLanguageFile(CordsPlugin.getInstance(), selected);
            language = YamlConfiguration.loadConfiguration(languageFile);
            bundledLanguage = loadBundledLanguage(CordsPlugin.getInstance(), selected);
            fallbackLanguage = loadBundledLanguage(CordsPlugin.getInstance(), "en_us");
        }
    }

    public static String translate(String key) {
        if (language == null || key == null) {
            return "";
        }

        String value = language.getString(key);
        if ((value == null || value.isBlank()) && bundledLanguage != null) {
            value = bundledLanguage.getString(key);
        }
        if ((value == null || value.isBlank()) && key.startsWith("messages.")) {
            String legacyKey = "Pointcords." + key.substring("messages.".length());
            value = language.getString(legacyKey);
            if ((value == null || value.isBlank()) && bundledLanguage != null) {
                value = bundledLanguage.getString(legacyKey);
            }
        }
        if ((value == null || value.isBlank()) && key.startsWith("messages.")) {
            String commandScopedKey = "commands." + key.substring("messages.".length());
            value = language.getString(commandScopedKey);
            if ((value == null || value.isBlank()) && bundledLanguage != null) {
                value = bundledLanguage.getString(commandScopedKey);
            }
        }
        if ((value == null || value.isBlank()) && fallbackLanguage != null) {
            value = fallbackLanguage.getString(key);
            if ((value == null || value.isBlank()) && key.startsWith("messages.")) {
                String legacyKey = "Pointcords." + key.substring("messages.".length());
                value = fallbackLanguage.getString(legacyKey);
            }
            if ((value == null || value.isBlank()) && key.startsWith("messages.")) {
                String commandScopedKey = "commands." + key.substring("messages.".length());
                value = fallbackLanguage.getString(commandScopedKey);
            }
        }

        if (value == null || value.isBlank()) {
            return "";
        }
        return ColorUtil.colorize(value);
    }

    private static File ensureLanguageFile(CordsPlugin plugin, String languageCode) {
        String resolved = normalizeLanguageCode(languageCode);
        File resolvedFile = new File(plugin.getDataFolder(), "languages/" + resolved + ".yml");
        languageFileWritable = true;

        if (!resolvedFile.exists()) {
            try {
                plugin.saveResource("languages/" + resolved + ".yml", false);
            } catch (IllegalArgumentException ignored) {
                resolved = "en_us";
                resolvedFile = new File(plugin.getDataFolder(), "languages/en_us.yml");
            }
        }

        if (!resolvedFile.exists()) {
            plugin.saveResource("languages/en_us.yml", false);
            resolvedFile = new File(plugin.getDataFolder(), "languages/en_us.yml");
            resolved = "en_us";
        }

        try {
            ConfigUpdater.update(plugin, "languages/" + resolved + ".yml", resolvedFile);
        } catch (IOException exception) {
            languageFileWritable = false;
            plugin.getLogger().severe("Unable to update language file: " + resolved);
            exception.printStackTrace();
        }

        selected = resolved;
        languageFile = resolvedFile;
        return resolvedFile;
    }

    private static FileConfiguration loadBundledLanguage(CordsPlugin plugin, String languageCode) {
        String resolved = normalizeLanguageCode(languageCode);
        String resourcePath = "languages/" + resolved + ".yml";
        try (InputStream stream = plugin.getResource(resourcePath)) {
            if (stream == null) {
                if (!"en_us".equals(resolved)) {
                    return loadBundledLanguage(plugin, "en_us");
                }
                return null;
            }

            YamlConfiguration bundled = new YamlConfiguration();
            bundled.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return bundled;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().warning("Unable to load bundled language: " + resolved);
            return null;
        }
    }

    private static String normalizeLanguageCode(String languageCode) {
        if (languageCode == null || languageCode.isBlank()) {
            return "en_us";
        }

        String normalized = languageCode.trim().toLowerCase().replace('-', '_');
        return switch (normalized) {
            case "en" -> "en_us";
            case "ru" -> "ru_ru";
            case "es" -> "es_es";
            case "fr" -> "fr_fr";
            case "de" -> "de_de";
            case "pt" -> "pt_br";
            case "zh", "cn" -> "zh_cn";
            default -> normalized;
        };
    }

}
