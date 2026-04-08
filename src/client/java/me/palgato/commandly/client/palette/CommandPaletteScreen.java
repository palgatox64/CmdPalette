package me.palgato.commandly.client.palette;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

    private static final int PALETTE_WIDTH = 350;
    private static final int INPUT_HEIGHT = 20;
    private static final int SUGGESTION_ITEM_HEIGHT = 18;
    private static final int MAX_VISIBLE = 12;
    private static final int PADDING = 8;
    private static final int NAVBAR_HEIGHT = 18;
    private static final int NAVBAR_GAP = 6;
    private static final int STAR_BUTTON_SIZE = 20;
    private static final int STAR_GAP = 4;
    private static final int STAR_ROW_SIZE = 12;
    private static final int TAB_BUTTON_WIDTH = 86;
    private static final int TAB_GAP = 4;
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
    private final List<String> suggestions = new CopyOnWriteArrayList<>();
    private final List<String> favorites = new CopyOnWriteArrayList<>();
    private final List<String> history = new CopyOnWriteArrayList<>();
    private int selectedIndex = -1;
    private int scrollOffset = 0;
    private ViewMode currentView = ViewMode.COMMANDS;

    private String cachedColorText = "";
    private int[] cachedColors = new int[0];

    private enum ViewMode {
        COMMANDS,
        FAVORITES,
        HISTORY
    }

    public CommandPaletteScreen() {
        super(Text.translatable("screen.commandly.command_palette"));
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

        addDrawableChild(inputField);
        setInitialFocus(inputField);

        favorites.clear();
        favorites.addAll(FavoriteCommandsStore.load());
        history.clear();
        history.addAll(CommandHistoryStore.load());
        refreshSuggestions("");
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
            case FAVORITES -> favorites;
            case HISTORY -> history;
            default -> suggestions;
        };
    }

    private int getInputX() {
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
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
        return PALETTE_WIDTH - PADDING * 2 - STAR_BUTTON_SIZE - STAR_GAP;
    }

    private int getFavoritesTabX() {
        return getInputX();
    }

    private int getHistoryTabX() {
        return getFavoritesTabX() + TAB_BUTTON_WIDTH + TAB_GAP;
    }

    private int getSettingsButtonX() {
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        return paletteX + PALETTE_WIDTH - PADDING - NAVBAR_HEIGHT;
    }

    private int getAddStarX() {
        return getInputX() + getInputWidth() + STAR_GAP;
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

    private boolean isCurrentInputFavorite() {
        String normalized = normalizeCommand(inputField.getText());
        return !normalized.isBlank() && favorites.contains(normalized);
    }

    private void toggleCurrentInputFavorite() {
        String normalized = normalizeCommand(inputField.getText());
        if (normalized.isBlank()) return;

        if (favorites.contains(normalized)) {
            favorites.remove(normalized);
        } else {
            favorites.add(normalized);
        }

        FavoriteCommandsStore.save(favorites);
        clampSelectionAndScroll();
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

    private boolean isFavoritesView() {
        return currentView == ViewMode.FAVORITES;
    }

    private void removeFavoriteAt(int index) {
        if (index < 0 || index >= favorites.size()) return;
        favorites.remove(index);
        FavoriteCommandsStore.save(favorites);
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
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        int paletteY = this.height / 5;
        int paletteHeight = computePaletteHeight();
        int navbarY = getNavbarY();
        int inputY = getInputY();

        ctx.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        ctx.fill(paletteX - 2, paletteY - 2,
                paletteX + PALETTE_WIDTH + 2, paletteY + paletteHeight + 2, COLOR_SHADOW);
        ctx.fill(paletteX, paletteY,
                paletteX + PALETTE_WIDTH, paletteY + paletteHeight, COLOR_BG);

        ctx.fill(paletteX, paletteY, paletteX + PALETTE_WIDTH, paletteY + 2, COLOR_BORDER_TOP);

        ctx.fill(paletteX + 4, paletteY + 4,
            paletteX + PALETTE_WIDTH - 4, inputY + INPUT_HEIGHT + 4, COLOR_INPUT_BG);

        int favoritesTabX = getFavoritesTabX();
        int historyTabX = getHistoryTabX();
        int actionX = getSettingsButtonX();
        int addStarX = getAddStarX();
        boolean isHistoryView = currentView == ViewMode.HISTORY;

        boolean hoverFavoritesTab = mouseX >= favoritesTabX && mouseX < favoritesTabX + TAB_BUTTON_WIDTH
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverHistoryTab = mouseX >= historyTabX && mouseX < historyTabX + TAB_BUTTON_WIDTH
                && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverAction = mouseX >= actionX && mouseX < actionX + NAVBAR_HEIGHT
            && mouseY >= navbarY && mouseY < navbarY + NAVBAR_HEIGHT;
        boolean hoverAddStar = mouseX >= addStarX && mouseX < addStarX + STAR_BUTTON_SIZE
            && mouseY >= inputY && mouseY < inputY + STAR_BUTTON_SIZE;

        int favoritesTabBg = isFavoritesView() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;
        int historyTabBg = currentView == ViewMode.HISTORY ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;
        int actionBg = COLOR_BUTTON_BG;
        int addBg = isCurrentInputFavorite() ? COLOR_BUTTON_ACTIVE : COLOR_BUTTON_BG;

        if (hoverFavoritesTab) favoritesTabBg = COLOR_ACCENT;
        if (hoverHistoryTab) historyTabBg = COLOR_ACCENT;
        if (hoverAction) actionBg = COLOR_ACCENT;
        if (hoverAddStar) addBg = COLOR_ACCENT;

        ctx.fill(favoritesTabX, navbarY, favoritesTabX + TAB_BUTTON_WIDTH, navbarY + NAVBAR_HEIGHT, favoritesTabBg);
        ctx.fill(historyTabX, navbarY, historyTabX + TAB_BUTTON_WIDTH, navbarY + NAVBAR_HEIGHT, historyTabBg);
        ctx.fill(actionX, navbarY, actionX + NAVBAR_HEIGHT, navbarY + NAVBAR_HEIGHT, actionBg);
        ctx.fill(addStarX, inputY, addStarX + STAR_BUTTON_SIZE, inputY + STAR_BUTTON_SIZE, addBg);

        ctx.drawText(this.textRenderer, "★", favoritesTabX + 6, navbarY + 5,
            isFavoritesView() ? COLOR_STAR : COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, Text.translatable("screen.commandly.tab.favorites").getString(),
            favoritesTabX + 18, navbarY + 5, 0xFFC5C8C6, false);

        ctx.drawText(this.textRenderer, "↺", historyTabX + 6, navbarY + 5,
                currentView == ViewMode.HISTORY ? COLOR_STAR : COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, Text.translatable("screen.commandly.tab.history").getString(),
                historyTabX + 18, navbarY + 5, 0xFFC5C8C6, false);

        ctx.drawText(this.textRenderer, isHistoryView ? "✕" : "⚙", actionX + 6, navbarY + 5, COLOR_STAR_OFF, false);
        ctx.drawText(this.textRenderer, "★+", addStarX + 3, inputY + 6,
            isCurrentInputFavorite() ? COLOR_STAR : COLOR_STAR_OFF, false);

        super.render(ctx, mouseX, mouseY, delta);

        if (hoverAddStar) {
            Text addFavoriteTooltip = isCurrentInputFavorite()
                    ? Text.translatable("screen.commandly.tooltip.remove_favorite")
                    : Text.translatable("screen.commandly.tooltip.add_favorite");
            ctx.drawTooltip(this.textRenderer, addFavoriteTooltip, mouseX, mouseY);
        } else if (hoverAction) {
            ctx.drawTooltip(this.textRenderer,
                Text.translatable(isHistoryView
                    ? "screen.commandly.tooltip.clear_history"
                    : "screen.commandly.tooltip.settings_soon"),
                    mouseX, mouseY);
        } else if (hoverFavoritesTab) {
            ctx.drawTooltip(this.textRenderer,
                    Text.translatable("screen.commandly.tooltip.favorites_tab"),
                    mouseX, mouseY);
        } else if (hoverHistoryTab) {
            ctx.drawTooltip(this.textRenderer,
                Text.translatable("screen.commandly.tooltip.history_tab"),
                mouseX, mouseY);
        }

        int separatorY = getSeparatorY();
        ctx.fill(paletteX + PADDING, separatorY,
                paletteX + PALETTE_WIDTH - PADDING, separatorY + 1, COLOR_SEPARATOR);

        int listStartY = separatorY + 4;
        List<String> entries = getVisibleEntries();
        int currentSize = entries.size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);
        boolean hoverFavoriteRowStar = false;

        for (int i = 0; i < visibleCount; i++) {
            int idx = i + scrollOffset;
            if (idx >= currentSize) break;

            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;

            boolean selected = idx == selectedIndex;
            boolean hovered = mouseX >= paletteX + 4 && mouseX <= paletteX + PALETTE_WIDTH - 4
                    && mouseY >= sy && mouseY < sy + SUGGESTION_ITEM_HEIGHT;

            if (selected) {
                ctx.fill(paletteX + 4, sy, paletteX + PALETTE_WIDTH - 4,
                        sy + SUGGESTION_ITEM_HEIGHT, COLOR_SELECTED);
            } else if (hovered) {
                ctx.fill(paletteX + 4, sy, paletteX + PALETTE_WIDTH - 4,
                        sy + SUGGESTION_ITEM_HEIGHT, COLOR_HOVER);
            }

            String entry = entries.get(idx);
            renderSyntaxHighlighted(ctx, entry, paletteX + 12, sy + 4);

            if (isFavoritesView()) {
                int starX = paletteX + PALETTE_WIDTH - PADDING - STAR_ROW_SIZE;
                int starY = sy + (SUGGESTION_ITEM_HEIGHT - STAR_ROW_SIZE) / 2;
                boolean hoverRowStar = mouseX >= starX && mouseX < starX + STAR_ROW_SIZE
                        && mouseY >= starY && mouseY < starY + STAR_ROW_SIZE;
                if (hoverRowStar) {
                    hoverFavoriteRowStar = true;
                }
                int rowStarColor = hoverRowStar ? 0xFFFFF2A8 : COLOR_STAR;
                ctx.drawText(this.textRenderer, "★", starX + 1, starY + 1, rowStarColor, false);
            }
        }

        if (hoverFavoriteRowStar) {
            ctx.drawTooltip(this.textRenderer,
                    Text.translatable("screen.commandly.tooltip.remove_row_favorite"),
                    mouseX, mouseY);
        }

        if (currentSize > MAX_VISIBLE) {
            int trackHeight = MAX_VISIBLE * SUGGESTION_ITEM_HEIGHT;
            int barHeight = Math.max(16, trackHeight * MAX_VISIBLE / currentSize);
            int barY = listStartY + (int) ((float) scrollOffset / currentSize * trackHeight);
            ctx.fill(paletteX + PALETTE_WIDTH - 6, barY,
                    paletteX + PALETTE_WIDTH - 3, barY + barHeight, COLOR_SCROLLBAR);
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
        int favoritesTabX = getFavoritesTabX();
        int historyTabX = getHistoryTabX();
        int actionX = getSettingsButtonX();
        int addStarX = getAddStarX();

        if (click.x() >= favoritesTabX && click.x() < favoritesTabX + TAB_BUTTON_WIDTH
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            toggleView(ViewMode.FAVORITES);
            return true;
        }

        if (click.x() >= historyTabX && click.x() < historyTabX + TAB_BUTTON_WIDTH
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            toggleView(ViewMode.HISTORY);
            return true;
        }

        if (click.x() >= actionX && click.x() < actionX + NAVBAR_HEIGHT
                && click.y() >= navbarY && click.y() < navbarY + NAVBAR_HEIGHT) {
            if (currentView == ViewMode.HISTORY) {
                clearHistory();
                return true;
            }
        }

        if (click.x() >= addStarX && click.x() < addStarX + STAR_BUTTON_SIZE
                && click.y() >= inputY && click.y() < inputY + STAR_BUTTON_SIZE) {
            toggleCurrentInputFavorite();
            return true;
        }

        int removeIndex = getFavoriteRemoveIndexAt(click.x(), click.y());
        if (removeIndex >= 0) {
            removeFavoriteAt(removeIndex);
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
        int currentSize = getVisibleEntries().size();
        if (currentSize > MAX_VISIBLE) {
            int maxScroll = currentSize - MAX_VISIBLE;
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, maxScroll));
        }
        return true;
    }

    private int getSuggestionIndexAt(double mouseX, double mouseY) {
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        int listStartY = getSeparatorY() + 4;
        int currentSize = getVisibleEntries().size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);

        if (mouseX < paletteX + 4 || mouseX > paletteX + PALETTE_WIDTH - 4) return -1;

        for (int i = 0; i < visibleCount; i++) {
            int sy = listStartY + i * SUGGESTION_ITEM_HEIGHT;
            if (mouseY >= sy && mouseY < sy + SUGGESTION_ITEM_HEIGHT) {
                return i + scrollOffset;
            }
        }
        return -1;
    }

    private int getFavoriteRemoveIndexAt(double mouseX, double mouseY) {
        if (!isFavoritesView()) return -1;

        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        int listStartY = getSeparatorY() + 4;
        int currentSize = favorites.size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);
        int starX = paletteX + PALETTE_WIDTH - PADDING - STAR_ROW_SIZE;

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
