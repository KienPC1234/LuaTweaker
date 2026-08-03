package com.luatweaker.api.command;

import java.util.Set;

/**
 * Names reserved by the built-in LuaTweaker command tree and by Minecraft.
 *
 * <p>Lua mods may not register commands under these names:</p>
 * <ul>
 *   <li>{@code lt} / {@code luatweaker} - the built-in command roots.</li>
 *   <li>{@code reload}, {@code hand}, {@code syntax}, {@code list},
 *       {@code debug}, {@code doctor}, {@code help} - built-in {@code /lt}
 *       sub-commands, most of which also exist as vanilla root commands
 *       ({@code /reload}, {@code /list}, {@code /debug}, {@code /help}).</li>
 * </ul>
 *
 * <p>Any attempt to shadow them from a script is rejected loudly by
 * {@link ICommandService#register}.</p>
 */
public final class CoreCommandNames {

    /** Command roots and names owned by LuaTweaker or by vanilla Minecraft. */
    public static final Set<String> RESERVED = Set.of(
            "lt", "luatweaker",
            "reload", "hand", "syntax", "list", "debug", "doctor", "help"
    );

    private CoreCommandNames() {}
}
