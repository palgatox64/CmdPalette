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
import java.util.LinkedHashSet;
import java.util.List;

public final class FavoriteCommandsStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-favorites.json");

    private FavoriteCommandsStore() {
    }

    public static List<String> load() {
        if (!Files.exists(FILE_PATH)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            FavoritesPayload payload = GSON.fromJson(reader, FavoritesPayload.class);
            if (payload == null || payload.favorites == null) {
                return new ArrayList<>();
            }
            return sanitize(payload.favorites);
        } catch (IOException | JsonSyntaxException ignored) {
            return new ArrayList<>();
        }
    }

    public static void save(List<String> favorites) {
        FavoritesPayload payload = new FavoritesPayload();
        payload.favorites = sanitize(favorites);

        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> sanitize(List<String> favorites) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String favorite : favorites) {
            String normalized = normalize(favorite);
            if (!normalized.isBlank()) {
                unique.add(normalized);
            }
        }
        return new ArrayList<>(unique);
    }

    private static String normalize(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
    }

    private static final class FavoritesPayload {
        private List<String> favorites = new ArrayList<>();
    }
}