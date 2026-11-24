package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;

/**
 * Shortcut for {@code /lt dimension tp}: {@code /lt tp <dimensionId> [playerName]}.
 *
 * <p>Delegates to {@link DimensionCommand} so the teleport, validation and
 * error paths have a single implementation.</p>
 */
public class TeleportCommand implements ILuaTweakerCommand {
    private static final String SUBCOMMAND = "tp";

    @Override
    public String getName() {
        return SUBCOMMAND;
    }

    @Override
    public String getDescription() {
        return "Teleport to a Lua-registered dimension (console needs a player name).";
    }

    @Override
    public boolean isConsoleAllowed() {
        return true;
    }

    @Override
    public int execute(ICommandSender sender, String... args) {
        String[] forwarded = new String[args.length + 1];
        forwarded[0] = SUBCOMMAND;
        System.arraycopy(args, 0, forwarded, 1, args.length);
        return new DimensionCommand().execute(sender, forwarded);
    }
}
