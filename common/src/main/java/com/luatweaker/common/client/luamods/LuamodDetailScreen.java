package com.luatweaker.common.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModDependency;
import com.luatweaker.core.mod.ModSummary;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One mod's info card: identity, load state, dependency lists, and management
 * actions inside a scissor-clipped scrollable body viewport so content never
 * overlaps warning banners or action buttons on short/scaled windows.
 */
public class LuamodDetailScreen extends Screen {
    private static final int LINE_HEIGHT = 12;
    private static final int HEADER_TOP = 8;
    private static final int BODY_TOP = 24;
    private static final double SCROLL_PIXELS_PER_NOTCH = 16.0;
    private static final double SCROLL_EPSILON = 1.0e-6;

    private record StyledLine(String text, int color) {
    }

    private final Screen parent;
    private final String modId;

    private ModSummary summary;
    private List<String> cascade = List.of();
    private List<ModSummary> all = List.of();
    private Button toggleButton;
    private Button configButton;
    private Button logButton;
    private Button enableDepsButton;
    private Button deleteButton;
    private boolean armed;
    private boolean deleteArmed;
    private String actionStatus = "";
    private double scrollOffset;

    public LuamodDetailScreen(@Nullable Screen parent, @NotNull String modId) {
        super(Component.literal("LuaMod details"));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        reloadSummary();
        boolean isCompact = compact();
        int rowBottom = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        int rowTop = rowBottom - LuamodLayouts.BUTTON_ROW_STEP;
        if (isCompact) {
            int rowMid = rowTop - LuamodLayouts.BUTTON_ROW_STEP;
            int bw2 = LuamodLayouts.buttonWidth(width, 2);
            int left2 = LuamodLayouts.rowLeft(width, 2, bw2);
            enableDepsButton = buildButton("Enable + deps", LuamodLayouts.buttonX(left2, 0, bw2), rowMid, bw2, this::onEnableWithDeps);
            Button folderButton = buildButton("Open Folder", LuamodLayouts.buttonX(left2, 1, bw2), rowMid, bw2, this::openFolder);
            deleteButton = buildButton("Delete", LuamodLayouts.buttonX(left2, 0, bw2), rowTop, bw2, this::onDelete);
            toggleButton = buildButton("ON/OFF", LuamodLayouts.buttonX(left2, 1, bw2), rowTop, bw2, this::onToggle);
            int bw3b = LuamodLayouts.buttonWidth(width, 3);
            int left3b = LuamodLayouts.rowLeft(width, 3, bw3b);
            configButton = buildButton("Config", LuamodLayouts.buttonX(left3b, 0, bw3b), rowBottom, bw3b, this::openConfig);
            logButton = buildButton("Logs", LuamodLayouts.buttonX(left3b, 1, bw3b), rowBottom, bw3b, this::openLogs);
            Button backButton = buildButton("Back", LuamodLayouts.buttonX(left3b, 2, bw3b), rowBottom, bw3b, this::onClose);
            addRenderableWidget(folderButton);
            addRenderableWidget(backButton);
        } else {
            int bw3 = LuamodLayouts.buttonWidth(width, 3);
            int left3 = LuamodLayouts.rowLeft(width, 3, bw3);
            int bw4 = LuamodLayouts.buttonWidth(width, 4);
            int left4 = LuamodLayouts.rowLeft(width, 4, bw4);
            enableDepsButton = buildButton("Enable + deps", LuamodLayouts.buttonX(left3, 0, bw3), rowTop, bw3, this::onEnableWithDeps);
            Button folderButton = buildButton("Open Folder", LuamodLayouts.buttonX(left3, 1, bw3), rowTop, bw3, this::openFolder);
            deleteButton = buildButton("Delete", LuamodLayouts.buttonX(left3, 2, bw3), rowTop, bw3, this::onDelete);
            toggleButton = buildButton("ON/OFF", LuamodLayouts.buttonX(left4, 0, bw4), rowBottom, bw4, this::onToggle);
            configButton = buildButton("Config", LuamodLayouts.buttonX(left4, 1, bw4), rowBottom, bw4, this::openConfig);
            logButton = buildButton("Logs", LuamodLayouts.buttonX(left4, 2, bw4), rowBottom, bw4, this::openLogs);
            Button backButton = buildButton("Back", LuamodLayouts.buttonX(left4, 3, bw4), rowBottom, bw4, this::onClose);
            addRenderableWidget(folderButton);
            addRenderableWidget(backButton);
        }
        addRenderableWidget(enableDepsButton);
        addRenderableWidget(deleteButton);
        addRenderableWidget(toggleButton);
        addRenderableWidget(configButton);
        addRenderableWidget(logButton);
        updateButtons();
    }

