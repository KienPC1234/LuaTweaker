package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies BFS hierarchy specificity in {@link LuaResultConverter} when both a supertype
 * and a more specific sub-interface have registered return converters, including inside lists.
 */
class LuaResultConverterTest {

    @FunctionalInterface
    interface BaseEntityStub {
        String getName();
    }

    interface SpecificPlayerStub extends BaseEntityStub {
        int getLevel();
    }

    private static class BaseEntityImpl implements BaseEntityStub {
        private final String name;

        BaseEntityImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }

    private static final class SpecificPlayerImpl extends BaseEntityImpl implements SpecificPlayerStub {
        private final int level;

        SpecificPlayerImpl(String name, int level) {
            super(name);
            this.level = level;
        }

        @Override
        public int getLevel() {
            return level;
        }
    }

    public interface RosterApi {
        List<SpecificPlayerStub> getPlayers();

        List<BaseEntityStub> getEntities();
    }

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void listReturnPrefersSpecificInterfaceConverterOverSuperInterface() {
        engine = LuaEngineFactory.create(false);
        LuaBinder.registerReturnConverter(BaseEntityStub.class, (eng, value) -> {
            ILuaTable table = eng.createTable();
            table.rawset("Kind", eng.wrapString("base"));
            table.rawset("Name", eng.wrapString(((BaseEntityStub) value).getName()));
            return table;
        });
        LuaBinder.registerReturnConverter(SpecificPlayerStub.class, (eng, value) -> {
            SpecificPlayerStub player = (SpecificPlayerStub) value;
            ILuaTable table = eng.createTable();
            table.rawset("Kind", eng.wrapString("player"));
            table.rawset("Name", eng.wrapString(player.getName()));
            table.rawset("Level", eng.wrapNumber(player.getLevel()));
            return table;
        });

        RosterApi api = new RosterApi() {
            @Override
            public List<SpecificPlayerStub> getPlayers() {
                return List.of(new SpecificPlayerImpl("Alice", 12));
            }

            @Override
            public List<BaseEntityStub> getEntities() {
                return List.of(new BaseEntityImpl("Zombie"), new SpecificPlayerImpl("Bob", 25));
            }
        };
        LuaBinder.bind(engine, "Roster", api, RosterApi.class);

        engine.executeStringOrThrow("""
                local players = Roster:GetPlayers()
                assert(#players == 1, "expected 1 player")
                pKind = players[1].Kind
                pName = players[1].Name
                pLevel = players[1].Level

                local entities = Roster:GetEntities()
                assert(#entities == 2, "expected 2 entities")
                e1Kind = entities[1].Kind
                e2Kind = entities[2].Kind
                e2Level = entities[2].Level
                """, "converter_bfs_test");

        ILuaValue pKind = engine.getGlobalEnvironment().rawget("pKind");
        assertAll("BFS return converter hierarchy",
                () -> assertEquals("player", pKind.asString(), "IPlayer sub-interface converter must beat super-interface"),
                () -> assertEquals("Alice", engine.getGlobalEnvironment().rawget("pName").asString(), "player name preserved"),
                () -> assertEquals(12, engine.getGlobalEnvironment().rawget("pLevel").asInt(), "player level preserved"),
                () -> assertEquals("base", engine.getGlobalEnvironment().rawget("e1Kind").asString(), "base impl uses base converter"),
                () -> assertEquals("player", engine.getGlobalEnvironment().rawget("e2Kind").asString(), "mixed list picks specific converter"),
                () -> assertEquals(25, engine.getGlobalEnvironment().rawget("e2Level").asInt(), "specific property preserved in mixed list"));
    }
}
