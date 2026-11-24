package com.luatweaker.common.client.download;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.client.luamods.LuamodLayouts;
import com.luatweaker.common.modsync.ClientModSync;
import com.luatweaker.core.serverpacks.ServerBundleInfo;
import com.luatweaker.core.serverpacks.ServerConsentStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Resource-pack-style prompt for a server-advertised LuaMod bundle.
 * Wraps and clamps all informational lines so long paths or warnings never
 * bleed outside narrow or scaled windows.
 */
public final class ServerPackPromptScreen extends Screen {

    private static final int BUTTON_HEIGHT = 20;
    private static final int TITLE_Y = 16;
    private static final int FIRST_LINE_Y = 34;
    private static final int LINE_STEP = 11;

    private final Screen parent;
    private final ServerBundleInfo offer;
    private final Path serverDir;

    private boolean remember;
    private Button rememberButton;

    public ServerPackPromptScreen(@Nullable Screen parent, @NotNull ServerBundleInfo offer,
                                  @NotNull Path serverDir) {
        super(Component.literal("Server Mod Pack"));
        this.parent = parent;
        this.offer = offer;
        this.serverDir = serverDir;
    }

    @Override
    protected void init() {
        int bw = LuamodLayouts.buttonWidth(width, 2);
        int left = LuamodLayouts.rowLeft(width, 2, bw);
        int bottomRowY = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET;
        int rememberRowY = bottomRowY - LuamodLayouts.BUTTON_ROW_STEP;
        int rememberWidth = bw * 2 + 6;

        rememberButton = Button.builder(Component.literal(rememberLabel()), pressed -> {
            remember = !remember;
            rememberButton.setMessage(Component.literal(rememberLabel()));
        }).bounds(left, rememberRowY, rememberWidth, BUTTON_HEIGHT).build();
        addRenderableWidget(rememberButton);

        addRenderableWidget(Button.builder(Component.literal("Download"), pressed -> downloadOnce())
                .bounds(LuamodLayouts.buttonX(left, 0, bw), bottomRowY, bw, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Skip"), pressed -> skip())
                .bounds(LuamodLayouts.buttonX(left, 1, bw), bottomRowY, bw, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int maxTextWidth = Math.max(60, width - 28);
        graphics.drawCenteredString(font,
                LuamodLayouts.trim(font, "Server offers a LuaMod pack", maxTextWidth),
                width / 2, TITLE_Y, 0xFFFFFF);

        String[] paragraphs = {
            "Pack: " + offer.label() + "  v" + offer.version(),
            "SHA-256: " + offer.sha256().substring(0, ServerBundleInfo.BUNDLE_ID_CHARS) + "...",
            "Saves to: luatweaker/servers/" + offer.bundleId() + "/luamods/" + offer.filename(),
            "Files are verified, then stored. They are NOT loaded automatically.",
            "Only download from servers you trust: Lua code can do anything a mod can.",
        };
        List<String> wrapped = new ArrayList<>();
        for (String paragraph : paragraphs) {
            wrapped.addAll(LuamodLayouts.wrapLines(font, paragraph, maxTextWidth, 2));
        }

        int bottomLimit = height - LuamodLayouts.FOOTER_BOTTOM_OFFSET - LuamodLayouts.BUTTON_ROW_STEP - 6;
        int y = FIRST_LINE_Y;
        for (String line : wrapped) {
            if (y + LINE_STEP > bottomLimit) {
                break;
            }
            graphics.drawCenteredString(font, line, width / 2, y, 0xFFAAAAAA);
            y += LINE_STEP;
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    private void downloadOnce() {
        if (remember) {
            writeConsent(ServerConsentStore.Decision.ALLOW);
        }
        ClientModSync.startDownload(offer, serverDir);
        close();
    }

    private void skip() {
        if (remember) {
            writeConsent(ServerConsentStore.Decision.DENY);
        }
        close();
    }

    private void close() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void writeConsent(ServerConsentStore.Decision decision) {
        try {
            ServerConsentStore.write(serverDir, decision, offer.label());
        } catch (IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ServerPack] Could not remember your choice: " + e.getMessage());
        }
    }

    private @NotNull String rememberLabel() {
        return remember ? "Remember: ON" : "Remember: OFF";
    }
}
