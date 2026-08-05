package com.luatweaker.api.pal;

public interface IPlatformClient {
    void spawnParticle(String particleId, double x, double y, double z, double vx, double vy, double vz);
    void playSound(String soundId, double volume, double pitch);
    int getGuiScaledWidth();
    int getGuiScaledHeight();
}
