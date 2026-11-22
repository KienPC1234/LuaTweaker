package com.luatweaker.common.client.luamods;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.luatweaker.core.mod.LuaModManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enhanced config editor: supports both Visual Form UI and clean Raw JSON editor
 * for {@code luaconfig/<mod>.json} with hot reloading and error handling.
 */
public class LuamodConfigScreen extends Screen {

    public enum Mode {
        FORM,
        RAW
    }

    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String MODE_FORM_TEXT = "View: Form UI";
    private static final String MODE_RAW_TEXT = "View: Raw JSON";
    private static final String DEFAULT_EMPTY_JSON = "{\n}";

    private final Screen parent;
    private final String modId;

    private Mode mode = Mode.FORM;
    private JsonObject rootJson;

    private Button modeButton;
    private LuamodConfigFormList formList;
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
        mode = Mode.FORM;
        String current = LuaModManager.readConfigFor(LuamodClientActions.luamodsDir(), modId);
        if (current == null) {
            current = DEFAULT_EMPTY_JSON;
            status = "No config yet - saving creates luaconfig/" + modId + ".json.";
        } else {
            current = LuamodConfigJson.normalizeLineEndings(current);
        }

        try {
            JsonElement parsed = JsonParser.parseString(current);
            if (parsed != null && parsed.isJsonObject()) {
                rootJson = parsed.getAsJsonObject();
                current = LuamodConfigJson.normalizeLineEndings(PRETTY_GSON.toJson(rootJson));
            } else {
                mode = Mode.RAW;
            }
        } catch (JsonSyntaxException | IllegalStateException e) {
            mode = Mode.RAW;
        }

        int listTop = 46;
        int listHeight = Math.max(60, height - listTop - 56);

        modeButton = Button.builder(Component.literal(MODE_FORM_TEXT), pressed -> toggleMode())
                .bounds(width - 145, 8, 130, 20).build();
        addRenderableWidget(modeButton);

        formList = new LuamodConfigFormList(minecraft, width, listHeight, listTop, 26, this::onDirty);
        formList.updateSizeAndPosition(width, listHeight, listTop);
        if (rootJson != null) {
            formList.populate(rootJson);
        }
        addRenderableWidget(formList);

        int editWidth = Math.max(120, width - 40);
        editor = new MultiLineEditBox(font, width / 2 - editWidth / 2, listTop, editWidth, listHeight,
                Component.literal("Config JSON"), Component.literal("Config JSON"));
        editor.setCharacterLimit(128_000);
        editor.setValue(current);
        openedValue = current;
        editor.setValueListener(ignored -> onDirty());
        addRenderableWidget(editor);

        updateModeVisibility();

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

    private void toggleMode() {
        if (mode == Mode.FORM) {
            String pretty = LuamodConfigJson.normalizeLineEndings(
                    rootJson != null ? PRETTY_GSON.toJson(rootJson) : DEFAULT_EMPTY_JSON);
            editor.setValue(pretty);
            mode = Mode.RAW;
            status = "Switched to Raw JSON editor.";
            statusColor = 0xFFAAAAAA;
        } else {
            String raw = editor.getValue();
            try {
                JsonElement el = JsonParser.parseString(raw);
                if (el != null && el.isJsonObject()) {
                    rootJson = el.getAsJsonObject();
                    formList.populate(rootJson);
                    mode = Mode.FORM;
                    status = "Parsed successfully into Form UI.";
                    statusColor = 0xFF55FF55;
                } else {
                    status = "Config must be a JSON object ({...}) to view as Form UI.";
                    statusColor = 0xFFFF5555;
                    return;
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                status = "Invalid JSON syntax: " + e.getMessage();
                statusColor = 0xFFFF5555;
                return;
            }
        }
        updateModeVisibility();
    }

    private void updateModeVisibility() {
        boolean isForm = mode == Mode.FORM;
        formList.visible = isForm;
        editor.visible = !isForm;
        modeButton.setMessage(Component.literal(isForm ? MODE_FORM_TEXT : MODE_RAW_TEXT));
    }

    private void onDirty() {
        dirtyArmed = false;
        updateBackLabel();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, title.getString(), width - 160),
                width / 2, 12, 0xFFFFFF);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, status, width - 20),
                width / 2, 30, statusColor);
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
        String contentToSave = (mode == Mode.FORM && rootJson != null)
                ? PRETTY_GSON.toJson(rootJson)
                : editor.getValue();
        contentToSave = LuamodConfigJson.normalizeLineEndings(contentToSave);
        String error = LuaModManager.writeConfigFor(
                LuamodClientActions.luamodsDir(), modId, contentToSave);
        if (error == null) {
            status = andReload ? "Saved - reloading..." : "Saved.";
            statusColor = 0xFF55FF55;
            dirtyArmed = false;
            openedValue = contentToSave;
            editor.setValue(contentToSave);
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
        String currentContent = (mode == Mode.FORM && rootJson != null)
                ? PRETTY_GSON.toJson(rootJson)
                : editor.getValue();
        if (!currentContent.equals(openedValue) && !dirtyArmed) {
            dirtyArmed = true;
            updateBackLabel();
            status = "Unsaved edits will be lost - press again to discard.";
            statusColor = 0xFFFFAA00;
            return;
        }
        onClose();
    }
}
