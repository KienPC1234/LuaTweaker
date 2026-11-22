package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.command.MinecraftCommandSender;
import com.luatweaker.common.network.OpenGuiEditorPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import dev.architectury.networking.NetworkManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * /luatweaker gui [title] [width] [height]
 *
 * Opens the in-game interactive visual GUI Editor screen on the executing player's client.
 */
public class GuiCommand implements ILuaTweakerCommand {
    private static final String JEI_MODE_ARG = "jei";
    private static final String CONTAINER_TITLE = "custom_machine";
    private static final String JEI_TITLE = "custom_recipe_category";
    private static final int CONTAINER_WIDTH = 176;
    private static final int CONTAINER_HEIGHT = 166;
    private static final int JEI_WIDTH = 150;
    private static final int JEI_HEIGHT = 65;
    /** {@code title width height} = three trailing arguments after an optional mode tag. */
    private static final int SIZED_ARGS = 3;

    /** Parsed {@code /lt gui} invocation: title plus the editor geometry to open. */
    private record GuiRequest(@NotNull String title, int width, int height, boolean isJei) {
    }

    @Override
    public String getName() {
        return "gui";
    }

    @Override
    public String getDescription() {
        return "Open the interactive visual GUI Editor to design and export container layouts.";
    }

    @Override
    public boolean isConsoleAllowed() {
        return false;
    }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (!(sender instanceof MinecraftCommandSender neoforgeSender)) {
            sender.sendError("Only in-game players can open the GUI Editor.");
            return 0;
        }

        CommandSourceStack source = neoforgeSender.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            sender.sendError("Only in-game players can open the GUI Editor.");
            return 0;
        }

        GuiRequest request = parseArgs(args);
        String modeTag = request.isJei() ? "JEI Category Mode" : "Container Mode";
        player.sendSystemMessage(Component.literal("§a[LuaTweaker] §fOpening visual GUI Editor (§6" + modeTag
                + "§f: §e" + request.title() + "§f, §b" + request.width() + "x" + request.height() + "§f)..."));
        NetworkManager.sendToPlayer(player,
                new OpenGuiEditorPayload(request.title(), request.width(), request.height(), request.isJei()));
        return 1;
    }

    private static @NotNull GuiRequest parseArgs(@Nullable String... args) {
        if (args == null || args.length == 0) {
            return new GuiRequest(CONTAINER_TITLE, CONTAINER_WIDTH, CONTAINER_HEIGHT, false);
        }
        if (JEI_MODE_ARG.equalsIgnoreCase(args[0])) {
            return parseJeiArgs(args);
        }
        return parseContainerArgs(args);
    }

    private static @NotNull GuiRequest parseJeiArgs(@NotNull String... args) {
        int titleIndex = 1;
        String title = args.length > titleIndex ? args[titleIndex] : JEI_TITLE;
        if (args.length < titleIndex + SIZED_ARGS) {
            return new GuiRequest(title, JEI_WIDTH, JEI_HEIGHT, true);
        }
        return new GuiRequest(title,
                parseIntOr(args[titleIndex + 1], JEI_WIDTH),
                parseIntOr(args[titleIndex + 2], JEI_HEIGHT),
                true);
    }

    private static @NotNull GuiRequest parseContainerArgs(@NotNull String... args) {
        if (args.length < SIZED_ARGS) {
            return new GuiRequest(args[0], CONTAINER_WIDTH, CONTAINER_HEIGHT, false);
        }
        return new GuiRequest(args[0],
                parseIntOr(args[1], CONTAINER_WIDTH),
                parseIntOr(args[2], CONTAINER_HEIGHT),
                false);
    }

    /**
     * Parses one geometry argument, keeping {@code fallback} for a non-numeric value so a typo
     * degrades to the default size instead of failing the command. The rejected value is logged.
     */
    private static int parseIntOr(@NotNull String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            LuaTweakerLog.get().debug(LogStage.COMMAND,
                    "Ignoring non-numeric GUI size '" + raw + "', using " + fallback + ": " + e.getMessage());
            return fallback;
        }
    }
}
