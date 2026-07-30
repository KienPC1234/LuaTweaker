package com.luatweaker.api.command;

/**
 * A single LuaTweaker sub-command that can be registered on any platform.
 *
 * <h3>Extension guide</h3>
 * <ol>
 *   <li>Create a new class in {@code neoforge-platform} (or any platform module)
 *       that implements {@code ILuaTweakerCommand}.</li>
 *   <li>Register it via {@code LuaTweakerCommandRegistry.register(myCmd)} inside
 *       a {@code RegisterCommandsEvent} handler.</li>
 *   <li>The command will be reachable as
 *       {@code /luatweaker <getName()> [args…]}.</li>
 * </ol>
 */
public interface ILuaTweakerCommand {

    /**
     * The sub-command name (no spaces, lower-case).
     * Used as the Brigadier literal node: {@code /luatweaker <name>}.
     */
    String getName();

    /**
     * Short one-line description shown in {@code /luatweaker help}.
     */
    String getDescription();

    /**
     * Required operator permission level (0 = all, 2 = op, 4 = server owner).
     * Default: 2 (operator).
     */
    default int getPermissionLevel() { return 2; }

    /**
     * Whether this command can be run from the server console.
     * Default: true.
     */
    default boolean isConsoleAllowed() { return true; }

    /**
     * Execute the command.
     *
     * @param sender  The command sender (player or console).
     * @param args    Remaining arguments after the sub-command literal.
     * @return        Brigadier result code (1 = success, 0 = failure).
     */
    int execute(ICommandSender sender, String[] args);
}
