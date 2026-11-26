package com.luatweaker.neoforge.content.registrar;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies NeoForge dynamic fluid container bucket item model generation.
 */
class FluidBucketAssetsTest {

    @Test
    void injectWritesValidNeoForgeFluidContainerModelJson() {
        ContentServiceImpl content = new ContentServiceImpl();
        content.createFluid("liquid_crystal", null);
        content.createFluid("mymod:mana_fluid", null);

        DatapackServiceImpl datapack = new DatapackServiceImpl();
        content.getRegisteredFluids().forEach(fluid -> FluidBucketAssets.injectBucketAssets(datapack, fluid));

        JsonObject crystalModel = JsonParser.parseString(
                datapack.getVirtualFiles().get("assets/luatweaker/models/item/liquid_crystal_bucket.json"))
                .getAsJsonObject();
        JsonObject aliasModel = JsonParser.parseString(
                datapack.getVirtualFiles().get("assets/mymod/models/item/mana_bucket.json"))
                .getAsJsonObject();

        assertEquals(
                List.of(
                        "neoforge:fluid_container",
                        "neoforge:item/bucket",
                        "luatweaker:liquid_crystal",
                        "mymod:mana_fluid"),
                List.of(
                        crystalModel.get("loader").getAsString(),
                        crystalModel.get("parent").getAsString(),
                        crystalModel.get("fluid").getAsString(),
                        aliasModel.get("fluid").getAsString()),
                "NeoForge bucket models must bind neoforge:fluid_container to the exact fluid id");
    }
}
