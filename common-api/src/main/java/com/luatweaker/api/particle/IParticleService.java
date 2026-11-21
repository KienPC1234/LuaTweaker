package com.luatweaker.api.particle;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;

/**
 * Low-level particle primitives: one spawn call plus generic point-shape generators.
 *
 * <p>Every shape method spawns the requested particle at each generated point. The generators
 * are pure math (line, circle, helix, sphere, Bezier), so a script can use the same coordinates
 * for other purposes by calling {@code Particle:Spawn} at each point.</p>
 *
 * <p>This is a tool, not a gameplay system: it does not decide what the particles mean.</p>
 */
@LuaDoc(description = "Low-level particle spawner and generic shape/path generators (line, circle, helix, sphere, Bezier).")
public interface IParticleService {

    String PARAM_PARTICLE = "particleId: string";
    String PARAM_SPEED = "[speed: number]";
    String PARAM_DIMENSION = "[dimension: string]";
    String PARAM_COUNT = "[count: integer]";
    String ZERO = "0.0";
    String DEFAULT_DIMENSION = "minecraft:overworld";

    @LuaDoc(
        description = "Spawns particles at one point in a dimension.",
        params = {PARAM_PARTICLE, "x: number", "y: number", "z: number",
                  "[vx: number]", "[vy: number]", "[vz: number]",
                  PARAM_COUNT, PARAM_SPEED, PARAM_DIMENSION}
    )
    void spawn(String particleId, double x, double y, double z,
               @LuaDefault(ZERO) double vx, @LuaDefault(ZERO) double vy,
               @LuaDefault(ZERO) double vz, @LuaDefault("1") int count,
               @LuaDefault(ZERO) double speed,
               @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns a particle that needs options (dust color/size, block, item). "
                    + "options keys: Color (hex or int), Size, Block, Item. The particle id decides "
                    + "which keys are valid; unsupported combinations fail loudly.",
        params = {PARAM_PARTICLE, "x: number", "y: number", "z: number", "options: table",
                  PARAM_COUNT, PARAM_SPEED, PARAM_DIMENSION}
    )
    void spawnOptions(String particleId, double x, double y, double z,
                      ILuaTable options, @LuaDefault("1") int count,
                      @LuaDefault(ZERO) double speed,
                      @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns particles along a straight line. spacing is the distance between "
                    + "consecutive points (must be > 0).",
        params = {PARAM_PARTICLE, "x1: number", "y1: number", "z1: number",
                  "x2: number", "y2: number", "z2: number", "[spacing: number]",
                  PARAM_SPEED, PARAM_DIMENSION}
    )
    void line(String particleId, double x1, double y1, double z1,
              double x2, double y2, double z2,
              @LuaDefault("0.25") double spacing, @LuaDefault(ZERO) double speed,
              @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns particles on a horizontal circle (X/Z plane) with the given radius and point count.",
        params = {PARAM_PARTICLE, "centerX: number", "centerY: number", "centerZ: number",
                  "radius: number", "[points: integer]", PARAM_SPEED, PARAM_DIMENSION}
    )
    void circle(String particleId, double centerX, double centerY, double centerZ, double radius,
                @LuaDefault("32") int points, @LuaDefault(ZERO) double speed,
                @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns particles on a vertical helix. height is the total rise, turns is "
                    + "the number of full rotations, points is the sample count.",
        params = {PARAM_PARTICLE, "centerX: number", "centerY: number", "centerZ: number",
                  "radius: number", "[height: number]", "[turns: number]", "[points: integer]",
                  PARAM_SPEED, PARAM_DIMENSION}
    )
    void helix(String particleId, double centerX, double centerY, double centerZ, double radius,
               @LuaDefault("3.0") double height, @LuaDefault("3.0") double turns,
               @LuaDefault("48") int points, @LuaDefault(ZERO) double speed,
               @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns particles on a sphere surface using a Fibonacci distribution.",
        params = {PARAM_PARTICLE, "centerX: number", "centerY: number", "centerZ: number",
                  "radius: number", "[points: integer]", PARAM_SPEED, PARAM_DIMENSION}
    )
    void sphere(String particleId, double centerX, double centerY, double centerZ, double radius,
                @LuaDefault("32") int points, @LuaDefault(ZERO) double speed,
                @LuaDefault(DEFAULT_DIMENSION) String dimension);

    @LuaDoc(
        description = "Spawns particles along a Bezier curve through the supplied control points. "
                    + "controlPoints is a 1-based array of Vector3-shaped tables ({X,Y,Z} or {x,y,z}); "
                    + "at least two points are required.",
        params = {PARAM_PARTICLE, "controlPoints: table", "[samples: integer]",
                  PARAM_SPEED, PARAM_DIMENSION}
    )
    void bezier(String particleId, ILuaTable controlPoints, @LuaDefault("16") int samples,
                @LuaDefault(ZERO) double speed,
                @LuaDefault(DEFAULT_DIMENSION) String dimension);
}
