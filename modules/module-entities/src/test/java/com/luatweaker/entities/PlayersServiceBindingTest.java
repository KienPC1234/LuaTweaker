package com.luatweaker.entities;

import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.pal.IPlatformEntity;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.server.IServerService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@code Players:GetPlayer(nameOrUuid)} and {@code IPlayer} return conversion
 * when bound methods such as {@code Server:GetPlayers()} return {@code List<IPlayer>}.
 */
class PlayersServiceBindingTest {

    private static final String UUID_ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String UUID_BOB = "22222222-2222-2222-2222-222222222222";
    private static final int ALICE_FOOD = 17;
    private static final int BOB_FOOD = 12;

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void lookupResolvesByUuidAndCaseInsensitiveName() {
        IPlayer alice = mockPlayer(UUID_ALICE, "Alice", ALICE_FOOD);
        IPlayer bob = mockPlayer(UUID_BOB, "BobBuilder", BOB_FOOD);
        Platform.setEntity(stubEntityPlatform(List.of(alice, bob)));

        engine = LuaEngineFactory.create(false);
        EntitiesLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow("""
                local byUuid = Players:GetPlayer('11111111-1111-1111-1111-111111111111')
                assert(byUuid ~= nil, 'lookup by UUID must succeed')
                assert(byUuid:GetName() == 'Alice', 'UUID lookup must return Alice')
                assert(byUuid:GetFoodLevel() == 17, 'player methods must be bound')

                local byCase = Players:getPlayer('bobbuilder')
                assert(byCase ~= nil, 'case-insensitive name lookup must succeed')
                caseUuid = byCase:GetUuid()
                """, "players_lookup_test");

        assertEquals(UUID_BOB, engine.getGlobalEnvironment().rawget("caseUuid").asString(),
                "case-insensitive name lookup must return Bob");
    }

    @Test
    void lookupReturnsNilForMissingBlankOrNilInput() {
        Platform.setEntity(stubEntityPlatform(List.of()));

        engine = LuaEngineFactory.create(false);
        EntitiesLuaBinding.registerBindings(engine);

        engine.executeStringOrThrow("""
                allNil = (Players:GetPlayer('UnknownPlayer') == nil)
                    and (Players:GetPlayer('   ') == nil)
                    and (Players:GetPlayer(nil) == nil)
                """, "players_missing_test");

        assertTrue(engine.getGlobalEnvironment().rawget("allNil").asBoolean(),
                "missing, blank, and nil identifiers must all return nil");
    }

    @Test
    void serverGetPlayersConvertsListOfIPlayerToPlayerTables() {
        IPlayer alice = mockPlayer(UUID_ALICE, "Alice", ALICE_FOOD);
        Platform.setEntity(stubEntityPlatform(List.of(alice)));

        engine = LuaEngineFactory.create(false);
        EntitiesLuaBinding.registerBindings(engine);
        LuaBinder.bind(engine, "Server", stubServerService(List.of(alice)), IServerService.class);

        engine.executeStringOrThrow("""
                local list = Server:GetPlayers()
                assert(#list == 1, 'expected one player')
                local p = list[1]
                assert(type(p) == 'table', 'IPlayer list element must convert to Lua table')
                assert(p:GetName() == 'Alice', 'player name must match')
                assert(type(p.actionText) == 'function', 'player table aliases must be applied')
                playerFood = p:GetFoodLevel()
                """, "server_players_converter_test");

        assertEquals(ALICE_FOOD, engine.getGlobalEnvironment().rawget("playerFood").asInt(),
                "IPlayer methods must win over IEntity");
    }

    private static IPlayer mockPlayer(String uuid, String name, int food) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (IPlayer) Proxy.newProxyInstance(
                cl,
                new Class<?>[]{IPlayer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getUuid" -> uuid;
                    case "getName" -> name;
                    case "getFoodLevel" -> food;
                    case "isAlive" -> true;
                    case "equals" -> args[0] != null && ("MockPlayer(" + uuid + ")").equals(args[0].toString());
                    case "hashCode" -> uuid.hashCode();
                    case "toString" -> "MockPlayer(" + uuid + ")";
                    default -> defaultReturn(method.getReturnType());
                });
    }

    private static IPlatformEntity stubEntityPlatform(List<IPlayer> players) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (IPlatformEntity) Proxy.newProxyInstance(
                cl,
                new Class<?>[]{IPlatformEntity.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayer" -> {
                        String id = String.valueOf(args[0]);
                        yield players.stream().filter(p -> p.getUuid().equalsIgnoreCase(id)).findFirst().orElse(null);
                    }
                    case "getAllPlayers" -> players;
                    case "listGoals" -> List.of();
                    default -> defaultReturn(method.getReturnType());
                });
    }

    private static Object defaultReturn(Class<?> type) {
        if (type == boolean.class) {
            return false;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == double.class) {
            return 0.0;
        }
        if (type == float.class) {
            return 0.0f;
        }
        if (type == String.class) {
            return "";
        }
        return null;
    }

    private static IServerService stubServerService(List<IPlayer> players) {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return (IServerService) Proxy.newProxyInstance(
                cl,
                new Class<?>[]{IServerService.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlayers" -> players;
                    case "getPlayerCount" -> players.size();
                    case "getMaxPlayers" -> 20;
                    case "getTps" -> 20.0;
                    default -> defaultReturn(method.getReturnType());
                });
    }
}
