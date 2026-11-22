package com.luatweaker.common.client.luamods;

import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.mod.ModDependency;
import com.luatweaker.core.mod.ModSummary;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One mod's info card: identity, load state, dependency lists, and the
 * management actions. Disabling arms a two-step confirm inline when dependents
 * would cascade off, so no modal dialog API is needed.
 */
public class LuamodDetailScreen extends Screen {
    /** Sentinel returned when the fixed warning/action zone has been reached. */
    private static final int OVERFLOW_CURSOR = Integer.MIN_VALUE;

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

    public LuamodDetailScreen(@Nullable Screen parent, @NotNull String modId) {
        super(Component.literal("LuaMod details"));
        this.parent = parent;
        this.modId = modId;
    }

    @Override
    protected void init() {
        reloadSummary();
        boolean compact = !LuamodLayouts.fitsRow(width, 4);
        int rowBottom = height - 54;
        int rowTop = height - 80;
        int bw3 = LuamodLayouts.buttonWidth(width, 3);
        int left3 = LuamodLayouts.rowLeft(width, 3, bw3);
        if (compact) {
            // Narrow windows: three short rows instead of two wide ones.
            int rowMid = height - 106;
            int bw2 = LuamodLayouts.buttonWidth(width, 2);
            int left2 = LuamodLayouts.rowLeft(width, 2, bw2);
            enableDepsButton = Button.builder(Component.literal("Enable + deps"), pressed -> onEnableWithDeps())
                    .bounds(LuamodLayouts.buttonX(left2, 0, bw2), rowMid, bw2, 20).build();
            Button folderButton = Button.builder(Component.literal("Open Folder"), pressed -> openFolder())
                    .bounds(LuamodLayouts.buttonX(left2, 1, bw2), rowMid, bw2, 20).build();
            deleteButton = Button.builder(Component.literal("Delete"), pressed -> onDelete())
                    .bounds(LuamodLayouts.buttonX(left2, 0, bw2), rowTop, bw2, 20).build();
            toggleButton = Button.builder(Component.literal("ON/OFF"), pressed -> onToggle())
                    .bounds(LuamodLayouts.buttonX(left2, 1, bw2), rowTop, bw2, 20).build();
            int bw3b = LuamodLayouts.buttonWidth(width, 3);
            int left3b = LuamodLayouts.rowLeft(width, 3, bw3b);
            configButton = Button.builder(Component.literal("Config"), pressed -> openConfig())
                    .bounds(LuamodLayouts.buttonX(left3b, 0, bw3b), rowBottom, bw3b, 20).build();
            logButton = Button.builder(Component.literal("Logs"), pressed -> openLogs())
                    .bounds(LuamodLayouts.buttonX(left3b, 1, bw3b), rowBottom, bw3b, 20).build();
            Button backButton = Button.builder(Component.literal("Back"), pressed -> onClose())
                    .bounds(LuamodLayouts.buttonX(left3b, 2, bw3b), rowBottom, bw3b, 20).build();
            addRenderableWidget(backButton);
            addRenderableWidget(folderButton);
        } else {
            int bw4 = LuamodLayouts.buttonWidth(width, 4);
            int left4 = LuamodLayouts.rowLeft(width, 4, bw4);
            enableDepsButton = Button.builder(Component.literal("Enable + deps"), pressed -> onEnableWithDeps())
                    .bounds(LuamodLayouts.buttonX(left3, 0, bw3), rowTop, bw3, 20).build();
            Button folderButton = Button.builder(Component.literal("Open Folder"), pressed -> openFolder())
                    .bounds(LuamodLayouts.buttonX(left3, 1, bw3), rowTop, bw3, 20).build();
            deleteButton = Button.builder(Component.literal("Delete"), pressed -> onDelete())
                    .bounds(LuamodLayouts.buttonX(left3, 2, bw3), rowTop, bw3, 20).build();
            toggleButton = Button.builder(Component.literal("ON/OFF"), pressed -> onToggle())
                    .bounds(LuamodLayouts.buttonX(left4, 0, bw4), rowBottom, bw4, 20).build();
            configButton = Button.builder(Component.literal("Config"), pressed -> openConfig())
                    .bounds(LuamodLayouts.buttonX(left4, 1, bw4), rowBottom, bw4, 20).build();
            logButton = Button.builder(Component.literal("Logs"), pressed -> openLogs())
                    .bounds(LuamodLayouts.buttonX(left4, 2, bw4), rowBottom, bw4, 20).build();
            Button backButton = Button.builder(Component.literal("Back"), pressed -> onClose())
                    .bounds(LuamodLayouts.buttonX(left4, 3, bw4), rowBottom, bw4, 20).build();
            addRenderableWidget(backButton);
            addRenderableWidget(folderButton);
        }
        addRenderableWidget(enableDepsButton);
        addRenderableWidget(deleteButton);
        addRenderableWidget(toggleButton);
        addRenderableWidget(configButton);
        addRenderableWidget(logButton);
        updateButtons();
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int center = width / 2;
        int y = 30;
        graphics.drawCenteredString(font,
                LuamodLayouts.trim(font, summary == null ? modId : summary.name(), width - 20),
                center, y, 0xFFFFFF);
        y += 14;
        if (summary == null) {
            graphics.drawCenteredString(font, "(mod no longer discovered)", center, y, 0xFFFF5555);
            return;
        }
        y = drawLine(graphics, center, y, "id: " + summary.id());
        y = drawLine(graphics, center, y, "version: " + summary.version() + "   by " + summary.author());
        y = drawLine(graphics, center, y, "environment: " + summary.environment());
        y = drawLine(graphics, center, y, "state: " + summary.statusLine(),
                summary.loaded() ? 0xFF55FF55 : 0xFFFFAA00);
        String updateInfo = LuamodClientActions.updateInfoFor(modId);
        if (updateInfo != null) {
            y = drawLine(graphics, center, y, updateInfo, 0xFFFFAA00);
        }
        if (y < contentLimit()) {
            drawCappedBlock(graphics, center, y);
        }
        if (!actionStatus.isEmpty()) {
            drawLine(graphics, center, statusY(), actionStatus, 0xFF55FFFF);
        }
        if (armed) {
            drawLine(graphics, center, warnY(),
                    LuamodLayouts.trim(font,
                            "Turning OFF also disables: " + String.join(", ", cascade), width - 20),
                    0xFFFF5555);
            drawLine(graphics, center, warnY() + 12, "Press the OFF button again to confirm.", 0xFFFF5555);
        } else {
            List<String> missing = disabledRequirements();
            if (!missing.isEmpty()) {
                drawLine(graphics, center, warnY(),
                        LuamodLayouts.trim(font,
                                "Note: requires " + String.join(", ", missing) + " (OFF)", width - 20),
                        0xFFFFAA00);
                drawLine(graphics, center, warnY() + 12,
                        "Enable it too or the next load will skip this mod.", 0xFFFFAA00);
            }
        }
    }

