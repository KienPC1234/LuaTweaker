package com.luatweaker.platform.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * In-game LuaMod manager: every discovered mod with its on/off state and last
 * load result. Switches persist to {@code mods_manager.json} immediately and
 * take effect on the next reload (list screen offers a Reload button).
 */
public class LuamodListScreen extends Screen {
    private static final int MIN_HEIGHT_FOR_HINT = 110;

    private final Screen parent;

    private ModList list;
    private java.util.Set<String> updateIds = java.util.Set.of();
    private String query = "";
    private net.minecraft.client.gui.components.EditBox searchBox;
    private Button detailsButton;
    private Button toggleButton;

    public LuamodListScreen(@Nullable Screen parent) {
        super(Component.literal("LuaMods"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<ModSummary> summaries = LuamodClientActions.refreshSummaries();
        updateIds = LuamodClientActions.updateAvailableIds();
        int searchWidth = Math.min(400, Math.max(120, width - 40));
        searchBox = new net.minecraft.client.gui.components.EditBox(font,
                width / 2 - searchWidth / 2, 38, searchWidth, 20, Component.literal("Search mods"));
        searchBox.setValue(query);
        searchBox.setHint(Component.literal("Search id or name..."));
        searchBox.setResponder(ignored -> {
            query = searchBox.getValue();
            refresh();
        });
        addRenderableWidget(searchBox);

        int listTop = 64;
        int reservedBottom = LuamodLayouts.fitsRow(width, 5) ? 60 : 84;
        int listHeight = Math.max(60, height - listTop - reservedBottom);
        list = new ModList(minecraft, width, listHeight, listTop, 26);
        list.updateSizeAndPosition(width, listHeight, listTop);
        list.setX(0);
        list.refreshEntries(LuamodClientActions.filterSummaries(summaries, query));
        addRenderableWidget(list);

        if (LuamodLayouts.fitsRow(width, 5)) {
            int bottom = height - 26;
            int bw = LuamodLayouts.buttonWidth(width, 5);
            int left = LuamodLayouts.rowLeft(width, 5, bw);
            detailsButton = Button.builder(Component.literal("Details"), pressed -> openDetails())
                    .bounds(LuamodLayouts.buttonX(left, 0, bw), bottom, bw, 20).build();
            addRenderableWidget(detailsButton);
            toggleButton = Button.builder(Component.literal("ON/OFF"), pressed -> toggleSelected())
                    .bounds(LuamodLayouts.buttonX(left, 1, bw), bottom, bw, 20).build();
            addRenderableWidget(toggleButton);
            addRenderableWidget(Button.builder(Component.literal("Refresh"), pressed -> refresh())
                    .bounds(LuamodLayouts.buttonX(left, 2, bw), bottom, bw, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reload"), pressed -> LuamodClientActions.requestReload())
                    .bounds(LuamodLayouts.buttonX(left, 3, bw), bottom, bw, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"), pressed -> onClose())
                    .bounds(LuamodLayouts.buttonX(left, 4, bw), bottom, bw, 20).build());
        } else {
            int bottom = height - 26;
            int topRow = height - 50;
            int bw3 = LuamodLayouts.buttonWidth(width, 3);
            int left3 = LuamodLayouts.rowLeft(width, 3, bw3);
            int bw2 = LuamodLayouts.buttonWidth(width, 2);
            int left2 = LuamodLayouts.rowLeft(width, 2, bw2);
            detailsButton = Button.builder(Component.literal("Details"), pressed -> openDetails())
                    .bounds(LuamodLayouts.buttonX(left3, 0, bw3), topRow, bw3, 20).build();
            addRenderableWidget(detailsButton);
            toggleButton = Button.builder(Component.literal("ON/OFF"), pressed -> toggleSelected())
                    .bounds(LuamodLayouts.buttonX(left3, 1, bw3), topRow, bw3, 20).build();
            addRenderableWidget(toggleButton);
            addRenderableWidget(Button.builder(Component.literal("Refresh"), pressed -> refresh())
                    .bounds(LuamodLayouts.buttonX(left3, 2, bw3), topRow, bw3, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reload"), pressed -> LuamodClientActions.requestReload())
                    .bounds(LuamodLayouts.buttonX(left2, 0, bw2), bottom, bw2, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Close"), pressed -> onClose())
                    .bounds(LuamodLayouts.buttonX(left2, 1, bw2), bottom, bw2, 20).build());
        }
        updateButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 14, 0xFFFFFF);
        if (height > MIN_HEIGHT_FOR_HINT) {
            graphics.drawCenteredString(font,
                    LuamodLayouts.trim(font, "Switches save instantly - press Reload to apply", width - 20),
                    width / 2, 26, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void refresh() {
        List<ModSummary> summaries = LuamodClientActions.refreshSummaries();
        updateIds = LuamodClientActions.updateAvailableIds();
        String keepSelected = selected() == null ? null : selected().id();
        list.refreshEntries(LuamodClientActions.filterSummaries(summaries, query));
        if (keepSelected != null) {
            for (int i = 0; i < list.children().size(); i++) {
                ModList.ModRow row = list.children().get(i);
                if (row.summary.id().equals(keepSelected)) {
                    list.setSelected(row);
                    break;
                }
            }
        }
        updateButtons();
    }

    /** Called by the detail screen after it flips a switch, so the list restates. */
    public void refreshFromChild() {
        refresh();
    }

    private @Nullable ModSummary selected() {
        ModList.ModRow row = list.getSelected();
        return row == null ? null : row.summary;
    }

    private void updateButtons() {
        ModSummary current = selected();
        detailsButton.active = current != null;
        if (current == null) {
            toggleButton.setMessage(Component.literal("ON/OFF"));
            toggleButton.active = false;
            return;
        }
        toggleButton.active = true;
        toggleButton.setMessage(Component.literal(current.enabled() ? "Turn OFF" : "Turn ON"));
    }

    private void openDetails() {
        ModSummary current = selected();
        if (current != null) {
            minecraft.setScreen(new LuamodDetailScreen(this, current.id()));
        }
    }

    private void toggleSelected() {
        ModSummary current = selected();
        if (current == null) { return; }
        LuaModManager.setModEnabled(LuamodClientActions.luamodsDir(), current.id(), !current.enabled());
        refresh();
    }

    /** Scrollable two-line rows: name + version, then a colored status line. */
    class ModList extends ObjectSelectionList<ModList.ModRow> {
        ModList(Minecraft minecraft, int width, int height, int top, int itemHeight) {
            super(minecraft, width, height, top, itemHeight);
        }

        void refreshEntries(@NotNull List<ModSummary> entries) {
            clearEntries();
            for (ModSummary summary : entries) {
                addRow(summary);
            }
        }

        private void addRow(ModSummary summary) {
            addEntry(new ModRow(summary));
        }

        @Override
        public void setSelected(@Nullable ModRow selected) {
            super.setSelected(selected);
            updateButtons();
        }

        @Override
        public int getRowWidth() {
            return Math.max(120, Math.min(480, width - 40));
        }

        class ModRow extends Entry<ModRow> {
            final ModSummary summary;

            ModRow(ModSummary summary) {
                super();
                this.summary = summary;
            }

            @Override
            public void render(@NotNull GuiGraphics graphics, int index, int top, int left,
                               int rowWidth, int rowHeight, int mouseX, int mouseY,
                               boolean hovering, float partialTick) {
                String head = summary.name() + "  v" + summary.version();
                int textWidth = rowWidth - 8;
                graphics.drawString(font, LuamodLayouts.trim(font, head, textWidth), left + 4, top + 3, 0xFFFFFFFF);
                if (LuamodListScreen.this.updateIds.contains(summary.id())) {
                    String badge = "[! update]";
                    int headWidth = Math.min(font.width(head), Math.max(0, textWidth - font.width(badge) - 6));
                    graphics.drawString(font, badge, left + 4 + headWidth + 6, top + 3, 0xFFFFAA00);
                }
                graphics.drawString(font,
                        LuamodLayouts.trim(font, summary.statusLine(), textWidth), left + 4, top + 13,
                        statusColor(summary));
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.literal(summary.id() + " " + summary.statusLine());
            }

            private int statusColor(ModSummary summary) {
                if (!summary.enabled()) { return 0xFF888888; }
                if (summary.loaded()) { return 0xFF55FF55; }
                if (summary.loadError() != null && !summary.loadError().isBlank()) { return 0xFFFF5555; }
                return 0xFFFFAA00;
            }
        }
    }
}
