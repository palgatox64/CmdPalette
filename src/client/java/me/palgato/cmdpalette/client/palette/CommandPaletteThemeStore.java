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

    private CommandPaletteThemeStore() {
    }

    public static ThemeLibrary load() {
        ThemePreset defaultTheme = defaultTheme();

        if (!Files.exists(LIBRARY_PATH)) {
            List<ThemePreset> onlyDefault = new ArrayList<>();
            onlyDefault.add(defaultTheme);
            appendBundledEditableThemes(onlyDefault, true);
            return new ThemeLibrary(onlyDefault, DEFAULT_THEME_ID);
        }

        try (Reader reader = Files.newBufferedReader(LIBRARY_PATH)) {
            ThemeLibraryPayload payload = GSON.fromJson(reader, ThemeLibraryPayload.class);
            if (payload == null) {
                List<ThemePreset> onlyDefault = new ArrayList<>();
                onlyDefault.add(defaultTheme);
                appendBundledEditableThemes(onlyDefault, true);
                return new ThemeLibrary(onlyDefault, DEFAULT_THEME_ID);
            }

            List<ThemePreset> themes = new ArrayList<>();
            themes.add(defaultTheme);

            if (payload.themes != null) {
                Map<String, ThemePreset> unique = new LinkedHashMap<>();
                for (ThemePresetPayload presetPayload : payload.themes) {
                    ThemePreset preset = presetPayload == null ? null : presetPayload.toThemePreset();
                    if (preset == null) continue;
                    if (DEFAULT_THEME_ID.equals(preset.id())) continue;
                    unique.put(preset.id(), preset.withEditable(true));
                }
                themes.addAll(unique.values());
            }

            if (needsBundledThemeMigration(payload)) {
                boolean includeDracula = !payload.seededBaseThemes;
                removeDeprecatedBundledThemes(themes, payload);
                appendBundledEditableThemes(themes, includeDracula);
            }

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
        } catch (IOException | JsonSyntaxException ignored) {
            List<ThemePreset> onlyDefault = new ArrayList<>();
            onlyDefault.add(defaultTheme);
            appendBundledEditableThemes(onlyDefault, true);
            return new ThemeLibrary(onlyDefault, DEFAULT_THEME_ID);
        }
    }

    public static void save(ThemeLibrary library) {
        ThemeLibraryPayload payload = new ThemeLibraryPayload();
        payload.selectedThemeId = library.selectedThemeId();
        payload.themes = new ArrayList<>();
        payload.seededBaseThemes = true;
        payload.bundledThemesRevision = BUNDLED_THEMES_REVISION;

        for (ThemePreset theme : library.themes()) {
            if (DEFAULT_THEME_ID.equals(theme.id())) continue;
            payload.themes.add(ThemePresetPayload.fromThemePreset(theme.withEditable(true)));
        }

        try {
            Files.createDirectories(LIBRARY_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(LIBRARY_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static void exportTheme(ThemePreset theme) {
        ThemePresetPayload payload = ThemePresetPayload.fromThemePreset(theme);
        try {
            Files.createDirectories(EXPORT_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(EXPORT_PATH)) {
                GSON.toJson(payload, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static ThemePreset importTheme() {
        if (!Files.exists(IMPORT_PATH)) {
            return null;
        }

        try (Reader reader = Files.newBufferedReader(IMPORT_PATH)) {
            ThemePresetPayload payload = GSON.fromJson(reader, ThemePresetPayload.class);
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

    private static void removeDeprecatedBundledThemes(List<ThemePreset> themes, ThemeLibraryPayload payload) {
        if (payload.bundledThemesRevision < 5) {
            themes.removeIf(theme -> "base-light".equals(theme.id()));
        }
    }

    private static boolean needsBundledThemeMigration(ThemeLibraryPayload payload) {
        return !payload.seededBaseThemes || payload.bundledThemesRevision < BUNDLED_THEMES_REVISION;
    }

    private static void appendBundledEditableThemes(List<ThemePreset> themes, boolean includeDracula) {
        if (includeDracula && !containsThemeId(themes, BASE_THEME_DRACULA_ID)) {
            themes.add(draculaTheme());
        }
        if (!containsThemeId(themes, BASE_THEME_NORD_ID)) {
            themes.add(nordTheme());
        }
        if (!containsThemeId(themes, BASE_THEME_GRUVBOX_ID)) {
            themes.add(gruvboxTheme());
        }
        if (!containsThemeId(themes, BASE_THEME_MATRIX_ID)) {
            themes.add(matrixTheme());
        }
        if (!containsThemeId(themes, BASE_THEME_GITHUB_ID)) {
            themes.add(githubTheme());
        }
    }

    private static boolean containsThemeId(List<ThemePreset> themes, String id) {
        for (ThemePreset theme : themes) {
            if (id.equals(theme.id())) {
                return true;
            }
        }
        return false;
    }

    private static String sanitizeName(String name) {
        if (name == null) return "Imported Theme";
        String trimmed = name.trim();
        if (trimmed.isEmpty()) return "Imported Theme";
        return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
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
        private ThemeColors colors;

        private ThemePreset toThemePreset() {
            if (id == null || id.isBlank()) return null;
            if (colors == null) return null;
            return new ThemePreset(
                    id,
                    sanitizeName(name),
                    true,
                    colors
            );
        }

        private static ThemePresetPayload fromThemePreset(ThemePreset preset) {
            ThemePresetPayload payload = new ThemePresetPayload();
            payload.id = preset.id();
            payload.name = preset.name();
            payload.colors = preset.colors();
            return payload;
        }
    }
}
