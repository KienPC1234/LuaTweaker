package com.luatweaker.text;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.text.TextSpec;

import java.util.List;

/** Minecraft JSON text serializer entry point. */
final class TextJsonWriter {

    private static final Gson COMPACT = new Gson();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().create();

    private TextJsonWriter() {
    }

    static String write(TextSpec node, boolean pretty) {
        return (pretty ? PRETTY : COMPACT).toJson(tree(node));
    }

    static JsonObject tree(TextSpec node) {
        JsonObject json = new JsonObject();
        TextJsonNodeWriter.addBase(json, node);
        TextJsonStyleWriter.addStyle(json, node.style());
        return json;
    }

    static JsonArray array(List<TextSpec> nodes) {
        JsonArray array = new JsonArray();
        for (TextSpec child : nodes) {
            array.add(tree(child));
        }
        return array;
    }
}
