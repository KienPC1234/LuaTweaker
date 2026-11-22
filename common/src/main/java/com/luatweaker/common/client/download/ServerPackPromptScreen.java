package com.luatweaker.common.client.download;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
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

/**
 * Resource-pack-style prompt for a server-advertised LuaMod bundle.
 *
 * <p>Download saves a verified zip under {@code luatweaker/servers/<id>/luamods/}
 * and nothing more: the pack is never loaded or executed by accepting. Closing
 * the screen (buttons or ESC) without remembering keeps consent at ASK; the
 * Remember toggle persists ALLOW on Download or DENY on Skip.</p>
 */
public final class ServerPackPromptScreen extends Screen {

    private static final int BUTTON_WIDTH = 100;
    private static final int BUTTON_HEIGHT = 20;
    private static final int TITLE_Y = 40;
    private static final int FIRST_LINE_Y = 62;
    private static final int LINE_STEP = 12;
    private static final int BOTTOM_MARGIN = 60;
    private static final int REMEMBER_Y_OFFSET = 24;

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
        int centerX = width / 2;
        rememberButton = Button.builder(Component.literal(rememberLabel()), pressed -> {
            remember = !remember;
            rememberButton.setMessage(Component.literal(rememberLabel()));
        }).bounds(centerX - BUTTON_WIDTH, height - BOTTOM_MARGIN - REMEMBER_Y_OFFSET,
                BUTTON_WIDTH * 2, BUTTON_HEIGHT).build();
        addRenderableWidget(rememberButton);
        addRenderableWidget(Button.builder(Component.literal("Download"), pressed -> downloadOnce())
                .bounds(centerX - BUTTON_WIDTH - 4, height - BOTTOM_MARGIN,
                        BUTTON_WIDTH, BUTTON_HEIGHT).build());
        addRenderableWidget(Button.builder(Component.literal("Skip"), pressed -> skip())
                .bounds(centerX + 4, height - BOTTOM_MARGIN, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Server offers a LuaMod pack", width / 2, TITLE_Y, 0xFFFFFF);
        String[] lines = {
            "Pack: " + offer.label() + "  v" + offer.version(),
            "SHA-256: " + offer.sha256().substring(0, ServerBundleInfo.BUNDLE_ID_CHARS) + "...",
            "Saves to: luatweaker/servers/" + offer.bundleId() + "/luamods/" + offer.filename(),
            "Files are verified, then stored. They are NOT loaded automatically.",
            "Only download from servers you trust: Lua code can do anything a mod can.",
        };
        for (int i = 0; i < lines.length; i++) {
            graphics.drawCenteredString(font, lines[i], width / 2, FIRST_LINE_Y + i * LINE_STEP,
                    0xFFAAAAAA);
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
        com.luatweaker.common.modsync.ClientModSync.startDownload(offer, serverDir);
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
