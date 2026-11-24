package com.luatweaker.common.client.luamods;

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
 * Enhanced config editor: supports both Visual Form UI and Raw JSON editor
 * for {@code luaconfig/<mod>.json} with responsive header/footer wrapping
 * and active-view input isolation.
 */
public class LuamodConfigScreen extends Screen {

    public enum Mode {
        FORM,
        RAW
    }

    private static final String MODE_FORM_TEXT = "View: Form UI";
    private static final String MODE_RAW_TEXT = "View: Raw JSON";
    private static final String BACK_LABEL = "Back";

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
        LuamodConfigJson.LoadedConfig loaded = LuamodConfigJson.parseInitial(
                LuaModManager.readConfigFor(LuamodClientActions.luamodsDir(), modId));
        if (loaded.missingOnDisk()) {
            status = "No config yet - saving creates luaconfig/" + modId + ".json.";
        }
        rootJson = loaded.root();
        mode = rootJson != null ? Mode.FORM : Mode.RAW;

        int modeBtnW = width >= 320 ? 116 : 84;
        modeButton = Button.builder(Component.literal(MODE_FORM_TEXT), pressed -> toggleMode())
                .bounds(width - modeBtnW - 12, 6, modeBtnW, 18).build();
        addRenderableWidget(modeButton);

        boolean singleFooterRow = LuamodLayouts.fitsRow(width, 4);
        int bottomRowY = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        int footerTopY = singleFooterRow ? bottomRowY : bottomRowY - LuamodLayouts.BUTTON_ROW_STEP;
        int listTop = 40;
        int listHeight = Math.max(36, footerTopY - listTop - 6);

        formList = new LuamodConfigFormList(minecraft, width, listHeight, listTop, 24, this::onDirty);
        formList.updateSizeAndPosition(width, listHeight, listTop);
        if (rootJson != null) {
            formList.populate(rootJson);
        }
        addRenderableWidget(formList);

        int editWidth = Math.max(100, width - 32);
        editor = new MultiLineEditBox(font, width / 2 - editWidth / 2, listTop, editWidth, listHeight,
                Component.literal("Config JSON"), Component.literal("Config JSON"));
        editor.setCharacterLimit(128_000);
        editor.setValue(loaded.text());
        openedValue = loaded.text();
        editor.setValueListener(ignored -> onDirty());
        addRenderableWidget(editor);

        buildFooterButtons(singleFooterRow, footerTopY, bottomRowY);
        updateModeVisibility();
    }

    private void buildFooterButtons(boolean singleRow, int topRowY, int bottomRowY) {
        if (singleRow) {
            int bw = LuamodLayouts.buttonWidth(width, 4);
            int left = LuamodLayouts.rowLeft(width, 4, bw);
            addFooterButton("Save", LuamodLayouts.buttonX(left, 0, bw), bottomRowY, bw, () -> onSave(false));
            addFooterButton("Save & Reload", LuamodLayouts.buttonX(left, 1, bw), bottomRowY, bw, () -> onSave(true));
            addFooterButton("Reload Mods", LuamodLayouts.buttonX(left, 2, bw), bottomRowY, bw, LuamodClientActions::requestReload);
            backButton = addFooterButton(BACK_LABEL, LuamodLayouts.buttonX(left, 3, bw), bottomRowY, bw, this::onBack);
            return;
        }
        int bw2 = LuamodLayouts.buttonWidth(width, 2);
        int left2 = LuamodLayouts.rowLeft(width, 2, bw2);
        addFooterButton("Save", LuamodLayouts.buttonX(left2, 0, bw2), topRowY, bw2, () -> onSave(false));
        addFooterButton("Save & Reload", LuamodLayouts.buttonX(left2, 1, bw2), topRowY, bw2, () -> onSave(true));
        addFooterButton("Reload Mods", LuamodLayouts.buttonX(left2, 0, bw2), bottomRowY, bw2, LuamodClientActions::requestReload);
        backButton = addFooterButton(BACK_LABEL, LuamodLayouts.buttonX(left2, 1, bw2), bottomRowY, bw2, this::onBack);
    }

    private Button addFooterButton(String label, int x, int y, int bw, Runnable action) {
        String fit = LuamodLayouts.trim(font, label, Math.max(20, bw - 8));
        Button btn = Button.builder(Component.literal(fit), pressed -> action.run())
                .bounds(x, y, bw, 20)
                .build();
        addRenderableWidget(btn);
        return btn;
    }

    private void toggleMode() {
        if (mode == Mode.FORM) {
            editor.setValue(LuamodConfigJson.formatObject(rootJson));
            mode = Mode.RAW;
            status = "Switched to Raw JSON editor.";
            statusColor = 0xFFAAAAAA;
        } else {
            try {
                JsonElement el = JsonParser.parseString(editor.getValue());
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
        formList.active = isForm;
        editor.visible = !isForm;
        editor.active = !isForm;
        setFocused(isForm ? formList : editor);
        String rawModeLabel = isForm ? MODE_FORM_TEXT : MODE_RAW_TEXT;
        modeButton.setMessage(Component.literal(
                LuamodLayouts.trim(font, rawModeLabel, Math.max(24, modeButton.getWidth() - 8))));
    }

    private void onDirty() {
        dirtyArmed = false;
        if (backButton != null) {
            backButton.setMessage(Component.literal(BACK_LABEL));
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int rightReserved = modeButton != null ? (width - modeButton.getX() + 8) : 140;
        int maxTitleWidth = Math.max(40, width - rightReserved - 16);
        graphics.drawString(font, LuamodLayouts.trim(font, title.getString(), maxTitleWidth),
                16, 11, 0xFFFFFF);
        if (!status.isEmpty()) {
            graphics.drawCenteredString(font, LuamodLayouts.trim(font, status, Math.max(40, width - 24)),
                    width / 2, 27, statusColor);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void onSave(boolean andReload) {
        String contentToSave = (mode == Mode.FORM && rootJson != null)
                ? LuamodConfigJson.formatObject(rootJson)
                : LuamodConfigJson.normalizeLineEndings(editor.getValue());
        String error = LuaModManager.writeConfigFor(
                LuamodClientActions.luamodsDir(), modId, contentToSave);
        if (error == null) {
            status = andReload ? "Saved - reloading..." : "Saved.";
            statusColor = 0xFF55FF55;
            dirtyArmed = false;
            openedValue = contentToSave;
            editor.setValue(contentToSave);
            if (backButton != null) {
                backButton.setMessage(Component.literal(BACK_LABEL));
            }
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
                ? LuamodConfigJson.formatObject(rootJson)
                : LuamodConfigJson.normalizeLineEndings(editor.getValue());
        if (!currentContent.equals(openedValue) && !dirtyArmed) {
            dirtyArmed = true;
            if (backButton != null) {
                backButton.setMessage(Component.literal(
                        LuamodLayouts.trim(font, "Discard?", Math.max(20, backButton.getWidth() - 8))));
            }
            status = "Unsaved edits will be lost - press Discard again to exit.";
            statusColor = 0xFFFFAA00;
            return;
        }
        onClose();
    }
}
