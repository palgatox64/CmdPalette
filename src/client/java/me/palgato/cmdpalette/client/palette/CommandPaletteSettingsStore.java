package me.palgato.cmdpalette.client.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public final class CommandPaletteSettingsStore {

    public static final int DEFAULT_MAX_VISIBLE_ITEMS = 12;
    public static final int MIN_MAX_VISIBLE_ITEMS = 4;
    public static final int MAX_MAX_VISIBLE_ITEMS = 30;

    public enum ScopeMode {
        GLOBAL,
        PER_SERVER,
        PER_WORLD
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-settings.json");

    private CommandPaletteSettingsStore() {
    }

    public static Settings load() {
        if (!Files.exists(FILE_PATH)) {
            return new Settings(DEFAULT_MAX_VISIBLE_ITEMS, false, ScopeMode.GLOBAL);
        }

        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            SettingsPayload payload = GSON.fromJson(reader, SettingsPayload.class);
            if (payload == null) {
                return new Settings(DEFAULT_MAX_VISIBLE_ITEMS, false, ScopeMode.GLOBAL);
            }
            return sanitize(payload.toSettings());
        } catch (IOException | JsonSyntaxException ignored) {
            return new Settings(DEFAULT_MAX_VISIBLE_ITEMS, false, ScopeMode.GLOBAL);
        }
    }

    public static void save(Settings settings) {
        Settings sanitized = sanitize(settings);
        SettingsPayload payload = SettingsPayload.fromSettings(sanitized);

        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static Settings sanitize(Settings settings) {
        int clamped = Math.max(MIN_MAX_VISIBLE_ITEMS,
                Math.min(settings.maxVisibleItems(), MAX_MAX_VISIBLE_ITEMS));
        ScopeMode scopeMode = settings.scopeMode() == null ? ScopeMode.GLOBAL : settings.scopeMode();
        return new Settings(clamped, settings.hideSlashPrefix(), scopeMode);
    }

    public record Settings(int maxVisibleItems, boolean hideSlashPrefix, ScopeMode scopeMode) {
    }

    private static final class SettingsPayload {
        private Integer maxVisibleItems;
        private Boolean hideSlashPrefix;
        private String scopeMode;

        private Settings toSettings() {
            int visible = maxVisibleItems == null ? DEFAULT_MAX_VISIBLE_ITEMS : maxVisibleItems;
            boolean hideSlash = hideSlashPrefix != null && hideSlashPrefix;
            ScopeMode parsedScope = ScopeMode.GLOBAL;
            if (scopeMode != null && !scopeMode.isBlank()) {
                try {
                    parsedScope = ScopeMode.valueOf(scopeMode);
                } catch (IllegalArgumentException ignored) {
                    parsedScope = ScopeMode.GLOBAL;
                }
            }
            return new Settings(visible, hideSlash, parsedScope);
        }

        private static SettingsPayload fromSettings(Settings settings) {
            SettingsPayload payload = new SettingsPayload();
            payload.maxVisibleItems = settings.maxVisibleItems();
            payload.hideSlashPrefix = settings.hideSlashPrefix();
            payload.scopeMode = settings.scopeMode().name();
            return payload;
        }
    }
}
