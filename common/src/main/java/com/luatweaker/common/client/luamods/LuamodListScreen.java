package com.luatweaker.common.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModSummary;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * In-game LuaMod manager: every discovered mod with its on/off state and last
 * load result. Responsive across GUI scales 1..4 without overlapping rows or
 * overflowing screen borders.
 */
public class LuamodListScreen extends Screen {
    private static final int MIN_HEIGHT_FOR_HINT = 180;
    private static final String UPDATE_BADGE = "[! update]";

    private final Screen parent;

    private ModList list;
    private Set<String> updateIds = Set.of();
    private String query = "";
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

        boolean showHint = height >= MIN_HEIGHT_FOR_HINT;
        int searchY = showHint ? 34 : 22;
        int searchWidth = Math.min(400, Math.max(100, width - 32));
        EditBox searchBox = new EditBox(font,
                width / 2 - searchWidth / 2, searchY, searchWidth, 18, Component.literal("Search mods"));
        searchBox.setValue(query);
        searchBox.setHint(Component.literal("Search id or name..."));
        searchBox.setResponder(value -> {
            query = value;
            refresh();
        });
        addRenderableWidget(searchBox);

        boolean singleFooterRow = LuamodLayouts.fitsRow(width, 5);
        int bottomRowY = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        int footerTopY = singleFooterRow ? bottomRowY : bottomRowY - LuamodLayouts.BUTTON_ROW_STEP;
        int listTop = searchY + 22;
        int listHeight = Math.max(32, footerTopY - listTop - 6);

        list = new ModList(minecraft, width, listHeight, listTop, 26);
        list.updateSizeAndPosition(width, listHeight, listTop);
        list.setX(0);
        list.refreshEntries(LuamodClientActions.filterSummaries(summaries, query));
        addRenderableWidget(list);

