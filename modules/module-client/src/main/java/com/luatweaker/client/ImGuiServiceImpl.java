package com.luatweaker.client;

import com.luatweaker.api.client.IImGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.pal.Platform;
import java.util.List;

/**
 * Lua-facing ImGui service facade.
 * Implements the full IImGuiService API surface, bridging Lua calls to PlatformClient.
 */
public class ImGuiServiceImpl implements IImGuiService {

protected static final String LABEL_INPUT = "input";

    protected IPlatformClient clientOrNull() {
        return Platform.getClientOrNull();
    }

    @Override
    public boolean begin(String title, boolean open, String flags) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM, "[ImGui] Begin ignored: no client.");
            return open;
        }
        // Flag parsing lives in the platform (real ImGuiWindowFlags constants);
        // unknown names fail fast there with the valid list.
        return client.imguiBegin(title == null ? "window" : title, open, flags == null ? "" : flags);
    }

    @Override
    public void end() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEnd();
        }
    }

    @Override
    public boolean beginChild(String id, double width, double height) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginChild(id == null ? "child" : id, (float) width, (float) height);
    }

    @Override
    public void endChild() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndChild();
        }
    }

    @Override
    public void setNextWindowSize(double width, double height) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextWindowSize((float) width, (float) height);
        }
    }

    @Override
    public void setNextWindowPos(double x, double y) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextWindowPos((float) x, (float) y);
        }
    }

    @Override
    public void sameLine() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSameLine();
        }
    }

    @Override
    public void newLine() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiNewLine();
        }
    }

    @Override
    public void spacing() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSpacing();
        }
    }

@Override
    public void separator() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSeparator();
        }
    }

    @Override
    public void indent() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiIndent();
        }
    }

    @Override
    public void unindent() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiUnindent();
        }
    }

    @Override
    public void setCursorPosX(double x) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetCursorPosX((float) x);
        }
    }

    @Override
    public void text(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiText(message == null ? "" : message);
        }
    }

    @Override
    public void bulletText(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBulletText(message == null ? "" : message);
        }
    }

    @Override
    public boolean button(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiButton(label == null ? "button" : label);
    }

    @Override
    public boolean smallButton(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSmallButton(label == null ? "button" : label);
    }

    @Override
    public boolean checkbox(String label, boolean checked) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return checked;
        }
        return client.imguiCheckbox(label == null ? "checkbox" : label, checked);
    }

@Override
    public boolean radioButton(String label, boolean active) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiRadioButton(label == null ? "radio" : label, active);
    }

    @Override
    public double sliderFloat(String label, double value, double min, double max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiSliderFloat(label == null ? "slider" : label, value, min, max);
    }

    @Override
    public int sliderInt(String label, int value, int min, int max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiSliderInt(label == null ? "slider" : label, value, min, max);
    }

    @Override
    public double dragFloat(String label, double value, double speed, double min, double max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiDragFloat(label == null ? "drag" : label, value, speed, min, max);
    }

    @Override
    public int dragInt(String label, int value, double speed, int min, int max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiDragInt(label == null ? "drag" : label, value, speed, min, max);
    }

    @Override
    public String inputText(String label, String current) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputText(label == null ? LABEL_INPUT : label, current == null ? "" : current, false);
    }

    @Override
    public String inputTextMultiline(String label, String current) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputText(label == null ? LABEL_INPUT : label, current == null ? "" : current, true);
    }

    @Override
    public int inputInt(String label, int value) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiInputInt(label == null ? LABEL_INPUT : label, value);
    }

    @Override
    public double inputFloat(String label, double value) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiInputFloat(label == null ? LABEL_INPUT : label, value);
    }

@Override
    public int combo(String label, int current, String[] items) {
        IPlatformClient client = clientOrNull();
        if (client == null || items == null || items.length == 0) {
            return current;
        }
        int clamped = Math.min(Math.max(current, 1), items.length);
        return client.imguiCombo(label == null ? "combo" : label, clamped, items);
    }

    @Override
    public List<Double> colorEdit3(String label, double r, double g, double b) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(r, g, b, 1.0);
        }
        return client.imguiColorEdit(label == null ? "color" : label, r, g, b, 1.0);
    }

    @Override
    public List<Double> colorEdit4(String label, double r, double g, double b, double a) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(r, g, b, a);
        }
        return client.imguiColorEdit(label == null ? "color" : label, r, g, b, a);
    }

    @Override
    public void progressBar(double fraction, String label, double width, double height) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            double clamped = Math.min(Math.max(fraction, 0.0), 1.0);
            client.imguiProgressBar(clamped, label == null ? "" : label, width, height);
        }
    }

    @Override
    public boolean collapsingHeader(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiCollapsingHeader(label == null ? "header" : label);
    }

    @Override
    public boolean treeNode(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiTreeNode(label == null ? "node" : label);
    }

    @Override
    public void treePop() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTreePop();
        }
    }

    @Override
    public boolean selectable(String label, boolean selected) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSelectable(label == null ? "selectable" : label, selected);
    }

    @Override
    public boolean isItemHovered() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemHovered();
    }

