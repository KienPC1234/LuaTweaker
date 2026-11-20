package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ShaderServiceImplTest {

    private static class FakeDatapack implements IDatapackService {
        final Map<String, String> files = new HashMap<>();

        @Override public void addJsonRecipe(String id, String json) { files.put("recipe:" + id, json); }
        @Override public void addLootTable(String path, String json) { files.put("loot:" + path, json); }
        @Override public void addAdvancement(String path, String json) { files.put("adv:" + path, json); }
        @Override public void addFunction(String path, String commands) { files.put("fn:" + path, commands); }
        @Override public void addData(String relPath, String json) { files.put(relPath, json); }
        @Override public void addTag(String type, String id, List<String> values) { files.put("tag:" + id, id); }
        @Override public Map<String, String> getVirtualFiles() { return files; }
        @Override public void clear() { files.clear(); }
    }

    private static final String BODY =
            "void main() {\n"
                    + "    vec4 base = texture(DiffuseSampler, texCoord);\n"
                    + "    float pulse = sin(Time * 3.0) * 0.5 + 0.5;\n"
                    + "    fragColor = vec4(base.rgb + pulse * 0.2, base.a);\n"
                    + "}\n";

    private ShaderServiceImpl service(FakeDatapack datapack, File cacheDir) {
        return new ShaderServiceImpl(datapack, cacheDir);
    }

    @Test
    public void unknownEffectNamesFailLoudly(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        IllegalArgumentException setError = assertThrows(IllegalArgumentException.class,
                () -> service.setEffect("bloom", null));
        assertTrue(setError.getMessage().contains("kick"), "error must list supported names: " + setError.getMessage());
        IllegalArgumentException clearError = assertThrows(IllegalArgumentException.class,
                () -> service.clearEffect("bloom"));
        assertTrue(clearError.getMessage().contains("kick"));
    }

    @Test
    public void defaultsApplyWithoutParams(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        service.setEffect("letterbox", null);
        assertEquals(0.12, ShaderServiceImpl.getLetterboxHeight(), 1e-9);
        service.clearEffect("letterbox");
        assertEquals(0.0, ShaderServiceImpl.getLetterboxHeight(), 1e-9);
    }

    @Test
    public void outOfRangeParamsClamp(@TempDir File cacheDir) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable params = engine.createTable();
        params.rawset("height", 99.0);
        service(new FakeDatapack(), cacheDir).setEffect("letterbox", params);
        assertEquals(0.4, ShaderServiceImpl.getLetterboxHeight(), 1e-9);
        service(new FakeDatapack(), cacheDir).clearEffect("letterbox");
    }

    @Test
    public void nanParamsAreRejected(@TempDir File cacheDir) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable params = engine.createTable();
        params.rawset("height", Double.NaN);
        assertThrows(IllegalArgumentException.class,
                () -> service(new FakeDatapack(), cacheDir).setEffect("letterbox", params));
    }

    @Test
    public void badColorsAreRejected(@TempDir File cacheDir) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable params = engine.createTable();
        params.rawset("strength", 0.5);
        params.rawset("color", "not-a-color");
        assertThrows(IllegalArgumentException.class,
                () -> service(new FakeDatapack(), cacheDir).setEffect("tint", params));

        ILuaTable shortHex = engine.createTable();
        shortHex.rawset("color", "#FFF");
        assertThrows(IllegalArgumentException.class,
                () -> service(new FakeDatapack(), cacheDir).setEffect("tint", shortHex));

        ILuaTable good = engine.createTable();
        good.rawset("strength", 0.5);
        good.rawset("color", "#FF0000");
        service(new FakeDatapack(), cacheDir).setEffect("tint", good);
        assertEquals(0.5, ShaderServiceImpl.getTintStrength(), 1e-9);
        assertEquals(0xFFFF0000, ShaderServiceImpl.getTintArgb());
        service(new FakeDatapack(), cacheDir).clearEffect("tint");
    }

    @Test
    public void cameraPulsesExpire(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        service.setEffect("kick", null);
        long now = System.currentTimeMillis();
        assertNotNull(ShaderServiceImpl.getCameraDelta(now), "fresh kick must produce offsets");
        assertNull(ShaderServiceImpl.getCameraDelta(now + 60_000L), "expired kick must go idle");
        service.clearAll();
    }

    @Test
    public void registerValidationNamesTheProblem(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        assertThrows(IllegalArgumentException.class, () -> service.registerGlslPass("UPPER", BODY));
        assertThrows(IllegalArgumentException.class, () -> service.registerGlslPass("has space", BODY));
        assertThrows(IllegalArgumentException.class, () -> service.registerGlslPass("", BODY));
        assertThrows(IllegalArgumentException.class, () -> service.registerGlslPass("ok", "   "));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass("ok", "fragColor = vec4(1.0);"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass("ok", "#version 150\nvoid main() {}"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass("ok", "uniform sampler2D DiffuseSampler;\nvoid main() {}"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass("ok", "in vec2 texCoord;\nvoid main() {}"));
        assertThrows(IllegalArgumentException.class,
                () -> service.registerGlslPass("ok", "out vec4 fragColor;\nvoid main() {}"));
        String huge = "void main() {}\n" + "x".repeat(20000);
        assertThrows(IllegalArgumentException.class, () -> service.registerGlslPass("ok", huge));
    }

    @Test
    public void registerPublishesThreeVirtualFiles(@TempDir File cacheDir) {
        FakeDatapack datapack = new FakeDatapack();
        service(datapack, cacheDir).registerGlslPass("warp", BODY);

        String chain = datapack.files.get("assets/luatweaker/shaders/post/lt_warp.json");
        String program = datapack.files.get("assets/luatweaker/shaders/program/lt_warp.json");
        String fragment = datapack.files.get("assets/luatweaker/shaders/program/lt_warp.fsh");
        assertNotNull(chain, "chain JSON missing: " + datapack.files.keySet());
        assertNotNull(program, "program JSON missing");
        assertNotNull(fragment, "fragment source missing");

        assertTrue(chain.contains("\"name\": \"luatweaker:lt_warp\""), "chain must run our program");
        assertTrue(chain.contains("\"intarget\": \"minecraft:main\""), "chain must read the main target");
        assertTrue(chain.contains("\"name\": \"minecraft:blit\""), "chain must blit back to main");
        assertTrue(program.contains("\"vertex\": \"minecraft:blit\""), "program must reuse the vanilla blit vertex");
        assertTrue(program.contains("\"fragment\": \"luatweaker:lt_warp\""), "program must point at our fragment");
        assertTrue(program.contains("\"DiffuseSampler\""), "program must declare the input sampler");
        assertTrue(fragment.contains("#version 150"), "engine must inject the version header");
        assertTrue(fragment.contains("uniform sampler2D DiffuseSampler;"), "engine must declare the sampler");
        assertTrue(fragment.contains("uniform float Time;"), "engine must declare Time");
        assertTrue(fragment.contains("in vec2 texCoord;"), "engine must declare texCoord");
        assertTrue(fragment.contains("out vec4 fragColor;"), "engine must declare fragColor");
        assertTrue(fragment.contains("sin(Time * 3.0)"), "user body must survive verbatim");
    }

    @Test
    public void registerWritesShaCache(@TempDir File cacheDir) throws Exception {
        FakeDatapack datapack = new FakeDatapack();
        service(datapack, cacheDir).registerGlslPass("warp", BODY);
        File[] markers = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(markers);
        assertEquals(1, markers.length, "exactly one cache marker expected");
        String marker = Files.readString(markers[0].toPath(), StandardCharsets.UTF_8);
        assertTrue(marker.contains("\"id\": \"warp\""), "marker must record the id: " + marker);

        // Re-registering identical source is a cache hit: same marker, same files, no error.
        service(datapack, cacheDir).registerGlslPass("warp", BODY);
        File[] markersAfter = cacheDir.listFiles((dir, name) -> name.endsWith(".json"));
        assertNotNull(markersAfter);
        assertEquals(1, markersAfter.length, "cache hit must not duplicate markers");
        assertEquals(3, datapack.files.size(), "three virtual files expected");
    }

    @Test
    public void activateDeactivateAndUniforms(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class,
                () -> service.activatePass("ghost"));
        assertTrue(unknown.getMessage().contains("registerGlslPass"), "error must point at registration");

        service.registerGlslPass("warp", BODY);
        service.deactivatePass();
        IllegalArgumentException noActive = assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniform("Glow", 1.0));
        assertTrue(noActive.getMessage().contains("activatePass"));

        service.activatePass("warp");
        assertEquals("warp", service.getActivePass());

        assertThrows(IllegalArgumentException.class, () -> service.setPassUniform("Glow", Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> service.setPassUniform("  ", 1.0));
        // Headless (no Platform client): records without throwing.
        service.setPassUniform("Glow", 0.75);

        service.deactivatePass();
        assertNull(service.getActivePass());
        service.clearAll();
    }

    @Test
    public void clearAllResetsEverything(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        service.setEffect("letterbox", null);
        service.setEffect("vignette", null);
        service.registerGlslPass("warp", BODY);
        service.activatePass("warp");
        service.clearAll();
        assertEquals(0.0, ShaderServiceImpl.getLetterboxHeight(), 1e-9);
        assertEquals(0.0, ShaderServiceImpl.getVignetteStrength(), 1e-9);
        assertNull(service.getActivePass());
    }

    @Test
    public void builtinEffectsRegisterAndActivate(@TempDir File cacheDir) {
        FakeDatapack datapack = new FakeDatapack();
        ShaderServiceImpl service = service(datapack, cacheDir);
        service.setEffect("grain", null);
        assertEquals("builtin_grain", service.getActivePass());
        String fragment = datapack.files.get("assets/luatweaker/shaders/program/lt_builtin_grain.fsh");
        assertNotNull(fragment, "builtin fragment must be published");
        assertTrue(fragment.contains("hash("), "grain body must survive verbatim");
        assertTrue(fragment.contains("#version 150"), "engine header must be injected");

        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable params = engine.createTable();
        params.rawset("brightness", 0.2);
        params.rawset("contrast", 1.5);
        params.rawset("saturation", 0.5);
        service.setEffect("grade", params);
        assertEquals("builtin_grade", service.getActivePass());

        service.clearEffect("grade");
        assertNull(service.getActivePass(), "clearing the active builtin must deactivate");
        service.clearAll();
    }

    @Test
    public void builtinNamesAreListed(@TempDir File cacheDir) {
        assertTrue(ShaderServiceImpl.SUPPORTED_EFFECTS.contains("grain"));
        assertTrue(ShaderServiceImpl.SUPPORTED_EFFECTS.contains("wobble"));
        assertTrue(ShaderServiceImpl.SUPPORTED_EFFECTS.contains("kick"));
        assertEquals(24, ShaderServiceImpl.SUPPORTED_EFFECTS.size(),
                "11 direct + 13 builtins expected, got: " + ShaderServiceImpl.SUPPORTED_EFFECTS);
    }

    @Test
    public void vecUniformValidation(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        assertThrows(IllegalArgumentException.class, () -> service.setPassUniformVec("Tint", new float[]{1.0f}));
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec("Tint", new float[]{1.0f, 2.0f, 3.0f, 4.0f, 5.0f}));
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec("Tint", new float[]{1.0f, Float.NaN, 0.0f}));
        assertThrows(IllegalArgumentException.class,
                () -> service.setPassUniformVec("Tint", new float[]{1.0f, 0.5f}));
        service.registerGlslPass("vec", BODY);
        service.activatePass("vec");
        service.setPassUniformVec("Tint", new float[]{1.0f, 0.5f, 0.0f});
        service.deactivatePass();
        service.clearAll();
    }

    private static byte[] minimalSpirv() {
        int[] words = {
                0x07230203, 0x00010000, 0x00000000, 0x00000005, 0x00000000,
                0x00020011, 0x00000001,
                0x0003000E, 0x00000000, 0x00000001,
                0x0005000F, 0x00000004, 0x00000003, 0x6E69616D, 0x00000000,
                0x00030010, 0x00000003, 0x00000007,
                0x00020013, 0x00000001,
                0x00030021, 0x00000002, 0x00000001,
                0x00050036, 0x00000001, 0x00000003, 0x00000000, 0x00000002,
                0x000200F8, 0x00000004,
                0x000100FD,
                0x00010038,
        };
        java.nio.ByteBuffer buffer =
                java.nio.ByteBuffer.allocate(words.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.asIntBuffer().put(words);
        return buffer.array();
    }

    @Test
    public void spirvValidationRejectsGarbage(@TempDir File cacheDir) {
        ShaderServiceImpl service = service(new FakeDatapack(), cacheDir);
        assertThrows(IllegalArgumentException.class, () -> service.registerSpirvPass("s1", "!!!not-base64!!!"));
        String notSpirv = java.util.Base64.getEncoder().encodeToString("hello world, not spirv!!".getBytes());
        IllegalArgumentException badMagic = assertThrows(IllegalArgumentException.class,
                () -> service.registerSpirvPass("s1", notSpirv));
        assertTrue(badMagic.getMessage().contains("magic"), "must name the cause: " + badMagic.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.registerSpirvPass("BAD ID", notSpirv));
    }

    @Test
    public void spirvTranslatesAndPublishes(@TempDir File cacheDir) {
        FakeDatapack datapack = new FakeDatapack();
        ShaderServiceImpl service = service(datapack, cacheDir);
        String base64 = java.util.Base64.getEncoder().encodeToString(minimalSpirv());
        service.registerSpirvPass("tiny", base64);
        String fragment = datapack.files.get("assets/luatweaker/shaders/program/lt_tiny.fsh");
        assertNotNull(fragment, "translated fragment must be published");
        assertTrue(fragment.contains("void main"), "translated GLSL must define main: " + fragment);
        assertTrue(fragment.contains("#version"), "spvc output carries its own version header");
        service.activatePass("tiny");
        assertEquals("tiny", service.getActivePass());
        service.deactivatePass();
        service.clearAll();
    }

    @Test
    public void luaBindingEndToEnd() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        FakeDatapack datapack = new FakeDatapack();
        File cacheDir = new File(System.getProperty("java.io.tmpdir"), "lt-shader-test-cache");
        ShaderServiceImpl service = new ShaderServiceImpl(datapack, cacheDir);
        ShaderLuaBinding.registerBindings(engine, service);

        engine.executeString(
                "assert(Shaders ~= nil, 'Shaders global must exist')\n"
                        + "assert(Shaders:getBackend() == 'unknown', 'headless backend must be unknown')\n"
                        + "assert(Shaders:supportsCustomPasses() == false, 'headless must not support passes')\n"
                        + "Shaders:setEffect('letterbox', { height = 0.2 })\n"
                        + "Shaders:clearEffect('letterbox')\n"
                        + "local ok, err = pcall(function() Shaders:setEffect('bloom', {}) end)\n"
                        + "assert(not ok, 'unknown effect must fail')\n"
                        + "assert(string.find(err, 'kick', 1, true), 'error must list effects, got: ' .. tostring(err))\n"
                        + "Shaders:registerGlslPass('warp', 'void main() { fragColor = texture(DiffuseSampler, texCoord); }')\n"
                        + "Shaders:activatePass('warp')\n"
                        + "assert(Shaders:getActivePass() == 'warp', 'active pass must round-trip')\n"
                        + "Shaders:setPassUniform('Glow', 0.5)\n"
                        + "Shaders:setPassUniformVec('Tint', { 1.0, 0.5, 0.0 })\n"
                        + "local ok3, err3 = pcall(function() Shaders:setPassUniformVec('Tint', { 1.0 }) end)\n"
                        + "assert(not ok3, 'vec arity must fail')\n"
                        + "Shaders:setEffect('grain', { strength = 0.5 })\n"
                        + "assert(Shaders:getActivePass() == 'builtin_grain', 'builtin must activate')\n"
                        + "Shaders:clearEffect('grain')\n"
                        + "assert(Shaders:getActivePass() == nil, 'clearing builtin must deactivate')\n"
                        + "local ok4, err4 = pcall(function() Shaders:registerSpirvPass('x', '!!!') end)\n"
                        + "assert(not ok4, 'bad base64 must fail')\n"
                        + "local ok2, err2 = pcall(function() Shaders:activatePass('ghost') end)\n"
                        + "assert(not ok2, 'unknown pass must fail')\n"
                        + "assert(string.find(err2, 'registerGlslPass', 1, true), 'error must point at registration')\n"
                        + "Shaders:deactivatePass()\n"
                        + "assert(Shaders:getActivePass() == nil, 'deactivate must clear')\n"
                        + "Shaders:clearAll()\n",
                "ShaderBindingTest");
        assertNull(engine.getAndClearLastExecutionError(),
                "Lua binding run must be error-free");
        assertTrue(datapack.files.containsKey("assets/luatweaker/shaders/post/lt_warp.json"));
    }
}
