package com.luatweaker.client;

import com.luatweaker.api.log.ILuaTweakerLog;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.client.tween.TweenImpl;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TweenFailureLoggingTest {

    /** Test double that records WARNING entries only; all other levels are intentionally ignored. */
    private static final class CapturingLog implements ILuaTweakerLog {
        final List<String> warnings = new ArrayList<>();

        @Override
        public void info(LogStage stage, String message) {
        }

        @Override
        public void warn(LogStage stage, String message) {
            warnings.add(stage + ": " + message);
        }

        @Override
        public void error(LogStage stage, String message) {
        }

        @Override
        public void debug(LogStage stage, String message) {
        }

        @Override
        public void script(LogStage stage, String message, String file, int line) {
        }

        @Override
        public void recipe(LogStage stage, String recipeId, String outputItem, String details) {
        }

        @Override
        public void section(String title) {
        }
    }

    private ILuaTweakerLog previousLog;
    private CapturingLog capture;

    @BeforeEach
    public void installCapturingLog() {
        previousLog = LuaTweakerLog.get();
        capture = new CapturingLog();
        LuaTweakerLog.set(capture);
    }

    @AfterEach
    public void restoreLog() {
        if (previousLog != null) {
            LuaTweakerLog.set(previousLog);
        }
    }

    @Test
    public void invalidEasingEnumsAreLoggedAndFallBackToDefaults() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable instance = engine.createTable();
        instance.rawset("Value", engine.wrapNumber(0.0));

        ILuaTable tweenInfo = engine.createTable();
        tweenInfo.rawset("Duration", engine.wrapNumber(1.0));
        tweenInfo.rawset("EasingStyle", engine.wrapString("NO_SUCH_STYLE"));
        tweenInfo.rawset("EasingDirection", engine.wrapString("NO_SUCH_DIRECTION"));

        ILuaTable properties = engine.createTable();
        properties.rawset("Value", engine.wrapNumber(10.0));

        TweenImpl tween = new TweenImpl(engine, instance, tweenInfo, properties);
        tween.play();
        tween.tick(0.5);

        assertTrue(capture.warnings.stream().anyMatch(w -> w.contains("EasingStyle")
                        && w.contains("NO_SUCH_STYLE")),
                "invalid EasingStyle must be logged, got: " + capture.warnings);
        assertTrue(capture.warnings.stream().anyMatch(w -> w.contains("EasingDirection")
                        && w.contains("NO_SUCH_DIRECTION")),
                "invalid EasingDirection must be logged, got: " + capture.warnings);
        assertEquals(5.0, instance.rawget("Value").asDouble(), 1e-9,
                "invalid enums must fall back to LINEAR/OUT interpolation");
    }
}
