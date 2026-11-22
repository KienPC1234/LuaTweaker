package com.luatweaker.common.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.content.IDatapackService;

import java.util.List;

/**
 * Generates blockstate/model JSONs for Lua-configured block variants entirely in
 * memory (zero hand-written JSON):
 *
 * <ul>
 *   <li><b>boolean state</b> ({@code :BooleanState("running", offTex, onTex)})</li>
 *   <li><b>facing orientation</b> ({@code :Facing("horizontal" | "directional")})</li>
 *   <li><b>pipe connections</b> ({@code :ConnectionState(true)})</li>
 * </ul>
 */
public final class MachineAssetGenerator {
    static final String DIRECTION_NORTH = "north";
    static final String DIRECTION_EAST = "east";
    static final String DIRECTION_SOUTH = "south";
    static final String DIRECTION_WEST = "west";
    static final String DIRECTION_UP = "up";
    static final String DIRECTION_DOWN = "down";
    static final String BLOCK_MODEL_PREFIX = ":block/";
    static final String ASSETS_PREFIX = "assets/";
    static final String MODELS_BLOCK_DIR = "/models/block/";
    static final String JSON_EXTENSION = ".json";

    static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private record FacingRot(String dir, int x, int y) {}

    private static final List<FacingRot> HORIZONTAL_DIRS = List.of(
            new FacingRot(DIRECTION_NORTH, 0, 0),
            new FacingRot(DIRECTION_EAST, 0, 90),
            new FacingRot(DIRECTION_SOUTH, 0, 180),
            new FacingRot(DIRECTION_WEST, 0, 270)
    );

    private static final List<FacingRot> DIRECTIONAL_DIRS = List.of(
            new FacingRot(DIRECTION_DOWN, 90, 0),
            new FacingRot(DIRECTION_UP, 270, 0),
            new FacingRot(DIRECTION_NORTH, 0, 0),
            new FacingRot(DIRECTION_SOUTH, 0, 180),
            new FacingRot(DIRECTION_WEST, 0, 270),
            new FacingRot(DIRECTION_EAST, 0, 90)
    );

    private MachineAssetGenerator() {}

    public static void generate(IDatapackService datapack, String ns, String id,
                                BooleanStateSpec state, boolean connections, String texture) {
        generate(datapack, ns, id, state, connections, "NONE", texture, null);
    }

    public static void generate(IDatapackService datapack, String ns, String id,
                                BooleanStateSpec state, boolean connections, String facing,
                                String texture, String model) {
        if (datapack == null) { return; }
        boolean hasFacing = facing != null && !"NONE".equalsIgnoreCase(facing);
        if (connections) {
            PipeAssetGenerator.generatePipe(datapack, ns, id, state, texture);
        } else if (hasFacing) {
            generateFacingVariants(datapack, ns, id, state, facing, texture, model);
        } else if (state != null) {
            generateBooleanState(datapack, ns, id, state);
        }
    }

    private static void generateFacingVariants(IDatapackService datapack, String ns, String id,
                                               BooleanStateSpec state, String facing,
                                               String texture, String model) {
        boolean directional = "DIRECTIONAL".equalsIgnoreCase(facing) || "6WAY".equalsIgnoreCase(facing);
        List<FacingRot> dirs = directional ? DIRECTIONAL_DIRS : HORIZONTAL_DIRS;
        String baseModel = model != null ? model : (ns + BLOCK_MODEL_PREFIX + id);
        JsonObject variants = new JsonObject();
        if (state != null) {
            addStateVariants(datapack, variants, ns, id, state, dirs, baseModel);
        } else {
            addPlainVariants(datapack, variants, ns, id, dirs, baseModel, texture, model);
        }
        datapack.addData(ASSETS_PREFIX + ns + "/blockstates/" + id + JSON_EXTENSION, blockstate(variants));
        datapack.addData(ASSETS_PREFIX + ns + "/models/item/" + id + JSON_EXTENSION, parentModel(baseModel));
    }

    private static void addStateVariants(IDatapackService datapack, JsonObject variants, String ns, String id,
                                         BooleanStateSpec state, List<FacingRot> dirs, String baseModel) {
        String property = state.property();
        String onModel = ns + BLOCK_MODEL_PREFIX + id + "_" + property;
        for (FacingRot rotation : dirs) {
            variants.add("facing=" + rotation.dir() + "," + property + "=false",
                    variantModel(baseModel, rotation.x(), rotation.y()));
            variants.add("facing=" + rotation.dir() + "," + property + "=true",
                    variantModel(onModel, rotation.x(), rotation.y()));
        }
        datapack.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, cubeAll(state.offTexture()));
        datapack.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_" + property + JSON_EXTENSION,
                cubeAll(state.onTexture()));
    }

    private static void addPlainVariants(IDatapackService datapack, JsonObject variants, String ns, String id,
                                         List<FacingRot> dirs, String baseModel, String texture, String model) {
        for (FacingRot rotation : dirs) {
            variants.add("facing=" + rotation.dir(), variantModel(baseModel, rotation.x(), rotation.y()));
        }
        if (model == null) {
            String resolvedTexture = texture != null && !texture.isBlank()
                    ? texture
                    : (ns + BLOCK_MODEL_PREFIX + id);
            datapack.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, cubeAll(resolvedTexture));
        }
    }

    private static void generateBooleanState(IDatapackService datapack, String ns, String id, BooleanStateSpec state) {
        String property = state.property();
        JsonObject variants = new JsonObject();
        variants.add(property + "=false", variantModel(ns + BLOCK_MODEL_PREFIX + id, 0, 0));
        variants.add(property + "=true", variantModel(ns + BLOCK_MODEL_PREFIX + id + "_" + property, 0, 0));
        datapack.addData(ASSETS_PREFIX + ns + "/blockstates/" + id + JSON_EXTENSION, blockstate(variants));
        datapack.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, cubeAll(state.offTexture()));
        datapack.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_" + property + JSON_EXTENSION,
                cubeAll(state.onTexture()));
        datapack.addData(ASSETS_PREFIX + ns + "/models/item/" + id + JSON_EXTENSION,
                parentModel(ns + BLOCK_MODEL_PREFIX + id));
    }

    static String blockstate(JsonObject variants) {
        JsonObject blockstate = new JsonObject();
        blockstate.add("variants", variants);
        return GSON.toJson(blockstate);
    }

    static JsonObject variantModel(String model, int xRot, int yRot) {
        JsonObject variant = new JsonObject();
        variant.addProperty("model", model);
        if (xRot != 0) {
            variant.addProperty("x", xRot);
        }
        if (yRot != 0) {
            variant.addProperty("y", yRot);
        }
        return variant;
    }

    static String cubeAll(String texture) {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \""
                + texture + "\"\n  }\n}";
    }

    static String parentModel(String parent) {
        return "{\n  \"parent\": \"" + parent + "\"\n}";
    }
}
