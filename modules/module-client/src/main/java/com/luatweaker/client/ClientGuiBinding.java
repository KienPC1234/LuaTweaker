package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.stub.StubCatalog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Binds/merges the Roblox {@code GuiService} table, including the two table-returning
 * methods (GetScreenSize / MeasureText returns an opaque int[] otherwise) and the
 * screen-helper closures.
 */
final class ClientGuiBinding {

    private static final String GUI_SERVICE = "GuiService";
    private static final int NEXT_ARG = 1;

    private ClientGuiBinding() {}

    static void register(@NotNull ILuaEngine engine, @NotNull ILuaTable globals,
                         @Nullable IGuiService guiService, boolean hasHudRenderer,
                         @Nullable ILuaValue signalClass) {
        ILuaValue existingGui = globals.rawget(GUI_SERVICE);
        if (guiService == null) {
            reportMissing(existingGui);
            return;
        }
        ILuaTable guiTable = LuaBinder.bind(engine, GUI_SERVICE, guiService, IGuiService.class);
        StubCatalog.register(GUI_SERVICE, IGuiService.class);
        installHudSignal(engine, guiTable, hasHudRenderer, signalClass);
        installScreenSize(engine, guiTable, guiService);
        installMeasureText(engine, guiTable, guiService);
        installCreateScreen(engine, guiTable, guiService);
        installRegisterScreen(engine, guiTable, guiService);
        engine.registerService(GUI_SERVICE, guiTable);
    }

    private static void reportMissing(@Nullable ILuaValue existingGui) {
        if (existingGui != null && existingGui.isTable()) {
            // A second registration without an implementation must never replace a working
            // GuiService: that silently turned every draw call into a nil-returning no-op.
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "GuiService re-binding skipped: no IGuiService supplied, keeping the existing table");
        } else {
            // Only reachable when a caller supplies no implementation (tests, custom embedders).
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "GuiService is not bound: no IGuiService was supplied");
        }
    }

    private static void installHudSignal(@NotNull ILuaEngine engine, @NotNull ILuaTable guiTable,
                                         boolean hasHudRenderer, @Nullable ILuaValue signalClass) {
        if (!hasHudRenderer || signalClass == null || !signalClass.isTable()) {
            return;
        }
        ILuaValue newSignalFn = signalClass.asTable().rawget("new");
        if (newSignalFn != null && !newSignalFn.isNil()) {
            guiTable.rawset("OnRenderHUD", engine.callFunction(newSignalFn, signalClass));
        }
    }

    private static void installScreenSize(@NotNull ILuaEngine engine, @NotNull ILuaTable guiTable,
                                          @NotNull IGuiService guiService) {
        guiTable.rawset("GetScreenSize", args -> {
            int[] size = guiService.getScreenSize();
            return sizeTable(engine, size);
        });
        guiTable.rawset("getScreenSize", guiTable.rawget("GetScreenSize"));
    }

    private static void installMeasureText(@NotNull ILuaEngine engine, @NotNull ILuaTable guiTable,
                                           @NotNull IGuiService guiService) {
        guiTable.rawset("MeasureText", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length - off < NEXT_ARG) {
                return engine.nilValue();
            }
            return sizeTable(engine, guiService.measureText(args[off].asString()));
        });
        guiTable.rawset("measureText", guiTable.rawget("MeasureText"));
    }

    private static void installCreateScreen(@NotNull ILuaEngine engine, @NotNull ILuaTable guiTable,
                                            @NotNull IGuiService guiService) {
        guiTable.rawset("CreateScreen", args -> {
            int off = LuaBinder.getOffset(args);
            String sid = args.length > off ? args[off].asString() : "screen";
            String title = args.length > off + NEXT_ARG ? args[off + NEXT_ARG].asString() : sid;
            ILuaTable screen = engine.createTable();
            screen.rawset("Id", engine.wrapString(sid));
            screen.rawset("Title", engine.wrapString(title));
            screen.rawset("Open", screenArgs -> {
                guiService.openScreen(sid, title, false);
                return engine.nilValue();
            });
            screen.rawset("open", screen.rawget("Open"));
            screen.rawset("Close", screenArgs -> {
                guiService.closeScreen();
                return engine.nilValue();
            });
            screen.rawset("close", screen.rawget("Close"));
            guiService.registerScreen(sid, screen);
            return screen;
        });
        guiTable.rawset("createScreen", guiTable.rawget("CreateScreen"));
    }

    private static void installRegisterScreen(@NotNull ILuaEngine engine, @NotNull ILuaTable guiTable,
                                              @NotNull IGuiService guiService) {
        guiTable.rawset("RegisterScreen", args -> {
            int off = LuaBinder.getOffset(args);
            if (args.length > off + NEXT_ARG && args[off + NEXT_ARG].isTable()) {
                guiService.registerScreen(args[off].asString(), args[off + NEXT_ARG].asTable());
            }
            return engine.nilValue();
        });
        guiTable.rawset("registerScreen", guiTable.rawget("RegisterScreen"));
    }

    private static @NotNull ILuaTable sizeTable(@NotNull ILuaEngine engine, int... size) {
        ILuaTable result = engine.createTable();
        result.rawset(1, engine.wrapNumber(size[0]));
        result.rawset(2, engine.wrapNumber(size[1]));
        result.rawset("Width", engine.wrapNumber(size[0]));
        result.rawset("Height", engine.wrapNumber(size[1]));
        return result;
    }
}
