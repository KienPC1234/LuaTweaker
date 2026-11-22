package com.luatweaker.content;

import com.luatweaker.api.vm.ILuaValue;

/** Shared argument parsing for the {@code :GeoModel(...)} builder methods. */
final class GeoModelArgs {

    private GeoModelArgs() {
    }

    /**
     * Extracts {@code geoModel(modelPath[, texturePath[, animationPath[, defaultAnimation]]])}.
     * Only the model path is required; texture/animation fall back to model-derived
     * paths when omitted or nil. Throws on a missing model path instead of
     * silently ignoring the call.
     *
     * @return {model, textureOrNull, animationOrNull, defaultAnimationOrNull}
     */
    static String[] parse(ILuaValue[] args, int off, String usage) {
        if (!ContentBindingSupport.hasMinArgs(args, off, 1)) {
            throw new IllegalArgumentException(usage + " requires at least a model path");
        }
        String model = args[off].asString();
        String texture = ContentBindingSupport.hasMinArgs(args, off, 2) && !args[off + 1].isNil()
                ? args[off + 1].asString() : null;
        String animation = ContentBindingSupport.hasMinArgs(args, off, 3) && !args[off + 2].isNil()
                ? args[off + 2].asString() : null;
        String defaultAnim = ContentBindingSupport.hasMinArgs(args, off, 4) && !args[off + 3].isNil()
                ? args[off + 3].asString() : null;
        return new String[]{model, texture, animation, defaultAnim};
    }
}