@Override
    public boolean isItemClicked() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemClicked();
    }

    @Override
    public boolean isWindowHovered() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsWindowHovered();
    }

    @Override
    public boolean wantCaptureMouse() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiWantCaptureMouse();
    }

    @Override
    public boolean wantCaptureKeyboard() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiWantCaptureKeyboard();
    }

    @Override
    public void pushStyleColor(String colorName, int argbColor) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            // Name-to-constant mapping lives in the platform (real ImGuiCol
            // constants); unknown names fail fast there with the valid list.
            client.imguiPushStyleColor(colorName == null ? "" : colorName.strip(), argbColor);
        }
    }

    @Override
    public void popStyleColor(int count) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPopStyleColor(Math.max(count, 1));
        }
    }

    @Override
    public boolean itemIcon(String itemId, int sizePx, int count) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiItemIcon(itemId == null ? "" : itemId, Math.max(sizePx, 8), Math.max(count, 1));
    }

    @Override
    public boolean showDemoWindow(boolean open) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return open;
        }
        return client.imguiShowDemoWindow(open);
    }

    @Override
    public String getVersion() {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return "unavailable";
        }
        return client.imguiGetVersion();
    }

@Override
    public boolean beginTable(String id, int columns, String flags) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginTable(id == null ? "table" : id, Math.max(columns, 1), flags == null ? "" : flags);
    }

    @Override
    public void endTable() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTable();
        }
    }

    @Override
    public void tableNextRow() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableNextRow();
        }
    }

    @Override
    public boolean tableNextColumn() {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiTableNextColumn();
    }

    @Override
    public void tableSetupColumn(String name, double width, String flags) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableSetupColumn(name == null ? "" : name, (float) width, flags == null ? "" : flags);
        }
    }

    @Override
    public void tableHeadersRow() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableHeadersRow();
        }
    }

    @Override
    public boolean beginTabBar(String id) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginTabBar(id == null ? "tabs" : id);
    }

    @Override
    public void endTabBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTabBar();
        }
    }

    @Override
    public boolean beginTabItem(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginTabItem(label == null ? "tab" : label);
    }

@Override
    public void endTabItem() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTabItem();
        }
    }

    @Override
    public boolean beginMenuBar() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMenuBar();
    }

    @Override
    public void endMenuBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMenuBar();
        }
    }

    @Override
    public boolean beginMenu(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMenu(label == null ? "menu" : label);
    }

    @Override
    public void endMenu() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMenu();
        }
    }

    @Override
    public boolean menuItem(String label, String shortcut, boolean selected, boolean enabled) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiMenuItem(label == null ? "item" : label, shortcut == null ? "" : shortcut, selected, enabled);
    }

    @Override
    public int listBox(String label, int current, String[] items) {
        IPlatformClient client = clientOrNull();
        if (client == null || items == null || items.length == 0) {
            return current;
        }
        int clamped = Math.min(Math.max(current, 1), items.length);
        return client.imguiListBox(label == null ? "list" : label, clamped, items);
    }

    @Override
    public void image(String textureId, double width, double height) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiImage(textureId == null ? "" : textureId, (float) width, (float) height);
        }
    }

    @Override
    public void imageSub(String textureId, double width, double height,
                         double u0, double v0, double u1, double v1) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiImageSub(textureId == null ? "" : textureId,
                    (float) width, (float) height, (float) u0, (float) v0, (float) u1, (float) v1);
        }
    }

@Override
    public List<Double> getSpriteUv(String atlasId, String spriteId) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of();
        }
        return client.imguiSpriteUv(atlasId == null ? "" : atlasId, spriteId == null ? "" : spriteId);
    }

    @Override
    public void openPopup(String id) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiOpenPopup(id == null ? "" : id);
        }
    }

    @Override
    public boolean beginPopup(String id) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginPopup(id == null ? "" : id);
    }

    @Override
    public void endPopup() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndPopup();
        }
    }

    @Override
    public boolean beginPopupModal(String title, boolean open) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return open;
        }
        return client.imguiBeginPopupModal(title == null ? "dialog" : title, open);
    }

    @Override
    public void closeCurrentPopup() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiCloseCurrentPopup();
        }
    }

    @Override
    public boolean beginPopupContextItem(String id) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginPopupContextItem(id == null ? "" : id);
    }

    @Override
    public void openPopupOnItemClick(String id) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiOpenPopupOnItemClick(id == null ? "" : id);
        }
    }

    @Override
    public void beginTooltip() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBeginTooltip();
        }
    }

