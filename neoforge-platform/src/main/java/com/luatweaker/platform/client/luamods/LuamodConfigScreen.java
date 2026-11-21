package com.luatweaker.platform.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Direct config editor: raw JSON of {@code luaconfig/<mod>.json} with
 * validation on save. "Save & Reload" persists and triggers the same reload
 * as {@code /lt reload} so hot config actually applies.
 */
public class LuamodConfigScreen extends Screen {
    private final Screen parent;
    private final String modId;

    private MultiLineEditBox editor;
    private Button backButton;
    private String status = "";
    private int statusColor = 0xFFAAAAAA;
    private boolean dirtyArmed;
    private String openedValue = "";

    public LuamodConfigScreen(@Nullable Screen parent, @NotNull String modId) {
        super(Component.literal("Config: " + modId));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        String current = LuaModManager.readConfigFor(LuamodClientActions.luamodsDir(), modId);
        if (current == null) {
            current = "{\n}";
            status = "No config yet - saving creates luaconfig/" + modId + ".json.";
        }
        int editHeight = Math.max(60, height - 130);
        int editWidth = Math.max(120, width - 40);
        editor = new MultiLineEditBox(font, width / 2 - editWidth / 2, 40, editWidth, editHeight,
                Component.literal("Config JSON"), Component.literal("Config JSON"));
        editor.setCharacterLimit(128_000);
        editor.setValue(current);
        openedValue = current;
        editor.setValueListener(ignored -> {
            dirtyArmed = false;
            updateBackLabel();
        });
        addRenderableWidget(editor);

        int row = height - 52;
        int bw = LuamodLayouts.buttonWidth(width, 4);
        int left = LuamodLayouts.rowLeft(width, 4, bw);
        addRenderableWidget(Button.builder(Component.literal("Save"), pressed -> onSave(false))
                .bounds(LuamodLayouts.buttonX(left, 0, bw), row, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save & Reload"), pressed -> onSave(true))
                .bounds(LuamodLayouts.buttonX(left, 1, bw), row, bw, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reload Mods"), pressed -> LuamodClientActions.requestReload())
                .bounds(LuamodLayouts.buttonX(left, 2, bw), row, bw, 20).build());
        backButton = Button.builder(Component.literal("Back"), pressed -> onBack())
                .bounds(LuamodLayouts.buttonX(left, 3, bw), row, bw, 20).build();
        addRenderableWidget(backButton);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, title.getString(), width - 20),
                width / 2, 14, 0xFFFFFF);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, status, width - 20),
                width / 2, 28, statusColor);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void updateBackLabel() {
        if (backButton != null) {
            backButton.setMessage(Component.literal(dirtyArmed ? "Confirm discard" : "Back"));
        }
    }

    private void onSave(boolean andReload) {
        String error = LuaModManager.writeConfigFor(
                LuamodClientActions.luamodsDir(), modId, editor.getValue());
        if (error == null) {
            status = andReload ? "Saved - reloading..." : "Saved.";
            statusColor = 0xFF55FF55;
            dirtyArmed = false;
            openedValue = editor.getValue();
            updateBackLabel();
            if (andReload) {
                LuamodClientActions.requestReload();
            }
        } else {
            status = error;
            statusColor = 0xFFFF5555;
        }
    }

    private void onBack() {
        if (!editor.getValue().equals(openedValue) && !dirtyArmed) {
            dirtyArmed = true;
            updateBackLabel();
            status = "Unsaved edits will be lost - press again to discard.";
            statusColor = 0xFFFFAA00;
            return;
        }
        onClose();
    }
}
