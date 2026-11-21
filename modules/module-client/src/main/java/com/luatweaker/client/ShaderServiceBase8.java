package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import java.io.File;

abstract class ShaderServiceBase8 extends ShaderServiceBase7 {

    protected ShaderServiceBase8(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
    }

    public static double getVignetteStrength() {
        return VIGNETTE_STRENGTH.get();
    }

    public static int getVignetteArgb() {
        return VIGNETTE_ARGB.get();
    }

    public static double getTintStrength() {
        return TINT_STRENGTH.get();
    }

    public static int getTintArgb() {
        return TINT_ARGB.get();
    }

    public static double getPulseStrength() {
        return PULSE_STRENGTH.get();
    }
}
