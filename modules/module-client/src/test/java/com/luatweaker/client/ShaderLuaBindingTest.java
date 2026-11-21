package com.luatweaker.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end {@code Shaders} binding: a script drives effects, passes and uniforms, and the call
 * must neither record an error nor skip publishing the registered pass.
 */
class ShaderLuaBindingTest extends ShaderTestFixture {

    /** Runs a script that touches every Shaders entry point, asserting the Lua-visible contract. */
    private void runShadersScript() {
        ShaderLuaBinding.registerBindings(engine, service);
        engine.executeStringOrThrow("""
                assert(Shaders ~= nil, 'Shaders global must exist')
                assert(Shaders:getBackend() == 'unknown', 'headless backend must be unknown')
                assert(Shaders:supportsCustomPasses() == false, 'headless must not support passes')
                Shaders:setEffect('letterbox', { height = 0.2 })
                Shaders:clearEffect('letterbox')
                local ok, err = pcall(function() Shaders:setEffect('bloom', {}) end)
                assert(not ok, 'unknown effect must fail')
                assert(string.find(err, 'kick', 1, true), 'error must list effects, got: '
                    .. tostring(err))
                Shaders:registerGlslPass('warp',
                    'void main() { fragColor = texture(DiffuseSampler, texCoord); }')
                Shaders:activatePass('warp')
                assert(Shaders:getActivePass() == 'warp', 'active pass must round-trip')
                Shaders:setPassUniform('Glow', 0.5)
                Shaders:setPassUniformVec('Tint', { 1.0, 0.5, 0.0 })
                local okVec, errVec = pcall(function()
                    Shaders:setPassUniformVec('Tint', { 1.0 })
                end)
                assert(not okVec, 'vec arity must fail: ' .. tostring(errVec))
                Shaders:setEffect('grain', { strength = 0.5 })
                assert(Shaders:getActivePass() == 'builtin_grain', 'builtin must activate')
                Shaders:clearEffect('grain')
                assert(Shaders:getActivePass() == nil, 'clearing builtin must deactivate')
                local okSpirv = pcall(function() Shaders:registerSpirvPass('x', '!!!') end)
                assert(not okSpirv, 'bad base64 must fail')
                local okGhost, errGhost = pcall(function() Shaders:activatePass('ghost') end)
                assert(not okGhost, 'unknown pass must fail')
                assert(string.find(errGhost, 'registerGlslPass', 1, true),
                    'error must point at registration')
                Shaders:deactivatePass()
                assert(Shaders:getActivePass() == nil, 'deactivate must clear')
                Shaders:clearAll()
                """, "ShaderBindingTest");
    }

    @Test
    void luaBindingRunIsErrorFree() {
        runShadersScript();
        assertNull(engine.getAndClearLastExecutionError(),
                "driving every Shaders entry point from Lua must not record an execution error");
    }

    @Test
    void luaBindingPublishesTheRegisteredPass() {
        runShadersScript();
        assertTrue(chainJson("warp") != null,
                "registerGlslPass('warp', ...) must publish the post-processing chain for Lua");
    }
}
