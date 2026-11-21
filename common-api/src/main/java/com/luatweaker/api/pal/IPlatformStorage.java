package com.luatweaker.api.pal;

import java.io.File;

@FunctionalInterface
public interface IPlatformStorage {
    File getStorageDirectory();
}
