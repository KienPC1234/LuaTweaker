package com.luatweaker.platform.client.gui.imgui;

import imgui.ImColor;
import imgui.ImDrawList;
import imgui.ImGui;
import imgui.ImVec2;
import imgui.flag.ImGuiCond;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImBoolean;
import imgui.type.ImFloat;
import imgui.type.ImInt;
import imgui.type.ImString;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Dear ImGui powered visual GUI editor for LuaTweaker.
 * Supports both Container Block GUIs and JEI Recipe Category GUIs with
 * live canvas manipulation, hierarchy tree, property inspection, state-binding,
 * and instant Lua code export.
 */
public class ImGuiGuiEditor {
    private static final String DEFAULT_PROGRESS_ID = "progress";
    private static final String DEFAULT_ENERGY_ID = "energy";
    private static final String DEFAULT_INPUT_ID = "input";
    private static final String DEFAULT_ARROW_ID = "arrow";
    private static final String DEFAULT_OUTPUT_ID = "output";
    private static final int MIN_SNAP_GRID = 1;
    private static final String CALL_END = ")\n";
    private static final String STRING_ARG_END = "\")\n";


    public enum WidgetType {
        SLOT,
        PROGRESS_ARROW,
        FURNACE_FLAME,
        ENERGY_BAR,
        FLUID_BAR,
        ANIMATED_SPRITE,
        BUTTON,
        LABEL
    }

    public enum SlotRole {
        INPUT,
        OUTPUT,
        FUEL,
        LOCKED,
        CATALYST
    }

    public static class GuiWidget {
        public WidgetType type;
        public String id;
        public int x;
        public int y;
        public int width;
        public int height;
        public int rotation; // 0, 90, 180, 270
        public String extra;
        public float[] colorRgba = {1.0f, 1.0f, 1.0f, 1.0f};

        // Slot properties
        public SlotRole slotRole = SlotRole.INPUT;
        public String previewItemId = "";
        public boolean locked;

        // Bar properties
        public String barSource = DEFAULT_PROGRESS_ID;
        public String currentKey = "";
        public String maxKey = "";

        // Animated sprite properties
        public int frameCount = 4;
        public int frameDuration = 4;

        public GuiWidget(WidgetType type, String id, int x, int y, int width, int height, int rotation, String extra) {
            this.type = type;
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.rotation = rotation;
            this.extra = extra;
        }
    }

    private String containerId;
    private final ImBoolean jeiMode = new ImBoolean(false);
    private final ImInt panelWidth = new ImInt(176);
    private final ImInt panelHeight = new ImInt(166);
    private final List<GuiWidget> widgets = new ArrayList<>();
    private GuiWidget selectedWidget;

    private int gridSnap = 18;
    private final ImBoolean showGrid = new ImBoolean(true);
    private final ImBoolean showPlayerInventory = new ImBoolean(true);

    // JEI Category metadata
    private final ImString jeiCategoryTitle = new ImString(64);
    private final ImString jeiCategoryIcon = new ImString(128);
    private final ImString jeiCatalyst = new ImString(128);
    private final ImInt jeiCookTime = new ImInt(100);
    private final ImFloat jeiXp = new ImFloat(1.0f);

    // Inspector buffers
    private final ImString idBuf = new ImString(64);
    private final ImString extraBuf = new ImString(128);
    private final ImString previewItemBuf = new ImString(128);
    private final ImString currentKeyBuf = new ImString(64);
    private final ImString maxKeyBuf = new ImString(64);
    private final ImInt widgetX = new ImInt();
    private final ImInt widgetY = new ImInt();
    private final ImInt widgetW = new ImInt();
    private final ImInt widgetH = new ImInt();

    // Canvas dragging state
    private boolean isDragging;
    private int dragStartMouseX;
    private int dragStartMouseY;
    private int dragStartWidgetX;
    private int dragStartWidgetY;

    // Toast feedback
    private String statusMessage = "";
    private long statusMessageExpireTime;

    public ImGuiGuiEditor(String containerId, int width, int height) {
        this(containerId, width, height, false);
    }

    public ImGuiGuiEditor(String containerId, int width, int height, boolean isJeiMode) {
        this.containerId = containerId != null && !containerId.isBlank() ? containerId : isJeiMode ? "crystal_infusion" : "custom_machine";
        this.jeiMode.set(isJeiMode);
        this.jeiCategoryTitle.set(formatDisplayName(this.containerId));
        this.jeiCategoryIcon.set("minecraft:furnace");
        this.jeiCatalyst.set("minecraft:furnace");

        if (isJeiMode) {
            this.panelWidth.set(width > 0 ? width : 150);
            this.panelHeight.set(height > 0 ? height : 65);
            applyTemplateJeiSmelting();
        } else {
            this.panelWidth.set(width > 0 ? width : 176);
            this.panelHeight.set(height > 0 ? height : 166);
            applyTemplateSmelter();
        }
    }

    public void render() {
        renderMainMenuBar();

        // 1. Left Window: Toolbox & Hierarchy
        renderToolboxAndHierarchy();

        // 2. Center Window: Visual Canvas
        renderCanvasViewport();

        // 3. Right Window: Properties Inspector
        renderPropertiesInspector();

        // 4. Bottom Window: Live Lua Code Generator
        renderCodeExporter();
    }

    private void renderMainMenuBar() {
        if (!ImGui.beginMainMenuBar()) {
            return;
        }
        renderFileMenu();
        renderModeMenu();
        renderTemplatesMenu();
        renderViewMenu();
        renderModeIndicator();
        if (!statusMessage.isEmpty() && System.currentTimeMillis() < statusMessageExpireTime) {
            ImGui.sameLine(ImGui.getWindowWidth() - 340);
            ImGui.textColored(0.2f, 0.9f, 0.4f, 1.0f, statusMessage);
        }
        ImGui.endMainMenuBar();
    }

    private void renderFileMenu() {
        if (!ImGui.beginMenu("File")) {
            return;
        }
        if (ImGui.menuItem("New Empty Layout")) {
            widgets.clear();
            selectWidget(null);
            notifyStatus("Created new empty layout");
        }
        if (ImGui.menuItem("Copy Lua Code to Clipboard")) {
            copyToClipboard();
        }
        if (ImGui.menuItem("Save to luamods/exported_gui.lua")) {
            saveToFile();
        }
        ImGui.endMenu();
    }

