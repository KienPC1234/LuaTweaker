package com.luatweaker.tasks;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.CobaltLuaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TaskServiceImplTest {

    private ILuaEngine engine;
    private TaskServiceImpl taskService;

    @BeforeEach
    public void setUp() {
        engine = new CobaltLuaEngine(true);
        taskService = new TaskServiceImpl();
        TaskLuaBinding.registerBindings(engine, taskService);
    }

    @Test
    public void testTaskServiceBindings() {
        ILuaTable global = engine.getGlobalEnvironment();
        ILuaTable taskTable = global.rawget("task").asTable();

        assertNotNull(taskTable, "Global task table should be registered");
        assertNotNull(taskTable.rawget("spawn"), "task.spawn should exist");
        assertNotNull(taskTable.rawget("delay"), "task.delay should exist");
        assertNotNull(taskTable.rawget("wait"), "task.wait should exist");
    }

    @Test
    public void testTaskSpawnAndWaitScriptExecution() {
        assertDoesNotThrow(() -> {
            engine.executeString("task.spawn(function() print('Task spawned successfully') end)", "TaskTest");
        });
    }
}