        buildFooterButtons(singleFooterRow, footerTopY, bottomRowY);
        updateButtons();
    }

    private void buildFooterButtons(boolean singleRow, int topRowY, int bottomRowY) {
        if (singleRow) {
            int bw = LuamodLayouts.buttonWidth(width, 5);
            int left = LuamodLayouts.rowLeft(width, 5, bw);
            detailsButton = addActionButton("Details", LuamodLayouts.buttonX(left, 0, bw), bottomRowY, bw, this::openDetails);
            toggleButton = addActionButton("ON/OFF", LuamodLayouts.buttonX(left, 1, bw), bottomRowY, bw, this::toggleSelected);
            addActionButton("Refresh", LuamodLayouts.buttonX(left, 2, bw), bottomRowY, bw, this::refresh);
            addActionButton("Reload", LuamodLayouts.buttonX(left, 3, bw), bottomRowY, bw, LuamodClientActions::requestReload);
            addActionButton("Close", LuamodLayouts.buttonX(left, 4, bw), bottomRowY, bw, this::onClose);
            return;
        }
        int bw3 = LuamodLayouts.buttonWidth(width, 3);
        int left3 = LuamodLayouts.rowLeft(width, 3, bw3);
        int bw2 = LuamodLayouts.buttonWidth(width, 2);
        int left2 = LuamodLayouts.rowLeft(width, 2, bw2);
        detailsButton = addActionButton("Details", LuamodLayouts.buttonX(left3, 0, bw3), topRowY, bw3, this::openDetails);
        toggleButton = addActionButton("ON/OFF", LuamodLayouts.buttonX(left3, 1, bw3), topRowY, bw3, this::toggleSelected);
        addActionButton("Refresh", LuamodLayouts.buttonX(left3, 2, bw3), topRowY, bw3, this::refresh);
        addActionButton("Reload", LuamodLayouts.buttonX(left2, 0, bw2), bottomRowY, bw2, LuamodClientActions::requestReload);
        addActionButton("Close", LuamodLayouts.buttonX(left2, 1, bw2), bottomRowY, bw2, this::onClose);
    }

    private Button addActionButton(String text, int x, int y, int bw, Runnable action) {
        String fit = LuamodLayouts.trim(font, text, Math.max(20, bw - 8));
        Button btn = Button.builder(Component.literal(fit), pressed -> action.run())
                .bounds(x, y, bw, 20)
                .build();
        addRenderableWidget(btn);
        return btn;
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, title.getString(), width - 60),
                width / 2, 8, 0xFFFFFF);
        if (height >= MIN_HEIGHT_FOR_HINT) {
            graphics.drawCenteredString(font,
                    LuamodLayouts.trim(font, "Switches save instantly - press Reload to apply", width - 24),
                    width / 2, 20, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    public void refreshFromChild() {
        refresh();
    }

    private void refresh() {
        List<ModSummary> summaries = LuamodClientActions.refreshSummaries();
        updateIds = LuamodClientActions.updateAvailableIds();
        ModList.ModRow currentRow = list.getSelected();
        String keepSelected = currentRow == null ? null : currentRow.summary.id();
        list.refreshEntries(LuamodClientActions.filterSummaries(summaries, query));
        if (keepSelected != null) {
            for (ModList.ModRow row : list.children()) {
                if (row.summary.id().equals(keepSelected)) {
                    list.setSelected(row);
                    break;
                }
            }
        }
        updateButtons();
    }

    private void updateButtons() {
        ModList.ModRow row = list.getSelected();
        ModSummary current = row == null ? null : row.summary;
        detailsButton.active = current != null;
        if (current == null) {
            toggleButton.setMessage(Component.literal("ON/OFF"));
            toggleButton.active = false;
            return;
        }
        toggleButton.active = true;
        String label = current.enabled() ? "Turn OFF" : "Turn ON";
        toggleButton.setMessage(Component.literal(
                LuamodLayouts.trim(font, label, Math.max(24, toggleButton.getWidth() - 8))));
    }

    private void openDetails() {
        ModList.ModRow row = list.getSelected();
        if (row != null) {
            minecraft.setScreen(new LuamodDetailScreen(this, row.summary.id()));
        }
    }

    private void toggleSelected() {
        ModList.ModRow row = list.getSelected();
        if (row == null) {
            return;
        }
        LuaModManager.setModEnabled(LuamodClientActions.luamodsDir(), row.summary.id(), !row.summary.enabled());
        refresh();
    }

    /** Scrollable two-line rows: name + version (+ optional right-aligned update badge), then status. */
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
            return Math.max(100, Math.min(460, width - 36));
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
                int innerWidth = Math.max(20, rowWidth - 10);
                boolean hasUpdate = LuamodListScreen.this.updateIds.contains(summary.id());
                int badgeWidth = hasUpdate ? font.width(UPDATE_BADGE) : 0;
                int headMaxWidth = hasUpdate ? Math.max(20, innerWidth - badgeWidth - 8) : innerWidth;

                graphics.drawString(font, LuamodLayouts.trim(font, head, headMaxWidth),
                        left + 5, top + 3, 0xFFFFFFFF);
                if (hasUpdate && innerWidth > badgeWidth + 24) {
                    graphics.drawString(font, UPDATE_BADGE,
                            left + rowWidth - badgeWidth - 5, top + 3, 0xFFFFAA00);
                }
                graphics.drawString(font,
                        LuamodLayouts.trim(font, summary.statusLine(), innerWidth),
                        left + 5, top + 13, statusColor(summary));
            }

            @Override
            public @NotNull Component getNarration() {
                return Component.literal(summary.id() + " " + summary.statusLine());
            }

            private int statusColor(ModSummary summary) {
                if (!summary.enabled()) {
                    return 0xFF888888;
                }
                if (summary.loaded()) {
                    return 0xFF55FF55;
                }
                if (summary.loadError() != null && !summary.loadError().isBlank()) {
                    return 0xFFFF5555;
                }
                return 0xFFFFAA00;
            }
        }
    }
}
