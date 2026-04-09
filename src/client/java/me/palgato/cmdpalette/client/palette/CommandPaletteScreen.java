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
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class CommandPaletteScreen extends Screen {

    private static final int PALETTE_MAX_WIDTH = 520;
    private static final int PALETTE_MIN_WIDTH = 320;
    private static final int INPUT_HEIGHT = 20;
    private static final int SUGGESTION_ITEM_HEIGHT = 18;
    private static final int MAX_VISIBLE = 12;
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
    private static final int MAX_HISTORY_ENTRIES = 100;

    private static final int COLOR_OVERLAY = 0xB0000000;
    private static final int COLOR_BG = 0xFF1A1A1A;
    private static final int COLOR_SHADOW = 0xFF0A0A0A;
    private static final int COLOR_ACCENT = 0xFF4A4A4A;
    private static final int COLOR_INPUT_BG = 0xFF242424;
    private static final int COLOR_SEPARATOR = 0xFF333333;
    private static final int COLOR_HOVER = 0xFF2E2E2E;
    private static final int COLOR_SELECTED = 0xFF3A3A3A;
    private static final int COLOR_SCROLLBAR = 0xFF555555;
    private static final int COLOR_BORDER_TOP = 0xFF505050;
    private static final int COLOR_STAR = 0xFFFFD75E;
    private static final int COLOR_STAR_OFF = 0xFF808080;
    private static final int COLOR_BUTTON_BG = 0xFF2A2A2A;
    private static final int COLOR_BUTTON_ACTIVE = 0xFF3B3B3B;
    private static final int COLOR_CATEGORY_ACTIVE = 0xFF4A3F2A;

    private static final int COLOR_SLASH = 0xFF888888;
    private static final int COLOR_COMMAND = 0xFF5AF5E2;
    private static final int COLOR_SELECTOR = 0xFFFFE066;
    private static final int COLOR_COORDINATE = 0xFFFF6B6B;
    private static final int COLOR_STRING = 0xFFFFB347;
    private static final int COLOR_NUMBER = 0xFF98E66B;
    private static final int COLOR_BOOLEAN = 0xFF7EC8E3;

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
    private final List<String> suggestions = new CopyOnWriteArrayList<>();
    private final List<CommandCategoriesStore.Category> categories = new CopyOnWriteArrayList<>();
    private final List<String> history = new CopyOnWriteArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private int selectedCategoryIndex = -1;
    private int categoryScrollIndex = 0;
    private boolean creatingCategoryInput = false;
    private ViewMode currentView = ViewMode.COMMANDS;

    private String cachedColorText = "";
    private int[] cachedColors = new int[0];

    private enum ViewMode {
        COMMANDS,
        CATEGORY,
        HISTORY
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

        addDrawableChild(inputField);
        addDrawableChild(categoryInputField);
        setInitialFocus(inputField);

        categories.clear();
        categories.addAll(CommandCategoriesStore.load());
        ensureDefaultCategory();
        history.clear();
        history.addAll(CommandHistoryStore.load());
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
            default -> suggestions;
        };
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

    private int getCreateCategoryTabX() {
        int favoritesWidth = getFavoritesTabWidth();
        if (favoritesWidth > 0) {
            return getFavoritesTabX() + favoritesWidth + TAB_GAP;
        }
        return getHistoryTabX() + TAB_BUTTON_WIDTH + TAB_GAP;
    }

    private int getCategoryScrollLeftX() {
        return getCreateCategoryTabX() + NAVBAR_HEIGHT + TAB_GAP;
    }

    private int getCategoryTabsStartX() {
        return getCategoryScrollLeftX() + NAVBAR_HEIGHT + TAB_GAP;
    }

    private int getSettingsButtonX() {
        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        return paletteX + paletteWidth - PADDING - NAVBAR_HEIGHT;
    }

    private int getCategoryScrollRightX() {
        return getSettingsButtonX() - TAB_GAP - NAVBAR_HEIGHT;
    }

    private int getAddToCategoryButtonX() {
        return getInputX() + getInputWidth() + STAR_GAP;
    }

    private int getFavoriteButtonX() {
        return getAddToCategoryButtonX() + STAR_BUTTON_SIZE + STAR_GAP;
    }

    private int getCategoryInputX() {
        return getCategoryTabsStartX();
    }

    private int getCategoryInputY() {
        return getNavbarY();
    }

    private int getCategoryInputWidth() {
        return Math.max(96, getCategoryTabsAreaRightX() - getCategoryInputX());
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

    private String normalizeCommand(String text) {
        if (text == null) return "";
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return "";
        return trimmed.startsWith("/") ? trimmed : "/" + trimmed;
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
        creatingCategoryInput = true;
        categoryInputField.setText("");
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

        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name().equalsIgnoreCase(name)) {
                selectedCategoryIndex = i;
                setView(ViewMode.CATEGORY);
                ensureCategoryTabVisible(selectedCategoryIndex);
                closeCategoryCreationInput();
                return;
            }
        }

        if (name.length() > 24) {
            name = name.substring(0, 24);
        }

        categories.add(new CommandCategoriesStore.Category(name, new ArrayList<>()));
        selectedCategoryIndex = categories.size() - 1;
        CommandCategoriesStore.save(categories);
        setView(ViewMode.CATEGORY);
        ensureCategoryTabVisible(selectedCategoryIndex);
        closeCategoryCreationInput();
    }

    private void setView(ViewMode viewMode) {
        currentView = viewMode;
        selectedIndex = -1;
        scrollOffset = 0;
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
        if (size == 0) {
            selectedIndex = -1;
            scrollOffset = 0;
            return;
        }

        selectedIndex = Math.min(selectedIndex, size - 1);
        if (selectedIndex < 0) {
            selectedIndex = -1;
        }

        int maxScroll = Math.max(0, size - MAX_VISIBLE);
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
            dispatcher.getRoot().getChildren().forEach(node -> rootCommands.add("/" + node.getName()));
            Collections.sort(rootCommands);
            suggestions.addAll(rootCommands);
            return;
        }

        ParseResults<ClientCommandSource> parseResults = dispatcher.parse(raw, source);
        CompletableFuture<Suggestions> future = dispatcher.getCompletionSuggestions(parseResults);

        future.thenAccept(result -> {
            List<String> completions = new ArrayList<>();
            for (Suggestion s : result.getList()) {
                String text = "/" + raw.substring(0, s.getRange().getStart()) + s.getText();
                if (!completions.contains(text)) {
                    completions.add(text);
                }
            }

            if (completions.isEmpty()) {
                String lowerInput = ("/" + raw).toLowerCase();
                dispatcher.getRoot().getChildren().forEach(node -> {
                    String name = "/" + node.getName();
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
        int createCategoryX = getCreateCategoryTabX();
        int categoriesStartX = getCategoryTabsStartX();
        int scrollLeftX = getCategoryScrollLeftX();
        int scrollRightX = getCategoryScrollRightX();
        int actionX = getSettingsButtonX();
        int addCategoryX = getAddToCategoryButtonX();
        int favoriteButtonX = getFavoriteButtonX();
        boolean isHistoryView = currentView == ViewMode.HISTORY;
        boolean isCategoryView = isCategoryView();
        boolean canDeleteCategory = canDeleteSelectedCategory();
        boolean canScrollLeft = canScrollCategoriesLeft();
        boolean canScrollRight = canScrollCategoriesRight();

        boolean hoverHistoryTab = mouseX >= historyTabX && mouseX < historyTabX + TAB_BUTTON_WIDTH
                && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverCreateCategory = mouseX >= createCategoryX && mouseX < createCategoryX + NAVBAR_HEIGHT
                && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverFavoritesTab = favoritesTabWidth > 0
            && mouseX >= favoritesTabX && mouseX < favoritesTabX + favoritesTabWidth
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverScrollLeft = mouseX >= scrollLeftX && mouseX < scrollLeftX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverScrollRight = mouseX >= scrollRightX && mouseX < scrollRightX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverAction = mouseX >= actionX && mouseX < actionX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverAddCategory = mouseX >= addCategoryX && mouseX < addCategoryX + STAR_BUTTON_SIZE
            && mouseY >= inputY && mouseY < inputY + STAR_BUTTON_SIZE;
        boolean hoverFavoriteButton = mouseX >= favoriteButtonX && mouseX < favoriteButtonX + STAR_BUTTON_SIZE
            && mouseY >= inputY && mouseY < inputY + STAR_BUTTON_SIZE;

        int createCategoryBg = COLOR_BUTTON_BG;
        int favoritesTabBg = COLOR_BUTTON_BG;
        int scrollLeftBg = COLOR_BUTTON_BG;
        int scrollRightBg = COLOR_BUTTON_BG;
        int historyTabBg = currentView == ViewMode.HISTORY ? COLOR_CATEGORY_ACTIVE : COLOR_BUTTON_BG;
        int actionBg = COLOR_BUTTON_BG;
        int addCategoryBg = isCurrentInputInSelectedCategory() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;
        int favoriteBg = isCurrentInputInFavoritesCategory() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;

        if (hoverHistoryTab) historyTabBg = COLOR_ACCENT;
        if (favoritesTabWidth > 0 && isCategoryView && selectedCategoryIndex == favoritesIndex) {
            favoritesTabBg = COLOR_CATEGORY_ACTIVE;
        }
        if (hoverFavoritesTab) favoritesTabBg = COLOR_ACCENT;
        if (hoverCreateCategory) createCategoryBg = COLOR_ACCENT;
        if (hoverScrollLeft && canScrollLeft) scrollLeftBg = COLOR_ACCENT;
        if (hoverScrollRight && canScrollRight) scrollRightBg = COLOR_ACCENT;
        if (hoverAction && (isHistoryView || canDeleteCategory)) actionBg = COLOR_ACCENT;
        if (hoverAddCategory) addCategoryBg = COLOR_ACCENT;
        if (hoverFavoriteButton) favoriteBg = COLOR_ACCENT;

        ctx.fill(historyTabX, navbarY, historyTabX + TAB_BUTTON_WIDTH, navbarY + NAVBAR_HEIGHT, historyTabBg);
        if (favoritesTabWidth > 0) {
            ctx.fill(favoritesTabX, navbarY, favoritesTabX + favoritesTabWidth, navbarY + NAVBAR_HEIGHT, favoritesTabBg);
        }
        ctx.fill(createCategoryX, navbarY, createCategoryX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT, createCategoryBg);
        ctx.fill(scrollLeftX, navbarY, scrollLeftX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT,
            canScrollLeft ? scrollLeftBg : COLOR_BUTTON_BG);
        ctx.fill(scrollRightX, navbarY, scrollRightX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT,
            canScrollRight ? scrollRightBg : COLOR_BUTTON_BG);
        ctx.fill(actionX, navbarY, actionX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT, actionBg);
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

        ctx.drawText(this.textRenderer, "+", createCategoryX + 6, navbarY + 5, COLOR_STAR_OFF, false);
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

        String actionLabel = isHistoryView ? "✕" : (isCategoryView ? "✕" : "⚙");
        int actionColor = (isHistoryView || canDeleteCategory) ? COLOR_STAR_OFF : 0xFF555555;
        ctx.drawText(this.textRenderer, actionLabel, actionX + 6, navbarY + 5, actionColor, false);
        ctx.drawText(this.textRenderer, "★+", addCategoryX + 3, inputY + 6,
            isCurrentInputInSelectedCategory() ? COLOR_STAR : COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, "★", favoriteButtonX + 6, inputY + 6,
                isCurrentInputInFavoritesCategory() ? COLOR_STAR : COLOR_STAR_OFF, false);

        super.render(ctx, mouseX, mouseY, delta);

        if (creatingCategoryInput) {
            categoryInputField.render(ctx, mouseX, mouseY, delta);
        }

        if (hoverAddCategory) {
            Text addCategoryTooltip = isCurrentInputInSelectedCategory()
                    ? Text.translatable("screen.cmdpalette.tooltip.remove_from_selected_category")
                    : Text.translatable("screen.cmdpalette.tooltip.add_to_selected_category");
            ctx.drawTooltip(this.textRenderer, addCategoryTooltip, mouseX, mouseY);
        } else if (hoverFavoriteButton) {
            Text favoriteTooltip = isCurrentInputInFavoritesCategory()
                    ? Text.translatable("screen.cmdpalette.tooltip.remove_from_favorites")
                    : Text.translatable("screen.cmdpalette.tooltip.add_to_favorites");
            ctx.drawTooltip(this.textRenderer, favoriteTooltip, mouseX, mouseY);
        } else if (hoverAction) {
            String tooltipKey = isHistoryView
                    ? "screen.cmdpalette.tooltip.clear_history"
                    : (isCategoryView
                        ? (canDeleteCategory
                            ? "screen.cmdpalette.tooltip.delete_category"
                            : "screen.cmdpalette.tooltip.delete_category_disabled")
                        : "screen.cmdpalette.tooltip.settings_soon");
            ctx.drawTooltip(this.textRenderer, Text.translatable(tooltipKey), mouseX, mouseY);
        } else if (hoverCreateCategory) {
            ctx.drawTooltip(this.textRenderer,
                    Text.translatable("screen.cmdpalette.tooltip.create_category_input"),
                    mouseX, mouseY);
        } else if (hoverHistoryTab) {
            ctx.drawTooltip(this.textRenderer,
                Text.translatable("screen.cmdpalette.tooltip.history_tab"),
                mouseX, mouseY);
        }

        int separatorY = getSeparatorY();
        ctx.fill(paletteX + PADDING, separatorY,
            paletteX + paletteWidth - PADDING, separatorY + 1, COLOR_SEPARATOR);

        int listStartY = separatorY + 4;
        List<String> entries = getVisibleEntries();
        int currentSize = entries.size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);
        boolean hoverCategoryRowRemove = false;

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= currentSize) break;

            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;

            boolean selected = idx == selectedIndex;
            boolean hovered = mouseX >= paletteX + 4 && mouseX <= paletteX + paletteWidth - 4
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
                boolean hoverRowRemove = mouseX >= removeX && mouseX < removeX + STAR_ROW_SIZE
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

        if (currentSize > MAX_VISIBLE) {
            int trackHeight = MAX_VISIBLE * SUGGESTION_ITEM_HEIGHT;
            int barHeight = Math.max(16, trackHeight * MAX_VISIBLE / currentSize);
            int barY = listStartY + (int) ((float) scrollOffset / currentSize * trackHeight);
            ctx.fill(paletteX + paletteWidth - 6, barY,
                    paletteX + paletteWidth - 3, barY + barHeight, COLOR_SCROLLBAR);
        }
    }

    private int computePaletteHeight() {
        int headerHeight = PADDING + NAVBAR_HEIGHT + NAVBAR_GAP + INPUT_HEIGHT + 12 + 1 + 4;
        int listHeight = Math.min(getVisibleEntries().size(), MAX_VISIBLE) * SUGGESTION_ITEM_HEIGHT;
        return headerHeight + listHeight + PADDING;
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

    private static int classifyToken(String token, int positionIndex) {
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
                closeCategoryCreationInput();
                return true;
            }
            if (categoryInputField.keyPressed(keyInput)) {
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
        return super.charTyped(charInput);
    }

    private void moveSelection(int direction) {
        List<String> entries = getVisibleEntries();
        if (entries.isEmpty()) return;

        selectedIndex = Math.max(0, Math.min(selectedIndex + direction, entries.size() - 1));

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + MAX_VISIBLE) {
            scrollOffset = selectedIndex - MAX_VISIBLE + 1;
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
        int historyTabX = getHistoryTabX();
        int favoritesTabX = getFavoritesTabX();
        int favoritesTabWidth = getFavoritesTabWidth();
        int favoritesIndex = getFavoritesCategoryIndex(false);
        int createCategoryX = getCreateCategoryTabX();
        int categoriesStartX = getCategoryTabsStartX();
        int scrollLeftX = getCategoryScrollLeftX();
        int scrollRightX = getCategoryScrollRightX();
        int actionX = getSettingsButtonX();
        int addCategoryX = getAddToCategoryButtonX();
        int favoriteButtonX = getFavoriteButtonX();

        if (creatingCategoryInput && categoryInputField.mouseClicked(click, bl)) {
            return true;
        }

        if (click.x() >= historyTabX && click.x() < historyTabX + TAB_BUTTON_WIDTH
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            toggleView(ViewMode.HISTORY);
            return true;
        }

        if (click.x() >= createCategoryX && click.x() < createCategoryX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            openCategoryCreationInput();
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

        if (click.x() >= actionX && click.x() < actionX + NAVBAR_HEIGHT
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

        if (click.x() >= addCategoryX && click.x() < addCategoryX + STAR_BUTTON_SIZE
            && click.y() >= inputY && click.y() < inputY + STAR_BUTTON_SIZE) {
            toggleCurrentInputCategoryCommand();
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
        if (currentSize > MAX_VISIBLE) {
            int maxScroll = currentSize - MAX_VISIBLE;
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, maxScroll));
        }
        return true;
    }

    private int getSuggestionIndexAt(double mouseX, double mouseY) {
        int paletteWidth = getPaletteWidth();
        int paletteX = (this.width - paletteWidth) / 2;
        int listStartY = getSeparatorY() + 4;
        int currentSize = getVisibleEntries().size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);

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
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);
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