    /** Narrow-window layout uses three button rows; zones shift up accordingly. */
    private boolean compact() {
        return !LuamodLayouts.fitsRow(width, 4);
    }

    private int contentLimit() {
        return height - (compact() ? 148 : 122);
    }

    private int statusY() {
        return height - (compact() ? 134 : 108);
    }

    private int warnY() {
        return height - (compact() ? 122 : 96);
    }

    /**
     * Error + dependency lines, cut off before the fixed warning/action zone so
     * long dep lists never draw under the buttons on short windows.
     */
    private int drawCappedBlock(GuiGraphics graphics, int center, int y) {
        int limit = contentLimit();
        int cursorY = y;
        cursorY = drawCappedLine(graphics, center, cursorY, limit,
                summary.loadError() != null && !summary.loadError().isBlank(),
                "error: " + summary.loadError(), 0xFFFF5555);
        if (cursorY == OVERFLOW_CURSOR) { return drawOverflowNote(graphics, center); }
        cursorY = drawCappedLine(graphics, center, cursorY, limit,
                !summary.requiredDeps().isEmpty(),
                "requires: " + describe(summary.requiredDeps()), 0xFFCCCCCC);
        if (cursorY == OVERFLOW_CURSOR) { return drawOverflowNote(graphics, center); }
        cursorY = drawCappedLine(graphics, center, cursorY, limit,
                !summary.optionalDeps().isEmpty(),
                "optional: " + describe(summary.optionalDeps()), 0xFFCCCCCC);
        if (cursorY == OVERFLOW_CURSOR) { return drawOverflowNote(graphics, center); }
        cursorY = drawCappedLine(graphics, center, cursorY, limit,
                !summary.platformMods().isEmpty(),
                "platform mods: " + describe(summary.platformMods()), 0xFFCCCCCC);
        if (cursorY == OVERFLOW_CURSOR) { return drawOverflowNote(graphics, center); }
        if (!summary.integrations().isEmpty()) {
            cursorY = drawIntegrations(graphics, center, cursorY, limit);
            if (cursorY == OVERFLOW_CURSOR) { return drawOverflowNote(graphics, center); }
        }
        return cursorY;
    }

    /** Draws one optional line, or returns {@link #OVERFLOW_CURSOR} when the fixed zone is reached. */
    private int drawCappedLine(GuiGraphics graphics, int center, int cursorY, int limit,
                               boolean present, String text, int color) {
        if (!present) { return cursorY; }
        if (cursorY > limit) { return OVERFLOW_CURSOR; }
        return drawLine(graphics, center, cursorY, text, color);
    }

