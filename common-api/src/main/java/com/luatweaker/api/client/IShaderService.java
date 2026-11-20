package com.luatweaker.api.client;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Service for screen-space visual effects.
 *
 * <p>Two tiers, one stable Lua surface across render backends:
 * <ol>
 *   <li><b>Named effects</b> ({@code setEffect}) — camera and overlay presets with typed
 *       params. They are backend-agnostic and keep working if the renderer migrates.</li>
 *   <li><b>Custom GLSL passes</b> ({@code registerGlslPass} + {@code activatePass}) —
 *       a fragment-shader body in the engine's P1 subset. The engine injects the
 *       {@code #version} header and standard uniforms, publishes vanilla post-chain
 *       files through the virtual pack, and activates them via the vanilla
 *       {@code GameRenderer} post pipeline. Dynamic per-frame values go through
 *       {@code setPassUniform}; time-based animation needs no Lua updates because
 *       the pipeline sets {@code Time} every frame.</li>
 * </ol>
 */
@LuaDoc(description = "Service for screen-space visual effects: named camera/overlay effects plus custom GLSL post passes.")
public interface IShaderService {
    @LuaDoc(
        description = "Renderer backend behind this client: 'opengl' on 1.21.1, 'vulkan' once the renderer migrates, 'unknown' on a dedicated server or before the client exists.",
        returnType = "string"
    )
    @NotNull String getBackend();

    @LuaDoc(
        description = "Whether custom GLSL passes can be activated here. False on dedicated servers (no client to render on).",
        returnType = "boolean"
    )
    boolean supportsCustomPasses();

    @LuaDoc(
        description = "Enables a named effect. Unknown names fail loudly with the supported list. Omitted params fall back to defaults; NaN/infinite numbers are rejected. Camera/overlay: kick, roll, sway, fovPunch, fadeTo, letterbox, vignette, tint, lowHealthPulse, frost, bloodSplatter. Shader-backed: grade, sepia, noir, temperature, nightVision, grain, blur, radialBlur, chromaticAberration, pixelate, glitch, scanlines, wobble.",
        params = {"name: string — effect name", "params: table — e.g. { strength = 0.7, seconds = 0.5, color = '#FF0000' }"},
        returnType = "void"
    )
    void setEffect(@NotNull String name, @Nullable ILuaTable params);

    @LuaDoc(
        description = "Disables one named effect. Unknown names fail loudly with the supported list.",
        params = {"name: string — effect name"},
        returnType = "void"
    )
    void clearEffect(@NotNull String name);

    @LuaDoc(
        description = "Disables every named effect and deactivates the custom pass.",
        returnType = "void"
    )
    void clearAll();

    @LuaDoc(
        description = "Registers a custom fragment-shader pass (P1 subset: body only, must contain 'void main', must NOT contain '#version'/'sampler2D'/'fragColor' declarations — the engine injects the header plus DiffuseSampler/Time/InSize/OutSize/ScreenSize). Publishes chain+program+fragment files to the virtual pack and writes a SHA-256 cache entry under luatweaker/shader-cache. Re-registering an id replaces it (reload-safe).",
        params = {"id: string — [a-z0-9_], 1-48 chars", "fragmentSrc: string — GLSL body, max 16KB"},
        returnType = "void"
    )
    void registerGlslPass(@NotNull String id, @NotNull String fragmentSrc);

    @LuaDoc(
        description = "Activates a registered pass through the vanilla post pipeline (replaces any active pass). Unknown ids fail loudly. Re-call after F3+T resource reloads.",
        params = {"id: string — registered pass id"},
        returnType = "void"
    )
    void activatePass(@NotNull String id);

    @LuaDoc(
        description = "Deactivates the custom pass, if any.",
        returnType = "void"
    )
    void deactivatePass();

    @LuaDoc(
        description = "Active custom pass id, or nil when none.",
        returnType = "string|nil"
    )
    @Nullable String getActivePass();

    @LuaDoc(
        description = "Sets one single-float uniform on the active pass (matches the chain API; vec uniforms go through setPassUniformVec). Fails loudly with no active pass. Dynamic values should be set every tick; Time needs no updates.",
        params = {"name: string — uniform name", "value: number"},
        returnType = "void"
    )
    void setPassUniform(@NotNull String name, double value);

    @LuaDoc(
        description = "Sets one vec2/vec3/vec4 uniform on the active pass (2-4 numbers, e.g. a tint color). Requires the uniform declared at that size in your fragment. Fails loudly with no active pass, wrong arity, or non-finite values.",
        params = {"name: string — uniform name", "values: table — e.g. { 1.0, 0.5, 0.0 }"},
        returnType = "void"
    )
    void setPassUniformVec(@NotNull String name, @NotNull float[] values);

    @LuaDoc(
        description = "Registers a raw SPIR-V fragment module (base64): validated (magic, word count, size cap), translated to desktop GLSL 150 via SPIRV-Cross on the CPU, then published exactly like registerGlslPass. Name the input sampler 'DiffuseSampler' (the only bound name) and animate your own uniforms per tick via setPassUniform/setPassUniformVec. Needs the SPIRV-Cross natives; without them this fails loudly and GLSL remains the portable path.",
        params = {"id: string — [a-z0-9_], 1-48 chars", "spirvBase64: string — base64 SPIR-V words, max 256KB"},
        returnType = "void"
    )
    void registerSpirvPass(@NotNull String id, @NotNull String spirvBase64);
}
