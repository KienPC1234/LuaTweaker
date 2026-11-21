package com.luatweaker.client;

import com.luatweaker.api.content.IDatapackService;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Public shader service facade. The state/registration/JSON helpers live in the
 * {@code ShaderServiceBase*} collaborators; this class only forwards the constructor.
 */
public class ShaderServiceImpl extends ShaderServiceBase9 {

    public ShaderServiceImpl(@NotNull IDatapackService datapackService, @NotNull File cacheDir) {
        super(datapackService, cacheDir);
    }
}
