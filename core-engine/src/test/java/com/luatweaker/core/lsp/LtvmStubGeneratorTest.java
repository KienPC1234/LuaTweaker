package com.luatweaker.core.lsp;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the stub generator emits typed classes with interface inheritance
 * for the dynamic entity/player Lua tables (Player: Entity), so IDE autocomplete
 * works for inherited methods such as player:getHealth().
 */
public class LtvmStubGeneratorTest {

    @Test
    public void playerClassInheritsEntityAndListsMethods() {
        LtvmStubGenerator generator = new LtvmStubGenerator();
        generator.registerClassStub(IEntity.class, "Entity");
        generator.registerClassStub(IPlayer.class, "Player");

        String stubs = generator.getResult();

        assertTrue(stubs.contains("---@class Player: Entity"),
                "Player stub must inherit Entity:\n" + stubs);
        assertTrue(stubs.contains("---@class Entity"),
                "Entity stub must exist:\n" + stubs);
        assertTrue(stubs.contains("function Player:sendMessage(message) end"),
                "Player stub must include sendMessage:\n" + stubs);
        assertTrue(stubs.contains("function Entity:getHealth() end"),
                "Entity stub must include getHealth (inherited by Player via class hierarchy):\n" + stubs);
    }
}
