package com.luatweaker.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import org.jetbrains.annotations.NotNull;

/**
 * Writes one {@link EnchantmentConfig} into the virtual datapack on {@code register()}.
 * Field mutation lives in the Lua binding so this class stays a thin serializer.
 */
final class EnchantmentBuilderImpl {

    private final EnchantmentConfig values;
    private final IDatapackService datapack;

    EnchantmentBuilderImpl(@NotNull String id, @NotNull IDatapackService datapack) {
        this.values = new EnchantmentConfig(id);
        this.datapack = datapack;
    }

    EnchantmentConfig config() {
        return values;
    }

    boolean register() {
        String json = toJson();
        datapack.addData(values.namespace + "/enchantment/" + values.path + ".json", json);
        if (values.curse) {
            datapack.addTag("enchantment", "minecraft:curse",
                    java.util.List.of(values.namespace + ":" + values.path));
        }
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered data-driven enchantment: " + values.namespace + ":" + values.path);
        return true;
    }

    String toJson() {
        if (values.supportedItems.isBlank()) {
            throw new IllegalArgumentException("Enchantment " + values.namespace + ":"
                    + values.path + " needs supportedItems before Register()");
        }
        return EnchantmentJson.render(values.toSpec());
    }
}
