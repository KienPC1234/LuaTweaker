package com.luatweaker.command;

import com.luatweaker.api.vm.ILuaEngine;

import java.util.List;

/** Outcome of asking a registered Lua command for tab-completion suggestions. */
record SuggestRun(ILuaEngine engine, List<String> suggestions) {
}
