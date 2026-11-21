package com.luatweaker.text;

import com.luatweaker.api.text.ITextService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/** Binds the text builder as the {@code Text} service and global. */
public final class TextLuaBinding {

    private TextLuaBinding() {
    }

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull ITextService service) {
        LuaBinder.bind(engine, "Text", service, ITextService.class, "text");
    }
}