    private int drawIntegrations(GuiGraphics graphics, int center, int cursorY, int limit) {
        List<String> ids = new ArrayList<>(summary.integrations().keySet());
        java.util.Collections.sort(ids);
        int cursor = cursorY;
        for (String integrationId : ids) {
            if (cursor > limit) { return OVERFLOW_CURSOR; }
            String integrationStatus = summary.integrations().get(integrationId);
            int color = integrationStatus != null && integrationStatus.startsWith("loaded")
                    ? 0xFF55FF55
                    : integrationStatus != null && integrationStatus.startsWith("error")
                            ? 0xFFFF5555 : 0xFFAAAAAA;
            cursor = drawLine(graphics, center, cursor,
                    "integration " + integrationId + ": " + integrationStatus, color);
        }
        return cursor;
    }

    private int drawOverflowNote(GuiGraphics graphics, int center) {
        return drawLine(graphics, center, contentLimit(), "(...more in Logs)", 0xFF888888);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private int drawLine(GuiGraphics graphics, int center, int y, String text) {
        return drawLine(graphics, center, y, text, 0xFFCCCCCC);
    }

    private int drawLine(GuiGraphics graphics, int center, int y, String text, int color) {
        graphics.drawCenteredString(font, LuamodLayouts.trim(font, text, width - 20), center, y, color);
        return y + 12;
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

    /** Required deps currently switched off: enabling alone will still skip at load. */
    private @NotNull List<String> disabledRequirements() {
        List<String> off = new ArrayList<>();
        if (summary == null) { return off; }
        java.util.Map<String, Boolean> enabledById = new java.util.concurrent.ConcurrentHashMap<>();
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
            enableDepsButton.setMessage(Component.literal(
                    missing.size() <= 3 ? "Enable + deps" : "Enable + " + missing.size()));
        }
        if (deleteButton != null) {
            deleteButton.active = known;
            deleteButton.setMessage(Component.literal(deleteArmed ? "Confirm delete" : "Delete"));
        }
    }

    private void updateToggleButton(boolean known) {
        if (!known) {
            toggleButton.active = false;
            toggleButton.setMessage(Component.literal("ON/OFF"));
            return;
        }
        toggleButton.active = true;
        toggleButton.setMessage(Component.literal(
                LuamodClientActions.toggleConfirmText(summary.enabled(), armed ? cascade : List.of())));
    }

    private void onToggle() {
        if (summary == null) { return; }
        if (summary.enabled() && cascade.size() > 1 && !armed) {
            armed = true;
            updateButtons();
            return;
        }
        LuaModManager.setModEnabled(LuamodClientActions.luamodsDir(), modId, !summary.enabled());
        afterMutation();
    }

    /** Enables the mod plus every required dep that is currently off. Additive only. */
    private void onEnableWithDeps() {
        if (summary == null || summary.enabled()) { return; }
        java.util.Map<String, Boolean> enabledById = new java.util.concurrent.ConcurrentHashMap<>();
        for (ModSummary candidate : all) {
            enabledById.put(candidate.id(), candidate.enabled());
        }
        java.io.File dir = LuamodClientActions.luamodsDir();
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
            java.io.File folder = new java.io.File(LuamodClientActions.luamodsDir(), modId);
            if (!folder.isDirectory()) {
                folder = LuamodClientActions.luamodsDir();
            }
            if (!folder.exists()) {
                folder.mkdirs();
            }
            net.minecraft.Util.getPlatform().openPath(folder.toPath().toAbsolutePath().normalize());
        } catch (SecurityException e) {
            // Vanilla already logs IO failures inside openPath; only an OS security manager
            // rejection can escape, so that is the one case worth surfacing in the UI.
            String msg = e.getMessage();
            boolean hasMsg = msg != null && !msg.isBlank() && !"null".equalsIgnoreCase(msg.trim());
            actionStatus = "Could not open folder: " + (hasMsg ? msg : e.getClass().getSimpleName());
        }
    }

    private void onDelete() {
        if (summary == null) { return; }
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
        if (parent instanceof LuamodListScreen) {
            ((LuamodListScreen) parent).refreshFromChild();
        }
        onClose();
    }

    private void afterMutation() {
        reloadSummary();
        deleteArmed = false;
        updateButtons();
        if (parent instanceof LuamodListScreen) {
            ((LuamodListScreen) parent).refreshFromChild();
        }
    }

    private void openConfig() {
        minecraft.setScreen(new LuamodConfigScreen(this, modId));
    }

    private void openLogs() {
        minecraft.setScreen(new LuamodLogScreen(this, modId));
    }
}
