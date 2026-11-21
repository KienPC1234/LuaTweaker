package com.luatweaker.text;

import com.google.gson.JsonObject;
import com.luatweaker.api.text.TextSpec;

/** Writes the node kind/base fields of a Minecraft JSON text component. */
final class TextJsonNodeWriter {

    private TextJsonNodeWriter() {
    }

    static void addBase(JsonObject json, TextSpec node) {
        switch (node.kind()) {
            case TextSpec.KIND_TRANSLATABLE -> addTranslatable(json, node);
            case TextSpec.KIND_KEYBIND -> json.addProperty("keybind", node.text());
            case TextSpec.KIND_SCORE -> addScore(json, node);
            case TextSpec.KIND_JOIN -> addJoin(json, node);
            default -> addLiteral(json, node);
        }
    }

    private static void addTranslatable(JsonObject json, TextSpec node) {
        json.addProperty("translate", node.text());
        if (!node.children().isEmpty()) {
            json.add("with", TextJsonWriter.array(node.children()));
        }
    }

    private static void addScore(JsonObject json, TextSpec node) {
        JsonObject score = new JsonObject();
        score.addProperty("name", node.text());
        score.addProperty("objective", node.secondary());
        json.add("score", score);
    }

    private static void addJoin(JsonObject json, TextSpec node) {
        json.addProperty("text", "");
        if (!node.children().isEmpty()) {
            json.add("extra", TextJsonWriter.array(node.children()));
        }
    }

    private static void addLiteral(JsonObject json, TextSpec node) {
        json.addProperty("text", node.text());
        if (!node.children().isEmpty()) {
            json.add("extra", TextJsonWriter.array(node.children()));
        }
    }
}