    private Button buildButton(String label, int x, int y, int bw, Runnable onClick) {
        String trimmed = LuamodLayouts.trim(font, label, Math.max(20, bw - 8));
        return Button.builder(Component.literal(trimmed), pressed -> onClick.run())
                .bounds(x, y, bw, 20)
                .build();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        String headerTitle = summary == null ? modId : summary.name();
        graphics.drawCenteredString(font,
                LuamodLayouts.trim(font, headerTitle, Math.max(40, width - 32)),
                center, HEADER_TOP, 0xFFFFFF);

        List<StyledLine> footerNotices = buildNoticeLines(Math.max(40, width - 28));
        int buttonsTop = buttonsTopY();
        int noticeAreaHeight = footerNotices.isEmpty() ? 0 : footerNotices.size() * LINE_HEIGHT + 4;
        int bodyBottom = Math.max(BODY_TOP + 20, buttonsTop - noticeAreaHeight - 4);

        renderScrollableBody(graphics, center, BODY_TOP, bodyBottom);
        renderNotices(graphics, center, buttonsTop - noticeAreaHeight, footerNotices);
    }

    private void renderScrollableBody(GuiGraphics graphics, int center, int top, int bottom) {
        int viewportH = Math.max(12, bottom - top);
        int maxTextWidth = Math.max(40, Math.min(460, width - 36));
        List<StyledLine> lines = buildBodyLines(maxTextWidth);
        int contentH = lines.size() * LINE_HEIGHT + 4;
        int maxScroll = Math.max(0, contentH - viewportH);
        scrollOffset = Math.clamp(scrollOffset, 0.0, maxScroll);
        int scrollPx = (int) Math.round(scrollOffset);

        int panelHalf = maxTextWidth / 2 + 8;
        int panelLeft = Math.max(8, center - panelHalf);
        int panelRight = Math.min(width - 8, center + panelHalf);
        graphics.fill(panelLeft, top, panelRight, bottom, 0x55000000);
        graphics.hLine(panelLeft, panelRight - 1, top, 0x66FFFFFF);
        graphics.hLine(panelLeft, panelRight - 1, bottom - 1, 0x66444444);

        graphics.enableScissor(panelLeft + 2, top + 2, panelRight - 2, bottom - 2);
        try {
            int drawY = top + 4 - scrollPx;
            for (StyledLine line : lines) {
                if (drawY + LINE_HEIGHT >= top && drawY <= bottom) {
                    graphics.drawCenteredString(font, line.text(), center, drawY, line.color());
                }
                drawY += LINE_HEIGHT;
            }
        } finally {
            graphics.disableScissor();
        }

        if (maxScroll > 0) {
            int trackX = panelRight - 5;
            graphics.fill(trackX, top + 2, trackX + 3, bottom - 2, 0x66222222);
            int trackH = Math.max(8, bottom - top - 4);
            int thumbH = Math.clamp(trackH * viewportH / Math.max(1, contentH), 10, trackH);
            int thumbY = top + 2 + (int) Math.round((trackH - thumbH) * (scrollOffset / maxScroll));
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xCCBBBBBB);
        }
    }

    private List<StyledLine> buildBodyLines(int maxWidth) {
        List<StyledLine> lines = new ArrayList<>();
        if (summary == null) {
            addWrapped(lines, "(mod no longer discovered)", 0xFFFF5555, maxWidth, 2);
            return lines;
        }
        addWrapped(lines, "id: " + summary.id() + "   v" + summary.version() + "   by " + summary.author(),
                0xFFCCCCCC, maxWidth, 2);
        addWrapped(lines, "environment: " + summary.environment(), 0xFFCCCCCC, maxWidth, 1);
        addWrapped(lines, "state: " + summary.statusLine(),
                summary.loaded() ? 0xFF55FF55 : 0xFFFFAA00, maxWidth, 2);
        String updateInfo = LuamodClientActions.updateInfoFor(modId);
        if (updateInfo != null) {
            addWrapped(lines, updateInfo, 0xFFFFAA00, maxWidth, 2);
        }
        appendDetailMetadata(lines, maxWidth);
        return lines;
    }

    private void appendDetailMetadata(List<StyledLine> lines, int maxWidth) {
        if (summary.loadError() != null && !summary.loadError().isBlank()) {
            addWrapped(lines, "error: " + summary.loadError(), 0xFFFF5555, maxWidth, 4);
        }
        if (!summary.requiredDeps().isEmpty()) {
            addWrapped(lines, "requires: " + describe(summary.requiredDeps()), 0xFFCCCCCC, maxWidth, 3);
        }
        if (!summary.optionalDeps().isEmpty()) {
            addWrapped(lines, "optional: " + describe(summary.optionalDeps()), 0xFFCCCCCC, maxWidth, 3);
        }
        if (!summary.platformMods().isEmpty()) {
            addWrapped(lines, "platform mods: " + describe(summary.platformMods()), 0xFFCCCCCC, maxWidth, 3);
        }
        appendIntegrationLines(lines, maxWidth);
    }

    private void appendIntegrationLines(List<StyledLine> lines, int maxWidth) {
        if (summary.integrations().isEmpty()) {
            return;
        }
        List<String> ids = new ArrayList<>(summary.integrations().keySet());
        Collections.sort(ids);
        for (String integrationId : ids) {
            String st = summary.integrations().get(integrationId);
            int col = integrationColor(st);
            addWrapped(lines, "integration " + integrationId + ": " + st, col, maxWidth, 2);
        }
    }

    private static int integrationColor(@Nullable String state) {
        if (state != null && state.startsWith("loaded")) {
            return 0xFF55FF55;
        }
        if (state != null && state.startsWith("error")) {
            return 0xFFFF5555;
        }
        return 0xFFAAAAAA;
    }

    private List<StyledLine> buildNoticeLines(int maxWidth) {
        List<StyledLine> notices = new ArrayList<>();
        if (!actionStatus.isEmpty()) {
            addWrapped(notices, actionStatus, 0xFF55FFFF, maxWidth, 1);
        }
        if (armed) {
            addWrapped(notices, "Turning OFF also disables: " + String.join(", ", cascade)
                    + " (press again to confirm)", 0xFFFF5555, maxWidth, 2);
            return notices;
        }
        List<String> missing = disabledRequirements();
        if (!missing.isEmpty()) {
            addWrapped(notices, "Requires OFF dep(s): " + String.join(", ", missing), 0xFFFFAA00, maxWidth, 1);
        }
        return notices;
    }

    private void addWrapped(List<StyledLine> out, String text, int color, int maxWidth, int maxLines) {
        for (String part : LuamodLayouts.wrapLines(font, text, maxWidth, maxLines)) {
            addStyledLine(out, part, color);
        }
    }

    private static void addStyledLine(List<StyledLine> out, String part, int color) {
        out.add(new StyledLine(part, color));
    }

    private void renderNotices(GuiGraphics graphics, int center, int startY, List<StyledLine> notices) {
        int y = startY;
        for (StyledLine notice : notices) {
            graphics.drawCenteredString(font, notice.text(), center, y, notice.color());
            y += LINE_HEIGHT;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (Math.abs(scrollY) > SCROLL_EPSILON) {
            scrollOffset = Math.max(0.0, scrollOffset - scrollY * SCROLL_PIXELS_PER_NOTCH);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean compact() {
        return !LuamodLayouts.fitsRow(width, 4);
    }

    private int buttonsTopY() {
        int rowBottom = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        return compact()
                ? rowBottom - LuamodLayouts.BUTTON_ROW_STEP * 2
                : rowBottom - LuamodLayouts.BUTTON_ROW_STEP;
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private static String describe(List<ModDependency> deps) {
        List<String> parts = new ArrayList<>();
        for (ModDependency dep : deps) {
            parts.add(dep.describeRequirement());
        }
        return String.join(", ", parts);
    }

    private void reloadSummary() {
        all = LuamodClientActions.refreshSummaries();
        summary = all.stream().filter(candidate -> candidate.id().equals(modId)).findFirst().orElse(null);
        armed = false;
        cascade = summary != null && summary.enabled()
                ? LuaModManager.previewDisableCascade(LuamodClientActions.luamodsDir(), modId)
                : List.of();
    }

    private @NotNull List<String> disabledRequirements() {
        List<String> off = new ArrayList<>();
        if (summary == null) {
            return off;
        }
        Map<String, Boolean> enabledById = new ConcurrentHashMap<>();
        for (ModSummary candidate : all) {
            enabledById.put(candidate.id(), candidate.enabled());
        }
        for (ModDependency dep : summary.requiredDeps()) {
            if (!Boolean.TRUE.equals(enabledById.get(dep.id()))) {
                off.add(dep.describeRequirement());
            }
        }
        return off;
    }

    private void updateButtons() {
        boolean known = summary != null;
        updateInfoButtons(known);
        updateToggleButton(known);
    }

    private void updateInfoButtons(boolean known) {
        if (configButton != null) {
            configButton.active = known;
        }
        if (logButton != null) {
            logButton.active = known;
        }
        if (enableDepsButton != null) {
            List<String> missing = disabledRequirements();
            enableDepsButton.active = known && !summary.enabled() && !missing.isEmpty();
            String depLabel = missing.size() <= 3 ? "Enable + deps" : "Enable + " + missing.size();
            enableDepsButton.setMessage(Component.literal(
                    LuamodLayouts.trim(font, depLabel, Math.max(20, enableDepsButton.getWidth() - 8))));
        }
        if (deleteButton != null) {
            deleteButton.active = known;
            String delLabel = deleteArmed ? "Confirm del" : "Delete";
            deleteButton.setMessage(Component.literal(
                    LuamodLayouts.trim(font, delLabel, Math.max(20, deleteButton.getWidth() - 8))));
        }
    }

    private void updateToggleButton(boolean known) {
        if (!known) {
            toggleButton.active = false;
            toggleButton.setMessage(Component.literal("ON/OFF"));
            return;
        }
        toggleButton.active = true;
        String raw = LuamodClientActions.toggleConfirmText(summary.enabled(), armed ? cascade : List.of());
        toggleButton.setMessage(Component.literal(
                LuamodLayouts.trim(font, raw, Math.max(20, toggleButton.getWidth() - 8))));
    }

    private void onToggle() {
        if (summary == null) {
            return;
        }
        if (summary.enabled() && cascade.size() > 1 && !armed) {
            armed = true;
            updateButtons();
            return;
        }
        LuaModManager.setModEnabled(LuamodClientActions.luamodsDir(), modId, !summary.enabled());
        afterMutation();
    }

    private void onEnableWithDeps() {
        if (summary == null || summary.enabled()) {
            return;
        }
        Map<String, Boolean> enabledById = new ConcurrentHashMap<>();
        for (ModSummary candidate : all) {
            enabledById.put(candidate.id(), candidate.enabled());
        }
        File dir = LuamodClientActions.luamodsDir();
        List<String> enabledNow = new ArrayList<>();
        for (ModDependency dep : summary.requiredDeps()) {
            if (!Boolean.TRUE.equals(enabledById.get(dep.id()))) {
                enabledNow.addAll(LuaModManager.setModEnabled(dir, dep.id(), true));
            }
        }
        enabledNow.addAll(LuaModManager.setModEnabled(dir, modId, true));
        actionStatus = enabledNow.isEmpty()
                ? "Nothing changed."
                : "Enabled " + String.join(", ", enabledNow) + " - Reload to apply.";
        afterMutation();
    }

    private void openFolder() {
        try {
            File folder = new File(LuamodClientActions.luamodsDir(), modId);
            if (!folder.isDirectory()) {
                folder = LuamodClientActions.luamodsDir();
            }
            if (!folder.exists()) {
                folder.mkdirs();
            }
            Util.getPlatform().openPath(folder.toPath().toAbsolutePath().normalize());
        } catch (SecurityException e) {
            String msg = e.getMessage();
            boolean hasMsg = msg != null && !msg.isBlank() && !"null".equalsIgnoreCase(msg.trim());
            actionStatus = "Could not open folder: " + (hasMsg ? msg : e.getClass().getSimpleName());
        }
    }

    private void onDelete() {
        if (summary == null) {
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            updateButtons();
            return;
        }
        String error = LuaModManager.deleteMod(LuamodClientActions.luamodsDir(), modId);
        if (error != null) {
            actionStatus = error;
            deleteArmed = false;
            updateButtons();
            return;
        }
        if (parent instanceof LuamodListScreen listScreen) {
            listScreen.refreshFromChild();
        }
        onClose();
    }

    private void afterMutation() {
        reloadSummary();
        deleteArmed = false;
        updateButtons();
        if (parent instanceof LuamodListScreen listScreen) {
            listScreen.refreshFromChild();
        }
    }

    private void openConfig() {
        minecraft.setScreen(new LuamodConfigScreen(this, modId));
    }

    private void openLogs() {
        minecraft.setScreen(new LuamodLogScreen(this, modId));
    }
}
