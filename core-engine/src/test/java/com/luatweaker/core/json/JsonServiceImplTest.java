package com.luatweaker.core.json;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JsonServiceImplTest {

    private static final String ARRAY_JSON = "[1, \"two\", true, 4.5]";

    private static final String RUBY_ORE_JSON =
            "{\"id\":\"ruby_ore\",\"hardness\":3.0,\"drop\":{\"item\":\"ruby\",\"count\":1}}";

    private static final String BAD_JSON = "{bad json";

    private static final String KEY_NAME = "name";
    private static final String KEY_TIER = "tier";
    private static final String KEY_UNBREAKABLE = "unbreakable";
    private static final String DIAMOND_WAND = "Diamond Wand";

    private static final String NOTHING_THROWN = "expected IllegalArgumentException but nothing was thrown";

    private ILuaEngine engine;
    private JsonServiceImpl jsonService;

    @BeforeEach
    void setUp() {
        engine = new LuavaEngine(false);
        jsonService = new JsonServiceImpl(engine);
    }

    @Nested
    class Primitives {

        @Test
        void testDecodePrimitivesHelloWorld() {
            ILuaValue str = jsonService.decode("\"hello world\"");
            assertEquals("hello world", str.asString(), "a JSON string must decode to a Lua string");
        }

        @Test
        void testDecodePrimitivesNumInt() {
            ILuaValue numInt = jsonService.decode("42");
            assertEquals(42.0, numInt.asDouble(), "a JSON integer must decode to the number 42");
        }

        @Test
        void testDecodePrimitivesNumInt2() {
            ILuaValue numInt = jsonService.decode("42");
            assertEquals(42, numInt.asInt(), "a JSON integer must be convertible back to int 42");
        }

        @Test
        void testDecodePrimitivesNumFloat() {
            ILuaValue numFloat = jsonService.decode("3.14159");
            assertEquals(3.141_59, numFloat.asDouble(), 1e-5,
                    "a JSON fraction must decode to its double value");
        }

        @Test
        void testDecodePrimitivesBoolTrue() {
            ILuaValue boolTrue = jsonService.decode("true");
            assertTrue(boolTrue.asBoolean(), "JSON true must decode to Lua true");
        }

        @Test
        void testDecodePrimitivesBoolFalse() {
            ILuaValue boolFalse = jsonService.decode("false");
            assertFalse(boolFalse.asBoolean(), "JSON false must decode to Lua false");
        }

        @Test
        void testDecodePrimitivesNilVal() {
            ILuaValue nilVal = jsonService.decode("null");
            assertTrue(nilVal.isNil(), "JSON null must decode to Lua nil");
        }
    }

    @Nested
    class Arrays {

        private ILuaTable decodeArray() {
            return jsonService.decode(ARRAY_JSON).asTable();
        }

        @Test
        void testDecodeJsonArrayVal() {
            assertTrue(jsonService.decode(ARRAY_JSON).isTable(),
                    "a JSON array must decode to a Lua table");
        }

        @Test
        void testDecodeJsonArrayTable() {
            assertEquals(4, decodeArray().length(), "the decoded array must report four entries");
        }

        @Test
        void testDecodeJsonArrayTable2() {
            assertEquals(1, decodeArray().rawget(1).asInt(),
                    "the first array entry must be the number 1");
        }

        @Test
        void testDecodeJsonArrayTwo() {
            assertEquals("two", decodeArray().rawget(2).asString(),
                    "the second array entry must be the string 'two'");
        }

        @Test
        void testDecodeJsonArrayTable3() {
            assertTrue(decodeArray().rawget(3).asBoolean(),
                    "the third array entry must be boolean true");
        }

        @Test
        void testDecodeJsonArrayTable4() {
            assertEquals(4.5, decodeArray().rawget(4).asDouble(), 1e-5,
                    "the fourth array entry must be the number 4.5");
        }
    }

    @Nested
    class Objects {

        private ILuaTable decodeRubyOre() {
            return jsonService.decode(RUBY_ORE_JSON).asTable();
        }

        @Test
        void testDecodeJsonObjectVal() {
            assertTrue(jsonService.decode(RUBY_ORE_JSON).isTable(),
                    "a JSON object must decode to a Lua table");
        }

        @Test
        void testDecodeJsonObjectId() {
            assertEquals("ruby_ore", decodeRubyOre().rawget("id").asString(),
                    "the decoded object must keep its string id");
        }

        @Test
        void testDecodeJsonObjectHardness() {
            assertEquals(3.0, decodeRubyOre().rawget("hardness").asDouble(), 1e-5,
                    "the decoded object must keep its numeric hardness");
        }

        @Test
        void testDecodeJsonObjectDropVal() {
            assertTrue(decodeRubyOre().rawget("drop").isTable(),
                    "a nested JSON object must decode to a nested Lua table");
        }

        @Test
        void testDecodeJsonObjectItem() {
            ILuaValue dropVal = decodeRubyOre().rawget("drop");
            assertEquals("ruby", dropVal.asTable().rawget("item").asString(),
                    "the nested object must keep its string item");
        }

        @Test
        void testDecodeJsonObjectCount() {
            ILuaValue dropVal = decodeRubyOre().rawget("drop");
            assertEquals(1, dropVal.asTable().rawget("count").asInt(),
                    "the nested object must keep its integer count");
        }
    }

    @Nested
    class MalformedJson {

        @Test
        void testDecodeMalformedJsonThrowsRejectsBadJson() {
            assertThrows(IllegalArgumentException.class, () -> jsonService.decode(BAD_JSON),
                    "unbalanced JSON must be rejected");
        }

        @Test
        void testDecodeMalformedJsonThrowsJsonDecodeFailed() {
            IllegalArgumentException ex;
            try {
                jsonService.decode(BAD_JSON);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                ex = expectedFailure;
            }
            assertTrue(ex.getMessage().contains("Json.Decode failed"), ex.getMessage());
        }

        @Test
        void testDecodeMalformedJsonThrowsRejectsDecode() {
            assertThrows(IllegalArgumentException.class, () -> jsonService.decode(""),
                    "an empty document must be rejected");
        }

        @Test
        void testDecodeMalformedJsonThrowsRejectsDecode2() {
            assertThrows(IllegalArgumentException.class, () -> jsonService.decode("   "),
                    "a whitespace-only document must be rejected");
        }

        @Test
        void testDecodeMalformedJsonThrowsRejectsDecode3() {
            assertThrows(IllegalArgumentException.class, () -> jsonService.decode(null),
                    "a null document must be rejected");
        }
    }

    @Nested
    class EncodeTables {

        private ILuaTable wandTable() {
            ILuaTable table = engine.createTable();
            table.rawset(KEY_NAME, DIAMOND_WAND);
            table.rawset(KEY_TIER, 3L);
            table.rawset(KEY_UNBREAKABLE, false);
            return table;
        }

        @Test
        void testEncodePrimitivesAndTablesNameDiamondWand() {
            String compact = jsonService.encode(wandTable(), false);
            assertTrue(compact.contains("\"name\":\"Diamond Wand\""),
                    "a string field must be encoded as a JSON string: " + compact);
        }

        @Test
        void testEncodePrimitivesAndTablesTier3() {
            String compact = jsonService.encode(wandTable(), false);
            assertTrue(compact.contains("\"tier\":3"),
                    "an integer field must be encoded as a JSON number: " + compact);
        }

        @Test
        void testEncodePrimitivesAndTablesUnbreakableFalse() {
            String compact = jsonService.encode(wandTable(), false);
            assertTrue(compact.contains("\"unbreakable\":false"),
                    "a boolean field must be encoded as a JSON boolean: " + compact);
        }

        @Test
        void testEncodePrimitivesAndTablesN() {
            String compact = jsonService.encode(wandTable(), false);
            assertFalse(compact.contains("\n"), "compact encoding must stay on a single line");
        }

        @Test
        void testEncodePrimitivesAndTablesN2() {
            String pretty = jsonService.encode(wandTable(), true);
            assertTrue(pretty.contains("\n"), "pretty encoding must break lines");
        }

        @Test
        void testEncodePrimitivesAndTablesNameDiamondWand2() {
            String pretty = jsonService.encode(wandTable(), true);
            assertTrue(pretty.contains("  \"name\": \"Diamond Wand\""),
                    "pretty encoding must indent object fields: " + pretty);
        }
    }

    @Nested
    class EncodeArraysAndCycles {

        private ILuaTable circularRoot() {
            ILuaTable root = engine.createTable();
            ILuaTable child = engine.createTable();
            root.rawset("child", child);
            child.rawset("parent", root);
            return root;
        }

        @Test
        void testEncodeArrayTable() {
            ILuaTable arr = engine.createTable();
            arr.rawset(1, "apple");
            arr.rawset(2, "banana");
            arr.rawset(3, "cherry");

            String json = jsonService.encode(arr, false);
            assertEquals("[\"apple\",\"banana\",\"cherry\"]", json,
                    "a sequence table must be encoded as a JSON array in index order");
        }

        @Test
        void testEncodeCircularReferenceThrowsRejectsEncode() {
            assertThrows(IllegalArgumentException.class, () -> jsonService.encode(circularRoot(), false),
                    "a table that refers to itself must be rejected");
        }

        @Test
        void testEncodeCircularReferenceThrowsCircularTableReference() {
            IllegalArgumentException ex;
            try {
                jsonService.encode(circularRoot(), false);

                throw new AssertionError(NOTHING_THROWN);
            } catch (IllegalArgumentException expectedFailure) {
                ex = expectedFailure;
            }
            assertTrue(ex.getMessage().contains("circular table reference"), ex.getMessage());
        }
    }

    @Nested
    class IsValid {

        private static final String JSON_NUMBER = "42";
        private static final String JSON_BLANK = "   ";
        private static final String JSON_EMPTY = "";

        @Test
        void testIsValidAcceptsObjectsArraysAndPrimitivesObject() {
            assertTrue(jsonService.isValid(RUBY_ORE_JSON),
                    "a valid JSON object must be accepted");
        }

        @Test
        void testIsValidAcceptsObjectsArraysAndPrimitivesArray() {
            assertTrue(jsonService.isValid(ARRAY_JSON),
                    "a valid JSON array must be accepted");
        }

        @Test
        void testIsValidAcceptsObjectsArraysAndPrimitivesNumber() {
            assertTrue(jsonService.isValid(JSON_NUMBER),
                    "a valid JSON number must be accepted");
        }

        @Test
        void testIsValidRejectsMalformedJson() {
            assertFalse(jsonService.isValid(BAD_JSON),
                    "unbalanced JSON must be rejected without throwing");
        }

        @Test
        void testIsValidRejectsNullBlankInputsNull() {
            assertFalse(jsonService.isValid(null),
                    "a null document must be rejected without throwing");
        }

        @Test
        void testIsValidRejectsNullBlankInputsEmpty() {
            assertFalse(jsonService.isValid(JSON_EMPTY),
                    "an empty document must be rejected without throwing");
        }

        @Test
        void testIsValidRejectsNullBlankInputsBlank() {
            assertFalse(jsonService.isValid(JSON_BLANK),
                    "a whitespace-only document must be rejected without throwing");
        }

        @Test
        void testIsValidAvailableFromLua() {
            engine.executeString("""
                    assert(Json.IsValid('{"x": 1}') == true, 'valid object')
                    assert(Json.IsValid('[1, 2]') == true, 'valid array')
                    assert(Json.IsValid('{broken') == false, 'malformed is false, no throw')
                    assert(Json:IsValid('{broken') == false, 'colon call works')
                    """, "json-isvalid-test");
            assertNull(engine.getAndClearLastExecutionError(),
                    "Json.IsValid must be callable from Lua for objects, arrays and malformed input");
        }
    }

    @Nested
    class LuaApi {

        @Test
        void testLuaGlobalAndRequire() {
            engine.executeString("""
                    assert(Json ~= nil, 'Json global must exist')
                    assert(type(Json.Decode) == 'function', 'Json.Decode function')
                    assert(type(Json.Encode) == 'function', 'Json.Encode function')

                    local obj = Json.Decode('{"x": 10, "y": 20, "items": ["sword", "shield"]}')
                    assert(obj.x == 10, 'x=10')
                    assert(obj.y == 20, 'y=20')
                    assert(#obj.items == 2, 'items count')
                    assert(obj.items[1] == 'sword', 'first item')

                    local encoded = Json:Encode(obj)
                    assert(type(encoded) == 'string', 'encoded string')
                    local roundtrip = Json.Decode(encoded)
                    assert(roundtrip.x == 10, 'roundtrip x')

                    -- test aliases: parse, stringify
                    local p = Json.Parse('{"val": true}')
                    assert(p.val == true, 'parse alias')
                    local s = Json:Stringify(p)
                    assert(type(s) == 'string', 'stringify alias')

                    -- test colon calls
                    local c = Json:Decode('{"col": 42}')
                    assert(c.col == 42, 'colon decode')
                    local cs = Json:Encode(c)
                    assert(type(cs) == 'string', 'colon encode')
                    """, "json-test-1");
            assertNull(engine.getAndClearLastExecutionError(),
                    "the Json global, its aliases, and colon calls must all work");
        }

        @Test
        void testJsonModuleResolvesThroughRequire() {
            engine.executeString("""
                    local JsonLib = require("LuaTweaker.Json")
                    assert(JsonLib ~= nil, 'require LuaTweaker.Json')
                    local data = JsonLib.Decode('[100, 200]')
                    assert(#data == 2 and data[1] == 100, 'require decode')

                    local JsonLib2 = require("Json")
                    assert(JsonLib2 ~= nil, 'require Json')
                    """, "json-test-2");
            assertNull(engine.getAndClearLastExecutionError(),
                    "require('LuaTweaker.Json') and require('Json') must resolve to the same module");
        }
    }
}
