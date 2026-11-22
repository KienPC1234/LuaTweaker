package com.luatweaker.common.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.BooleanStateSpec;
import com.luatweaker.api.content.IDatapackService;

/** Generates the 64 connection-state blockstate variants and pipe models for machines. */
final class PipeAssetGenerator {

    private static final int[] DIR_X = {5, 11, 5, 0, 5, 5};
    private static final int[] DIR_Y = {5, 5, 5, 5, 11, 0};
    private static final int[] DIR_Z = {0, 5, 11, 5, 5, 5};
    private static final int[] DIR_TX = {11, 16, 11, 5, 11, 11};
    private static final int[] DIR_TY = {11, 11, 11, 11, 16, 5};
    private static final int[] DIR_TZ = {5, 11, 16, 11, 11, 11};
    private static final String[] CONNECTION_NAMES = {
            MachineAssetGenerator.DIRECTION_NORTH, MachineAssetGenerator.DIRECTION_EAST,
            MachineAssetGenerator.DIRECTION_SOUTH, MachineAssetGenerator.DIRECTION_WEST,
            MachineAssetGenerator.DIRECTION_UP, MachineAssetGenerator.DIRECTION_DOWN};
    private static final String[] FACE_NAMES = {
            MachineAssetGenerator.DIRECTION_DOWN, MachineAssetGenerator.DIRECTION_UP,
            MachineAssetGenerator.DIRECTION_NORTH, MachineAssetGenerator.DIRECTION_SOUTH,
            MachineAssetGenerator.DIRECTION_WEST, MachineAssetGenerator.DIRECTION_EAST};
    private static final int CONNECTION_COMBOS = 1 << 6;
    private static final int CONNECTION_COUNT = 6;

    private PipeAssetGenerator() {
    }

    static void generatePipe(IDatapackService datapack, String ns, String id,
                             BooleanStateSpec state, String texture) {
        String resolvedTexture = texture != null && !texture.isBlank() ? texture : "minecraft:block/iron_block";
        String modelPrefix = ns + MachineAssetGenerator.BLOCK_MODEL_PREFIX + id + "_pipe_";
        boolean hasState = state != null;

        JsonObject variants = new JsonObject();
        int stateValues = hasState ? 2 : 1;
        for (int stateValue = 0; stateValue < stateValues; stateValue++) {
            for (int combo = 0; combo < CONNECTION_COMBOS; combo++) {
                String connectionKey = connectionKey(combo);
                String stateKey = hasState
                        ? state.property() + "=" + (stateValue == 0) + "," + connectionKey
                        : connectionKey;
                variants.add(stateKey, MachineAssetGenerator.variantModel(modelPrefix + comboName(combo), 0, 0));
            }
        }
        datapack.addData(MachineAssetGenerator.ASSETS_PREFIX + ns + "/blockstates/" + id
                + MachineAssetGenerator.JSON_EXTENSION, MachineAssetGenerator.blockstate(variants));

        for (int combo = 0; combo < CONNECTION_COMBOS; combo++) {
            datapack.addData(MachineAssetGenerator.ASSETS_PREFIX + ns + MachineAssetGenerator.MODELS_BLOCK_DIR
                            + id + "_pipe_" + comboName(combo) + MachineAssetGenerator.JSON_EXTENSION,
                    pipeModel(resolvedTexture, combo));
        }

        datapack.addData(MachineAssetGenerator.ASSETS_PREFIX + ns + "/models/item/" + id
                + MachineAssetGenerator.JSON_EXTENSION, MachineAssetGenerator.parentModel(modelPrefix + "000000"));
    }

    private static String connectionKey(int combo) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < CONNECTION_COUNT; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(CONNECTION_NAMES[i]).append('=').append((combo & (1 << i)) != 0);
        }
        return builder.toString();
    }

    private static String comboName(int combo) {
        char[] chars = new char[CONNECTION_COUNT];
        for (int i = 0; i < CONNECTION_COUNT; i++) {
            chars[i] = (combo & (1 << i)) == 0 ? '0' : '1';
        }
        return new String(chars);
    }

    private static String pipeModel(String texture, int combo) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        model.addProperty("render_type", "minecraft:cutout");
        JsonObject textures = new JsonObject();
        textures.addProperty("texture", texture);
        textures.addProperty("particle", texture);
        textures.addProperty("pipe", texture);
        model.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(cubeElement(5, 5, 5, 11, 11, 11));
        for (int i = 0; i < CONNECTION_COUNT; i++) {
            if ((combo & (1 << i)) != 0) {
                elements.add(cubeElement(DIR_X[i], DIR_Y[i], DIR_Z[i], DIR_TX[i], DIR_TY[i], DIR_TZ[i]));
            }
        }
        model.add("elements", elements);
        return MachineAssetGenerator.GSON.toJson(model);
    }

    private static JsonObject cubeElement(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        JsonObject element = new JsonObject();
        JsonArray from = new JsonArray();
        from.add(fromX);
        from.add(fromY);
        from.add(fromZ);
        JsonArray to = new JsonArray();
        to.add(toX);
        to.add(toY);
        to.add(toZ);
        element.add("from", from);
        element.add("to", to);
        JsonObject faces = new JsonObject();
        for (String faceName : FACE_NAMES) {
            faces.add(faceName, face(faceName, canCull(faceName, fromX, fromY, fromZ, toX, toY, toZ)));
        }
        element.add("faces", faces);
        return element;
    }

    private static JsonObject face(String faceName, boolean canCull) {
        JsonObject face = new JsonObject();
        face.addProperty("texture", "#pipe");
        if (canCull) {
            face.addProperty("cullface", faceName);
        }
        return face;
    }

    private static boolean canCull(String faceName, int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        return switch (faceName) {
            case MachineAssetGenerator.DIRECTION_DOWN -> fromY == 0;
            case MachineAssetGenerator.DIRECTION_UP -> toY == 16;
            case MachineAssetGenerator.DIRECTION_NORTH -> fromZ == 0;
            case MachineAssetGenerator.DIRECTION_SOUTH -> toZ == 16;
            case MachineAssetGenerator.DIRECTION_WEST -> fromX == 0;
            case MachineAssetGenerator.DIRECTION_EAST -> toX == 16;
            default -> false;
        };
    }
}