    private void renderModeMenu() {
        if (!ImGui.beginMenu("Mode")) {
            return;
        }
        if (ImGui.menuItem("Container Block GUI", "", !jeiMode.get())) {
            jeiMode.set(false);
            this.panelWidth.set(176);
            this.panelHeight.set(166);
            applyTemplateSmelter();
            notifyStatus("Switched to Container Block GUI Mode");
        }
        if (ImGui.menuItem("JEI Recipe Category GUI", "", jeiMode.get())) {
            jeiMode.set(true);
            this.panelWidth.set(150);
            this.panelHeight.set(65);
            applyTemplateJeiSmelting();
            notifyStatus("Switched to JEI Recipe Category GUI Mode");
        }
        ImGui.endMenu();
    }

    private void renderTemplatesMenu() {
        if (!ImGui.beginMenu("Templates")) {
            return;
        }
        if (jeiMode.get()) {
            renderJeiTemplateItems();
        } else {
            renderContainerTemplateItems();
        }
        ImGui.endMenu();
    }

    private void renderJeiTemplateItems() {
        if (ImGui.menuItem("JEI: Smelting / Cooking")) { applyTemplateJeiSmelting(); }
        if (ImGui.menuItem("JEI: Crafting 3x3")) { applyTemplateJeiCrafting(); }
        if (ImGui.menuItem("JEI: Crusher / Grinder")) { applyTemplateJeiCrusher(); }
        if (ImGui.menuItem("JEI: Alloy Smelter")) { applyTemplateJeiAlloy(); }
    }

    private void renderContainerTemplateItems() {
        if (ImGui.menuItem("Smelter / Furnace")) { applyTemplateSmelter(); }
        if (ImGui.menuItem("Crusher / Grinder")) { applyTemplateCrusher(); }
        if (ImGui.menuItem("Alloy Smelter")) { applyTemplateAlloySmelter(); }
        if (ImGui.menuItem("Fluid Infuser")) { applyTemplateFluidInfuser(); }
    }

    private void renderViewMenu() {
        if (!ImGui.beginMenu("View")) {
            return;
        }
        ImGui.checkbox("Show Grid", showGrid);
        if (!jeiMode.get()) {
            ImGui.checkbox("Show Player Inventory Outline", showPlayerInventory);
        }
        ImGui.separator();
        if (ImGui.menuItem("Snap 1px")) { gridSnap = 1; }
        if (ImGui.menuItem("Snap 4px")) { gridSnap = 4; }
        if (ImGui.menuItem("Snap 9px")) { gridSnap = 9; }
        if (ImGui.menuItem("Snap 18px (Slot)")) { gridSnap = 18; }
        ImGui.endMenu();
    }

