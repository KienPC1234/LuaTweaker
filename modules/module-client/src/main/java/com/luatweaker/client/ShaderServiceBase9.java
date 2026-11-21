package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import java.io.File;

abstract class ShaderServiceBase9 extends ShaderServiceBase8 {

    protected ShaderServiceBase9(IDatapackService datapackService, File cacheDir) {
        super(datapackService, cacheDir);
    }

    public static int getPulseArgb() {
        return PULSE_ARGB.get();
    }

    public static double getFrostStrength() {
        return FROST_STRENGTH.get();
    }

    public static double getBloodStrength() {
        return BLOOD_STRENGTH.get();
    }
}
