package com.luatweaker.client;

import org.jetbrains.annotations.NotNull;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.spvc.Spvc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Translates user-authored SPIR-V (fragment stage) to desktop GLSL 150 via
 * SPIRV-Cross ({@code lwjgl-spvc}, pure CPU work, no GL context needed).
 *
 * <p>The output is a <b>complete</b> shader (own {@code #version} and
 * declarations), so it feeds the full-fragment publish path — not the
 * body-subset path. Name the input sampler {@code DiffuseSampler}: the
 * engine's program JSON only binds that name.</p>
 */
public final class SpirvTranslator {

    /** SPIR-V magic, little-endian. */
    public static final int SPIRV_MAGIC = 0x07230203;
    /** Largest accepted module: SPIR-V for a post pass is kilobytes, not megabytes. */
    public static final int MAX_SPIRV_BYTES = 256 * 1024;
    private static final int MIN_MODULE_BYTES = 20;
    private static final int WORD_BYTES = 4;
    private static final int GLSL_VERSION = 150;
    private static final long NO_CONTEXT = 0L;

    private SpirvTranslator() {}

    /**
     * @param spirv raw SPIR-V words (little-endian)
     * @return complete desktop GLSL 150 source
     * @throws IllegalArgumentException when the bytes are not SPIR-V, the module
     *         is invalid, translation fails, or the SPIRV-Cross natives are missing
     */
    public static @NotNull String translateToGlsl150(byte @NotNull [] spirv) {
        validateModule(spirv);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer words = stack.mallocInt(spirv.length / WORD_BYTES);
            words.put(ByteBuffer.wrap(spirv).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer());
            words.flip();
            PointerBuffer pContext = stack.mallocPointer(1);
            check(Spvc.spvc_context_create(pContext), NO_CONTEXT, "create context");
            long context = pContext.get(0);
            try {
                PointerBuffer pIr = stack.mallocPointer(1);
                check(Spvc.spvc_context_parse_spirv(context, words, words.remaining(), pIr),
                        context, "parse module");
                long ir = pIr.get(0);
                PointerBuffer pCompiler = stack.mallocPointer(1);
                check(Spvc.spvc_context_create_compiler(context, Spvc.SPVC_BACKEND_GLSL, ir,
                        Spvc.SPVC_CAPTURE_MODE_TAKE_OWNERSHIP, pCompiler), context, "create compiler");
                long compiler = pCompiler.get(0);
                PointerBuffer pOptions = stack.mallocPointer(1);
                check(Spvc.spvc_compiler_create_compiler_options(compiler, pOptions),
                        context, "create options");
                long options = pOptions.get(0);
                check(Spvc.spvc_compiler_options_set_uint(options,
                        Spvc.SPVC_COMPILER_OPTION_GLSL_VERSION, GLSL_VERSION),
                        context, "set GLSL version");
                check(Spvc.spvc_compiler_options_set_bool(options,
                        Spvc.SPVC_COMPILER_OPTION_GLSL_ES, false),
                        context, "select desktop GLSL");
                check(Spvc.spvc_compiler_install_compiler_options(compiler, options),
                        context, "install options");
                PointerBuffer pSource = stack.mallocPointer(1);
                check(Spvc.spvc_compiler_compile(compiler, pSource), context, "compile");
                return MemoryUtil.memUTF8(pSource.get(0));
            } finally {
                Spvc.spvc_context_destroy(context);
            }
        } catch (UnsatisfiedLinkError e) {
            throw new IllegalArgumentException(
                    "SPIR-V translation needs the SPIRV-Cross natives (lwjgl-spvc), which are not"
                            + " on this classpath. Write GLSL via registerGlslPass instead.", e);
        }
    }

    private static void validateModule(byte @NotNull [] spirv) {
        if (spirv.length < MIN_MODULE_BYTES || (spirv.length & 3) != 0) {
            throw new IllegalArgumentException(
                    "not SPIR-V: need a whole-word module of at least " + MIN_MODULE_BYTES
                            + " bytes, got " + spirv.length + ".");
        }
        if (spirv.length > MAX_SPIRV_BYTES) {
            throw new IllegalArgumentException(
                    "SPIR-V module is " + spirv.length + " bytes, max is " + MAX_SPIRV_BYTES + ".");
        }
        int magic = ByteBuffer.wrap(spirv, 0, WORD_BYTES).order(ByteOrder.LITTLE_ENDIAN).getInt();
        if (magic != SPIRV_MAGIC) {
            throw new IllegalArgumentException("not SPIR-V: bad magic 0x"
                    + Integer.toHexString(magic) + ".");
        }
    }

    private static void check(int code, long context, String step) {
        if (code != Spvc.SPVC_SUCCESS) {
            String detail = context == NO_CONTEXT
                    ? "code " + code : Spvc.spvc_context_get_last_error_string(context);
            throw new IllegalArgumentException("SPIR-V " + step + " failed: " + detail + ".");
        }
    }
}
