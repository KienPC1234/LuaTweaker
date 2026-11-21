package com.luatweaker.worldgen;

/** Fragment checks for generated worldgen JSON. */
final class JsonFragments {

    private JsonFragments() {
    }

    static boolean containsAll(String json, String... fragments) {
        for (String fragment : fragments) {
            if (!json.contains(fragment)) {
                return false;
            }
        }
        return true;
    }

    static boolean containsNone(String json, String... fragments) {
        for (String fragment : fragments) {
            if (json.contains(fragment)) {
                return false;
            }
        }
        return true;
    }
}
