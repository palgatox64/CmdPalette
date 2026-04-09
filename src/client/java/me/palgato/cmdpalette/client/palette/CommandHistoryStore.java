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

public final class CommandHistoryStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-history.json");

    private CommandHistoryStore() {
    }

    public static List<String> load() {
        if (!Files.exists(FILE_PATH)) {
            return new ArrayList<>();
        }

        try (Reader reader = Files.newBufferedReader(FILE_PATH)) {
            HistoryPayload payload = GSON.fromJson(reader, HistoryPayload.class);
            if (payload == null || payload.history == null) {
                return new ArrayList<>();
            }
            return sanitize(payload.history);
        } catch (IOException | JsonSyntaxException ignored) {
            return new ArrayList<>();
        }
    }

    public static void save(List<String> history) {
        HistoryPayload payload = new HistoryPayload();
        payload.history = sanitize(history);

        try {
            Files.createDirectories(FILE_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    private static List<String> sanitize(List<String> history) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String command : history) {
            String normalized = normalize(command);
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
        return trimmed;
    }

    private static final class HistoryPayload {
        private List<String> history = new ArrayList<>();
    }
}