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
    private int selectedIndex = -1;
    private int scrollOffset = 0;

    private String cachedColorText = "";
    private int[] cachedColors = new int[0];

    public CommandPaletteScreen() {
        super(Text.literal("Command Palette"));
    }

    @Override
    protected void init() {
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        int paletteY = this.height / 5;

        inputField = new TextFieldWidget(
                this.textRenderer,
                paletteX + PADDING,
                paletteY + PADDING,
                PALETTE_WIDTH - PADDING * 2,
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

        ctx.fill(0, 0, this.width, this.height, COLOR_OVERLAY);

        ctx.fill(paletteX - 2, paletteY - 2,
                paletteX + PALETTE_WIDTH + 2, paletteY + paletteHeight + 2, COLOR_SHADOW);
        ctx.fill(paletteX, paletteY,
                paletteX + PALETTE_WIDTH, paletteY + paletteHeight, COLOR_BG);

        ctx.fill(paletteX, paletteY, paletteX + PALETTE_WIDTH, paletteY + 2, COLOR_BORDER_TOP);

        ctx.fill(paletteX + 4, paletteY + 4,
                paletteX + PALETTE_WIDTH - 4, paletteY + PADDING + INPUT_HEIGHT + 4, COLOR_INPUT_BG);

        super.render(ctx, mouseX, mouseY, delta);

        int separatorY = paletteY + PADDING + INPUT_HEIGHT + 8;
        ctx.fill(paletteX + PADDING, separatorY,
                paletteX + PALETTE_WIDTH - PADDING, separatorY + 1, COLOR_SEPARATOR);

        int listStartY = separatorY + 4;
        int currentSize = suggestions.size();
        int visibleCount = Math.min(currentSize - scrollOffset, MAX_VISIBLE);

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

            renderSyntaxHighlighted(ctx, suggestions.get(idx), paletteX + 12, sy + 4);
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
        int headerHeight = PADDING + INPUT_HEIGHT + 12 + 1 + 4;
        int listHeight = Math.min(suggestions.size(), MAX_VISIBLE) * SUGGESTION_ITEM_HEIGHT;
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
        if (suggestions.isEmpty()) return;

        selectedIndex = Math.max(0, Math.min(selectedIndex + direction, suggestions.size() - 1));

        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + MAX_VISIBLE) {
            scrollOffset = selectedIndex - MAX_VISIBLE + 1;
        }
    }

    private void applySelectedSuggestion() {
        if (suggestions.isEmpty()) return;
        if (selectedIndex < 0) selectedIndex = 0;

        String selected = suggestions.get(selectedIndex);
        inputField.setText(selected);
        inputField.setCursorToEnd(false);
        refreshSuggestions(selected);
    }

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
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
        int currentSize = suggestions.size();
        if (currentSize > MAX_VISIBLE) {
            int maxScroll = currentSize - MAX_VISIBLE;
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) verticalAmount, maxScroll));
        }
        return true;
    }

    private int getSuggestionIndexAt(double mouseX, double mouseY) {
        int paletteX = (this.width - PALETTE_WIDTH) / 2;
        int paletteY = this.height / 5;
        int listStartY = paletteY + PADDING + INPUT_HEIGHT + 8 + 1 + 4;
        int currentSize = suggestions.size();
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

    private void executeCommand() {
        String text;
        if (selectedIndex >= 0 && selectedIndex < suggestions.size()) {
            text = suggestions.get(selectedIndex);
        } else {
            text = inputField.getText();
        }

        if (text == null || text.isBlank()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        String command = text.startsWith("/") ? text.substring(1) : text;
        client.player.networkHandler.sendChatCommand(command);
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
