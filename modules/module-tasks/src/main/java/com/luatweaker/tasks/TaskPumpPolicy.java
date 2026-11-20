package com.luatweaker.tasks;

/**
 * Decides which game loop owns the Lua task pump.
 *
 * <p>The queues ({@code task._tick} / {@code task._java_tick}) must be pumped exactly once
 * per game tick. A process that hosts a server — dedicated or the integrated server of a
 * single-player/LAN world — already gets that tick from {@code ServerTickEvent}, and its
 * Lua code must run on the server thread. A remote client has no server loop in its
 * process, so the client tick is the only pump it has; without it {@code Task.spawn},
 * {@code Task.delay}, {@code Task.wait} and async {@code Signal:Fire} never advance.</p>
 */
public final class TaskPumpPolicy {

    private TaskPumpPolicy() {
    }

    /**
     * @param hasLocalServer true when this process hosts a server (integrated or dedicated)
     * @return true when the client tick must pump the Lua task queues
     */
    public static boolean pumpFromClientTick(boolean hasLocalServer) {
        return !hasLocalServer;
    }
}