@Override
    public void endTooltip() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTooltip();
        }
    }

    @Override
    public void setTooltip(String text) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetTooltip(text == null ? "" : text);
        }
    }

    @Override
    public void beginDisabled(boolean disabled) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBeginDisabled(disabled);
        }
    }

    @Override
    public void endDisabled() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDisabled();
        }
    }

    @Override
    public void setScrollHereY() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetScrollHereY();
        }
    }

    @Override
    public void setKeyboardFocusHere() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetKeyboardFocusHere();
        }
    }

    @Override
    public void setNextItemOpen(boolean open) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextItemOpen(open);
        }
    }

    @Override
    public void textColored(String text, double r, double g, double b) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextColored((float) r, (float) g, (float) b, text == null ? "" : text);
        }
    }

    @Override
    public void textWrapped(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextWrapped(message == null ? "" : message);
        }
    }

@Override
    public void textDisabled(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextDisabled(message == null ? "" : message);
        }
    }

    @Override
    public void labelText(String label, String value) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiLabelText(label == null ? "" : label, value == null ? "" : value);
        }
    }

    @Override
    public boolean arrowButton(String id, String direction) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiArrowButton(id == null ? "arrow" : id, direction == null ? "" : direction);
    }

    @Override
    public boolean tabItemButton(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiTabItemButton(label == null ? "+" : label);
    }

    @Override
    public boolean beginMainMenuBar() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMainMenuBar();
    }

    @Override
    public void endMainMenuBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMainMenuBar();
        }
    }

    @Override
    public void pushItemWidth(double width) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPushItemWidth((float) width);
        }
    }

    @Override
    public void popItemWidth() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPopItemWidth();
        }
    }

    @Override
    public void setNextItemWidth(double width) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextItemWidth((float) width);
        }
    }

@Override
    public boolean beginCombo(String label, String preview) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginCombo(label == null ? "combo" : label, preview == null ? "" : preview);
    }

    @Override
    public void endCombo() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndCombo();
        }
    }

    @Override
    public void plotLines(String label, float[] values) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPlotLines(label == null ? "" : label, values == null ? new float[0] : values);
        }
    }

    @Override
    public void plotHistogram(String label, float[] values) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPlotHistogram(label == null ? "" : label, values == null ? new float[0] : values);
        }
    }

    @Override
    public String inputTextFlags(String label, String current, String flags) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputTextFlags(label == null ? LABEL_INPUT : label,
                current == null ? "" : current, flags == null ? "" : flags);
    }

    @Override
    public boolean isItemActive() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemActive();
    }

    @Override
    public boolean isItemFocused() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemFocused();
    }

    @Override
    public boolean isItemVisible() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemVisible();
    }

    @Override
    public boolean isItemEdited() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemEdited();
    }

@Override
    public List<Double> calcTextSize(String text) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(0.0, 0.0);
        }
        return client.imguiCalcTextSize(text == null ? "" : text);
    }

    @Override
    public List<Double> getItemRectSize() {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(0.0, 0.0);
        }
        return client.imguiGetItemRectSize();
    }

    @Override
    public boolean beginDragDropSource() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginDragDropSource();
    }

    @Override
    public boolean setDragDropPayload(String type, String data) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSetDragDropPayload(type == null ? "" : type, data == null ? "" : data);
    }

    @Override
    public void endDragDropSource() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDragDropSource();
        }
    }

    @Override
    public boolean beginDragDropTarget() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginDragDropTarget();
    }

    @Override
    public String acceptDragDropPayload(String type) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return null;
        }
        return client.imguiAcceptDragDropPayload(type == null ? "" : type);
    }

    @Override
    public void endDragDropTarget() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDragDropTarget();
        }
    }

@Override
    public void setScreen(boolean open) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetScreen(open);
        }
    }

    @Override
    public boolean isScreenOpen() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsScreenOpen();
    }

    @Override
    public boolean toggleScreen() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiToggleScreen();
    }

    @Override
    public boolean invisibleButton(String id, double width, double height) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiInvisibleButton(id == null ? "" : id, width, height);
    }

    @Override
    public double getMousePosX() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetMousePosX() : 0.0;
    }

    @Override
    public double getMousePosY() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetMousePosY() : 0.0;
    }

    @Override
    public double getCursorPosX() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetCursorPosX() : 0.0;
    }

@Override
    public double getCursorPosY() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetCursorPosY() : 0.0;
    }

    @Override
    public void setCursorPos(double x, double y) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetCursorPos(x, y);
        }
    }

    @Override
    public double getWindowPosX() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetWindowPosX() : 0.0;
    }

    @Override
    public double getWindowPosY() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetWindowPosY() : 0.0;
    }

    @Override
    public double getWindowWidth() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetWindowWidth() : 0.0;
    }

    @Override
    public double getWindowHeight() {
        IPlatformClient client = clientOrNull();
        return client != null ? client.imguiGetWindowHeight() : 0.0;
    }
}
