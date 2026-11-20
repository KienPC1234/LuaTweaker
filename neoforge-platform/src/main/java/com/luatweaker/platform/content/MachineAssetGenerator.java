package com.luatweaker.platform.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final int[] DIR_X = {5, 11, 5, 0, 5, 5};
    private static final int[] DIR_Y = {5, 5, 5, 5, 11, 0};
    private static final int[] DIR_Z = {0, 5, 11, 5, 5, 5};
    private static final int[] DIR_TX = {11, 16, 11, 5, 11, 11};
    private static final int[] DIR_TY = {11, 11, 11, 11, 16, 5};
    private static final int[] DIR_TZ = {5, 11, 16, 11, 11, 11};
    private static final String[] CONNECTION_NAMES = {"north", "east", "south", "west", "up", "down"};
    private static final int CONNECTION_COMBOS = 1 << 6;

    private record FacingRot(String dir, int x, int y) {}

    private static final List<FacingRot> HORIZONTAL_DIRS = List.of(
            new FacingRot("north", 0, 0),
            new FacingRot("east", 0, 90),
            new FacingRot("south", 0, 180),
            new FacingRot("west", 0, 270)
    );

    private static final List<FacingRot> DIRECTIONAL_DIRS = List.of(
            new FacingRot("down", 90, 0),
            new FacingRot("up", 270, 0),
            new FacingRot("north", 0, 0),
            new FacingRot("south", 0, 180),
            new FacingRot("west", 0, 270),
            new FacingRot("east", 0, 90)
    );

    private MachineAssetGenerator() {}

    public static void generate(IDatapackService datapack, String ns, String id,
                                BooleanStateSpec state, boolean connections, String texture) {
        generate(datapack, ns, id, state, connections, "NONE", texture, null);
    }

    public static void generate(IDatapackService datapack, String ns, String id,
                                BooleanStateSpec state, boolean connections, String facing,
                                String texture, String model) {
        if (datapack == null) return;
        boolean hasFacing = facing != null && !"NONE".equalsIgnoreCase(facing);
        if (connections) {
            generatePipe(datapack, ns, id, state, texture);
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
        String baseModel = model != null ? model : (ns + ":block/" + id);
        JsonObject variants = new JsonObject();

        if (state != null) {
            String prop = state.property();
            String onModel = ns + ":block/" + id + "_" + prop;
            for (FacingRot fr : dirs) {
                variants.add("facing=" + fr.dir() + "," + prop + "=false", variantModel(baseModel, fr.x(), fr.y()));
                variants.add("facing=" + fr.dir() + "," + prop + "=true", variantModel(onModel, fr.x(), fr.y()));
            }
            datapack.addData("assets/" + ns + "/models/block/" + id + ".json", cubeAll(state.offTexture()));
            datapack.addData("assets/" + ns + "/models/block/" + id + "_" + prop + ".json", cubeAll(state.onTexture()));
        } else {
            for (FacingRot fr : dirs) {
                variants.add("facing=" + fr.dir(), variantModel(baseModel, fr.x(), fr.y()));
            }
            if (model == null) {
                String tex = texture != null && !texture.isBlank() ? texture : (ns + ":block/" + id);
                datapack.addData("assets/" + ns + "/models/block/" + id + ".json", cubeAll(tex));
            }
        }

        datapack.addData("assets/" + ns + "/blockstates/" + id + ".json", blockstate(variants));
        datapack.addData("assets/" + ns + "/models/item/" + id + ".json", parentModel(baseModel));
    }

    private static void generateBooleanState(IDatapackService datapack, String ns, String id, BooleanStateSpec state) {
        String prop = state.property();
        JsonObject variants = new JsonObject();
        variants.add(prop + "=false", variantModel(ns + ":block/" + id, 0, 0));
        variants.add(prop + "=true", variantModel(ns + ":block/" + id + "_" + prop, 0, 0));
        datapack.addData("assets/" + ns + "/blockstates/" + id + ".json", blockstate(variants));
        datapack.addData("assets/" + ns + "/models/block/" + id + ".json", cubeAll(state.offTexture()));
        datapack.addData("assets/" + ns + "/models/block/" + id + "_" + prop + ".json", cubeAll(state.onTexture()));
        datapack.addData("assets/" + ns + "/models/item/" + id + ".json", parentModel(ns + ":block/" + id));
    }

    private static void generatePipe(IDatapackService datapack, String ns, String id,
                                     BooleanStateSpec state, String texture) {
        String tex = texture != null && !texture.isBlank() ? texture : "minecraft:block/iron_block";
        String modelPrefix = ns + ":block/" + id + "_pipe_";
        boolean hasState = state != null;

        JsonObject variants = new JsonObject();
        int stateValues = hasState ? 2 : 1;
        for (int sv = 0; sv < stateValues; sv++) {
            for (int combo = 0; combo < CONNECTION_COMBOS; combo++) {
                String connKey = connectionKey(combo);
                String stateKey = hasState ? state.property() + "=" + (sv == 0) + "," + connKey : connKey;
                variants.add(stateKey, variantModel(modelPrefix + comboName(combo), 0, 0));
            }
        }
        datapack.addData("assets/" + ns + "/blockstates/" + id + ".json", blockstate(variants));

        for (int combo = 0; combo < CONNECTION_COMBOS; combo++) {
            datapack.addData("assets/" + ns + "/models/block/" + id + "_pipe_" + comboName(combo) + ".json",
                    pipeModel(tex, combo));
        }

        datapack.addData("assets/" + ns + "/models/item/" + id + ".json",
                parentModel(modelPrefix + "000000"));
    }

    private static String connectionKey(int combo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) sb.append(',');
            sb.append(CONNECTION_NAMES[i]).append('=').append((combo & (1 << i)) != 0);
        }
        return sb.toString();
    }

    private static String comboName(int combo) {
        char[] chars = new char[6];
        for (int i = 0; i < 6; i++) {
            chars[i] = (combo & (1 << i)) != 0 ? '1' : '0';
        }
        return new String(chars);
    }

    private static String blockstate(JsonObject variants) {
        JsonObject bs = new JsonObject();
        bs.add("variants", variants);
        return GSON.toJson(bs);
    }

    private static JsonObject variantModel(String model, int xRot, int yRot) {
        JsonObject v = new JsonObject();
        v.addProperty("model", model);
        if (xRot != 0) {
            v.addProperty("x", xRot);
        }
        if (yRot != 0) {
            v.addProperty("y", yRot);
        }
        return v;
    }

    private static String cubeAll(String texture) {
        return "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \""
                + texture + "\"\n  }\n}";
    }

    private static String parentModel(String parent) {
        return "{\n  \"parent\": \"" + parent + "\"\n}";
    }

    private static String pipeModel(String texture, int combo) {
        JsonObject model = new JsonObject();
        model.addProperty("parent", "minecraft:block/block");
        JsonObject textures = new JsonObject();
        textures.addProperty("texture", texture);
        textures.addProperty("particle", texture);
        textures.addProperty("pipe", texture);
        model.add("textures", textures);

        JsonArray elements = new JsonArray();
        elements.add(cubeElement(5, 5, 5, 11, 11, 11));

        for (int i = 0; i < 6; i++) {
            if ((combo & (1 << i)) != 0) {
                elements.add(cubeElement(DIR_X[i], DIR_Y[i], DIR_Z[i], DIR_TX[i], DIR_TY[i], DIR_TZ[i]));
            }
        }
        model.add("elements", elements);
        return GSON.toJson(model);
    }

    private static JsonObject cubeElement(int fromX, int fromY, int fromZ, int toX, int toY, int toZ) {
        JsonObject el = new JsonObject();
        JsonArray from = new JsonArray();
        from.add(fromX); from.add(fromY); from.add(fromZ);
        JsonArray to = new JsonArray();
        to.add(toX); to.add(toY); to.add(toZ);
        el.add("from", from);
        el.add("to", to);

        JsonObject faces = new JsonObject();
        for (String faceName : new String[]{"down", "up", "north", "south", "west", "east"}) {
            JsonObject face = new JsonObject();
            face.addProperty("texture", "#pipe");
            face.addProperty("cullface", faceName);
            faces.add(faceName, face);
        }
        el.add("faces", faces);
        return el;
    }
}
