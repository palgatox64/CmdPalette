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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class CommandCategoriesStore {

    public static final String DEFAULT_CATEGORY_NAME = "Favorites";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-categories.json");

    private CommandCategoriesStore() {
    }

    public static List<Category> load() {
        if (!Files.exists(FILE_PATH)) {
            return migrateFromLegacyFavorites();
        }

        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            CategoriesPayload payload = GSON.fromJson(reader, CategoriesPayload.class);
            if (payload == null || payload.categories == null) {
                return new ArrayList<>();
            }
            return sanitize(payload.categories);
        } catch (IOException | JsonSyntaxException ignored) {
            return new ArrayList<>();
        }
    }

    public static void save(List<Category> categories) {
        CategoriesPayload payload = new CategoriesPayload();
        payload.categories = sanitize(categories);

        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static List<Category> migrateFromLegacyFavorites() {
        List<String> favorites = FavoriteCommandsStore.load();
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }

        List<Category> migrated = new ArrayList<>();
        migrated.add(new Category(DEFAULT_CATEGORY_NAME, favorites));
        save(migrated);
        return migrated;
    }

    private static List<Category> sanitize(List<Category> categories) {
        Map<String, CategoryBucket> grouped = new LinkedHashMap<>();

        for (Category category : categories) {
            String name = normalizeCategoryName(category.name());
            if (name.isBlank()) continue;

            CategoryBucket bucket = grouped.computeIfAbsent(name.toLowerCase(), key -> new CategoryBucket(name));
            for (String command : category.commands()) {
                String normalizedCommand = normalizeCommand(command);
                if (!normalizedCommand.isBlank()) {
                    bucket.commands.add(normalizedCommand);
                }
            }
        }

        List<Category> sanitized = new ArrayList<>();
        for (CategoryBucket bucket : grouped.values()) {
            sanitized.add(new Category(bucket.displayName, new ArrayList<>(bucket.commands)));
        }

        return sanitized;
    }

    private static String normalizeCategoryName(String name) {
        if (name == null) return "";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "";
        if (trimmed.length() > 24) {
            return trimmed.substring(0, 24);
        }
        return trimmed;
    }

    private static String normalizeCommand(String command) {
        if (command == null) return "";
        String trimmed = command.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    public record Category(String name, List<String> commands) {
    }

    private static final class CategoryBucket {
        private final String displayName;
        private final LinkedHashSet<String> commands = new LinkedHashSet<>();

        private CategoryBucket(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class CategoriesPayload {
        private List<Category> categories = new ArrayList<>();
    }
}