package me.palgato.cmdpalette.client.palette;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ClientCommandSource;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandPaletteScreen extends Screen {

    private static final int PALETTE_MAX_WIDTH = 520;
    private static final int PALETTE_MIN_WIDTH = 320;
    private static final int INPUT_HEIGHT = 20;
    private static final int SUGGESTION_ITEM_HEIGHT = 18;
    private static final int SETTINGS_ROW_HEIGHT = 22;
    private static final int SETTINGS_BUTTON_WIDTH = 20;
    private static final int PADDING = 8;
    private static final int NAVBAR_HEIGHT = 18;
    private static final int NAVBAR_GAP = 6;
    private static final int STAR_BUTTON_SIZE = 20;
    private static final int STAR_GAP = 4;
    private static final int STAR_ROW_SIZE = 12;
    private static final int TAB_BUTTON_WIDTH = 72;
    private static final int TAB_GAP = 4;
    private static final int CATEGORY_TAB_MIN_WIDTH = 64;
    private static final int CATEGORY_TAB_MAX_WIDTH = 120;
    private static final int CATEGORY_PICKER_WIDTH = 180;
    private static final int CATEGORY_PICKER_ROW_HEIGHT = 18;
    private static final int MAX_HISTORY_ENTRIES = 100;
    private static final Pattern CUSTOM_THEME_NUMBER_PATTERN = Pattern.compile("^theme-(\\d+)$");

    private int COLOR_OVERLAY = 0xB0000000;
    private int COLOR_BG = 0xFF1A1A1A;
    private int COLOR_SHADOW = 0xFF0A0A0A;
    private int COLOR_ACCENT = 0xFF4A4A4A;
    private int COLOR_INPUT_BG = 0xFF242424;
    private int COLOR_SEPARATOR = 0xFF333333;
    private int COLOR_HOVER = 0xFF2E2E2E;
    private int COLOR_SELECTED = 0xFF3A3A3A;
    private int COLOR_SCROLLBAR = 0xFF555555;
    private int COLOR_BORDER_TOP = 0xFF505050;
    private int COLOR_STAR = 0xFFFFD75E;
    private int COLOR_STAR_OFF = 0xFF808080;
    private int COLOR_BUTTON_BG = 0xFF2A2A2A;
    private int COLOR_BUTTON_ACTIVE = 0xFF3B3B3B;
    private int COLOR_CATEGORY_ACTIVE = 0xFF4A3F2A;

    private int COLOR_SLASH = 0xFF888888;
    private int COLOR_COMMAND = 0xFF5AF5E2;
    private int COLOR_SELECTOR = 0xFFFFE066;
    private int COLOR_COORDINATE = 0xFFFF6B6B;
    private int COLOR_STRING = 0xFFFFB347;
    private int COLOR_NUMBER = 0xFF98E66B;
    private int COLOR_BOOLEAN = 0xFF7EC8E3;

    private static final int[] POSITION_PALETTE = {
            0xFFE897FF,
            0xFF6BC5F7,
            0xFFFFD580,
            0xFFFF9E9E,
            0xFF7EEACC,
            0xFFDDA0F5,
    };

    private TextFieldWidget inputField;
    private TextFieldWidget categoryInputField;
    private TextFieldWidget themeRenameInputField;
    private TextFieldWidget themeHexInputField;
    private final List<String> suggestions = new CopyOnWriteArrayList<>();
    private final List<CommandCategoriesStore.Category> categories = new CopyOnWriteArrayList<>();
    private final List<String> history = new CopyOnWriteArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int selectedCategoryIndex = -1;
    private int categoryScrollIndex = 0;
    private boolean categoryPickerOpen = false;
    private final List<CommandPaletteThemeStore.ThemePreset> themeLibrary = new ArrayList<>();
    private int selectedThemeIndex = 0;
    private int selectedThemeAspectIndex = 0;
    private long themeLibraryLastModifiedMillis = -1L;
    private long lastThemeLibraryCheckMillis = 0L;
    private static final long THEME_LIBRARY_CHECK_INTERVAL_MS = 1000L;
    private int maxVisibleItems = CommandPaletteSettingsStore.DEFAULT_MAX_VISIBLE_ITEMS;
    private boolean hideSlashPrefix = false;
    private boolean creatingCategoryInput = false;
    private boolean renamingTheme = false;
    private ViewMode viewBeforeSettings = ViewMode.COMMANDS;
    private ViewMode currentView = ViewMode.COMMANDS;

    private String cachedColorText = "";
    private int[] cachedColors = new int[0];

    private enum ViewMode {
        COMMANDS,
        CATEGORY,
        HISTORY,
        SETTINGS
    }

    private enum ThemeAspect {
        OVERLAY("screen.cmdpalette.theme.aspect.overlay"),
        BG("screen.cmdpalette.theme.aspect.bg"),
        SHADOW("screen.cmdpalette.theme.aspect.shadow"),
        ACCENT("screen.cmdpalette.theme.aspect.accent"),
        INPUT_BG("screen.cmdpalette.theme.aspect.input_bg"),
        SEPARATOR("screen.cmdpalette.theme.aspect.separator"),
        HOVER("screen.cmdpalette.theme.aspect.hover"),
        SELECTED("screen.cmdpalette.theme.aspect.selected"),
        SCROLLBAR("screen.cmdpalette.theme.aspect.scrollbar"),
        BORDER_TOP("screen.cmdpalette.theme.aspect.border_top"),
        STAR("screen.cmdpalette.theme.aspect.star"),
        STAR_OFF("screen.cmdpalette.theme.aspect.star_off"),
        BUTTON_BG("screen.cmdpalette.theme.aspect.button_bg"),
        BUTTON_ACTIVE("screen.cmdpalette.theme.aspect.button_active"),
        CATEGORY_ACTIVE("screen.cmdpalette.theme.aspect.category_active"),
        SLASH("screen.cmdpalette.theme.aspect.slash"),
        COMMAND("screen.cmdpalette.theme.aspect.command"),
        SELECTOR("screen.cmdpalette.theme.aspect.selector"),
        COORDINATE("screen.cmdpalette.theme.aspect.coordinate"),
        STRING("screen.cmdpalette.theme.aspect.string"),
        NUMBER("screen.cmdpalette.theme.aspect.number"),
        BOOLEAN("screen.cmdpalette.theme.aspect.boolean");

        private final String translationKey;

        ThemeAspect(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    public CommandPaletteScreen() {
        super(Text.translatable("screen.cmdpalette.command_palette"));
    }

    @Override
    protected void init() {
        int inputX = getInputX();
        int inputY = getInputY();
        int inputWidth = getInputWidth();

        inputField = new TextFieldWidget(
                this.textRenderer,
                inputX,
                inputY,
                inputWidth,
                INPUT_HEIGHT,
                Text.empty()
        );
        inputField.setMaxLength(256);
        inputField.setDrawsBackground(false);
        inputField.setEditableColor(0xFFC5C8C6);
        inputField.setChangedListener(this::onInputChanged);
        inputField.addFormatter(this::formatInputText);

        categoryInputField = new TextFieldWidget(
            this.textRenderer,
            getCategoryInputX(),
            getCategoryInputY(),
            getCategoryInputWidth(),
            NAVBAR_HEIGHT,
            Text.empty()
        );
        categoryInputField.setMaxLength(24);
        categoryInputField.setDrawsBackground(true);
        categoryInputField.setEditableColor(0xFFC5C8C6);
        categoryInputField.setVisible(false);

        themeRenameInputField = new TextFieldWidget(
            this.textRenderer,
            getInputX() + 80,
            getSettingsContentY() + (SETTINGS_ROW_HEIGHT + 6) * 2,
            Math.max(40, getInputWidth() - 170),
            NAVBAR_HEIGHT,
            Text.empty()
        );
        themeRenameInputField.setMaxLength(32);
        themeRenameInputField.setDrawsBackground(true);
        themeRenameInputField.setEditableColor(0xFFC5C8C6);
        themeRenameInputField.setVisible(false);

        themeHexInputField = new TextFieldWidget(
            this.textRenderer,
            getSettingsValueX() + 34,
            getSettingsContentY() + (SETTINGS_ROW_HEIGHT + 6) * 5,
            92,
            NAVBAR_HEIGHT,
            Text.empty()
        );
        themeHexInputField.setMaxLength(9);
        themeHexInputField.setDrawsBackground(true);
        themeHexInputField.setEditableColor(0xFFC5C8C6);
        themeHexInputField.setVisible(false);

        addDrawableChild(inputField);
        addDrawableChild(categoryInputField);
        addDrawableChild(themeRenameInputField);
        addDrawableChild(themeHexInputField);
        setInitialFocus(inputField);

        CommandPaletteSettingsStore.Settings settings = CommandPaletteSettingsStore.load();
        maxVisibleItems = settings.maxVisibleItems();
        hideSlashPrefix = settings.hideSlashPrefix();

        loadThemeLibrary();

        categories.clear();
        categories.addAll(CommandCategoriesStore.load());
        ensureDefaultCategory();
        history.clear();
        history.addAll(CommandHistoryStore.load());
        applySlashPreferenceToStoredCommands();
        refreshSuggestions("");
    }

    private void ensureDefaultCategory() {
        boolean changed = false;

        int favoritesIndex = getFavoritesCategoryIndex(false);
        if (favoritesIndex < 0) {
            categories.add(0, new CommandCategoriesStore.Category(CommandCategoriesStore.DEFAULT_CATEGORY_NAME, new ArrayList<>()));
            changed = true;
            if (selectedCategoryIndex >= 0) {
                selectedCategoryIndex++;
            }
        }

        if (categories.isEmpty()) {
            categories.add(new CommandCategoriesStore.Category(CommandCategoriesStore.DEFAULT_CATEGORY_NAME, new ArrayList<>()));
            changed = true;
        }

        if (changed) {
            CommandCategoriesStore.save(categories);
        }

        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) {
            selectedCategoryIndex = 0;
        }
        clampCategoryScrollIndex();
        ensureCategoryTabVisible(selectedCategoryIndex);
    }

    private OrderedText formatInputText(String text, int firstCharIndex) {
        if (text == null || text.isEmpty()) return OrderedText.EMPTY;

        String completeText = inputField.getText();
        if (completeText == null || completeText.isEmpty()) return OrderedText.EMPTY;

        if (!completeText.equals(cachedColorText)) {
            cachedColors = computeCharacterColors(completeText);
            cachedColorText = completeText;
        }
        int[] charColors = cachedColors;

        MutableText result = Text.empty();
        int runStart = 0;
        int currentColor = colorAt(charColors, firstCharIndex);

        for (int i = 1; i <= text.length(); i++) {
            int nextColor = (i < text.length()) ? colorAt(charColors, firstCharIndex + i) : -1;
            if (nextColor != currentColor) {
                result.append(Text.literal(text.substring(runStart, i))
                        .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(currentColor & 0x00FFFFFF))));
                runStart = i;
                currentColor = nextColor;
            }
        }

        return result.asOrderedText();
    }

    private int[] computeCharacterColors(String text) {
        int[] colors = new int[text.length()];
        String[] tokens = text.split(" ", -1);

        int charPos = 0;
        for (int i = 0; i < tokens.length; i++) {
            if (i > 0 && charPos < colors.length) {
                colors[charPos] = 0xC5C8C6;
                charPos++;
            }

            String token = tokens[i];
            if (token.isEmpty()) continue;

            if (i == 0 && token.startsWith("/")) {
                colors[charPos] = COLOR_SLASH;
                for (int j = 1; j < token.length() && charPos + j < colors.length; j++) {
                    colors[charPos + j] = COLOR_COMMAND;
                }
            } else {
                int color = (i == 0) ? COLOR_COMMAND : classifyToken(token, i);
                for (int j = 0; j < token.length() && charPos + j < colors.length; j++) {
                    colors[charPos + j] = color;
                }
            }

            charPos += token.length();
        }

        return colors;
    }

    private int colorAt(int[] colors, int index) {
        return (index >= 0 && index < colors.length) ? colors[index] : 0xC5C8C6;
    }

    private void onInputChanged(String text) {
        selectedIndex = -1;
        scrollOffset = 0;
        refreshSuggestions(text);
    }

    private List<String> getVisibleEntries() {
        return switch (currentView) {
            case CATEGORY -> getSelectedCategoryCommands();
            case HISTORY -> history;
            case SETTINGS -> List.of();
            default -> suggestions;
        };
    }

    private int getConfiguredMaxVisibleItems() {
        return Math.max(CommandPaletteSettingsStore.MIN_MAX_VISIBLE_ITEMS,
                Math.min(maxVisibleItems, CommandPaletteSettingsStore.MAX_MAX_VISIBLE_ITEMS));
    }

    private List<String> getSelectedCategoryCommands() {
        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) {
            return List.of();
        }
        return categories.get(selectedCategoryIndex).commands();
    }

    private int getPaletteWidth() {
        int availableWidth = this.width - (PADDING * 2);
        int clampedToMax = Math.min(PALETTE_MAX_WIDTH, availableWidth);
        return Math.max(PALETTE_MIN_WIDTH, clampedToMax);
    }

    private int getInputX() {
        int paletteX = (this.width - getPaletteWidth()) / 2;
        return paletteX + PADDING;
    }

    private int getNavbarY() {
        int paletteY = this.height / 5;
        return paletteY + PADDING;
    }

    private int getInputY() {
        return getNavbarY() + NAVBAR_HEIGHT + NAVBAR_GAP;
    }

    private int getInputWidth() {
        return getPaletteWidth() - PADDING * 2 - STAR_BUTTON_SIZE * 2 - STAR_GAP * 2;
    }

    private int getHistoryTabX() {
        return getInputX();
    }

    private int getFavoritesTabX() {
        return getHistoryTabX() + TAB_BUTTON_WIDTH + TAB_GAP;
    }

    private int getFavoritesTabWidth() {
        int favoritesIndex = getFavoritesCategoryIndex(false);
        if (favoritesIndex < 0 || favoritesIndex >= categories.size()) {
            return 0;
        }
        return getCategoryTabWidth(categories.get(favoritesIndex));
    }

    private int getCategoryScrollLeftX() {
        int favoritesWidth = getFavoritesTabWidth();
        if (favoritesWidth > 0) {
            return getFavoritesTabX() + favoritesWidth + TAB_GAP;
        }
        return getHistoryTabX() + TAB_BUTTON_WIDTH + TAB_GAP;
    }

    private int getCategoryTabsStartX() {
        return getCategoryScrollLeftX() + NAVBAR_HEIGHT + TAB_GAP;
    }

    private int getSettingsButtonX() {
        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        return paletteX + paletteWidth - PADDING - NAVBAR_HEIGHT;
    }

    private int getContextActionButtonX() {
        return getSettingsButtonX() - TAB_GAP - NAVBAR_HEIGHT;
    }

    private int getCategoryScrollRightX() {
        return getContextActionButtonX() - TAB_GAP - NAVBAR_HEIGHT;
    }

    private int getAddToCategoryButtonX() {
        return getInputX() + getInputWidth() + STAR_GAP;
    }

    private int getFavoriteButtonX() {
        return getAddToCategoryButtonX() + STAR_BUTTON_SIZE + STAR_GAP;
    }

    private int getCategoryInputX() {
        return getCategoryPickerX() + 4;
    }

    private int getCategoryInputY() {
        return getCategoryPickerY() + 2;
    }

    private int getCategoryInputWidth() {
        return CATEGORY_PICKER_WIDTH - 8;
    }

    private int getCategoryPickerX() {
        int x = getAddToCategoryButtonX() - CATEGORY_PICKER_WIDTH + STAR_BUTTON_SIZE;
        return Math.max(getInputX(), x);
    }

    private int getCategoryPickerY() {
        return getInputY() + INPUT_HEIGHT + 4;
    }

    private int getCategoryPickerHeight() {
        if (creatingCategoryInput) {
            return CATEGORY_PICKER_ROW_HEIGHT + 4;
        }
        int rows = getAssignableCategoryIndices().size() + 1;
        return rows * CATEGORY_PICKER_ROW_HEIGHT + 4;
    }

    private int getCategoryTabsAreaRightX() {
        return getCategoryScrollRightX() - TAB_GAP;
    }

    private List<Integer> getScrollableCategoryIndices() {
        int favoritesIndex = getFavoritesCategoryIndex(false);
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < categories.size(); i++) {
            if (i != favoritesIndex) {
                indices.add(i);
            }
        }
        return indices;
    }

    private List<Integer> getAssignableCategoryIndices() {
        return getScrollableCategoryIndices();
    }

    private void openCategoryPicker() {
        categoryPickerOpen = true;
        creatingCategoryInput = false;
        categoryInputField.setVisible(false);
    }

    private void closeCategoryPicker() {
        categoryPickerOpen = false;
        closeCategoryCreationInput();
    }

    private boolean isInsideCategoryPicker(double mouseX, double mouseY) {
        if (!categoryPickerOpen) return false;
        int x = getCategoryPickerX();
        int y = getCategoryPickerY();
        int width = CATEGORY_PICKER_WIDTH;
        int height = getCategoryPickerHeight();
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    private int getCategoryPickerRowIndexAt(double mouseX, double mouseY) {
        if (!isInsideCategoryPicker(mouseX, mouseY) || creatingCategoryInput) {
            return -1;
        }
        int y = getCategoryPickerY() + 2;
        return (int) ((mouseY - y) / CATEGORY_PICKER_ROW_HEIGHT);
    }

    private void addCurrentInputToCategory(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= categories.size()) return;

        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return;

        CommandCategoriesStore.Category category = categories.get(categoryIndex);
        List<String> commands = new ArrayList<>(category.commands());
        if (!commands.contains(normalized)) {
            commands.add(normalized);
            categories.set(categoryIndex, new CommandCategoriesStore.Category(category.name(), commands));
            CommandCategoriesStore.save(categories);
        }
    }

    private boolean canScrollCategoriesLeft() {
        return categoryScrollIndex > 0;
    }

    private boolean canScrollCategoriesRight() {
        if (categories.isEmpty()) {
            return false;
        }
        int visibleCount = getVisibleCategoryCountFrom(categoryScrollIndex);
        return categoryScrollIndex + visibleCount < categories.size();
    }

    private int getCategoryTabWidth(CommandCategoriesStore.Category category) {
        String label = getCategoryDisplayName(category);
        return Math.max(CATEGORY_TAB_MIN_WIDTH,
                Math.min(CATEGORY_TAB_MAX_WIDTH, this.textRenderer.getWidth(label) + 20));
    }

    private int getVisibleCategoryCountFrom(int startIndex) {
        List<Integer> scrollableIndices = getScrollableCategoryIndices();
        if (startIndex < 0 || startIndex >= scrollableIndices.size()) {
            return 0;
        }

        int rightX = getCategoryTabsAreaRightX();
        int x = getCategoryTabsStartX();
        int count = 0;

        for (int i = startIndex; i < scrollableIndices.size(); i++) {
            int categoryIndex = scrollableIndices.get(i);
            int width = getCategoryTabWidth(categories.get(categoryIndex));
            if (x + width > rightX) {
                break;
            }
            count++;
            x += width + TAB_GAP;
        }

        if (count == 0) {
            return 1;
        }

        return count;
    }

    private void clampCategoryScrollIndex() {
        List<Integer> scrollableIndices = getScrollableCategoryIndices();
        if (scrollableIndices.isEmpty()) {
            categoryScrollIndex = 0;
            return;
        }

        int maxIndex = scrollableIndices.size() - 1;
        categoryScrollIndex = Math.max(0, Math.min(categoryScrollIndex, maxIndex));
    }

    private void ensureCategoryTabVisible(int index) {
        if (index < 0 || index >= categories.size()) {
            return;
        }

        int favoritesIndex = getFavoritesCategoryIndex(false);
        if (index == favoritesIndex) {
            return;
        }

        List<Integer> scrollableIndices = getScrollableCategoryIndices();
        int scrollablePosition = scrollableIndices.indexOf(index);
        if (scrollablePosition < 0) {
            return;
        }

        clampCategoryScrollIndex();

        if (scrollablePosition < categoryScrollIndex) {
            categoryScrollIndex = scrollablePosition;
            return;
        }

        while (categoryScrollIndex < categories.size() - 1) {
            int visibleCount = getVisibleCategoryCountFrom(categoryScrollIndex);
            if (visibleCount <= 0) {
                break;
            }

            int lastVisibleIndex = categoryScrollIndex + visibleCount - 1;
            if (scrollablePosition <= lastVisibleIndex) {
                break;
            }

            categoryScrollIndex++;
        }
    }

    private int getSeparatorY() {
        return getInputY() + INPUT_HEIGHT + 8;
    }

    private int getSettingsContentY() {
        return getSeparatorY() + 8;
    }

    private int getSettingsRow1Y() {
        return getSettingsContentY();
    }

    private int getSettingsRow2Y() {
        return getSettingsContentY() + SETTINGS_ROW_HEIGHT + 8;
    }

    private int getSettingsControlsRightX() {
        return getInputX() + getInputWidth() - 8;
    }

    private int getSettingsDecreaseX() {
        return getSettingsControlsRightX() - (SETTINGS_BUTTON_WIDTH * 2 + 4);
    }

    private int getSettingsIncreaseX() {
        return getSettingsDecreaseX() + SETTINGS_BUTTON_WIDTH + 4;
    }

    private int getSettingsSlashSwitchX() {
        return getSettingsControlsRightX() - 56;
    }

    private int getSettingsLabelX() {
        return getInputX();
    }

    private int getSettingsValueX() {
        int labelPadding = 18;
        int maxLabelWidth = 0;

        if (this.textRenderer != null) {
            maxLabelWidth = Math.max(maxLabelWidth,
                    this.textRenderer.getWidth(Text.translatable("screen.cmdpalette.settings.max_visible")));
            maxLabelWidth = Math.max(maxLabelWidth,
                    this.textRenderer.getWidth(Text.translatable("screen.cmdpalette.settings.hide_slash")));
            maxLabelWidth = Math.max(maxLabelWidth,
                    this.textRenderer.getWidth(Text.translatable("screen.cmdpalette.settings.theme")));
            maxLabelWidth = Math.max(maxLabelWidth,
                    this.textRenderer.getWidth(Text.translatable("screen.cmdpalette.settings.aspect")));
        }

        return getSettingsLabelX() + maxLabelWidth + labelPadding;
    }

    private void persistSettings() {
        maxVisibleItems = getConfiguredMaxVisibleItems();
        CommandPaletteSettingsStore.save(new CommandPaletteSettingsStore.Settings(maxVisibleItems, hideSlashPrefix));
    }

    private void loadThemeLibrary() {
        CommandPaletteThemeStore.ThemeLibrary loaded = CommandPaletteThemeStore.load();
        themeLibrary.clear();
        themeLibrary.addAll(loaded.themes());

        selectedThemeIndex = 0;
        for (int i = 0; i < themeLibrary.size(); i++) {
            if (themeLibrary.get(i).id().equals(loaded.selectedThemeId())) {
                selectedThemeIndex = i;
                break;
            }
        }

        selectedThemeAspectIndex = Math.max(0, Math.min(selectedThemeAspectIndex, ThemeAspect.values().length - 1));
        applyActiveTheme();
        themeLibraryLastModifiedMillis = CommandPaletteThemeStore.getThemeStoreLastModifiedMillis();
    }

    private void saveThemeLibrary() {
        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) return;
        CommandPaletteThemeStore.save(new CommandPaletteThemeStore.ThemeLibrary(new ArrayList<>(themeLibrary), active.id()));
        themeLibraryLastModifiedMillis = CommandPaletteThemeStore.getThemeStoreLastModifiedMillis();
    }

    private CommandPaletteThemeStore.ThemePreset getActiveTheme() {
        if (themeLibrary.isEmpty()) return null;
        selectedThemeIndex = Math.max(0, Math.min(selectedThemeIndex, themeLibrary.size() - 1));
        return themeLibrary.get(selectedThemeIndex);
    }

    private boolean isActiveThemeEditable() {
        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        return active != null && active.editable();
    }

    private void applyThemeColors(CommandPaletteThemeStore.ThemeColors colors) {
        COLOR_OVERLAY = colors.overlay();
        COLOR_BG = colors.bg();
        COLOR_SHADOW = colors.shadow();
        COLOR_ACCENT = colors.accent();
        COLOR_INPUT_BG = colors.inputBg();
        COLOR_SEPARATOR = colors.separator();
        COLOR_HOVER = colors.hover();
        COLOR_SELECTED = colors.selected();
        COLOR_SCROLLBAR = colors.scrollbar();
        COLOR_BORDER_TOP = colors.borderTop();
        COLOR_STAR = colors.star();
        COLOR_STAR_OFF = colors.starOff();
        COLOR_BUTTON_BG = colors.buttonBg();
        COLOR_BUTTON_ACTIVE = colors.buttonActive();
        COLOR_CATEGORY_ACTIVE = colors.categoryActive();
        COLOR_SLASH = colors.slash();
        COLOR_COMMAND = colors.command();
        COLOR_SELECTOR = colors.selector();
        COLOR_COORDINATE = colors.coordinate();
        COLOR_STRING = colors.string();
        COLOR_NUMBER = colors.number();
        COLOR_BOOLEAN = colors.bool();
    }

    private void applyActiveTheme() {
        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) {
            applyThemeColors(CommandPaletteThemeStore.defaultTheme().colors());
            return;
        }
        applyThemeColors(active.colors());
        syncThemeHexInputFromActiveAspect();
    }

    private void selectTheme(int delta) {
        if (themeLibrary.isEmpty()) return;
        selectedThemeIndex = (selectedThemeIndex + delta + themeLibrary.size()) % themeLibrary.size();
        applyActiveTheme();
        saveThemeLibrary();
    }

    private void createNewTheme() {
        CommandPaletteThemeStore.ThemeColors baseColors = CommandPaletteThemeStore.defaultTheme().colors();

        String generatedId = generateNextCustomThemeId();
        String name = "Theme " + generatedId.substring("theme-".length());
        CommandPaletteThemeStore.ThemePreset preset = new CommandPaletteThemeStore.ThemePreset(
            generatedId,
                name,
                true,
                baseColors
        );

        themeLibrary.add(preset);
        selectedThemeIndex = themeLibrary.size() - 1;
        applyActiveTheme();
        saveThemeLibrary();
    }

    private void deleteActiveTheme() {
        if (!isActiveThemeEditable()) return;
        if (themeLibrary.size() <= 1) return;

        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active != null) {
            CommandPaletteThemeStore.deleteThemeFile(active.id());
        }

        themeLibrary.remove(selectedThemeIndex);
        selectedThemeIndex = Math.max(0, Math.min(selectedThemeIndex, themeLibrary.size() - 1));
        applyActiveTheme();
        saveThemeLibrary();
    }

    private void startThemeRename() {
        if (!isActiveThemeEditable()) return;
        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) return;

        renamingTheme = true;
        themeRenameInputField.setText(active.name());
        themeRenameInputField.setCursorToEnd(false);
        themeRenameInputField.setVisible(true);
        inputField.setFocused(false);
        themeRenameInputField.setFocused(true);
        setFocused(themeRenameInputField);
    }

    private void cancelThemeRename() {
        renamingTheme = false;
        themeRenameInputField.setFocused(false);
        themeRenameInputField.setVisible(false);
        inputField.setFocused(true);
        setFocused(inputField);
    }

    private void commitThemeRename() {
        if (!renamingTheme) return;
        if (!isActiveThemeEditable()) {
            cancelThemeRename();
            return;
        }

        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) {
            cancelThemeRename();
            return;
        }

        String raw = themeRenameInputField.getText();
        String name = raw == null ? "" : raw.trim();
        if (name.isBlank()) {
            name = active.name();
        }
        if (name.length() > 32) {
            name = name.substring(0, 32);
        }

        String updatedId = active.id();
        if (isCustomThemeId(active.id())) {
            updatedId = generateCustomThemeIdFromName(name, selectedThemeIndex);
            if (!updatedId.equals(active.id())) {
                CommandPaletteThemeStore.deleteThemeFile(active.id());
            }
        }

        themeLibrary.set(selectedThemeIndex,
                new CommandPaletteThemeStore.ThemePreset(updatedId, name, active.editable(), active.colors()));
        saveThemeLibrary();
        cancelThemeRename();
    }

    private boolean isCustomThemeId(String id) {
        if (id == null || id.isBlank()) return false;
        if (CommandPaletteThemeStore.DEFAULT_THEME_ID.equals(id)) return false;
        return !id.startsWith("base-");
    }

    private String generateNextCustomThemeId() {
        int max = 0;
        for (CommandPaletteThemeStore.ThemePreset preset : themeLibrary) {
            if (preset == null || preset.id() == null) continue;
            Matcher matcher = CUSTOM_THEME_NUMBER_PATTERN.matcher(preset.id());
            if (matcher.matches()) {
                try {
                    int value = Integer.parseInt(matcher.group(1));
                    if (value > max) {
                        max = value;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return ensureUniqueThemeId("theme-" + (max + 1), -1);
    }

    private String generateCustomThemeIdFromName(String name, int ignoreIndex) {
        String normalized = name == null ? "" : name.trim().toLowerCase();
        normalized = normalized.replaceAll("[^a-z0-9]+", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            normalized = "theme";
        }
        return ensureUniqueThemeId("custom-" + normalized, ignoreIndex);
    }

    private String ensureUniqueThemeId(String baseId, int ignoreIndex) {
        String candidate = baseId;
        int suffix = 2;
        while (themeIdExists(candidate, ignoreIndex)) {
            candidate = baseId + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private boolean themeIdExists(String id, int ignoreIndex) {
        for (int i = 0; i < themeLibrary.size(); i++) {
            if (i == ignoreIndex) continue;
            CommandPaletteThemeStore.ThemePreset preset = themeLibrary.get(i);
            if (preset != null && id.equals(preset.id())) {
                return true;
            }
        }
        return false;
    }

    private void openThemeFilesForActiveTheme() {
        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) return;
        Path targetPath;
        if (active.editable()) {
            targetPath = CommandPaletteThemeStore.writeThemeFile(active);
        } else {
            targetPath = CommandPaletteThemeStore.getThemesDirectoryPath();
        }
        if (this.client != null) {
            this.client.keyboard.setClipboard(targetPath.toAbsolutePath().toString());
        }
        openThemesFolderInExplorer();
    }

    private void openThemesFolderInExplorer() {
        Path folder = CommandPaletteThemeStore.getThemesDirectoryPath();
        if (folder != null) {
            Util.getOperatingSystem().open(folder.toFile());
        }
    }

    private void checkExternalThemeLibraryUpdates() {
        long now = System.currentTimeMillis();
        if (now - lastThemeLibraryCheckMillis < THEME_LIBRARY_CHECK_INTERVAL_MS) {
            return;
        }
        lastThemeLibraryCheckMillis = now;

        long currentModified = CommandPaletteThemeStore.getThemeStoreLastModifiedMillis();
        if (currentModified <= 0L) {
            return;
        }

        if (themeLibraryLastModifiedMillis <= 0L) {
            themeLibraryLastModifiedMillis = currentModified;
            return;
        }

        if (currentModified == themeLibraryLastModifiedMillis) {
            return;
        }

        if (renamingTheme) {
            cancelThemeRename();
        }
        if (themeHexInputField != null) {
            themeHexInputField.setFocused(false);
            themeHexInputField.setVisible(false);
        }
        loadThemeLibrary();
    }

    private ThemeAspect getSelectedThemeAspect() {
        ThemeAspect[] aspects = ThemeAspect.values();
        selectedThemeAspectIndex = Math.max(0, Math.min(selectedThemeAspectIndex, aspects.length - 1));
        return aspects[selectedThemeAspectIndex];
    }

    private void selectThemeAspect(int delta) {
        ThemeAspect[] aspects = ThemeAspect.values();
        selectedThemeAspectIndex = (selectedThemeAspectIndex + delta + aspects.length) % aspects.length;
        syncThemeHexInputFromActiveAspect();
    }

    private int getAspectColor(CommandPaletteThemeStore.ThemeColors colors, ThemeAspect aspect) {
        return switch (aspect) {
            case OVERLAY -> colors.overlay();
            case BG -> colors.bg();
            case SHADOW -> colors.shadow();
            case ACCENT -> colors.accent();
            case INPUT_BG -> colors.inputBg();
            case SEPARATOR -> colors.separator();
            case HOVER -> colors.hover();
            case SELECTED -> colors.selected();
            case SCROLLBAR -> colors.scrollbar();
            case BORDER_TOP -> colors.borderTop();
            case STAR -> colors.star();
            case STAR_OFF -> colors.starOff();
            case BUTTON_BG -> colors.buttonBg();
            case BUTTON_ACTIVE -> colors.buttonActive();
            case CATEGORY_ACTIVE -> colors.categoryActive();
            case SLASH -> colors.slash();
            case COMMAND -> colors.command();
            case SELECTOR -> colors.selector();
            case COORDINATE -> colors.coordinate();
            case STRING -> colors.string();
            case NUMBER -> colors.number();
            case BOOLEAN -> colors.bool();
        };
    }

    private CommandPaletteThemeStore.ThemeColors withAspectColor(CommandPaletteThemeStore.ThemeColors colors, ThemeAspect aspect, int value) {
        int color = value;
        return switch (aspect) {
            case OVERLAY -> new CommandPaletteThemeStore.ThemeColors(color, colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case BG -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), color, colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SHADOW -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), color, colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case ACCENT -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), color, colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case INPUT_BG -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), color, colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SEPARATOR -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), color, colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case HOVER -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), color, colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SELECTED -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), color, colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SCROLLBAR -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), color, colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case BORDER_TOP -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), color, colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case STAR -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), color, colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case STAR_OFF -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), color, colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case BUTTON_BG -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), color, colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case BUTTON_ACTIVE -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), color, colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case CATEGORY_ACTIVE -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), color, colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SLASH -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), color, colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case COMMAND -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), color, colors.selector(), colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case SELECTOR -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), color, colors.coordinate(), colors.string(), colors.number(), colors.bool());
            case COORDINATE -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), color, colors.string(), colors.number(), colors.bool());
            case STRING -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), color, colors.number(), colors.bool());
            case NUMBER -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), color, colors.bool());
            case BOOLEAN -> new CommandPaletteThemeStore.ThemeColors(colors.overlay(), colors.bg(), colors.shadow(), colors.accent(), colors.inputBg(), colors.separator(), colors.hover(), colors.selected(), colors.scrollbar(), colors.borderTop(), colors.star(), colors.starOff(), colors.buttonBg(), colors.buttonActive(), colors.categoryActive(), colors.slash(), colors.command(), colors.selector(), colors.coordinate(), colors.string(), colors.number(), color);
        };
    }

    private void adjustActiveThemeAspectColor(int channel, int delta) {
        if (!isActiveThemeEditable()) return;

        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) return;

        ThemeAspect aspect = getSelectedThemeAspect();
        int original = getAspectColor(active.colors(), aspect);

        int alpha = (original >>> 24) & 0xFF;
        int red = (original >>> 16) & 0xFF;
        int green = (original >>> 8) & 0xFF;
        int blue = original & 0xFF;

        if (channel == 0) red = Math.max(0, Math.min(255, red + delta));
        if (channel == 1) green = Math.max(0, Math.min(255, green + delta));
        if (channel == 2) blue = Math.max(0, Math.min(255, blue + delta));

        int updated = (alpha << 24) | (red << 16) | (green << 8) | blue;
        CommandPaletteThemeStore.ThemeColors newColors = withAspectColor(active.colors(), aspect, updated);
        themeLibrary.set(selectedThemeIndex, new CommandPaletteThemeStore.ThemePreset(active.id(), active.name(), active.editable(), newColors));
        applyActiveTheme();
        saveThemeLibrary();
    }

    private void syncThemeHexInputFromActiveAspect() {
        if (themeHexInputField == null) return;
        if (themeHexInputField.isFocused()) return;

        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        ThemeAspect aspect = getSelectedThemeAspect();
        int color = active == null ? COLOR_STAR : getAspectColor(active.colors(), aspect);
        themeHexInputField.setText(String.format("#%06X", color & 0x00FFFFFF));
        themeHexInputField.setCursorToEnd(false);
    }

    private Integer parseHexColorInput(String text, int fallbackAlpha) {
        if (text == null) return null;
        String normalized = text.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        }

        if (normalized.length() != 6 && normalized.length() != 8) {
            return null;
        }

        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            boolean isHex = (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
            if (!isHex) {
                return null;
            }
        }

        try {
            long parsed = Long.parseLong(normalized, 16);
            if (normalized.length() == 6) {
                int rgb = (int) (parsed & 0x00FFFFFFL);
                return (fallbackAlpha << 24) | rgb;
            }
            return (int) (parsed & 0xFFFFFFFFL);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private boolean applyThemeHexInput(boolean force) {
        if (!isActiveThemeEditable()) return false;

        CommandPaletteThemeStore.ThemePreset active = getActiveTheme();
        if (active == null) return false;

        ThemeAspect aspect = getSelectedThemeAspect();
        int original = getAspectColor(active.colors(), aspect);
        Integer parsed = parseHexColorInput(themeHexInputField.getText(), (original >>> 24) & 0xFF);
        if (parsed == null) {
            if (force) {
                syncThemeHexInputFromActiveAspect();
            }
            return false;
        }

        if (parsed == original) {
            if (force) {
                syncThemeHexInputFromActiveAspect();
            }
            return true;
        }

        CommandPaletteThemeStore.ThemeColors newColors = withAspectColor(active.colors(), aspect, parsed);
        themeLibrary.set(selectedThemeIndex, new CommandPaletteThemeStore.ThemePreset(active.id(), active.name(), active.editable(), newColors));
        applyActiveTheme();
        saveThemeLibrary();
        return true;
    }

    private void focusMainInputFromSettingsEditor() {
        if (themeHexInputField != null) {
            themeHexInputField.setFocused(false);
            themeHexInputField.setVisible(false);
        }
        inputField.setFocused(true);
        setFocused(inputField);
    }

    private String formatCommandForDisplay(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        String noPrefix = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return hideSlashPrefix ? noPrefix : "/" + noPrefix;
    }

    private void applySlashPreferenceToStoredCommands() {
        boolean changedCategories = false;
        for (int i = 0; i < categories.size(); i++) {
            CommandCategoriesStore.Category category = categories.get(i);
            List<String> updated = new ArrayList<>();
            for (String command : category.commands()) {
                String normalized = normalizeCommand(command);
                if (!normalized.isBlank()) {
                    updated.add(normalized);
                }
            }

            if (!updated.equals(category.commands())) {
                categories.set(i, new CommandCategoriesStore.Category(category.name(), updated));
                changedCategories = true;
            }
        }

        if (changedCategories) {
            CommandCategoriesStore.save(categories);
        }

        List<String> updatedHistory = new ArrayList<>();
        for (String command : history) {
            String normalized = normalizeCommand(command);
            if (!normalized.isBlank()) {
                if (!updatedHistory.contains(normalized)) {
                    updatedHistory.add(normalized);
                }
            }
        }

        if (!updatedHistory.equals(history)) {
            history.clear();
            history.addAll(updatedHistory);
            CommandHistoryStore.save(history);
        }
    }

    private String normalizeCommand(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        String noPrefix = trimmed.startsWith("/") ? trimmed.substring(1) : trimmed;
        return hideSlashPrefix ? noPrefix : "/" + noPrefix;
    }

    private boolean isCurrentInputInSelectedCategory() {
        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return false;
        return getSelectedCategoryCommands().contains(normalized);
    }

    private boolean isCurrentInputInFavoritesCategory() {
        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return false;
        int favoritesIndex = getFavoritesCategoryIndex(false);
        if (favoritesIndex < 0) return false;
        return categories.get(favoritesIndex).commands().contains(normalized);
    }

    private void toggleCurrentInputCategoryCommand() {
        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return;

        ensureDefaultCategory();
        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) {
            selectedCategoryIndex = 0;
        }

        CommandCategoriesStore.Category current = categories.get(selectedCategoryIndex);
        List<String> commands = new ArrayList<>(current.commands());

        if (commands.contains(normalized)) {
            commands.remove(normalized);
        } else {
            commands.add(normalized);
        }

        categories.set(selectedCategoryIndex, new CommandCategoriesStore.Category(current.name(), commands));
        CommandCategoriesStore.save(categories);
        clampSelectionAndScroll();
    }

    private void toggleCurrentInputFavoriteCommand() {
        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return;

        int favoritesIndex = getFavoritesCategoryIndex(true);
        CommandCategoriesStore.Category favoritesCategory = categories.get(favoritesIndex);
        List<String> commands = new ArrayList<>(favoritesCategory.commands());

        if (commands.contains(normalized)) {
            commands.remove(normalized);
        } else {
            commands.add(normalized);
        }

        categories.set(favoritesIndex, new CommandCategoriesStore.Category(favoritesCategory.name(), commands));
        CommandCategoriesStore.save(categories);
        if (isCategoryView() && selectedCategoryIndex == favoritesIndex) {
            clampSelectionAndScroll();
        }
    }

    private int getFavoritesCategoryIndex(boolean createIfMissing) {
        for (int i = 0; i < categories.size(); i++) {
            if (isFavoritesCategoryName(categories.get(i).name())) {
                return i;
            }
        }

        if (!createIfMissing) {
            return -1;
        }

        categories.add(0, new CommandCategoriesStore.Category(CommandCategoriesStore.DEFAULT_CATEGORY_NAME, new ArrayList<>()));
        if (selectedCategoryIndex >= 0) {
            selectedCategoryIndex++;
        }
        CommandCategoriesStore.save(categories);
        return 0;
    }

    private boolean isFavoritesCategoryName(String name) {
        if (name == null) return false;
        return name.equalsIgnoreCase(CommandCategoriesStore.DEFAULT_CATEGORY_NAME)
                || name.equalsIgnoreCase("Favorites")
                || name.equalsIgnoreCase("Favoritos");
    }

    private String getCategoryDisplayName(CommandCategoriesStore.Category category) {
        if (isFavoritesCategoryName(category.name())) {
            return Text.translatable("screen.cmdpalette.category.favorites").getString();
        }
        return category.name();
    }

    private void openCategoryCreationInput() {
        categoryPickerOpen = true;
        creatingCategoryInput = true;
        categoryInputField.setText("");
        categoryInputField.setWidth(getCategoryInputWidth());
        categoryInputField.setPosition(getCategoryInputX(), getCategoryInputY());
        categoryInputField.setVisible(true);
        inputField.setFocused(false);
        categoryInputField.setFocused(true);
        setFocused(categoryInputField);
    }

    private void closeCategoryCreationInput() {
        creatingCategoryInput = false;
        categoryInputField.setFocused(false);
        categoryInputField.setVisible(false);
        inputField.setFocused(true);
        setFocused(inputField);
    }

    private void createCategoryFromDedicatedInput() {
        String raw = categoryInputField.getText();
        if (raw == null) return;
        String name = raw.trim();
        if (name.isBlank()) return;

        int targetIndex = -1;

        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name().equalsIgnoreCase(name)) {
                targetIndex = i;
                break;
            }
        }

        if (targetIndex < 0) {
            if (name.length() > 24) {
                name = name.substring(0, 24);
            }
            categories.add(new CommandCategoriesStore.Category(name, new ArrayList<>()));
            targetIndex = categories.size() - 1;
            CommandCategoriesStore.save(categories);
        }

        selectedCategoryIndex = targetIndex;
        addCurrentInputToCategory(targetIndex);
        ensureCategoryTabVisible(selectedCategoryIndex);
        closeCategoryPicker();
    }

    private void setView(ViewMode viewMode) {
        if (viewMode != ViewMode.COMMANDS) {
            closeCategoryPicker();
        }
        if (viewMode != ViewMode.SETTINGS && renamingTheme) {
            cancelThemeRename();
        }
        if (viewMode != ViewMode.SETTINGS && themeHexInputField != null) {
            if (themeHexInputField.isFocused()) {
                applyThemeHexInput(true);
            }
            themeHexInputField.setFocused(false);
            themeHexInputField.setVisible(false);
        }
        currentView = viewMode;
        selectedIndex = -1;
        scrollOffset = 0;
    }

    private void openSettingsView() {
        if (currentView == ViewMode.SETTINGS) {
            return;
        }
        viewBeforeSettings = currentView;
        setView(ViewMode.SETTINGS);
    }

    private void closeSettingsView() {
        if (currentView != ViewMode.SETTINGS) {
            return;
        }
        ViewMode target = viewBeforeSettings == ViewMode.SETTINGS ? ViewMode.COMMANDS : viewBeforeSettings;
        setView(target);
    }

    private void toggleView(ViewMode viewMode) {
        if (currentView == viewMode) {
            setView(ViewMode.COMMANDS);
            return;
        }
        setView(viewMode);
    }

    private void activateCategoryTab(int categoryIndex) {
        if (categoryIndex < 0 || categoryIndex >= categories.size()) {
            return;
        }

        if (isCategoryView() && selectedCategoryIndex == categoryIndex) {
            setView(ViewMode.COMMANDS);
            return;
        }

        selectedCategoryIndex = categoryIndex;
        ensureCategoryTabVisible(selectedCategoryIndex);
        setView(ViewMode.CATEGORY);
    }

    private boolean isCategoryView() {
        return currentView == ViewMode.CATEGORY;
    }

    private boolean canDeleteSelectedCategory() {
        if (!isCategoryView()) return false;
        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) return false;
        return !isFavoritesCategoryName(categories.get(selectedCategoryIndex).name());
    }

    private void deleteSelectedCategory() {
        if (!canDeleteSelectedCategory()) return;

        categories.remove(selectedCategoryIndex);
        if (selectedCategoryIndex >= categories.size()) {
            selectedCategoryIndex = categories.size() - 1;
        }

        ensureDefaultCategory();
        if (selectedCategoryIndex < 0) {
            selectedCategoryIndex = 0;
        }

        CommandCategoriesStore.save(categories);
        clampCategoryScrollIndex();
        ensureCategoryTabVisible(selectedCategoryIndex);
        clampSelectionAndScroll();
    }

    private void removeCategoryCommandAt(int index) {
        if (selectedCategoryIndex < 0 || selectedCategoryIndex >= categories.size()) return;

        CommandCategoriesStore.Category current = categories.get(selectedCategoryIndex);
        if (index < 0 || index >= current.commands().size()) return;

        List<String> commands = new ArrayList<>(current.commands());
        commands.remove(index);
        categories.set(selectedCategoryIndex, new CommandCategoriesStore.Category(current.name(), commands));
        CommandCategoriesStore.save(categories);
        clampSelectionAndScroll();
    }

    private void addToHistory(String commandText) {
        String normalized = normalizeCommand(commandText);
        if (normalized.isBlank()) return;

        history.remove(normalized);
        history.add(0, normalized);

        while (history.size() > MAX_HISTORY_ENTRIES) {
            history.remove(history.size() - 1);
        }

        CommandHistoryStore.save(history);
        if (currentView == ViewMode.HISTORY) {
            clampSelectionAndScroll();
        }
    }

    private void clearHistory() {
        if (history.isEmpty()) return;
        history.clear();
        CommandHistoryStore.save(history);
        clampSelectionAndScroll();
    }

    private void clampSelectionAndScroll() {
        List<String> entries = getVisibleEntries();
        int size = entries.size();
        int maxVisible = getConfiguredMaxVisibleItems();
        if (size == 0) {
            selectedIndex = -1;
            scrollOffset = 0;
            return;
        }

        selectedIndex = Math.min(selectedIndex, size - 1);
        if (selectedIndex < 0) {
            selectedIndex = -1;
        }

        int maxScroll = Math.max(0, size - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }

    private void refreshSuggestions(String input) {
        suggestions.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
        if (networkHandler == null) return;

        String raw = input.startsWith("/") ? input.substring(1) : input;
        CommandDispatcher<ClientCommandSource> dispatcher = networkHandler.getCommandDispatcher();
        ClientCommandSource source = networkHandler.getCommandSource();

        if (raw.isEmpty()) {
            List<String> rootCommands = new ArrayList<>();
            dispatcher.getRoot().getChildren().forEach(node -> rootCommands.add(formatCommandForDisplay(node.getName())));
            Collections.sort(rootCommands);
            suggestions.addAll(rootCommands);
            return;
        }

        ParseResults<ClientCommandSource> parseResults = dispatcher.parse(raw, source);
        CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parseResults);

        future.thenAccept(result -> {
            List<String> completions = new ArrayList<>();
            for (Suggestion s : result.getList()) {
                String text = raw.substring(0, s.getRange().getStart()) + s.getText();
                text = formatCommandForDisplay(text);
                if (!completions.contains(text)) {
                    completions.add(text);
                }
            }

            if (completions.isEmpty()) {
                String lowerInput = formatCommandForDisplay(raw).toLowerCase();
                dispatcher.getRoot().getChildren().forEach(node -> {
                    String name = formatCommandForDisplay(node.getName());
                    if (name.toLowerCase().contains(lowerInput)) {
                        completions.add(name);
                    }
                });
                Collections.sort(completions);
            }

            suggestions.clear();
            suggestions.addAll(completions);
        });
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        checkExternalThemeLibraryUpdates();

        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        int paletteY = this.height / 5;
        int paletteHeight = computePaletteHeight();
        int navbarY = getNavbarY();
        int inputY = getInputY();

        ctx.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        ctx.fill(paletteX - 2, paletteY - 2,
            paletteX + paletteWidth + 2, paletteY + paletteHeight + 2, COLOR_SHADOW);
        ctx.fill(paletteX, paletteY,
            paletteX + paletteWidth, paletteY + paletteHeight, COLOR_BG);

        ctx.fill(paletteX, paletteY, paletteX + paletteWidth, paletteY + 2, COLOR_BORDER_TOP);

        ctx.fill(paletteX + 4, paletteY + 4,
            paletteX + paletteWidth - 4, inputY + INPUT_HEIGHT + 4, COLOR_INPUT_BG);

        int historyTabX = getHistoryTabX();
        int favoritesTabX = getFavoritesTabX();
        int favoritesTabWidth = getFavoritesTabWidth();
        int favoritesIndex = getFavoritesCategoryIndex(false);
        int categoriesStartX = getCategoryTabsStartX();
        int scrollLeftX = getCategoryScrollLeftX();
        int scrollRightX = getCategoryScrollRightX();
        int contextActionX = getContextActionButtonX();
        int settingsX = getSettingsButtonX();
        int addCategoryX = getAddToCategoryButtonX();
        int favoriteButtonX = getFavoriteButtonX();
        boolean isHistoryView = currentView == ViewMode.HISTORY;
        boolean isCategoryView = isCategoryView();
        boolean isSettingsView = currentView == ViewMode.SETTINGS;
        boolean canDeleteCategory = canDeleteSelectedCategory();
        boolean contextActionVisible = isHistoryView || isCategoryView;
        boolean contextActionEnabled = isHistoryView || canDeleteCategory;
        boolean canScrollLeft = canScrollCategoriesLeft();
        boolean canScrollRight = canScrollCategoriesRight();

        boolean hoverHistoryTab = mouseX >= historyTabX && mouseX < historyTabX + TAB_BUTTON_WIDTH
                && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverFavoritesTab = favoritesTabWidth > 0
            && mouseX >= favoritesTabX && mouseX < favoritesTabX + favoritesTabWidth
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverScrollLeft = mouseX >= scrollLeftX && mouseX < scrollLeftX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverScrollRight = mouseX >= scrollRightX && mouseX < scrollRightX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverContextAction = mouseX >= contextActionX && mouseX < contextActionX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverSettings = mouseX >= settingsX && mouseX < settingsX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverAddCategory = mouseX >= addCategoryX && mouseX < addCategoryX + STAR_BUTTON_SIZE
            && mouseY >= inputY && mouseY < inputY + STAR_BUTTON_SIZE;
        boolean hoverFavoriteButton = mouseX >= favoriteButtonX && mouseX < favoriteButtonX + STAR_BUTTON_SIZE
            && mouseY >= inputY && mouseY < inputY + STAR_BUTTON_SIZE;

        int favoritesTabBg = COLOR_BUTTON_BG;
        int scrollLeftBg = COLOR_BUTTON_BG;
        int scrollRightBg = COLOR_BUTTON_BG;
        int historyTabBg = currentView == ViewMode.HISTORY ? COLOR_CATEGORY_ACTIVE : COLOR_BUTTON_BG;
        int contextActionBg = COLOR_BUTTON_BG;
        int settingsBg = COLOR_BUTTON_BG;
        int addCategoryBg = isCurrentInputInSelectedCategory() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;
        int favoriteBg = isCurrentInputInFavoritesCategory() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;

        if (hoverHistoryTab) historyTabBg = COLOR_ACCENT;
        if (favoritesTabWidth > 0 && isCategoryView && selectedCategoryIndex == favoritesIndex) {
            favoritesTabBg = COLOR_CATEGORY_ACTIVE;
        }
        if (hoverFavoritesTab) favoritesTabBg = COLOR_ACCENT;
        if (hoverScrollLeft && canScrollLeft) scrollLeftBg = COLOR_ACCENT;
        if (hoverScrollRight && canScrollRight) scrollRightBg = COLOR_ACCENT;
        if (hoverContextAction && contextActionEnabled) {
            contextActionBg = COLOR_ACCENT;
        }
        if (hoverSettings) {
            settingsBg = COLOR_ACCENT;
        }
        if (hoverAddCategory) addCategoryBg = COLOR_ACCENT;
        if (hoverFavoriteButton) favoriteBg = COLOR_ACCENT;

        ctx.fill(historyTabX, navbarY, historyTabX + TAB_BUTTON_WIDTH, navbarY + NAVBAR_HEIGHT, historyTabBg);
        if (favoritesTabWidth > 0) {
            ctx.fill(favoritesTabX, navbarY, favoritesTabX + favoritesTabWidth, navbarY + NAVBAR_HEIGHT, favoritesTabBg);
        }
        ctx.fill(scrollLeftX, navbarY, scrollLeftX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT,
            canScrollLeft ? scrollLeftBg : COLOR_BUTTON_BG);
        ctx.fill(scrollRightX, navbarY, scrollRightX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT,
            canScrollRight ? scrollRightBg : COLOR_BUTTON_BG);
        ctx.fill(contextActionX, navbarY, contextActionX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT,
                contextActionVisible ? contextActionBg : COLOR_BUTTON_BG);
        ctx.fill(settingsX, navbarY, settingsX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT, settingsBg);
        ctx.fill(addCategoryX, inputY, addCategoryX + STAR_BUTTON_SIZE, inputY + STAR_BUTTON_SIZE, addCategoryBg);
        ctx.fill(favoriteButtonX, inputY, favoriteButtonX + STAR_BUTTON_SIZE, inputY + STAR_BUTTON_SIZE, favoriteBg);

        boolean historyActive = currentView == ViewMode.HISTORY;
        ctx.drawText(this.textRenderer, "↺", historyTabX + 6, navbarY + 5,
            historyActive ? COLOR_STAR : COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, Text.translatable("screen.cmdpalette.tab.history").getString(),
            historyTabX + 18, navbarY + 5, historyActive ? COLOR_STAR : 0xFFC5C8C6, false);

        if (favoritesTabWidth > 0 && favoritesIndex >= 0 && favoritesIndex < categories.size()) {
            boolean favoritesActive = isCategoryView && selectedCategoryIndex == favoritesIndex;
            ctx.drawText(this.textRenderer, getCategoryDisplayName(categories.get(favoritesIndex)),
                favoritesTabX + 8, navbarY + 5, favoritesActive ? COLOR_STAR : 0xFFC5C8C6, false);
        }

        ctx.drawText(this.textRenderer, "<", scrollLeftX + 6, navbarY + 5,
            canScrollLeft ? 0xFFC5C8C6 : 0xFF555555, false);
        ctx.drawText(this.textRenderer, ">", scrollRightX + 6, navbarY + 5,
            canScrollRight ? 0xFFC5C8C6 : 0xFF555555, false);

        List<Integer> scrollableCategoryIndices = getScrollableCategoryIndices();
        int categoryX = categoriesStartX;
        for (int offset = categoryScrollIndex; offset < scrollableCategoryIndices.size(); offset++) {
            int categoryIndex = scrollableCategoryIndices.get(offset);
            CommandCategoriesStore.Category category = categories.get(categoryIndex);
            int categoryWidth = getCategoryTabWidth(category);
            if (categoryX + categoryWidth > getCategoryTabsAreaRightX()) {
                break;
            }

            boolean hovered = mouseX >= categoryX && mouseX < categoryX + categoryWidth
                    && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
                boolean active = isCategoryView && selectedCategoryIndex == categoryIndex;

            int bg = active ? COLOR_CATEGORY_ACTIVE : COLOR_BUTTON_BG;
            if (hovered) {
                bg = COLOR_ACCENT;
            }

            ctx.fill(categoryX, navbarY, categoryX + categoryWidth, navbarY + NAVBAR_HEIGHT, bg);
                ctx.drawText(this.textRenderer, getCategoryDisplayName(category), categoryX + 8, navbarY + 5,
                    active ? COLOR_STAR : 0xFFC5C8C6, false);

            categoryX += categoryWidth + TAB_GAP;
        }

        String contextActionLabel = "✕";
        int contextActionColor = contextActionVisible
            ? (contextActionEnabled ? COLOR_STAR_OFF : 0xFF555555)
            : 0xFF555555;
        ctx.drawText(this.textRenderer, contextActionLabel, contextActionX + 6, navbarY + 5, contextActionColor, false);

        String settingsLabel = "⚙";
        int settingsColor = isSettingsView ? COLOR_STAR : COLOR_STAR_OFF;
        ctx.drawText(this.textRenderer, settingsLabel, settingsX + 6, navbarY + 5, settingsColor, false);
        ctx.drawText(this.textRenderer, "★+", addCategoryX + 3, inputY + 6,
            isCurrentInputInSelectedCategory() ? COLOR_STAR : COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, "★", favoriteButtonX + 6, inputY + 6,
                isCurrentInputInFavoritesCategory() ? COLOR_STAR : COLOR_STAR_OFF, false);

        if (!isSettingsView && themeHexInputField != null) {
            themeHexInputField.setVisible(false);
            themeHexInputField.setFocused(false);
        }

        super.render(ctx, mouseX, mouseY, delta);

        if (hoverAddCategory) {
            ctx.drawTooltip(this.textRenderer,
                Text.translatable("screen.cmdpalette.tooltip.add_to_category_menu"),
                mouseX, mouseY);
        } else if (hoverFavoriteButton) {
            Text favoriteTooltip = isCurrentInputInFavoritesCategory()
                    ? Text.translatable("screen.cmdpalette.tooltip.remove_from_favorites")
                    : Text.translatable("screen.cmdpalette.tooltip.add_to_favorites");
            ctx.drawTooltip(this.textRenderer, favoriteTooltip, mouseX, mouseY);
        } else if (hoverContextAction && contextActionVisible) {
            String tooltipKey = isHistoryView
                ? "screen.cmdpalette.tooltip.clear_history"
                : (canDeleteCategory
                ? "screen.cmdpalette.tooltip.delete_category"
                : "screen.cmdpalette.tooltip.delete_category_disabled");
            ctx.drawTooltip(this.textRenderer, Text.translatable(tooltipKey), mouseX, mouseY);
        } else if (hoverSettings) {
            String tooltipKey = isSettingsView
                ? "screen.cmdpalette.tooltip.close_settings"
                : "screen.cmdpalette.tooltip.open_settings";
            ctx.drawTooltip(this.textRenderer, Text.translatable(tooltipKey), mouseX, mouseY);
        } else if (hoverHistoryTab) {
            ctx.drawTooltip(this.textRenderer,
                Text.translatable("screen.cmdpalette.tooltip.history_tab"),
                mouseX, mouseY);
        }

        int separatorY = getSeparatorY();
        ctx.fill(paletteX + PADDING, separatorY,
            paletteX + paletteWidth - PADDING, separatorY + 1, COLOR_SEPARATOR);

        if (isSettingsView) {
            renderSettingsContent(ctx, mouseX, mouseY);
            return;
        }

        int listStartY = separatorY + 4;
        List<String> entries = getVisibleEntries();
        int currentSize = entries.size();
        int maxVisible = getConfiguredMaxVisibleItems();
        int visibleCount = Math.min(currentSize - scrollOffset, maxVisible);
        boolean blockBackgroundHover = categoryPickerOpen;
        boolean hoverCategoryRowRemove = false;

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= currentSize) break;

            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;

            boolean selected = idx == selectedIndex;
                boolean hovered = !blockBackgroundHover
                    && mouseX >= paletteX + 4 && mouseX <= paletteX + paletteWidth - 4
                    && mouseY >= sy && mouseY < sy + SUGGESTION_ITEM_HEIGHT;

            if (selected) {
                ctx.fill(paletteX + 4, sy, paletteX + paletteWidth - 4,
                        sy + SUGGESTION_ITEM_HEIGHT, COLOR_SELECTED);
            } else if (hovered) {
                ctx.fill(paletteX + 4, sy, paletteX + paletteWidth - 4,
                        sy + SUGGESTION_ITEM_HEIGHT, COLOR_HOVER);
            }

            String entry = entries.get(idx);
            renderSyntaxHighlighted(ctx, entry, paletteX + 12, sy + 4);

            if (isCategoryView) {
                int removeX = paletteX + paletteWidth - PADDING - STAR_ROW_SIZE;
                int removeY = sy + (SUGGESTION_ITEM_HEIGHT - STAR_ROW_SIZE) / 2;
                boolean hoverRowRemove = !blockBackgroundHover
                    && mouseX >= removeX && mouseX < removeX + STAR_ROW_SIZE
                        && mouseY >= removeY && mouseY < removeY + STAR_ROW_SIZE;
                if (hoverRowRemove) {
                    hoverCategoryRowRemove = true;
                }
                int rowRemoveColor = hoverRowRemove ? COLOR_STAR : COLOR_STAR_OFF;
                ctx.drawText(this.textRenderer, "✕", removeX + 2, removeY + 1, rowRemoveColor, false);
            }
        }

        if (hoverCategoryRowRemove) {
            ctx.drawTooltip(this.textRenderer,
                    Text.translatable("screen.cmdpalette.tooltip.remove_row_category"),
                    mouseX, mouseY);
        }

        if (currentSize > maxVisible) {
            int trackHeight = maxVisible * SUGGESTION_ITEM_HEIGHT;
            int barHeight = Math.max(16, trackHeight * maxVisible / currentSize);
            int barY = listStartY + (int) ((float) scrollOffset / currentSize * trackHeight);
            ctx.fill(paletteX + paletteWidth - 6, barY,
                    paletteX + paletteWidth - 3, barY + barHeight, COLOR_SCROLLBAR);
        }

        if (categoryPickerOpen) {
            renderCategoryPicker(ctx, mouseX, mouseY);
            if (creatingCategoryInput) {
                categoryInputField.render(ctx, mouseX, mouseY, delta);
            }
        }
    }

    private int computePaletteHeight() {
        int headerHeight = PADDING + NAVBAR_HEIGHT + NAVBAR_GAP + INPUT_HEIGHT + 12 + 1 + 4;
        if (currentView == ViewMode.SETTINGS) {
            return headerHeight + (SETTINGS_ROW_HEIGHT * 6) + 28 + PADDING;
        }

        int listHeight = Math.min(getVisibleEntries().size(), getConfiguredMaxVisibleItems()) * SUGGESTION_ITEM_HEIGHT;
        return headerHeight + listHeight + PADDING;
    }

    private void renderCategoryPicker(DrawContext ctx, int mouseX, int mouseY) {
        int pickerX = getCategoryPickerX();
        int pickerY = getCategoryPickerY();
        int pickerWidth = CATEGORY_PICKER_WIDTH;
        int pickerHeight = getCategoryPickerHeight();

        ctx.fill(pickerX, pickerY, pickerX + pickerWidth, pickerY + pickerHeight, COLOR_BUTTON_BG);
        ctx.fill(pickerX, pickerY, pickerX + pickerWidth, pickerY + 1, COLOR_BORDER_TOP);

        if (creatingCategoryInput) {
            return;
        }

        List<Integer> assignable = getAssignableCategoryIndices();
        int rowY = pickerY + 2;
        for (int i = 0; i < assignable.size(); i++) {
            int idx = assignable.get(i);
            boolean hoverRow = mouseX >= pickerX && mouseX < pickerX + pickerWidth
                    && mouseY >= rowY && mouseY < rowY + CATEGORY_PICKER_ROW_HEIGHT;
            if (hoverRow) {
                ctx.fill(pickerX + 1, rowY, pickerX + pickerWidth - 1, rowY + CATEGORY_PICKER_ROW_HEIGHT, COLOR_ACCENT);
            }
            ctx.drawText(this.textRenderer, getCategoryDisplayName(categories.get(idx)),
                    pickerX + 6, rowY + 5, 0xFFC5C8C6, false);
            rowY += CATEGORY_PICKER_ROW_HEIGHT;
        }

        boolean hoverNew = mouseX >= pickerX && mouseX < pickerX + pickerWidth
                && mouseY >= rowY && mouseY < rowY + CATEGORY_PICKER_ROW_HEIGHT;
        if (hoverNew) {
            ctx.fill(pickerX + 1, rowY, pickerX + pickerWidth - 1, rowY + CATEGORY_PICKER_ROW_HEIGHT, COLOR_ACCENT);
        }
        ctx.drawText(this.textRenderer, "+ " + Text.translatable("screen.cmdpalette.menu.new_category").getString(),
                pickerX + 6, rowY + 5, COLOR_STAR, false);
    }

    private void renderSettingsContent(DrawContext ctx, int mouseX, int mouseY) {
        int rowGap = SETTINGS_ROW_HEIGHT + 6;
        int row1Y = getSettingsContentY();
        int row2Y = row1Y + rowGap;
        int row3Y = row2Y + rowGap;
        int row4Y = row3Y + rowGap;
        int row5Y = row4Y + rowGap;
        int row6Y = row5Y + rowGap;

        int controlsRight = getSettingsControlsRightX();
        int labelX = getSettingsLabelX();
        int valueX = getSettingsValueX();

        int decX = getSettingsDecreaseX();
        int incX = getSettingsIncreaseX();
        int switchX = getSettingsSlashSwitchX();

        boolean hoverDec = mouseX >= decX && mouseX < decX + SETTINGS_BUTTON_WIDTH
                && mouseY >= row1Y && mouseY < row1Y + NAVBAR_HEIGHT;
        boolean hoverInc = mouseX >= incX && mouseX < incX + SETTINGS_BUTTON_WIDTH
                && mouseY >= row1Y && mouseY < row1Y + NAVBAR_HEIGHT;
        boolean hoverSwitch = mouseX >= switchX && mouseX < switchX + 56
                && mouseY >= row2Y && mouseY < row2Y + NAVBAR_HEIGHT;

        int decBg = hoverDec ? COLOR_ACCENT : COLOR_BUTTON_BG;
        int incBg = hoverInc ? COLOR_ACCENT : COLOR_BUTTON_BG;
        int switchBg = hideSlashPrefix ? COLOR_CATEGORY_ACTIVE : COLOR_BUTTON_BG;
        if (hoverSwitch) switchBg = COLOR_ACCENT;

        ctx.drawText(this.textRenderer,
                Text.translatable("screen.cmdpalette.settings.max_visible").getString(),
            labelX, row1Y + 4, 0xFFC5C8C6, false);

        ctx.fill(decX, row1Y, decX + SETTINGS_BUTTON_WIDTH, row1Y + NAVBAR_HEIGHT, decBg);
        ctx.fill(incX, row1Y, incX + SETTINGS_BUTTON_WIDTH, row1Y + NAVBAR_HEIGHT, incBg);
        ctx.drawText(this.textRenderer, "-", decX + 7, row1Y + 5, COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, "+", incX + 7, row1Y + 5, COLOR_STAR_OFF, false);
        String maxVisibleText = String.valueOf(getConfiguredMaxVisibleItems());
        ctx.drawText(this.textRenderer, maxVisibleText, valueX, row1Y + 5, COLOR_STAR, false);

        ctx.drawText(this.textRenderer,
                Text.translatable("screen.cmdpalette.settings.hide_slash").getString(),
            labelX, row2Y + 4, 0xFFC5C8C6, false);
        ctx.fill(switchX, row2Y, switchX + 56, row2Y + NAVBAR_HEIGHT, switchBg);
        String switchText = hideSlashPrefix ? "ON" : "OFF";
        int switchTextX = switchX + (56 - this.textRenderer.getWidth(switchText)) / 2;
        ctx.drawText(this.textRenderer, switchText, switchTextX, row2Y + 5,
            hideSlashPrefix ? COLOR_STAR : COLOR_STAR_OFF, false);

        CommandPaletteThemeStore.ThemePreset activeTheme = getActiveTheme();
        String themeName = activeTheme == null ? "-" : activeTheme.name();
        boolean editable = activeTheme != null && activeTheme.editable();

        int themePrevX = controlsRight - 44;
        int themeNextX = themePrevX + 24;
        int themeNameX = valueX;
        int themeNameWidth = Math.max(40, themePrevX - themeNameX - 6);
        boolean hoverThemePrev = mouseX >= themePrevX && mouseX < themePrevX + 20
            && mouseY >= row3Y && mouseY < row3Y + NAVBAR_HEIGHT;
        boolean hoverThemeNext = mouseX >= themeNextX && mouseX < themeNextX + 20
            && mouseY >= row3Y && mouseY < row3Y + NAVBAR_HEIGHT;
        ctx.drawText(this.textRenderer,
                Text.translatable("screen.cmdpalette.settings.theme").getString(),
            labelX, row3Y + 4, 0xFFC5C8C6, false);
        ctx.fill(themePrevX, row3Y, themePrevX + 20, row3Y + NAVBAR_HEIGHT, hoverThemePrev ? COLOR_ACCENT : COLOR_BUTTON_BG);
        ctx.fill(themeNextX, row3Y, themeNextX + 20, row3Y + NAVBAR_HEIGHT, hoverThemeNext ? COLOR_ACCENT : COLOR_BUTTON_BG);
        ctx.drawText(this.textRenderer, "<", themePrevX + 7, row3Y + 5, COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, ">", themeNextX + 7, row3Y + 5, COLOR_STAR_OFF, false);
        if (renamingTheme && editable) {
            themeRenameInputField.setPosition(themeNameX, row3Y);
            themeRenameInputField.setWidth(themeNameWidth);
            themeRenameInputField.setVisible(true);
            themeRenameInputField.render(ctx, mouseX, mouseY, 0f);
        } else {
            themeRenameInputField.setVisible(false);
            ctx.drawText(this.textRenderer, themeName, themeNameX, row3Y + 4, COLOR_STAR, false);
        }

        int actionWidth = 40;
        int actionGap = 4;
        int actionsCount = 4;
        int actionsStartX = controlsRight - (actionWidth * actionsCount + actionGap * (actionsCount - 1));
        String[] actionLabels = {
            Text.translatable("screen.cmdpalette.theme.action.new").getString(),
            Text.translatable("screen.cmdpalette.theme.action.del").getString(),
            Text.translatable("screen.cmdpalette.theme.action.file").getString(),
            Text.translatable("screen.cmdpalette.theme.action.ren").getString()
        };
        for (int i = 0; i < actionLabels.length; i++) {
            int x = actionsStartX + i * (actionWidth + actionGap);
            boolean disabled = (i == 1 || i == 3) && !editable;
            boolean hoverAction = mouseX >= x && mouseX < x + actionWidth
                && mouseY >= row4Y && mouseY < row4Y + NAVBAR_HEIGHT;
            int bg = COLOR_BUTTON_BG;
            if (disabled) {
                bg = 0xFF1F1F1F;
            } else if (hoverAction) {
            bg = COLOR_ACCENT;
            }
            ctx.fill(x, row4Y, x + actionWidth, row4Y + NAVBAR_HEIGHT, bg);
            int txtColor = disabled ? 0xFF555555 : COLOR_STAR_OFF;
            int textX = x + Math.max(3, (actionWidth - this.textRenderer.getWidth(actionLabels[i])) / 2);
            ctx.drawText(this.textRenderer, actionLabels[i], textX, row4Y + 5, txtColor, false);
        }

        ThemeAspect aspect = getSelectedThemeAspect();
        int aspectPrevX = controlsRight - 44;
        int aspectNextX = aspectPrevX + 24;
        boolean hoverAspectPrev = mouseX >= aspectPrevX && mouseX < aspectPrevX + 20
            && mouseY >= row5Y && mouseY < row5Y + NAVBAR_HEIGHT;
        boolean hoverAspectNext = mouseX >= aspectNextX && mouseX < aspectNextX + 20
            && mouseY >= row5Y && mouseY < row5Y + NAVBAR_HEIGHT;
        ctx.drawText(this.textRenderer,
                Text.translatable("screen.cmdpalette.settings.aspect").getString(),
            labelX, row5Y + 4, 0xFFC5C8C6, false);
        ctx.fill(aspectPrevX, row5Y, aspectPrevX + 20, row5Y + NAVBAR_HEIGHT, hoverAspectPrev ? COLOR_ACCENT : COLOR_BUTTON_BG);
        ctx.fill(aspectNextX, row5Y, aspectNextX + 20, row5Y + NAVBAR_HEIGHT, hoverAspectNext ? COLOR_ACCENT : COLOR_BUTTON_BG);
        ctx.drawText(this.textRenderer, "<", aspectPrevX + 7, row5Y + 5, COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, ">", aspectNextX + 7, row5Y + 5, COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, Text.translatable(aspect.translationKey).getString(), valueX, row5Y + 4,
                COLOR_STAR, false);

        int currentColor = activeTheme == null ? COLOR_STAR : getAspectColor(activeTheme.colors(), aspect);
        int swatchX = valueX;
        ctx.fill(swatchX, row6Y + 2, swatchX + 28, row6Y + NAVBAR_HEIGHT - 2, currentColor);
        int hexX = swatchX + 34;
        int hexWidth = 92;
        themeHexInputField.setPosition(hexX, row6Y);
        themeHexInputField.setWidth(hexWidth);
        themeHexInputField.setVisible(true);
        if (!editable) {
            themeHexInputField.setFocused(false);
        }
        syncThemeHexInputFromActiveAspect();
        themeHexInputField.render(ctx, mouseX, mouseY, 0f);

        int rgbStartX = controlsRight - 150;
        String[] labels = {"R", "G", "B"};
        for (int i = 0; i < 3; i++) {
            int baseX = rgbStartX + i * 50;
            int minusX = baseX;
            int plusX = baseX + 20;
            boolean hoverMinus = mouseX >= minusX && mouseX < minusX + 18
                    && mouseY >= row6Y && mouseY < row6Y + NAVBAR_HEIGHT;
            boolean hoverPlus = mouseX >= plusX && mouseX < plusX + 18
                    && mouseY >= row6Y && mouseY < row6Y + NAVBAR_HEIGHT;
            ctx.drawText(this.textRenderer, labels[i], baseX + 40, row6Y + 5, COLOR_STAR_OFF, false);
            int minusBg = editable ? (hoverMinus ? COLOR_ACCENT : COLOR_BUTTON_BG) : 0xFF1F1F1F;
            int plusBg = editable ? (hoverPlus ? COLOR_ACCENT : COLOR_BUTTON_BG) : 0xFF1F1F1F;
            ctx.fill(minusX, row6Y, minusX + 18, row6Y + NAVBAR_HEIGHT, minusBg);
            ctx.fill(plusX, row6Y, plusX + 18, row6Y + NAVBAR_HEIGHT, plusBg);
            int c = editable ? COLOR_STAR_OFF : 0xFF555555;
            ctx.drawText(this.textRenderer, "-", minusX + 6, row6Y + 5, c, false);
            ctx.drawText(this.textRenderer, "+", plusX + 6, row6Y + 5, c, false);
        }
    }

    private void renderSyntaxHighlighted(DrawContext ctx, String command, int x, int y) {
        if (command == null || command.isEmpty()) return;

        String[] tokens = command.split(" ");
        int cx = x;

        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            int color;

            if (i == 0) {
                if (token.startsWith("/")) {
                    ctx.drawText(this.textRenderer, "/", cx, y, COLOR_SLASH, false);
                    cx += this.textRenderer.getWidth("/");
                    token = token.substring(1);
                }
                color = COLOR_COMMAND;
            } else {
                color = classifyToken(token, i);
            }

            ctx.drawText(this.textRenderer, token, cx, y, color, false);
            cx += this.textRenderer.getWidth(token);

            if (i < tokens.length - 1) {
                cx += this.textRenderer.getWidth(" ");
            }
        }
    }

    private int classifyToken(String token, int positionIndex) {
        if (token.startsWith("@")) return COLOR_SELECTOR;
        if (token.startsWith("~") || token.startsWith("^")) return COLOR_COORDINATE;
        if (token.startsWith("\"") || token.endsWith("\"")) return COLOR_STRING;
        if ("true".equals(token) || "false".equals(token)) return COLOR_BOOLEAN;
        if (isNumeric(token)) return COLOR_NUMBER;
        return POSITION_PALETTE[(positionIndex - 1) % POSITION_PALETTE.length];
    }

    private static boolean isNumeric(String s) {
        if (s.isEmpty()) return false;
        int start = (s.charAt(0) == '-' || s.charAt(0) == '+') ? 1 : 0;
        if (start >= s.length()) return false;
        boolean dot = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '.') {
                if (dot) return false;
                dot = true;
            } else if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        int keyCode = keyInput.key();

        if (creatingCategoryInput) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                createCategoryFromDedicatedInput();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeCategoryPicker();
                return true;
            }
            if (categoryInputField.keyPressed(keyInput)) {
                return true;
            }
            return true;
        }

        if (categoryPickerOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeCategoryPicker();
                return true;
            }
            return true;
        }

        if (currentView == ViewMode.SETTINGS) {
            if (renamingTheme) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    commitThemeRename();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    cancelThemeRename();
                    return true;
                }
                if (themeRenameInputField.keyPressed(keyInput)) {
                    return true;
                }
                return true;
            }

            if (themeHexInputField.isFocused()) {
                if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                    applyThemeHexInput(true);
                    focusMainInputFromSettingsEditor();
                    return true;
                }
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                    syncThemeHexInputFromActiveAspect();
                    focusMainInputFromSettingsEditor();
                    return true;
                }
                if (themeHexInputField.keyPressed(keyInput)) {
                    applyThemeHexInput(false);
                    return true;
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeSettingsView();
                return true;
            }
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_TAB -> {
                applySelectedSuggestion();
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                moveSelection(1);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                moveSelection(-1);
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                executeCommand();
                return true;
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                close();
                return true;
            }
        }
        return super.keyPressed(keyInput);
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (creatingCategoryInput) {
            return categoryInputField.charTyped(charInput);
        }
        if (categoryPickerOpen) {
            return true;
        }
        if (currentView == ViewMode.SETTINGS) {
            if (renamingTheme) {
                return themeRenameInputField.charTyped(charInput);
            }
            if (themeHexInputField.isFocused()) {
                boolean handled = themeHexInputField.charTyped(charInput);
                if (handled) {
                    applyThemeHexInput(false);
                }
                return handled;
            }
            return true;
        }
        return super.charTyped(charInput);
    }

    private void moveSelection(int direction) {
        List<String> entries = getVisibleEntries();
        int maxVisible = getConfiguredMaxVisibleItems();
        if (entries.isEmpty()) return;

        selectedIndex = Math.max(0, Math.min(selectedIndex + direction, entries.size() - 1));

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + maxVisible) {
            scrollOffset = selectedIndex - maxVisible + 1;
        }
    }

    private void applySelectedSuggestion() {
        List<String> entries = getVisibleEntries();
        if (entries.isEmpty()) return;
        if (selectedIndex < 0) selectedIndex = 0;

        String selected = entries.get(selectedIndex);
        inputField.setText(selected);
        inputField.setCursorToEnd(false);
        if (currentView == ViewMode.COMMANDS) {
            refreshSuggestions(selected);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        int navbarY = getNavbarY();
        int inputY = getInputY();
        int row1Y = getSettingsRow1Y();
        int row2Y = getSettingsRow2Y();
        int settingsDecX = getSettingsDecreaseX();
        int settingsIncX = getSettingsIncreaseX();
        int settingsSwitchX = getSettingsSlashSwitchX();
        int historyTabX = getHistoryTabX();
        int favoritesTabX = getFavoritesTabX();
        int favoritesTabWidth = getFavoritesTabWidth();
        int favoritesIndex = getFavoritesCategoryIndex(false);
        int categoriesStartX = getCategoryTabsStartX();
        int scrollLeftX = getCategoryScrollLeftX();
        int scrollRightX = getCategoryScrollRightX();
        int contextActionX = getContextActionButtonX();
        int settingsX = getSettingsButtonX();
        int addCategoryX = getAddToCategoryButtonX();
        int favoriteButtonX = getFavoriteButtonX();

        if (categoryPickerOpen) {
            if (creatingCategoryInput && categoryInputField.mouseClicked(click, bl)) {
                return true;
            }

            if (isInsideCategoryPicker(click.x(), click.y())) {
                int row = getCategoryPickerRowIndexAt(click.x(), click.y());
                if (row >= 0) {
                    List<Integer> assignable = getAssignableCategoryIndices();
                    if (row < assignable.size()) {
                        addCurrentInputToCategory(assignable.get(row));
                        closeCategoryPicker();
                        return true;
                    }
                    if (row == assignable.size()) {
                        openCategoryCreationInput();
                        return true;
                    }
                }
            } else {
                closeCategoryPicker();
            }
        }

        if (click.x() >= historyTabX && click.x() < historyTabX + TAB_BUTTON_WIDTH
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            toggleView(ViewMode.HISTORY);
            return true;
        }

        if (favoritesTabWidth > 0 && click.x() >= favoritesTabX && click.x() < favoritesTabX + favoritesTabWidth
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT
                && favoritesIndex >= 0 && favoritesIndex < categories.size()) {
            activateCategoryTab(favoritesIndex);
            return true;
        }

        if (click.x() >= scrollLeftX && click.x() < scrollLeftX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            if (canScrollCategoriesLeft()) {
                categoryScrollIndex--;
                clampCategoryScrollIndex();
            }
            return true;
        }

        if (click.x() >= scrollRightX && click.x() < scrollRightX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            if (canScrollCategoriesRight()) {
                categoryScrollIndex++;
                clampCategoryScrollIndex();
            }
            return true;
        }

        List<Integer> scrollableCategoryIndices = getScrollableCategoryIndices();
        int categoryX = categoriesStartX;
        for (int offset = categoryScrollIndex; offset < scrollableCategoryIndices.size(); offset++) {
            int i = scrollableCategoryIndices.get(offset);
            CommandCategoriesStore.Category category = categories.get(i);
            int categoryWidth = getCategoryTabWidth(category);
            if (categoryX + categoryWidth > getCategoryTabsAreaRightX()) {
                break;
            }

            if (click.x() >= categoryX && click.x() < categoryX + categoryWidth
                    && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
                activateCategoryTab(i);
                return true;
            }

            categoryX += categoryWidth + TAB_GAP;
        }

        if (click.x() >= contextActionX && click.x() < contextActionX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            if (currentView == ViewMode.HISTORY) {
                clearHistory();
                return true;
            }
            if (canDeleteSelectedCategory()) {
                deleteSelectedCategory();
                return true;
            }
        }

        if (click.x() >= settingsX && click.x() < settingsX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            if (currentView == ViewMode.SETTINGS) {
                closeSettingsView();
                return true;
            }
            openSettingsView();
            return true;
        }

        if (currentView == ViewMode.SETTINGS) {
            int rowGap = SETTINGS_ROW_HEIGHT + 6;
            int row3Y = getSettingsContentY() + (rowGap * 2);
            int row4Y = row3Y + rowGap;
            int row5Y = row4Y + rowGap;
            int row6Y = row5Y + rowGap;

            int controlsRight = getSettingsControlsRightX();
            int themePrevX = controlsRight - 44;
            int themeNextX = themePrevX + 24;

            int actionWidth = 40;
            int actionGap = 4;
            int actionsCount = 4;
            int actionsStartX = controlsRight - (actionWidth * actionsCount + actionGap * (actionsCount - 1));

            int aspectPrevX = controlsRight - 44;
            int aspectNextX = aspectPrevX + 24;
            int rgbStartX = controlsRight - 150;
            int themeNameX = getSettingsValueX();
            int themeNameWidth = Math.max(40, themePrevX - themeNameX - 6);
            int hexX = themeNameX + 34;
            int hexWidth = 92;

            if (renamingTheme) {
                if (themeRenameInputField.mouseClicked(click, bl)) {
                    return true;
                }
                if (!(click.x() >= themeNameX && click.x() < themeNameX + themeNameWidth
                        && click.y() >= row3Y && click.y() < row3Y + NAVBAR_HEIGHT)) {
                    commitThemeRename();
                }
            }

            if (themeHexInputField.isFocused()
                    && !(click.x() >= hexX && click.x() < hexX + hexWidth
                    && click.y() >= row6Y && click.y() < row6Y + NAVBAR_HEIGHT)) {
                applyThemeHexInput(true);
                focusMainInputFromSettingsEditor();
            }

            if (isActiveThemeEditable()
                    && click.x() >= hexX && click.x() < hexX + hexWidth
                    && click.y() >= row6Y && click.y() < row6Y + NAVBAR_HEIGHT) {
                themeHexInputField.setFocused(true);
                setFocused(themeHexInputField);
                themeHexInputField.mouseClicked(click, bl);
                return true;
            }

            if (click.x() >= settingsDecX && click.x() < settingsDecX + SETTINGS_BUTTON_WIDTH
                    && click.y() >= row1Y && click.y() < row1Y + NAVBAR_HEIGHT) {
                maxVisibleItems = Math.max(CommandPaletteSettingsStore.MIN_MAX_VISIBLE_ITEMS,
                        getConfiguredMaxVisibleItems() - 1);
                clampSelectionAndScroll();
                persistSettings();
                return true;
            }

            if (click.x() >= settingsIncX && click.x() < settingsIncX + SETTINGS_BUTTON_WIDTH
                    && click.y() >= row1Y && click.y() < row1Y + NAVBAR_HEIGHT) {
                maxVisibleItems = Math.min(CommandPaletteSettingsStore.MAX_MAX_VISIBLE_ITEMS,
                        getConfiguredMaxVisibleItems() + 1);
                clampSelectionAndScroll();
                persistSettings();
                return true;
            }

            if (click.x() >= settingsSwitchX && click.x() < settingsSwitchX + 56
                    && click.y() >= row2Y && click.y() < row2Y + NAVBAR_HEIGHT) {
                hideSlashPrefix = !hideSlashPrefix;
                applySlashPreferenceToStoredCommands();
                inputField.setText(normalizeCommand(inputField.getText()));
                inputField.setCursorToEnd(false);
                refreshSuggestions(inputField.getText());
                clampSelectionAndScroll();
                persistSettings();
                return true;
            }

            if (click.x() >= themePrevX && click.x() < themePrevX + 20
                    && click.y() >= row3Y && click.y() < row3Y + NAVBAR_HEIGHT) {
                selectTheme(-1);
                return true;
            }

            if (click.x() >= themeNextX && click.x() < themeNextX + 20
                    && click.y() >= row3Y && click.y() < row3Y + NAVBAR_HEIGHT) {
                selectTheme(1);
                return true;
            }

            for (int i = 0; i < actionsCount; i++) {
                int x = actionsStartX + i * (actionWidth + actionGap);
                if (click.x() >= x && click.x() < x + actionWidth
                        && click.y() >= row4Y && click.y() < row4Y + NAVBAR_HEIGHT) {
                    if (i == 0) createNewTheme();
                    if (i == 1) deleteActiveTheme();
                    if (i == 2) openThemeFilesForActiveTheme();
                    if (i == 3) startThemeRename();
                    return true;
                }
            }

            if (click.x() >= aspectPrevX && click.x() < aspectPrevX + 20
                    && click.y() >= row5Y && click.y() < row5Y + NAVBAR_HEIGHT) {
                selectThemeAspect(-1);
                return true;
            }

            if (click.x() >= aspectNextX && click.x() < aspectNextX + 20
                    && click.y() >= row5Y && click.y() < row5Y + NAVBAR_HEIGHT) {
                selectThemeAspect(1);
                return true;
            }

            for (int i = 0; i < 3; i++) {
                int baseX = rgbStartX + i * 50;
                int minusX = baseX;
                int plusX = baseX + 20;

                if (click.x() >= minusX && click.x() < minusX + 18
                        && click.y() >= row6Y && click.y() < row6Y + NAVBAR_HEIGHT) {
                    adjustActiveThemeAspectColor(i, -8);
                    return true;
                }

                if (click.x() >= plusX && click.x() < plusX + 18
                        && click.y() >= row6Y && click.y() < row6Y + NAVBAR_HEIGHT) {
                    adjustActiveThemeAspectColor(i, 8);
                    return true;
                }
            }

            return true;
        }

        if (click.x() >= addCategoryX && click.x() < addCategoryX + STAR_BUTTON_SIZE
            && click.y() >= inputY && click.y() < inputY + STAR_BUTTON_SIZE) {
            if (categoryPickerOpen) {
                closeCategoryPicker();
            } else {
                openCategoryPicker();
            }
            return true;
        }

        if (click.x() >= favoriteButtonX && click.x() < favoriteButtonX + STAR_BUTTON_SIZE
            && click.y() >= inputY && click.y() < inputY + STAR_BUTTON_SIZE) {
            toggleCurrentInputFavoriteCommand();
            return true;
        }

        int removeIndex = getCategoryCommandRemoveIndexAt(click.x(), click.y());
        if (removeIndex >= 0) {
            removeCategoryCommandAt(removeIndex);
            return true;
        }

        int hitIndex = getSuggestionIndexAt(click.x(), click.y());
        if (hitIndex >= 0) {
            selectedIndex = hitIndex;
            applySelectedSuggestion();
            return true;
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double horizontalAmount, double verticalAmount) {
        int maxVisible = getConfiguredMaxVisibleItems();
        int navbarY = getNavbarY();
        int categoriesStartX = getCategoryScrollLeftX();
        int categoriesEndX = getCategoryScrollRightX() + NAVBAR_HEIGHT;
        boolean overCategoryTabs = mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT
                && mouseX >= categoriesStartX && mouseX < categoriesEndX;

        if (overCategoryTabs && categories.size() > getVisibleCategoryCountFrom(0)) {
            if (verticalAmount < 0) {
                categoryScrollIndex++;
                clampCategoryScrollIndex();
                return true;
            }
            if (verticalAmount > 0) {
                categoryScrollIndex--;
                clampCategoryScrollIndex();
                return true;
            }
        }

        int currentSize = getVisibleEntries().size();
        if (currentSize > maxVisible) {
            int maxScroll = currentSize - maxVisible;
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, maxScroll));
        }
        return true;
    }

    private int getSuggestionIndexAt(double mouseX, double mouseY) {
        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        int listStartY = getSeparatorY() + 4;
        int currentSize = getVisibleEntries().size();
        int visibleCount = Math.min(currentSize - scrollOffset, getConfiguredMaxVisibleItems());

        if (mouseX < paletteX + 4 || mouseX > paletteX + paletteWidth - 4) return -1;

        for (int i = 0; i < visibleCount; i++) {
            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;
            if (mouseY >= sy && mouseY < sy + SUGGESTION_ITEM_HEIGHT) {
                return i + scrollOffset;
            }
        }
        return -1;
    }

    private int getCategoryCommandRemoveIndexAt(double mouseX, double mouseY) {
        if (!isCategoryView()) return -1;

        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        int listStartY = getSeparatorY() + 4;
        int currentSize = getSelectedCategoryCommands().size();
        int visibleCount = Math.min(currentSize - scrollOffset, getConfiguredMaxVisibleItems());
        int starX = paletteX + paletteWidth - PADDING - STAR_ROW_SIZE;

        for (int i = 0; i < visibleCount; i++) {
            int index = i + scrollOffset;
            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;
            int starY = sy + (SUGGESTION_ITEM_HEIGHT - STAR_ROW_SIZE) / 2;

            if (mouseX >= starX && mouseX < starX + STAR_ROW_SIZE
                    && mouseY >= starY && mouseY < starY + STAR_ROW_SIZE) {
                return index;
            }
        }

        return -1;
    }

    private void executeCommand() {
        String text;
        List<String> entries = getVisibleEntries();
        if (selectedIndex >= 0 && selectedIndex < entries.size()) {
            text = entries.get(selectedIndex);
        } else {
            text = inputField.getText();
        }

        if (text == null || text.isBlank()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        addToHistory(text);
        String command = text.startsWith("/") ? text.substring(1) : text;
        client.player.networkHandler.sendChatCommand(command);
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
