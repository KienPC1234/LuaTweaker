package com.luatweaker.api.doc;

/**
 * Shared {@link com.luatweaker.api.annotation.LuaDoc} fragments.
 *
 * <p>The API interfaces document hundreds of parameters, and the same fragments
 * ({@code "x: number"}, {@code "label: string"}, {@code "IItemBuilder"}, ...) recur
 * across services. Keeping them here gives every service one spelling to reuse and
 * keeps the annotations free of duplicated literals.</p>
 *
 * <p>This is a namespaced catalogue, not a type: declaring it as an interface keeps it
 * uninstantiable and groups the fragments below it. Every constant is a constant
 * variable, so it is legal as an annotation value; the stub generator reads the
 * resolved strings at runtime and emits exactly the same documentation as before.</p>
 */
public interface LuaDocFragments {

    /** Lua type names used by {@code params} and {@code returnType}. */
    final class Types {
        public static final String STRING = "string";
        public static final String NUMBER = "number";
        public static final String INTEGER = "integer";
        public static final String BOOLEAN = "boolean";
        public static final String TABLE = "table";

        private Types() {}
    }

    /** Default values handed to {@code @LuaDefault}. */
    final class Defaults {
        public static final String ZERO = "0.0";
        public static final String ONE = "1.0";
        public static final String TRUE = "true";

        private Defaults() {}
    }

    /** Chainable content builders returned by the builder DSL methods. */
    final class Builder {
        public static final String I_ITEM_BUILDER = "IItemBuilder";
        public static final String I_BLOCK_BUILDER = "IBlockBuilder";
        public static final String I_ENTITY_BUILDER = "IEntityBuilder";
        public static final String I_FLUID_BUILDER = "IFluidBuilder";
        public static final String I_ARMOR_MATERIAL_BUILDER = "IArmorMaterialBuilder";

        private Builder() {}
    }

    /** Coordinates, sizes and colours. */
    final class Geometry {
        public static final String X_NUMBER = "x: number";
        public static final String Y_NUMBER = "y: number";
        public static final String Z_NUMBER = "z: number";
        public static final String X_INTEGER = "x: integer";
        public static final String Y_INTEGER = "y: integer";
        public static final String WIDTH_NUMBER = "width: number";
        public static final String HEIGHT_NUMBER = "height: number";
        public static final String MIN_NUMBER = "min: number";
        public static final String MAX_NUMBER = "max: number";
        public static final String COLOR_NUMBER = "color: number";

        private Geometry() {}
    }

    /** Fragments used by most services: identifiers, paths, values and labels. */
    final class Common {
        public static final String ID_STRING = "id: string";
        public static final String KEY_STRING = "key: string";
        public static final String PATH_STRING = "path: string";
        public static final String DIMENSION_ID = "dimensionId: string";
        public static final String ENTITY_TABLE = "entity: table";
        public static final String PRIORITY_INTEGER = "priority: integer";
        public static final String SPEED_OPTIONAL = "[speed: number]";
        public static final String VALUE_NUMBER = "value: number";
        public static final String VALUE_BOOLEAN = "value: boolean";
        public static final String LABEL_STRING = "label: string";
        public static final String TEXT_STRING = "text: string";
        public static final String MESSAGE_STRING = "message: string";

        private Common() {}
    }

    /** Content, entity and recipe payload fragments. */
    final class Content {
        public static final String AMOUNT_NUMBER = "amount: number";
        public static final String ANIM_NAME = "animName: string";
        public static final String ATTRIBUTE_ID = "attributeId: string";
        public static final String ITEM_ID = "itemId: string";
        public static final String COUNT_INTEGER = "count: integer";
        public static final String TEXTURE_PATH = "texturePath: string";
        public static final String BUILDER_FUNCTION = "builder: function(item)";
        public static final String FN_FUNCTION = "fn: function";
        public static final String HANDLER_FUNCTION = "handler: function(player, itemStack)";
        public static final String JSON_CONTENT = "jsonContent: string \u2014 raw JSON";

        private Content() {}
    }

    /** Network payload fragments. */
    final class Network {
        public static final String ARGS_TABLE = "args: table";
        public static final String CHANNEL_NAME = "channelName: string";
        public static final String PLAYER_UUID = "playerUuid: string";

        private Network() {}
    }

    /** Recipe query and registration fragments. */
    final class Recipe {
        public static final String RECIPE_ID = "recipeId: string";
        public static final String OUTPUT_ITEM = "output: IItem";
        public static final String INPUT_INGREDIENT = "input: IngredientWrapper";
        public static final String XP_NUMBER = "xp: number";
        public static final String COOK_TIME = "cookTime: integer";

        private Recipe() {}
    }
}
