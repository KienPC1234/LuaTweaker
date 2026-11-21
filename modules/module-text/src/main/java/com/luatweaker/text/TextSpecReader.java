package com.luatweaker.text;

import com.luatweaker.api.text.TextSpec;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Converts Lua values into {@link TextSpec} handles for the builder methods. */
final class TextSpecReader {

    private TextSpecReader() {
    }

    static List<TextSpec> readList(@Nullable ILuaTable table, String label) {
        if (table == null) {
            return List.of();
        }
        int length = table.length();
        List<TextSpec> result = new ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            result.add(readValue(table.rawget(index), label + " #" + index));
        }
        return result;
    }

    static TextSpec readValue(@Nullable ILuaValue value, String label) {
        if (value == null || value.isNil()) {
            throw new IllegalArgumentException(label + " must be a TextSpec or a string/number");
        }
        Object raw = value.toJavaObject();
        if (raw instanceof TextSpec spec) {
            return spec;
        }
        if (value.isTable()) {
            throw new IllegalArgumentException(label + " must be a TextSpec handle, not a table");
        }
        return TextSpec.literal(value.asString());
    }
}
