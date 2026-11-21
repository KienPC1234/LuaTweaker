package com.luatweaker.api.particle;

/**
 * Position, velocity, count and speed for one particle spawn call. Keeping them together avoids
 * long PAL parameter lists and lets callers pass the same payload to simple and option particles.
 */
public record ParticleSpawn(double x, double y, double z,
                            double vx, double vy, double vz,
                            int count, double speed) {
}