    private void renderModeIndicator() {
        if (jeiMode.get()) {
            ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "[JEI Recipe Mode]");
        } else {
            ImGui.textColored(0.2f, 0.9f, 0.5f, 1.0f, "[Container Mode]");
        }
    }

    private void renderToolboxAndHierarchy() {
        ImGui.setNextWindowPos(10, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(280, 520, ImGuiCond.FirstUseEver);
        if (!ImGui.begin("Toolbox & Hierarchy", ImGuiWindowFlags.None)) {
            return;
        }
        renderAddWidgetSection();
        renderGridGenerators();
        ImGui.separator();
        ImGui.text("Widget Hierarchy (" + widgets.size() + "):");
        renderHierarchyList();
        renderSelectionActions();
        ImGui.end();
    }

    private void renderAddWidgetSection() {
        if (!ImGui.collapsingHeader("Add Widgets", ImGuiWindowFlags.None)) {
            return;
        }
        if (ImGui.button("+ Slot", 80, 24)) { addWidget(WidgetType.SLOT); }
        ImGui.sameLine();
        if (ImGui.button("+ Arrow", 80, 24)) { addWidget(WidgetType.PROGRESS_ARROW); }
        ImGui.sameLine();
        if (ImGui.button("+ Flame", 80, 24)) { addWidget(WidgetType.FURNACE_FLAME); }

        if (jeiMode.get()) {
            renderJeiAddButtons();
        } else {
            renderContainerAddButtons();
        }
    }

    private void renderJeiAddButtons() {
        if (ImGui.button("+ Output Slot", 100, 24)) {
            addWidget(WidgetType.SLOT);
            if (selectedWidget != null) {
                selectedWidget.slotRole = SlotRole.OUTPUT;
                selectedWidget.locked = true;
            }
        }
        ImGui.sameLine();
        if (ImGui.button("+ Label", 80, 24)) { addWidget(WidgetType.LABEL); }
    }

    private void renderContainerAddButtons() {
        if (ImGui.button("+ Energy Bar", 80, 24)) { addWidget(WidgetType.ENERGY_BAR); }
        ImGui.sameLine();
        if (ImGui.button("+ Fluid Tank", 80, 24)) { addWidget(WidgetType.FLUID_BAR); }
        ImGui.sameLine();
        if (ImGui.button("+ Sprite", 80, 24)) { addWidget(WidgetType.ANIMATED_SPRITE); }

        if (ImGui.button("+ Button", 80, 24)) { addWidget(WidgetType.BUTTON); }
        ImGui.sameLine();
        if (ImGui.button("+ Label", 80, 24)) { addWidget(WidgetType.LABEL); }
    }

    private void renderGridGenerators() {
        if (!ImGui.collapsingHeader("Multi-Slot Generators", ImGuiWindowFlags.None)) {
            return;
        }
        if (ImGui.button("3x3 Grid (Crafting)", 130, 24)) { generateGrid(3, 3, 20, 8); }
        ImGui.sameLine();
        if (ImGui.button("2x2 Grid", 110, 24)) { generateGrid(2, 2, 24, 14); }
        if (ImGui.button("1x4 Row", 130, 24)) { generateGrid(4, 1, 20, 24); }
        ImGui.sameLine();
        if (ImGui.button("Row 1x3", 110, 24)) { generateGrid(3, 1, 24, 24); }
    }

    private void renderHierarchyList() {
        ImGui.beginChild("HierarchyList", 0, 220, true);
        for (GuiWidget w : widgets) {
            boolean isSelected = java.util.Objects.equals(w, selectedWidget);
            String label = String.format("[%s] %s (%d, %d)", w.type.name(), w.id, w.x, w.y);
            if (ImGui.selectable(label, isSelected)) {
                selectWidget(w);
            }
        }
        ImGui.endChild();
    }

    private void renderSelectionActions() {
        if (selectedWidget == null) {
            return;
        }
        if (ImGui.button("Rotate (R)", 90, 24)) {
            selectedWidget.rotation = (selectedWidget.rotation + 90) % 360;
            if (selectedWidget.type == WidgetType.PROGRESS_ARROW || selectedWidget.type == WidgetType.ENERGY_BAR) {
                int tmp = selectedWidget.width;
                selectedWidget.width = selectedWidget.height;
                selectedWidget.height = tmp;
                widgetW.set(selectedWidget.width);
                widgetH.set(selectedWidget.height);
            }
        }
        ImGui.sameLine();
        if (ImGui.button("Duplicate", 80, 24)) {
            duplicateSelected();
        }
        ImGui.sameLine();
        if (ImGui.button("Delete", 70, 24)) {
            deleteSelected();
        }
    }

    private void renderCanvasViewport() {
        ImGui.setNextWindowPos(300, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(540, 520, ImGuiCond.FirstUseEver);
        if (!ImGui.begin("Canvas Viewport (WYSIWYG)", ImGuiWindowFlags.None)) {
            return;
        }
        String modeTitle = jeiMode.get() ? "JEI Category Canvas" : "Container Panel Canvas";
        ImGui.text(String.format("%s: %dx%d px | Grid Snap: %dpx", modeTitle, panelWidth.get(), panelHeight.get(), gridSnap));
        renderSnapButtons();

        ImVec2 canvasPos = ImGui.getCursorScreenPos();
        ImDrawList drawList = ImGui.getWindowDrawList();
        float originX = canvasPos.x + 20;
        float originY = canvasPos.y + 10;
        float pW = panelWidth.get();
        float pH = panelHeight.get();

        drawCanvasBackground(drawList, originX, originY, pW, pH);
        drawCanvasGrid(drawList, originX, originY, pW, pH);
        drawGhostInventory(drawList, originX, originY, pW, pH);
        drawWidgets(drawList, originX, originY);

        // Mouse Interaction on Canvas
        handleCanvasInput(originX, originY);

        ImGui.dummy(pW + 40, pH + 30);
        ImGui.end();
    }

    private void renderSnapButtons() {
        ImGui.sameLine();
        if (ImGui.smallButton("Snap 1px")) { gridSnap = 1; }
        ImGui.sameLine();
        if (ImGui.smallButton("Snap 4px")) { gridSnap = 4; }
        ImGui.sameLine();
        if (ImGui.smallButton("Snap 9px")) { gridSnap = 9; }
        ImGui.sameLine();
        if (ImGui.smallButton("Snap 18px")) { gridSnap = 18; }
    }

    private void drawCanvasBackground(ImDrawList drawList, float originX, float originY, float pW, float pH) {
        // 1. Draw Panel Background (#C6C6C6)
        int bgCol = ImColor.rgb(198, 198, 198);
        drawList.addRectFilled(originX, originY, originX + pW, originY + pH, bgCol);

        // 3D Bevel border
        int whiteCol = ImColor.rgb(255, 255, 255);
        int darkCol = ImColor.rgb(55, 55, 55);
        drawList.addRect(originX, originY, originX + pW, originY + pH, darkCol, 0, 0, 2.0f);
        drawList.addLine(originX, originY, originX + pW, originY, whiteCol, 2.0f);
        drawList.addLine(originX, originY, originX, originY + pH, whiteCol, 2.0f);

        // Title
        String titleText = jeiMode.get() ? jeiCategoryTitle.get() : containerId;
        drawList.addText(originX + 8, originY + 6, ImColor.rgb(64, 64, 64), titleText);
    }

    private void drawCanvasGrid(ImDrawList drawList, float originX, float originY, float pW, float pH) {
        if (!showGrid.get()) {
            return;
        }
        int gridColor = ImColor.rgba(160, 160, 160, 60);
        for (int xi = 0; xi <= pW; xi += 18) {
            float x = xi;
            drawList.addLine(originX + x, originY, originX + x, originY + pH, gridColor, 1.0f);
        }
        for (int yi = 0; yi <= pH; yi += 18) {
            float y = yi;
            drawList.addLine(originX, originY + y, originX + pW, originY + y, gridColor, 1.0f);
        }
    }

    private void drawGhostInventory(ImDrawList drawList, float originX, float originY, float pW, float pH) {
        if (jeiMode.get() || !showPlayerInventory.get()) {
            return;
        }
        int ghostCol = ImColor.rgba(140, 140, 140, 90);
        float invX = originX + (pW - 162) / 2.0f;
        float invY = originY + pH - 84;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                drawList.addRect(invX + col * 18, invY + row * 18, invX + col * 18 + 18,
                        invY + row * 18 + 18, ghostCol, 0, 0, 1.0f);
            }
        }
        float hotbarY = originY + pH - 24;
        for (int col = 0; col < 9; col++) {
            drawList.addRect(invX + col * 18, hotbarY, invX + col * 18 + 18, hotbarY + 18,
                    ghostCol, 0, 0, 1.0f);
        }
        drawList.addText(invX, invY - 10, ImColor.rgb(64, 64, 64), "Inventory");
    }

    private void drawWidgets(ImDrawList drawList, float originX, float originY) {
        long now = System.currentTimeMillis();
        float animProgress = now % 2000L / 2000.0f;
        float flameAnim = 0.5f + 0.5f * (float) Math.sin(now / 150.0);
        for (GuiWidget w : widgets) {
            drawWidget(w, drawList, originX, originY, animProgress, flameAnim);
        }
    }

    private void drawWidget(GuiWidget w, ImDrawList drawList, float originX, float originY,
                            float animProgress, float flameAnim) {
        float wx = originX + w.x;
        float wy = originY + w.y;
        float ww = w.width;
        float wh = w.height;
        int whiteCol = ImColor.rgb(255, 255, 255);
        int darkCol = ImColor.rgb(55, 55, 55);
        int midCol = ImColor.rgb(139, 139, 139);

        switch (w.type) {
            case SLOT -> {
                // Slot 3D inset cavity
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, midCol);
                drawList.addLine(wx, wy, wx + ww, wy, darkCol, 1.5f);
                drawList.addLine(wx, wy, wx, wy + wh, darkCol, 1.5f);
                drawList.addLine(wx, wy + wh, wx + ww, wy + wh, whiteCol, 1.5f);
                drawList.addLine(wx + ww, wy, wx + ww, wy + wh, whiteCol, 1.5f);

                // Role badge
                String roleStr = switch (w.slotRole) {
                    case INPUT -> "IN";
                    case OUTPUT -> "OUT";
                    case FUEL -> "FL";
                    case LOCKED -> "LCK";
                    case CATALYST -> "CAT";
                };
                int roleColor = switch (w.slotRole) {
                    case INPUT -> ImColor.rgb(33, 150, 83);
                    case OUTPUT -> ImColor.rgb(242, 153, 74);
                    case FUEL -> ImColor.rgb(235, 87, 87);
                    case LOCKED -> ImColor.rgb(130, 130, 130);
                    case CATALYST -> ImColor.rgb(47, 128, 237);
                };
                drawList.addText(wx + 2, wy + 2, roleColor, roleStr);

                if (!w.previewItemId.isEmpty()) {
                    String shortItem = w.previewItemId.contains(":") ? w.previewItemId.split(":")[1] : w.previewItemId;
                    drawList.addText(wx + 2, wy + 9, ImColor.rgb(40, 40, 40), shortItem.substring(0, Math.min(3, shortItem.length())));
                }
            }
            case PROGRESS_ARROW -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgba(80, 80, 80, 180));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                float fillW = ww * animProgress;
                drawList.addRectFilled(wx, wy, wx + fillW, wy + wh, ImColor.rgba(255, 200, 0, 200));

                String arrIcon = switch (w.rotation) {
                    case 90 -> "v";
                    case 180 -> "<";
                    case 270 -> "^";
                    default -> ">";
                };
                drawList.addText(wx + ww / 2 - 3, wy + wh / 2 - 6, ImColor.rgb(255, 255, 255), arrIcon);
            }
            case FURNACE_FLAME -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgba(50, 50, 50, 180));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                float fillH = wh * flameAnim;
                drawList.addRectFilled(wx, wy + wh - fillH, wx + ww, wy + wh, ImColor.rgb(255, (int)(120 * flameAnim + 80), 0));
                drawList.addText(wx + 2, wy + 2, ImColor.rgb(255, 255, 255), "F");
            }
            case ENERGY_BAR -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgba(30, 30, 30, 220));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                float fillH = wh * 0.75f;
                drawList.addRectFilled(wx + 1, wy + wh - fillH, wx + ww - 1, wy + wh - 1, ImColor.rgb(0, 230, 118));
                drawList.addText(wx + 1, wy + 2, ImColor.rgb(255, 255, 255), "RF");
            }
            case FLUID_BAR -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgba(30, 30, 30, 220));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                float fillH = wh * 0.60f;
                drawList.addRectFilled(wx + 1, wy + wh - fillH, wx + ww - 1, wy + wh - 1, ImColor.rgb(33, 150, 243));
                drawList.addText(wx + 1, wy + 2, ImColor.rgb(255, 255, 255), "mB");
            }
            case ANIMATED_SPRITE -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgba(60, 60, 80, 200));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                drawList.addText(wx + 2, wy + 2, ImColor.rgb(200, 200, 255), "SPR");
            }
            case BUTTON -> {
                drawList.addRectFilled(wx, wy, wx + ww, wy + wh, ImColor.rgb(180, 180, 180));
                drawList.addRect(wx, wy, wx + ww, wy + wh, darkCol);
                drawList.addText(wx + 4, wy + 2, ImColor.rgb(20, 20, 20), w.extra.isEmpty() ? "BTN" : w.extra);
            }
            case LABEL -> {
                int col = ImColor.rgba(w.colorRgba[0], w.colorRgba[1], w.colorRgba[2], w.colorRgba[3]);
                drawList.addText(wx, wy, col, w.extra.isEmpty() ? "Text" : w.extra);
            }
        }

        // Selection highlight
        if (java.util.Objects.equals(w, selectedWidget)) {
            int selCol = ImColor.rgb(255, 215, 0);
            drawList.addRect(wx - 2, wy - 2, wx + ww + 2, wy + wh + 2, selCol, 0, 0, 2.0f);
        }
    }

    private void handleCanvasInput(float originX, float originY) {
        ImVec2 mousePos = ImGui.getMousePos();
        float mx = mousePos.x;
        float my = mousePos.y;
        boolean leftDown = ImGui.isMouseDown(0);

        if (ImGui.isWindowHovered() && ImGui.isMouseClicked(0)) {
            trySelectWidget(mx, my, originX, originY);
        }

        if (isDragging && leftDown && selectedWidget != null) {
            dragSelected(mx, my);
        } else if (!leftDown) {
            isDragging = false;
        }
    }

    private void trySelectWidget(float mx, float my, float originX, float originY) {
        GuiWidget hit = findWidgetAt(mx - originX, my - originY);
        if (hit == null) {
            selectWidget(null);
            return;
        }
        selectWidget(hit);
        isDragging = true;
        dragStartMouseX = (int) mx;
        dragStartMouseY = (int) my;
        dragStartWidgetX = hit.x;
        dragStartWidgetY = hit.y;
    }

    private @Nullable GuiWidget findWidgetAt(float canvasX, float canvasY) {
        for (int i = widgets.size() - 1; i >= 0; i--) {
            GuiWidget w = widgets.get(i);
            if (canvasX >= w.x && canvasX <= w.x + w.width
                    && canvasY >= w.y && canvasY <= w.y + w.height) {
                return w;
            }
        }
        return null;
    }

    private void dragSelected(float mx, float my) {
        int dx = (int) mx - dragStartMouseX;
        int dy = (int) my - dragStartMouseY;
        int newX = dragStartWidgetX + dx;
        int newY = dragStartWidgetY + dy;

        if (gridSnap > MIN_SNAP_GRID) {
            newX = Math.round((float) newX / gridSnap) * gridSnap;
            newY = Math.round((float) newY / gridSnap) * gridSnap;
        }

        selectedWidget.x = Math.max(0, Math.min(panelWidth.get() - selectedWidget.width, newX));
        selectedWidget.y = Math.max(0, Math.min(panelHeight.get() - selectedWidget.height, newY));
        widgetX.set(selectedWidget.x);
        widgetY.set(selectedWidget.y);
    }

    private void renderPropertiesInspector() {
        ImGui.setNextWindowPos(850, 30, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(320, 520, ImGuiCond.FirstUseEver);
        if (!ImGui.begin("Properties Inspector", ImGuiWindowFlags.None)) {
            return;
        }
        if (jeiMode.get()) {
            renderJeiMetadata();
        }
        if (selectedWidget == null) {
            renderCanvasDimensions();
            ImGui.end();
            return;
        }
        renderSelectedWidgetProperties();
        ImGui.end();
    }

    private void renderJeiMetadata() {
        ImGui.textColored(1.0f, 0.8f, 0.2f, 1.0f, "JEI Category Metadata:");
        ImGui.inputText("Category Title", jeiCategoryTitle);
        ImGui.inputText("Icon Item", jeiCategoryIcon);
        ImGui.inputText("Catalyst", jeiCatalyst);
        ImGui.dragInt("Cook Time (ticks)", jeiCookTime.getData(), 5.0f, 10, 1200);
        ImGui.dragFloat("XP Reward", jeiXp.getData(), 0.1f, 0.0f, 100.0f);
        ImGui.separator();
    }

    private void renderCanvasDimensions() {
        ImGui.textColored(0.7f, 0.7f, 0.7f, 1.0f, "Select a widget on canvas or hierarchy.");
        ImGui.separator();
        ImGui.text("Canvas Dimensions:");
        ImGui.inputInt("Width", panelWidth);
        ImGui.inputInt("Height", panelHeight);
    }

    private void renderSelectedWidgetProperties() {
        ImGui.textColored(0.2f, 0.8f, 1.0f, 1.0f, "Widget: " + selectedWidget.type.name());
        ImGui.separator();
        renderWidgetTransform();
        ImGui.separator();
        renderWidgetTypeProperties();
    }

    private void renderWidgetTransform() {
        renderWidgetGeometry();
        renderRotationButtons();
    }

    private void renderWidgetGeometry() {
        if (ImGui.inputText("ID", idBuf)) {
            selectedWidget.id = idBuf.get();
        }
        if (ImGui.dragInt("X", widgetX.getData(), 1.0f, 0, panelWidth.get())) {
            selectedWidget.x = widgetX.get();
        }
        if (ImGui.dragInt("Y", widgetY.getData(), 1.0f, 0, panelHeight.get())) {
            selectedWidget.y = widgetY.get();
        }
        if (ImGui.dragInt("Width", widgetW.getData(), 1.0f, 4, panelWidth.get())) {
            selectedWidget.width = widgetW.get();
        }
        if (ImGui.dragInt("Height", widgetH.getData(), 1.0f, 4, panelHeight.get())) {
            selectedWidget.height = widgetH.get();
        }
    }

    private void renderRotationButtons() {
        ImGui.text("Rotation: " + selectedWidget.rotation + "°");
        if (ImGui.button("0°")) { selectedWidget.rotation = 0; }
        ImGui.sameLine();
        if (ImGui.button("90°")) { selectedWidget.rotation = 90; }
        ImGui.sameLine();
        if (ImGui.button("180°")) { selectedWidget.rotation = 180; }
        ImGui.sameLine();
        if (ImGui.button("270°")) { selectedWidget.rotation = 270; }
    }

    private void renderWidgetTypeProperties() {
        if (selectedWidget.type == WidgetType.SLOT) {
            renderSlotProperties();
        } else if (selectedWidget.type == WidgetType.PROGRESS_ARROW
                || selectedWidget.type == WidgetType.ENERGY_BAR
                || selectedWidget.type == WidgetType.FLUID_BAR) {
            renderBarProperties();
        } else if (selectedWidget.type == WidgetType.LABEL || selectedWidget.type == WidgetType.BUTTON) {
            renderTextProperties();
        }
    }

    private void renderSlotProperties() {
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f,
                jeiMode.get() ? "JEI Recipe Slot Role:" : "Slot State-Binding:");
        if (ImGui.beginCombo("Role", selectedWidget.slotRole.name())) {
            for (SlotRole r : SlotRole.values()) {
                if (ImGui.selectable(r.name(), r == selectedWidget.slotRole)) {
                    selectedWidget.slotRole = r;
                    selectedWidget.locked = (r == SlotRole.OUTPUT || r == SlotRole.LOCKED);
                }
            }
            ImGui.endCombo();
        }

        if (ImGui.inputText("Preview Item", previewItemBuf)) {
            selectedWidget.previewItemId = previewItemBuf.get();
        }
        if (!jeiMode.get()) {
            ImGui.checkbox("Locked (Output Slot)", selectedWidget.locked);
        }
    }

    private void renderBarProperties() {
        ImGui.textColored(0.4f, 1.0f, 0.4f, 1.0f, "Bar State-Binding:");
        if (ImGui.inputText("Source Type", extraBuf)) {
            selectedWidget.extra = extraBuf.get();
            selectedWidget.barSource = extraBuf.get();
        }
        if (!jeiMode.get()) {
            if (ImGui.inputText("Current Key", currentKeyBuf)) {
                selectedWidget.currentKey = currentKeyBuf.get();
            }
            if (ImGui.inputText("Max Key", maxKeyBuf)) {
                selectedWidget.maxKey = maxKeyBuf.get();
            }
        }
    }

    private void renderTextProperties() {
        if (ImGui.inputText("Text / Label", extraBuf)) {
            selectedWidget.extra = extraBuf.get();
        }
        ImGui.colorEdit4("Color", selectedWidget.colorRgba);
    }

    private void renderCodeExporter() {
        ImGui.setNextWindowPos(10, 560, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(1160, 220, ImGuiCond.FirstUseEver);
        if (ImGui.begin("Lua Code Exporter", ImGuiWindowFlags.None)) {

            if (ImGui.button("Copy Lua Code to Clipboard", 220, 26)) {
                copyToClipboard();
            }
            ImGui.sameLine();
            if (ImGui.button("Save to luamods/exported_gui.lua", 240, 26)) {
                saveToFile();
            }

            String code = generateLuaCode();
            ImString codeHolder = new ImString(code);
            ImGui.inputTextMultiline("##LuaOutput", codeHolder, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY() - 10, ImGuiInputTextFlags.ReadOnly);

            ImGui.end();
        }
    }

    private void selectWidget(GuiWidget w) {
        this.selectedWidget = w;
        if (w != null) {
            idBuf.set(w.id);
            extraBuf.set(w.extra != null ? w.extra : "");
            previewItemBuf.set(w.previewItemId != null ? w.previewItemId : "");
            currentKeyBuf.set(w.currentKey != null ? w.currentKey : "");
            maxKeyBuf.set(w.maxKey != null ? w.maxKey : "");
            widgetX.set(w.x);
            widgetY.set(w.y);
            widgetW.set(w.width);
            widgetH.set(w.height);
        }
    }

    private void addWidget(WidgetType type) {
        int x = 20;
        int y = 20;
        int w = 18;
        int h = 18;
        String id = "widget_" + widgets.size();
        String extra = "";

        switch (type) {
            case SLOT -> {
                w = 18; h = 18;
                id = "slot_" + getSlotCount();
                extra = String.valueOf(getSlotCount());
            }
            case PROGRESS_ARROW -> {
                w = 24; h = 17;
                id = DEFAULT_PROGRESS_ID;
                extra = DEFAULT_PROGRESS_ID;
            }
            case FURNACE_FLAME -> {
                w = 14; h = 14;
                id = "flame";
                extra = "fuel";
            }
            case ENERGY_BAR -> {
                w = 14; h = 50;
                id = DEFAULT_ENERGY_ID;
                extra = DEFAULT_ENERGY_ID;
            }
            case FLUID_BAR -> {
                w = 16; h = 50;
                id = "fluid";
                extra = "fluid";
            }
            case ANIMATED_SPRITE -> {
                w = 16; h = 16;
                id = "gear";
            }
            case BUTTON -> {
                w = 40; h = 16;
                id = "btn_start";
                extra = "Start";
            }
            case LABEL -> {
                w = 60; h = 10;
                id = "lbl_status";
                extra = "Status: OK";
            }
        }

        GuiWidget widget = new GuiWidget(type, id, x, y, w, h, 0, extra);
        widgets.add(widget);
        selectWidget(widget);
        notifyStatus("Added " + type.name());
    }

    private int getSlotCount() {
        int count = 0;
        for (GuiWidget w : widgets) {
            if (w.type == WidgetType.SLOT) { count++; }
        }
        return count;
    }

    private void generateGrid(int cols, int rows, int startX, int startY) {
        int baseSlot = getSlotCount();
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                addGridSlot(baseSlot + r * cols + c, startX + c * 18, startY + r * 18);
            }
        }
        notifyStatus(String.format("Generated %dx%d slot grid", cols, rows));
    }

    private void addGridSlot(int idx, int x, int y) {
        GuiWidget w = new GuiWidget(WidgetType.SLOT, "slot_" + idx, x, y, 18, 18, 0, String.valueOf(idx));
        widgets.add(w);
    }

    private void duplicateSelected() {
        if (selectedWidget == null) { return; }
        GuiWidget clone = new GuiWidget(selectedWidget.type, selectedWidget.id + "_copy",
                selectedWidget.x + 10, selectedWidget.y + 10, selectedWidget.width, selectedWidget.height,
                selectedWidget.rotation, selectedWidget.extra);
        clone.slotRole = selectedWidget.slotRole;
        clone.previewItemId = selectedWidget.previewItemId;
        clone.locked = selectedWidget.locked;
        clone.barSource = selectedWidget.barSource;
        clone.currentKey = selectedWidget.currentKey;
        clone.maxKey = selectedWidget.maxKey;
        widgets.add(clone);
        selectWidget(clone);
        notifyStatus("Duplicated widget");
    }

    private void deleteSelected() {
        if (selectedWidget == null) { return; }
        widgets.remove(selectedWidget);
        selectWidget(null);
        notifyStatus("Deleted widget");
    }

    // Container templates
    private void applyTemplateSmelter() {
        widgets.clear();
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_input", 56, 17, 18, 18, 0, "0"));
        widgets.get(0).previewItemId = "minecraft:raw_iron";
        widgets.get(0).slotRole = SlotRole.INPUT;

        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_fuel", 56, 53, 18, 18, 0, "1"));
        widgets.get(1).previewItemId = "minecraft:coal";
        widgets.get(1).slotRole = SlotRole.FUEL;

        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_output", 116, 35, 18, 18, 0, "2"));
        widgets.get(2).previewItemId = "minecraft:iron_ingot";
        widgets.get(2).slotRole = SlotRole.OUTPUT;
        widgets.get(2).locked = true;

        widgets.add(new GuiWidget(WidgetType.FURNACE_FLAME, "flame", 57, 37, 14, 14, 0, "fuel"));
        widgets.add(new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_PROGRESS_ID, 79, 34, 24, 17, 0, DEFAULT_PROGRESS_ID));
        selectWidget(widgets.get(0));
        notifyStatus("Loaded Smelter template");
    }

    private void applyTemplateCrusher() {
        widgets.clear();
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_input", 44, 35, 18, 18, 0, "0"));
        widgets.get(0).previewItemId = "minecraft:iron_ore";

        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_out1", 104, 35, 18, 18, 0, "1"));
        widgets.get(1).previewItemId = "minecraft:raw_iron";
        widgets.get(1).slotRole = SlotRole.OUTPUT;
        widgets.get(1).locked = true;

        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_out2", 126, 35, 18, 18, 0, "2"));
        widgets.get(2).previewItemId = "minecraft:cobblestone";
        widgets.get(2).slotRole = SlotRole.OUTPUT;
        widgets.get(2).locked = true;

        widgets.add(new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_PROGRESS_ID, 68, 34, 24, 17, 0, DEFAULT_PROGRESS_ID));
        widgets.add(new GuiWidget(WidgetType.ENERGY_BAR, DEFAULT_ENERGY_ID, 16, 18, 14, 50, 0, DEFAULT_ENERGY_ID));
        selectWidget(widgets.get(0));
        notifyStatus("Loaded Crusher template");
    }

    private void applyTemplateAlloySmelter() {
        widgets.clear();
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_in1", 44, 21, 18, 18, 0, "0"));
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_in2", 66, 21, 18, 18, 0, "1"));
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_fuel", 55, 53, 18, 18, 0, "2"));
        widgets.get(2).slotRole = SlotRole.FUEL;

        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_out", 124, 35, 18, 18, 0, "3"));
        widgets.get(3).slotRole = SlotRole.OUTPUT;
        widgets.get(3).locked = true;

        widgets.add(new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_PROGRESS_ID, 90, 34, 24, 17, 0, DEFAULT_PROGRESS_ID));
        widgets.add(new GuiWidget(WidgetType.ENERGY_BAR, DEFAULT_ENERGY_ID, 16, 18, 14, 50, 0, DEFAULT_ENERGY_ID));
        selectWidget(widgets.get(0));
        notifyStatus("Loaded Alloy Smelter template");
    }

    private void applyTemplateFluidInfuser() {
        widgets.clear();
        widgets.add(new GuiWidget(WidgetType.FLUID_BAR, "fluid_tank", 18, 18, 16, 50, 0, "fluid"));
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_input", 54, 35, 18, 18, 0, "0"));
        widgets.add(new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_PROGRESS_ID, 78, 34, 24, 17, 0, DEFAULT_PROGRESS_ID));
        widgets.add(new GuiWidget(WidgetType.SLOT, "slot_out", 116, 35, 18, 18, 0, "1"));
        widgets.get(2).slotRole = SlotRole.OUTPUT;
        widgets.get(2).locked = true;
        widgets.add(new GuiWidget(WidgetType.ENERGY_BAR, DEFAULT_ENERGY_ID, 146, 18, 14, 50, 0, DEFAULT_ENERGY_ID));
        selectWidget(widgets.get(0));
        notifyStatus("Loaded Fluid Infuser template");
    }

    // JEI Recipe Templates
    private void applyTemplateJeiSmelting() {
        widgets.clear();
        GuiWidget inSlot = new GuiWidget(WidgetType.SLOT, DEFAULT_INPUT_ID, 20, 20, 18, 18, 0, DEFAULT_INPUT_ID);
        inSlot.previewItemId = "minecraft:raw_iron";
        inSlot.slotRole = SlotRole.INPUT;
        widgets.add(inSlot);

        GuiWidget flame = new GuiWidget(WidgetType.FURNACE_FLAME, "flame", 21, 42, 14, 14, 0, "fuel");
        widgets.add(flame);

        GuiWidget arrow = new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_ARROW_ID, 55, 20, 24, 17, 0, DEFAULT_PROGRESS_ID);
        widgets.add(arrow);

        GuiWidget outSlot = new GuiWidget(WidgetType.SLOT, DEFAULT_OUTPUT_ID, 100, 20, 18, 18, 0, DEFAULT_OUTPUT_ID);
        outSlot.previewItemId = "minecraft:iron_ingot";
        outSlot.slotRole = SlotRole.OUTPUT;
        outSlot.locked = true;
        widgets.add(outSlot);

        selectWidget(inSlot);
        notifyStatus("Loaded JEI Smelting Recipe template");
    }

    private void applyTemplateJeiCrafting() {
        widgets.clear();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                addCraftingSlot(r, c);
            }
        }
        GuiWidget arrow = new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_ARROW_ID, 78, 24, 24, 17, 0, DEFAULT_PROGRESS_ID);
        widgets.add(arrow);

        GuiWidget outSlot = new GuiWidget(WidgetType.SLOT, DEFAULT_OUTPUT_ID, 112, 24, 18, 18, 0, DEFAULT_OUTPUT_ID);
        outSlot.slotRole = SlotRole.OUTPUT;
        outSlot.locked = true;
        widgets.add(outSlot);

        selectWidget(widgets.get(0));
        notifyStatus("Loaded JEI 3x3 Crafting Recipe template");
    }

    private void addCraftingSlot(int row, int col) {
        GuiWidget inSlot = new GuiWidget(WidgetType.SLOT, "in_" + (row * 3 + col),
                15 + col * 18, 6 + row * 18, 18, 18, 0, DEFAULT_INPUT_ID);
        inSlot.slotRole = SlotRole.INPUT;
        widgets.add(inSlot);
    }

    private void applyTemplateJeiCrusher() {
        widgets.clear();
        GuiWidget inSlot = new GuiWidget(WidgetType.SLOT, DEFAULT_INPUT_ID, 20, 22, 18, 18, 0, DEFAULT_INPUT_ID);
        inSlot.slotRole = SlotRole.INPUT;
        widgets.add(inSlot);

        GuiWidget arrow = new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_ARROW_ID, 50, 22, 24, 17, 0, DEFAULT_PROGRESS_ID);
        widgets.add(arrow);

        GuiWidget out1 = new GuiWidget(WidgetType.SLOT, "out1", 85, 22, 18, 18, 0, DEFAULT_OUTPUT_ID);
        out1.slotRole = SlotRole.OUTPUT;
        out1.locked = true;
        widgets.add(out1);

        GuiWidget out2 = new GuiWidget(WidgetType.SLOT, "out2", 108, 22, 18, 18, 0, DEFAULT_OUTPUT_ID);
        out2.slotRole = SlotRole.OUTPUT;
        out2.locked = true;
        widgets.add(out2);

        selectWidget(inSlot);
        notifyStatus("Loaded JEI Crusher Recipe template");
    }

    private void applyTemplateJeiAlloy() {
        widgets.clear();
        GuiWidget in1 = new GuiWidget(WidgetType.SLOT, "in1", 20, 12, 18, 18, 0, DEFAULT_INPUT_ID);
        in1.slotRole = SlotRole.INPUT;
        widgets.add(in1);

        GuiWidget in2 = new GuiWidget(WidgetType.SLOT, "in2", 20, 34, 18, 18, 0, DEFAULT_INPUT_ID);
        in2.slotRole = SlotRole.INPUT;
        widgets.add(in2);

        GuiWidget arrow = new GuiWidget(WidgetType.PROGRESS_ARROW, DEFAULT_ARROW_ID, 55, 22, 24, 17, 0, DEFAULT_PROGRESS_ID);
        widgets.add(arrow);

        GuiWidget out = new GuiWidget(WidgetType.SLOT, "out", 95, 22, 18, 18, 0, DEFAULT_OUTPUT_ID);
        out.slotRole = SlotRole.OUTPUT;
        out.locked = true;
        widgets.add(out);

        selectWidget(in1);
        notifyStatus("Loaded JEI Alloy Smelter Recipe template");
    }

    public String generateLuaCode() {
        if (jeiMode.get()) {
            return generateJeiLuaCode();
        }
        return generateContainerLuaCode();
    }

    private String generateJeiLuaCode() {
        StringBuilder sb = new StringBuilder(4096);
        String id = containerId;
        sb.append("-- ==========================================================================\n"
                + "-- Auto-generated by LuaTweaker Dear ImGui GUI Editor (JEI Mode)\n-- Category UID: ")
            .append(id).append(" (").append(panelWidth.get()).append('x').append(panelHeight.get())
            .append(CALL_END + "-- ==========================================================================\n\n"
                    + "local Recipe = require(\"LuaTweaker.Recipe\")\n\n"
                    + "-- 1. Register Custom JEI Recipe Category Layout\nRecipe.NewJeiCategory(\"")
            .append(id).append(STRING_ARG_END)
            .append("    :Title(\"").append(jeiCategoryTitle.get()).append(STRING_ARG_END);

        if (!jeiCategoryIcon.get().isBlank()) {
            sb.append("    :Icon(\"").append(jeiCategoryIcon.get()).append(STRING_ARG_END);
        }
        sb.append("    :Size(").append(panelWidth.get()).append(", ").append(panelHeight.get()).append(CALL_END);

        if (!jeiCatalyst.get().isBlank()) {
            sb.append("    :Catalyst(\"").append(jeiCatalyst.get()).append(STRING_ARG_END);
        }

        appendJeiSlots(sb);

        List<String> inItems = new ArrayList<>();
        List<String> outItems = new ArrayList<>();
        collectJeiItems(inItems, outItems);

        sb.append("    :Register()\n\n-- 2. Add Recipes to this JEI Category\nRecipe.AddJeiRecipe(\"")
            .append(id)
            .append("\", {\n    inputs = { ");
        appendItemList(sb, inItems);
        sb.append(" },\n    outputs = { ");
        appendItemList(sb, outItems);
        sb.append(" },\n    cookTime = ").append(jeiCookTime.get()).append(", -- ").append(jeiCookTime.get() / 20)
            .append("s\n    xp = ").append(jeiXp.get()).append("\n})\n");

        return sb.toString();
    }

    private void appendJeiSlots(StringBuilder sb) {
        for (GuiWidget w : widgets) {
            if (w.type == WidgetType.SLOT) {
                boolean isInput = w.slotRole != SlotRole.OUTPUT;
                sb.append("    :Slot(").append(w.x).append(", ").append(w.y).append(", ").append(isInput).append(CALL_END);
            } else if (w.type == WidgetType.PROGRESS_ARROW) {
                sb.append("    :Arrow(").append(w.x).append(", ").append(w.y).append(CALL_END);
            } else if (w.type == WidgetType.FURNACE_FLAME) {
                sb.append("    :Flame(").append(w.x).append(", ").append(w.y).append(CALL_END);
            }
        }
    }

    private void collectJeiItems(List<String> inItems, List<String> outItems) {
        for (GuiWidget w : widgets) {
            if (w.type != WidgetType.SLOT) {
                continue;
            }
            String item = w.previewItemId.isBlank() ? "minecraft:iron_ingot" : w.previewItemId;
            if (w.slotRole == SlotRole.OUTPUT) {
                outItems.add(item);
            } else {
                inItems.add(item);
            }
        }
    }

    private static void appendItemList(StringBuilder sb, List<String> items) {
        for (int i = 0; i < items.size(); i++) {
            sb.append('"').append(items.get(i)).append('"');
            if (i < items.size() - 1) { sb.append(", "); }
        }
    }

    private String generateContainerLuaCode() {
        StringBuilder sb = new StringBuilder(4096);
        String id = containerId;
        sb.append("-- ==========================================================================\n"
                + "-- Auto-generated by LuaTweaker Dear ImGui GUI Editor (Container Mode)\n-- Container: ")
            .append(id).append(" (").append(panelWidth.get()).append('x').append(panelHeight.get())
            .append(CALL_END + "-- ==========================================================================\n\n"
                    + "local Content = require(\"LuaTweaker.Content\")\n"
                    + "\nContent.NewBlock(\"")
            .append(id).append(STRING_ARG_END)
            .append("    :DisplayName(\"").append(formatDisplayName(id)).append(STRING_ARG_END);

        int slotCount = getSlotCount();
        int rows = Math.max(1, (int) Math.ceil(slotCount / 9.0));
        sb.append("    :Container(").append(rows).append(", ").append(Math.min(9, slotCount)).append(", \"drop\")\n");

        appendContainerSlots(sb);
        appendContainerBars(sb);

        sb.append("    :OnTick(function(data)\n"
                + "        local World = require(\"LuaTweaker.World\")\n"
                + "        local pos = { X = data.X, Y = data.Y, Z = data.Z }\n"
                + "        local inv = World:GetContainer(pos)\n"
                + "        if not inv then return end\n\n"
                + "        -- Sample machine processing loop with state-binding\n"
                + "        local input = inv:GetSlot(0)\n"
                + "        if input and input.Count > 0 then\n"
                + "            data.Progress = (data.Progress or 0) + 0.05\n"
                + "            if data.Progress >= 1.0 then\n"
                + "                data.Progress = 0\n"
                + "                inv:ExtractItem(0, 1)\n"
                + "                inv:InsertItem(1, \"minecraft:iron_ingot\", 1)\n"
                + "            end\n"
                + "        else\n"
                + "            data.Progress = 0\n"
                + "        end\n"
                + "    end)\n"
                + "    :Register()\n");

        return sb.toString();
    }

    private void appendContainerSlots(StringBuilder sb) {
        int sIdx = 0;
        for (GuiWidget w : widgets) {
            if (w.type != WidgetType.SLOT) {
                continue;
            }
            sb.append("    :SlotPosition(").append(sIdx).append(", ").append(w.x).append(", ").append(w.y).append(CALL_END);
            if (w.locked || w.slotRole == SlotRole.OUTPUT || w.slotRole == SlotRole.LOCKED) {
                sb.append("    :LockSlot(").append(sIdx).append(", true)\n");
            }
            sIdx++;
        }
    }

    private void appendContainerBars(StringBuilder sb) {
        for (GuiWidget w : widgets) {
            if (w.type == WidgetType.PROGRESS_ARROW || w.type == WidgetType.FURNACE_FLAME
                    || w.type == WidgetType.ENERGY_BAR || w.type == WidgetType.FLUID_BAR) {
                String source = (w.extra != null && !w.extra.isBlank()) ? w.extra : DEFAULT_PROGRESS_ID;
                sb.append("    :GuiBar(\"").append(w.id).append("\", ")
                  .append(w.x).append(", ").append(w.y).append(", ")
                  .append(w.width).append(", ").append(w.height).append(", \"")
                  .append(source).append(STRING_ARG_END);
            } else if (w.type == WidgetType.BUTTON) {
                sb.append("    :Button(\"").append(w.id).append("\", ")
                  .append(w.x).append(", ").append(w.y).append(", ")
                  .append(w.width).append(", ").append(w.height).append(", \"")
                  .append(w.extra).append(STRING_ARG_END);
            }
        }
    }

    private void copyToClipboard() {
        String code = generateLuaCode();
        ImGui.setClipboardText(code);
        notifyStatus("Lua code copied to clipboard!");
    }

    private void saveToFile() {
        String code = generateLuaCode();
        File dir = new File("luamods");
        if (!dir.exists()) { dir.mkdirs(); }
        File outFile = new File(dir, "exported_gui.lua");
        try (var writer = java.nio.file.Files.newBufferedWriter(outFile.toPath(), StandardCharsets.UTF_8)) {
            writer.write(code);
            notifyStatus("Saved to " + outFile.getPath());
        } catch (IOException e) {
            notifyStatus("Error saving file: " + e.getMessage());
        }
    }

    private void notifyStatus(String msg) {
        this.statusMessage = msg;
        this.statusMessageExpireTime = System.currentTimeMillis() + 4000L;
    }

    private String formatDisplayName(String id) {
        String[] parts = id.replace('_', ' ').replace('-', ' ').split("\\s+");
        StringBuilder sb = new StringBuilder(4096);
        for (String p : parts) {
            if (!p.isEmpty()) {
                sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }
}
