package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;

/** Outcome of driving a registered Lua command through {@link LuaCommandWrapper}. */
record CommandRun(ILuaEngine engine, MockCommandSender sender, int result) {
}
