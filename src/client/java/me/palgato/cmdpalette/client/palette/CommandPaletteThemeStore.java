package me.palgato.cmdpalette.client.palette;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CommandPaletteThemeStore {

    public static final String DEFAULT_THEME_ID = "default";
    public static final String BASE_THEME_DRACULA_ID = "base-dracula";
    public static final String BASE_THEME_NORD_ID = "base-nord";
    public static final String BASE_THEME_GRUVBOX_ID = "base-gruvbox";
    public static final String BASE_THEME_MATRIX_ID = "base-matrix";
    public static final String BASE_THEME_GITHUB_ID = "base-github";
    private static final int BUNDLED_THEMES_REVISION = 5;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path LIBRARY_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-themes.json");
    private static final Path EXPORT_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-theme-export.json");
    private static final Path IMPORT_PATH = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette-theme-import.json");
        private static final Path THEMES_DIR = FabricLoader.getInstance()
            .getConfigDir()
            .resolve("cmdpalette")
            .resolve("themes");

    private CommandPaletteThemeStore() {
    }

    public static ThemeLibrary load() {
        ThemePreset defaultTheme = defaultTheme();

        ThemeLibraryPayload payload = readLibraryPayload();

        Map<String, ThemePreset> unique = new LinkedHashMap<>();

        if (payload.themes != null) {
            for (ThemePresetPayload presetPayload : payload.themes) {
                ThemePreset preset = presetPayload == null ? null : presetPayload.toThemePreset();
                if (preset == null) continue;
                if (DEFAULT_THEME_ID.equals(preset.id())) continue;
                unique.put(preset.id(), preset.withEditable(true));
            }
        }

        for (ThemePreset preset : readThemesFromFiles()) {
            if (preset == null) continue;
            if (DEFAULT_THEME_ID.equals(preset.id())) continue;
            unique.put(preset.id(), preset.withEditable(true));
        }

        List<ThemePreset> themes = new ArrayList<>();
        themes.add(defaultTheme);

        if (needsBundledThemeMigration(payload)) {
            boolean includeDracula = !payload.seededBaseThemes;
            removeDeprecatedBundledThemes(unique, payload);
            appendBundledEditableThemes(unique, includeDracula);
        }

        themes.addAll(unique.values());

        String selectedId = payload.selectedThemeId;
        boolean exists = false;
        if (selectedId != null) {
            for (ThemePreset theme : themes) {
                if (theme.id().equals(selectedId)) {
                    exists = true;
                    break;
                }
            }
        }

        if (selectedId == null || !exists) {
            selectedId = DEFAULT_THEME_ID;
        }

        return new ThemeLibrary(themes, selectedId);
    }

    private static ThemeLibraryPayload readLibraryPayload() {
        if (!Files.exists(LIBRARY_PATH)) {
            return new ThemeLibraryPayload();
        }

        try (Reader reader = Files.newBufferedReader(LIBRARY_PATH)) {
            ThemeLibraryPayload payload = GSON.fromJson(reader, ThemeLibraryPayload.class);
            return payload == null ? new ThemeLibraryPayload() : payload;
        } catch (IOException | JsonSyntaxException ignored) {
            return new ThemeLibraryPayload();
        }
    }

    public static void save(ThemeLibrary library) {
        ThemeLibraryPayload payload = new ThemeLibraryPayload();
        payload.selectedThemeId = library.selectedThemeId();
        payload.themes = new ArrayList<>();
        payload.seededBaseThemes = true;
        payload.bundledThemesRevision = BUNDLED_THEMES_REVISION;

        try {
            Files.createDirectories(LIBRARY_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(LIBRARY_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }

        writeThemesToFiles(library.themes());
    }

    private static void writeThemesToFiles(List<ThemePreset> themes) {
        try {
            Files.createDirectories(THEMES_DIR);
        } catch (IOException ignored) {
            return;
        }

        for (ThemePreset theme : themes) {
            if (theme == null) continue;
            if (DEFAULT_THEME_ID.equals(theme.id())) continue;
            if (theme.id().isBlank()) continue;
            Path path = getThemePath(theme.id());
            ThemePresetPayload payload = ThemePresetPayload.fromThemePreset(theme.withEditable(true));
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(payload, writer);
            } catch (IOException ignored) {
            }
        }
    }

    private static List<ThemePreset> readThemesFromFiles() {
        List<ThemePreset> presets = new ArrayList<>();
        if (!Files.exists(THEMES_DIR)) {
            return presets;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(THEMES_DIR, "*.json")) {
            for (Path path : stream) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    ThemePresetPayload payload = ThemePresetPayload.readFromReader(reader);
                    ThemePreset preset = payload == null ? null : payload.toThemePreset();
                    if (preset != null) {
                        presets.add(preset.withEditable(true));
                    }
                } catch (IOException | JsonSyntaxException ignored) {
                }
            }
        } catch (IOException ignored) {
        }

        return presets;
    }

    public static Path exportTheme(ThemePreset theme) {
        ThemePresetPayload payload = ThemePresetPayload.fromThemePreset(theme);
        try {
            Files.createDirectories(EXPORT_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(EXPORT_PATH)) {
                GSON.toJson(payload, writer);
            }
            return EXPORT_PATH;
        } catch (IOException ignored) {
            return EXPORT_PATH;
        }
    }

    public static ThemePreset importTheme() {
        Path source = Files.exists(IMPORT_PATH)
                ? IMPORT_PATH
                : (Files.exists(EXPORT_PATH) ? EXPORT_PATH : null);

        if (source == null) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(source)) {
            ThemePresetPayload payload = ThemePresetPayload.readFromReader(reader);
            if (payload == null) return null;
            ThemePreset preset = payload.toThemePreset();
            if (preset == null) return null;

            return new ThemePreset(
                    "theme-" + UUID.randomUUID(),
                    sanitizeName(preset.name()),
                    true,
                    preset.colors()
            );
        } catch (IOException | JsonSyntaxException ignored) {
            return null;
        }
    }

    public static Path writeImportTemplate(ThemePreset theme) {
        ThemePresetPayload payload = ThemePresetPayload.fromThemePreset(theme);
        try {
            Files.createDirectories(IMPORT_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(IMPORT_PATH)) {
                GSON.toJson(payload, writer);
            }
            return IMPORT_PATH;
        } catch (IOException ignored) {
            return IMPORT_PATH;
        }
    }

    public static Path writeThemeFile(ThemePreset theme) {
        if (theme == null || theme.id() == null || theme.id().isBlank()) {
            return THEMES_DIR.resolve("theme.json");
        }
        Path path = getThemePath(theme.id());
        ThemePresetPayload payload = ThemePresetPayload.fromThemePreset(theme.withEditable(true));
        try {
            Files.createDirectories(THEMES_DIR);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
        return path;
    }

    public static void deleteThemeFile(String themeId) {
        if (themeId == null || themeId.isBlank() || DEFAULT_THEME_ID.equals(themeId)) {
            return;
        }

        Path path = getThemePath(themeId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static Path getThemePath(String themeId) {
        String safeId = themeId == null ? "theme" : themeId.replaceAll("[^a-zA-Z0-9._-]", "_");
        return THEMES_DIR.resolve(safeId + ".json");
    }

    public static Path getLibraryPath() {
        return LIBRARY_PATH;
    }

    public static Path getImportPath() {
        return IMPORT_PATH;
    }

    public static Path getExportPath() {
        return EXPORT_PATH;
    }

    public static Path getThemesDirectoryPath() {
        return THEMES_DIR;
    }

    public static long getThemeStoreLastModifiedMillis() {
        long latest = getLastModifiedMillis(LIBRARY_PATH);
        latest = Math.max(latest, getLastModifiedMillis(THEMES_DIR));

        if (Files.exists(THEMES_DIR)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(THEMES_DIR, "*.json")) {
                for (Path path : stream) {
                    latest = Math.max(latest, getLastModifiedMillis(path));
                }
            } catch (IOException ignored) {
            }
        }

        return latest;
    }

    public static long getLastModifiedMillis(Path path) {
        if (path == null || !Files.exists(path)) {
            return -1L;
        }
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ignored) {
            return -1L;
        }
    }

    public static ThemePreset defaultTheme() {
        return new ThemePreset(
                DEFAULT_THEME_ID,
                "Default",
                false,
                new ThemeColors(
                        0xB0000000,
                        0xFF1A1A1A,
                        0xFF0A0A0A,
                        0xFF4A4A4A,
                        0xFF242424,
                        0xFF333333,
                        0xFF2E2E2E,
                        0xFF3A3A3A,
                        0xFF555555,
                        0xFF505050,
                        0xFFFFD75E,
                        0xFF808080,
                        0xFF2A2A2A,
                        0xFF3B3B3B,
                        0xFF4A3F2A,
                        0xFF888888,
                        0xFF5AF5E2,
                        0xFFFFE066,
                        0xFFFF6B6B,
                        0xFFFFB347,
                        0xFF98E66B,
                        0xFF7EC8E3
                )
        );
    }

    public static ThemePreset draculaTheme() {
        return new ThemePreset(
                BASE_THEME_DRACULA_ID,
                "Dracula",
                true,
                new ThemeColors(
                        0xB0282A36,
                        0xFF282A36,
                        0xFF1E1F29,
                        0xFF44475A,
                        0xFF303341,
                        0xFF44475A,
                        0xFF383A4A,
                        0xFF44475A,
                        0xFF6272A4,
                        0xFF6272A4,
                        0xFFF1FA8C,
                        0xFF7A8096,
                        0xFF343746,
                        0xFF44475A,
                        0xFF5A4A3A,
                        0xFF8BE9FD,
                        0xFF50FA7B,
                        0xFFFF79C6,
                        0xFFFF5555,
                        0xFFF1FA8C,
                        0xFFBD93F9,
                        0xFF8BE9FD
                )
        );
    }

    public static ThemePreset nordTheme() {
        return new ThemePreset(
                BASE_THEME_NORD_ID,
                "Nord",
                true,
                new ThemeColors(
                        0xB02E3440,
                        0xFF2E3440,
                        0xFF242933,
                        0xFF434C5E,
                        0xFF3B4252,
                        0xFF4C566A,
                        0xFF434C5E,
                        0xFF4C566A,
                        0xFF81A1C1,
                        0xFF81A1C1,
                        0xFFEBCB8B,
                        0xFF6B7588,
                        0xFF3B4252,
                        0xFF4C566A,
                        0xFF5B4C3A,
                        0xFF88C0D0,
                        0xFF8FBCBB,
                        0xFFB48EAD,
                        0xFFBF616A,
                        0xFFD08770,
                        0xFFA3BE8C,
                        0xFF88C0D0
                )
        );
    }

    public static ThemePreset gruvboxTheme() {
        return new ThemePreset(
                BASE_THEME_GRUVBOX_ID,
                "Gruvbox",
                true,
                new ThemeColors(
                        0xB0282828,
                        0xFF282828,
                        0xFF1D2021,
                        0xFF504945,
                        0xFF32302F,
                        0xFF504945,
                        0xFF3C3836,
                        0xFF504945,
                        0xFFD79921,
                        0xFFD79921,
                        0xFFFABD2F,
                        0xFF7C6F64,
                        0xFF3C3836,
                        0xFF504945,
                        0xFF665C54,
                        0xFF83A598,
                        0xFFB8BB26,
                        0xFFD3869B,
                        0xFFFB4934,
                        0xFFFE8019,
                        0xFFB8BB26,
                        0xFF8EC07C
                )
        );
    }

    public static ThemePreset matrixTheme() {
        return new ThemePreset(
                BASE_THEME_MATRIX_ID,
                "Matrix",
                true,
                new ThemeColors(
                        0xB0001000,
                        0xFF001100,
                        0xFF000700,
                        0xFF003300,
                        0xFF001A00,
                        0xFF003800,
                        0xFF002600,
                        0xFF003300,
                        0xFF00AA00,
                        0xFF00AA00,
                        0xFF00FF66,
                        0xFF006600,
                        0xFF001F00,
                        0xFF003300,
                        0xFF2A3A00,
                        0xFF00AA00,
                        0xFF00FF66,
                        0xFF00E676,
                        0xFF00CC44,
                        0xFF66FF66,
                        0xFF00FF66,
                        0xFF00E676
                )
        );
    }

    public static ThemePreset githubTheme() {
        return new ThemePreset(
                BASE_THEME_GITHUB_ID,
                "GitHub",
                true,
                new ThemeColors(
                        0xB00D1117,
                        0xFF0D1117,
                        0xFF010409,
                        0xFF30363D,
                        0xFF161B22,
                        0xFF30363D,
                        0xFF1F2937,
                        0xFF21262D,
                        0xFF8B949E,
                        0xFF30363D,
                        0xFFE3B341,
                        0xFF6E7681,
                        0xFF21262D,
                        0xFF30363D,
                        0xFF4A4A2A,
                        0xFF79C0FF,
                        0xFF58A6FF,
                        0xFFD2A8FF,
                        0xFFFF7B72,
                        0xFFFFA657,
                        0xFF7EE787,
                        0xFF79C0FF
                )
        );
    }

    private static void removeDeprecatedBundledThemes(Map<String, ThemePreset> themes, ThemeLibraryPayload payload) {
        if (payload.bundledThemesRevision < 5) {
            themes.remove("base-light");
        }
    }

    private static boolean needsBundledThemeMigration(ThemeLibraryPayload payload) {
        return !payload.seededBaseThemes || payload.bundledThemesRevision < BUNDLED_THEMES_REVISION;
    }

    private static void appendBundledEditableThemes(Map<String, ThemePreset> themes, boolean includeDracula) {
        if (includeDracula && !themes.containsKey(BASE_THEME_DRACULA_ID)) {
            themes.put(BASE_THEME_DRACULA_ID, draculaTheme());
        }
        if (!themes.containsKey(BASE_THEME_NORD_ID)) {
            themes.put(BASE_THEME_NORD_ID, nordTheme());
        }
        if (!themes.containsKey(BASE_THEME_GRUVBOX_ID)) {
            themes.put(BASE_THEME_GRUVBOX_ID, gruvboxTheme());
        }
        if (!themes.containsKey(BASE_THEME_MATRIX_ID)) {
            themes.put(BASE_THEME_MATRIX_ID, matrixTheme());
        }
        if (!themes.containsKey(BASE_THEME_GITHUB_ID)) {
            themes.put(BASE_THEME_GITHUB_ID, githubTheme());
        }
    }

    private static String sanitizeName(String name) {
        if (name == null) return "Imported Theme";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "Imported Theme";
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
    }

    private static String colorToHex(int value) {
        return String.format("#%08X", value);
    }

    private static Integer parseColorValue(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }

        try {
            if (element.getAsJsonPrimitive().isNumber()) {
                return element.getAsInt();
            }

            String text = element.getAsString();
            if (text == null) return null;
            String normalized = text.trim();
            if (normalized.isEmpty()) return null;
            if (normalized.startsWith("#")) {
                normalized = normalized.substring(1);
            }

            if (normalized.length() != 6 && normalized.length() != 8) {
                return null;
            }

            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() == 6) {
                return (int) (0xFF000000L | parsed);
            }
            return (int) (parsed & 0xFFFFFFFFL);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseColorText(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }
        if (normalized.length() != 6 && normalized.length() != 8) {
            return null;
        }

        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() == 6) {
                return (int) (0xFF000000L | parsed);
            }
            return (int) (parsed & 0xFFFFFFFFL);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    public record ThemeLibrary(List<ThemePreset> themes, String selectedThemeId) {
    }

    public record ThemePreset(String id, String name, boolean editable, ThemeColors colors) {
        public ThemePreset withEditable(boolean editable) {
            return new ThemePreset(id, name, editable, colors);
        }
    }

    public record ThemeColors(
            int overlay,
            int bg,
            int shadow,
            int accent,
            int inputBg,
            int separator,
            int hover,
            int selected,
            int scrollbar,
            int borderTop,
            int star,
            int starOff,
            int buttonBg,
            int buttonActive,
            int categoryActive,
            int slash,
            int command,
            int selector,
            int coordinate,
            int string,
            int number,
            int bool
    ) {
    }

    private static final class ThemeLibraryPayload {
        private String selectedThemeId = DEFAULT_THEME_ID;
        private List<ThemePresetPayload> themes = new ArrayList<>();
        private boolean seededBaseThemes = false;
        private int bundledThemesRevision = 0;
    }

    private static final class ThemePresetPayload {
        private String id;
        private String name;
        private ThemeColorsPayload colors;

        private ThemePreset toThemePreset() {
            if (id == null || id.isBlank()) return null;
            if (colors == null) return null;
            ThemeColors decodedColors = colors.toThemeColors();
            if (decodedColors == null) return null;
            return new ThemePreset(
                    id,
                    sanitizeName(name),
                    true,
                    decodedColors
            );
        }

        private static ThemePresetPayload fromThemePreset(ThemePreset preset) {
            ThemePresetPayload payload = new ThemePresetPayload();
            payload.id = preset.id();
            payload.name = preset.name();
            payload.colors = ThemeColorsPayload.fromThemeColors(preset.colors());
            return payload;
        }

        private static ThemePresetPayload fromJsonObject(JsonObject object) {
            if (object == null) return null;
            ThemePresetPayload payload = new ThemePresetPayload();
            JsonElement idElement = object.get("id");
            JsonElement nameElement = object.get("name");
            payload.id = (idElement == null || idElement.isJsonNull()) ? null : idElement.getAsString();
            payload.name = (nameElement == null || nameElement.isJsonNull()) ? null : nameElement.getAsString();
            payload.colors = ThemeColorsPayload.fromJsonObject(object.getAsJsonObject("colors"));
            return payload;
        }

        private static ThemePresetPayload readFromReader(Reader reader) {
            try {
                JsonObject object = GSON.fromJson(reader, JsonObject.class);
                return fromJsonObject(object);
            } catch (JsonSyntaxException ignored) {
                return null;
            }
        }
    }

    private static final class ThemeColorsPayload {
        private String overlay;
        private String bg;
        private String shadow;
        private String accent;
        private String inputBg;
        private String separator;
        private String hover;
        private String selected;
        private String scrollbar;
        private String borderTop;
        private String star;
        private String starOff;
        private String buttonBg;
        private String buttonActive;
        private String categoryActive;
        private String slash;
        private String command;
        private String selector;
        private String coordinate;
        private String string;
        private String number;
        private String bool;

        private ThemeColors toThemeColors() {
            Integer overlayValue = parseColorText(overlay);
            Integer bgValue = parseColorText(bg);
            Integer shadowValue = parseColorText(shadow);
            Integer accentValue = parseColorText(accent);
            Integer inputBgValue = parseColorText(inputBg);
            Integer separatorValue = parseColorText(separator);
            Integer hoverValue = parseColorText(hover);
            Integer selectedValue = parseColorText(selected);
            Integer scrollbarValue = parseColorText(scrollbar);
            Integer borderTopValue = parseColorText(borderTop);
            Integer starValue = parseColorText(star);
            Integer starOffValue = parseColorText(starOff);
            Integer buttonBgValue = parseColorText(buttonBg);
            Integer buttonActiveValue = parseColorText(buttonActive);
            Integer categoryActiveValue = parseColorText(categoryActive);
            Integer slashValue = parseColorText(slash);
            Integer commandValue = parseColorText(command);
            Integer selectorValue = parseColorText(selector);
            Integer coordinateValue = parseColorText(coordinate);
            Integer stringValue = parseColorText(string);
            Integer numberValue = parseColorText(number);
            Integer boolValue = parseColorText(bool);

            if (overlayValue == null || bgValue == null || shadowValue == null || accentValue == null
                    || inputBgValue == null || separatorValue == null || hoverValue == null || selectedValue == null
                    || scrollbarValue == null || borderTopValue == null || starValue == null || starOffValue == null
                    || buttonBgValue == null || buttonActiveValue == null || categoryActiveValue == null
                    || slashValue == null || commandValue == null || selectorValue == null || coordinateValue == null
                    || stringValue == null || numberValue == null || boolValue == null) {
                return null;
            }

            return new ThemeColors(
                    overlayValue,
                    bgValue,
                    shadowValue,
                    accentValue,
                    inputBgValue,
                    separatorValue,
                    hoverValue,
                    selectedValue,
                    scrollbarValue,
                    borderTopValue,
                    starValue,
                    starOffValue,
                    buttonBgValue,
                    buttonActiveValue,
                    categoryActiveValue,
                    slashValue,
                    commandValue,
                    selectorValue,
                    coordinateValue,
                    stringValue,
                    numberValue,
                    boolValue
            );
        }

        private static ThemeColorsPayload fromThemeColors(ThemeColors colors) {
            ThemeColorsPayload payload = new ThemeColorsPayload();
            payload.overlay = colorToHex(colors.overlay());
            payload.bg = colorToHex(colors.bg());
            payload.shadow = colorToHex(colors.shadow());
            payload.accent = colorToHex(colors.accent());
            payload.inputBg = colorToHex(colors.inputBg());
            payload.separator = colorToHex(colors.separator());
            payload.hover = colorToHex(colors.hover());
            payload.selected = colorToHex(colors.selected());
            payload.scrollbar = colorToHex(colors.scrollbar());
            payload.borderTop = colorToHex(colors.borderTop());
            payload.star = colorToHex(colors.star());
            payload.starOff = colorToHex(colors.starOff());
            payload.buttonBg = colorToHex(colors.buttonBg());
            payload.buttonActive = colorToHex(colors.buttonActive());
            payload.categoryActive = colorToHex(colors.categoryActive());
            payload.slash = colorToHex(colors.slash());
            payload.command = colorToHex(colors.command());
            payload.selector = colorToHex(colors.selector());
            payload.coordinate = colorToHex(colors.coordinate());
            payload.string = colorToHex(colors.string());
            payload.number = colorToHex(colors.number());
            payload.bool = colorToHex(colors.bool());
            return payload;
        }

        private static ThemeColorsPayload fromJsonObject(JsonObject object) {
            if (object == null) return null;

            ThemeColorsPayload payload = new ThemeColorsPayload();
            payload.overlay = normalizeColorString(object.get("overlay"));
            payload.bg = normalizeColorString(object.get("bg"));
            payload.shadow = normalizeColorString(object.get("shadow"));
            payload.accent = normalizeColorString(object.get("accent"));
            payload.inputBg = normalizeColorString(object.get("inputBg"));
            payload.separator = normalizeColorString(object.get("separator"));
            payload.hover = normalizeColorString(object.get("hover"));
            payload.selected = normalizeColorString(object.get("selected"));
            payload.scrollbar = normalizeColorString(object.get("scrollbar"));
            payload.borderTop = normalizeColorString(object.get("borderTop"));
            payload.star = normalizeColorString(object.get("star"));
            payload.starOff = normalizeColorString(object.get("starOff"));
            payload.buttonBg = normalizeColorString(object.get("buttonBg"));
            payload.buttonActive = normalizeColorString(object.get("buttonActive"));
            payload.categoryActive = normalizeColorString(object.get("categoryActive"));
            payload.slash = normalizeColorString(object.get("slash"));
            payload.command = normalizeColorString(object.get("command"));
            payload.selector = normalizeColorString(object.get("selector"));
            payload.coordinate = normalizeColorString(object.get("coordinate"));
            payload.string = normalizeColorString(object.get("string"));
            payload.number = normalizeColorString(object.get("number"));
            payload.bool = normalizeColorString(object.get("bool"));
            return payload;
        }

        private static String normalizeColorString(JsonElement element) {
            Integer parsed = parseColorValue(element);
            return parsed == null ? null : colorToHex(parsed);
        }
    }
}
