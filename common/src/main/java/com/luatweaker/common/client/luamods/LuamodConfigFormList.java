package com.luatweaker.common.client.luamods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

/**
 * Visual Form list for editing mod configuration properties directly without writing raw JSON.
 */
public class LuamodConfigFormList extends ContainerObjectSelectionList<LuamodConfigFormList.ConfigEntry> {

    private static final String TRUE_LITERAL = "true";
    private static final String FALSE_LITERAL = "false";
    private static final String TRUE_DISPLAY = "§aTRUE";
    private static final String FALSE_DISPLAY = "§cFALSE";

    private final Runnable onDirty;

    public LuamodConfigFormList(@NotNull Minecraft minecraft, int width, int height, int y,
                                int itemHeight, @NotNull Runnable onDirty) {
        super(minecraft, width, height, y, itemHeight);
        this.onDirty = onDirty;
    }

    @Override
    public int getRowWidth() {
        return Math.max(160, Math.min(500, width - 40));
    }

    public void populate(JsonObject root) {
        clearEntries();
        if (root == null || root.isEmpty()) {
            addEntry(new EmptyEntry());
            return;
        }
        parseObject(root, "", 0);
    }

    private void parseObject(JsonObject obj, String prefix, int depth) {
        Font font = Minecraft.getInstance().font;
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            addEntryFor(entry.getKey(), entry.getValue(), prefix, depth, obj, font);
        }
    }

    private void addEntryFor(String key, JsonElement val, String prefix, int depth, JsonObject obj, Font font) {
        if (val.isJsonObject()) {
            String catTitle = prefix.isEmpty() ? key : prefix + "." + key;
            addEntry(new CategoryEntry(catTitle));
            parseObject(val.getAsJsonObject(), catTitle, depth + 1);
        } else if (val.isJsonPrimitive()) {
            addPrimitiveEntry(key, val.getAsJsonPrimitive(), depth, obj, font);
        } else if (val.isJsonArray()) {
            addEntry(new ArrayEntry(key, depth, obj, key, val.getAsJsonArray(), font, onDirty));
        }
    }

    private void addPrimitiveEntry(String key, JsonPrimitive prim, int depth, JsonObject obj, Font font) {
        if (prim.isBoolean()) {
            addEntry(new BooleanEntry(key, depth, obj, key, prim.getAsBoolean(), onDirty));
        } else if (prim.isNumber()) {
            addEntry(new StringOrNumberEntry(key, depth, obj, key, true, prim.getAsString(), font, onDirty));
        } else if (prim.isString()) {
            addEntry(new StringOrNumberEntry(key, depth, obj, key, false, prim.getAsString(), font, onDirty));
        }
    }

    public abstract static class ConfigEntry extends ContainerObjectSelectionList.Entry<ConfigEntry> {
        @Override
        public abstract @NotNull List<? extends GuiEventListener> children();

        @Override
        public abstract @NotNull List<? extends NarratableEntry> narratables();

        @Override
        public abstract void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                                    int rowWidth, int rowHeight, int mouseX, int mouseY,
                                    boolean hovering, float partialTick);
    }

    public static class EmptyEntry extends ConfigEntry {
        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                           int rowWidth, int rowHeight, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            String text = "§7(Empty configuration object)";
            graphics.drawCenteredString(font, text, left + rowWidth / 2, top + (rowHeight - 8) / 2, 0x888888);
        }
    }

    public static class CategoryEntry extends ConfigEntry {
        private final String title;

        public CategoryEntry(String title) {
            super();
            this.title = title;
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of();
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                           int rowWidth, int rowHeight, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            String text = "§6[ §b" + title + " §6]";
            int textWidth = font.width(text);
            int centerX = left + rowWidth / 2;
            int textY = top + (rowHeight - 8) / 2;

            int lineY = top + rowHeight / 2;
            int lineLeftEnd = Math.max(left + 8, centerX - textWidth / 2 - 6);
            int lineRightStart = Math.min(left + rowWidth - 8, centerX + textWidth / 2 + 6);

            graphics.hLine(left + 8, lineLeftEnd, lineY, 0xFF555555);
            graphics.drawCenteredString(font, text, centerX, textY, 0xFFFFFFFF);
            graphics.hLine(lineRightStart, left + rowWidth - 8, lineY, 0xFF555555);
        }
    }

    public static class BooleanEntry extends ConfigEntry {
        private final String label;
        private final int depth;
        private final JsonObject parent;
        private final String key;
        private final Button button;
        private final Runnable onDirty;
        private boolean value;

        public BooleanEntry(String label, int depth, JsonObject parent, String key,
                            boolean initialValue, Runnable onDirty) {
            super();
            this.label = label;
            this.depth = depth;
            this.parent = parent;
            this.key = key;
            this.value = initialValue;
            this.onDirty = onDirty;
            this.button = Button.builder(Component.literal(value ? TRUE_LITERAL : FALSE_LITERAL), b -> toggle())
                    .bounds(0, 0, 60, 20)
                    .build();
            updateButtonText();
        }

        private void toggle() {
            value = !value;
            parent.addProperty(key, value);
            updateButtonText();
            if (onDirty != null) {
                onDirty.run();
            }
        }

        private void updateButtonText() {
            button.setMessage(Component.literal(value ? TRUE_DISPLAY : FALSE_DISPLAY));
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(button);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of(button);
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                           int rowWidth, int rowHeight, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int indent = depth * 12;
            int textX = left + 6 + indent;
            int textY = top + (rowHeight - 8) / 2;
            int buttonWidth = 60;
            int buttonX = left + rowWidth - buttonWidth - 8;
            int buttonY = top + (rowHeight - 20) / 2;

            button.setX(buttonX);
            button.setY(buttonY);
            button.render(graphics, mouseX, mouseY, partialTick);

            int maxLabelWidth = buttonX - textX - 6;
            graphics.drawString(font, LuamodLayouts.trim(font, label, maxLabelWidth), textX, textY, 0xFFFFFFFF);
        }
    }

    public static class StringOrNumberEntry extends ConfigEntry {
        private final String label;
        private final int depth;
        private final JsonObject parent;
        private final String key;
        private final boolean isNumber;
        private final EditBox editBox;
        private final Runnable onDirty;

        public StringOrNumberEntry(String label, int depth, JsonObject parent, String key,
                                   boolean isNumber, String initialValue, Font font, Runnable onDirty) {
            super();
            this.label = label;
            this.depth = depth;
            this.parent = parent;
            this.key = key;
            this.isNumber = isNumber;
            this.onDirty = onDirty;
            this.editBox = new EditBox(font, 0, 0, 140, 18, Component.literal(label));
            this.editBox.setMaxLength(1024);
            this.editBox.setValue(initialValue);
            this.editBox.setResponder(this::onTextChanged);
        }

        private void onTextChanged(String text) {
            if (isNumber) {
                updateNumber(text);
            } else {
                parent.addProperty(key, text);
            }
            if (onDirty != null) {
                onDirty.run();
            }
        }

        private void updateNumber(String text) {
            try {
                if (text.contains(".")) {
                    double val = Double.parseDouble(text);
                    parent.addProperty(key, val);
                } else {
                    long val = Long.parseLong(text);
                    parent.addProperty(key, val);
                }
                editBox.setTextColor(0xFFFFFFFF);
            } catch (NumberFormatException e) {
                editBox.setTextColor(0xFFFF5555);
            }
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(editBox);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of(editBox);
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                           int rowWidth, int rowHeight, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int indent = depth * 12;
            int textX = left + 6 + indent;
            int textY = top + (rowHeight - 8) / 2;
            int editWidth = Math.min(160, Math.max(90, rowWidth / 3));
            int editX = left + rowWidth - editWidth - 8;
            int editY = top + (rowHeight - 18) / 2;

            editBox.setX(editX);
            editBox.setY(editY);
            editBox.setWidth(editWidth);
            editBox.render(graphics, mouseX, mouseY, partialTick);

            int maxLabelWidth = editX - textX - 6;
            graphics.drawString(font, LuamodLayouts.trim(font, label, maxLabelWidth), textX, textY, 0xFFFFFFFF);
        }
    }

    public static class ArrayEntry extends ConfigEntry {
        private final String label;
        private final int depth;
        private final JsonObject parent;
        private final String key;
        private final EditBox editBox;
        private final Runnable onDirty;

        public ArrayEntry(String label, int depth, JsonObject parent, String key,
                          JsonArray initialValue, Font font, Runnable onDirty) {
            super();
            this.label = label;
            this.depth = depth;
            this.parent = parent;
            this.key = key;
            this.onDirty = onDirty;
            this.editBox = new EditBox(font, 0, 0, 160, 18, Component.literal(label));
            this.editBox.setMaxLength(2048);
            this.editBox.setValue(initialValue.toString());
            this.editBox.setResponder(this::onTextChanged);
        }

        private void onTextChanged(String text) {
            try {
                JsonElement el = JsonParser.parseString(text);
                if (el != null && el.isJsonArray()) {
                    parent.add(key, el.getAsJsonArray());
                    editBox.setTextColor(0xFFFFFFFF);
                } else {
                    editBox.setTextColor(0xFFFF5555);
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                editBox.setTextColor(0xFFFF5555);
            }
            if (onDirty != null) {
                onDirty.run();
            }
        }

        @Override
        public @NotNull List<? extends GuiEventListener> children() {
            return List.of(editBox);
        }

        @Override
        public @NotNull List<? extends NarratableEntry> narratables() {
            return List.of(editBox);
        }

        @Override
        public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                           int rowWidth, int rowHeight, int mouseX, int mouseY,
                           boolean hovering, float partialTick) {
            Font font = Minecraft.getInstance().font;
            int indent = depth * 12;
            int textX = left + 6 + indent;
            int textY = top + (rowHeight - 8) / 2;
            int editWidth = Math.min(160, Math.max(90, rowWidth / 3));
            int editX = left + rowWidth - editWidth - 8;
            int editY = top + (rowHeight - 18) / 2;

            editBox.setX(editX);
            editBox.setY(editY);
            editBox.setWidth(editWidth);
            editBox.render(graphics, mouseX, mouseY, partialTick);

            int maxLabelWidth = editX - textX - 6;
            graphics.drawString(font, LuamodLayouts.trim(font, label, maxLabelWidth), textX, textY, 0xFFFFFFFF);
        }
    }
}
