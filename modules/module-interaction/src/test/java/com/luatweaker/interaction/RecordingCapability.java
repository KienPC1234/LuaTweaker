package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformCapability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Capability double that records every call as {@code name dim x,y,z side [details]} so tests can
 * assert the exact argument order the Lua bindings produce.
 */
final class RecordingCapability implements IPlatformCapability {

    private static final String IRON_INGOT = "minecraft:iron_ingot";
    private static final String COAL = "minecraft:coal";
    private static final String WATER = "minecraft:water";

    final List<String> calls = new ArrayList<>();

    private void remember(String call, String dimension, int x, int y, int z, String side,
                          String details) {
        calls.add(call + " " + dimension + " " + x + "," + y + "," + z + " "
                + (side == null ? "null" : side) + (details == null ? "" : " " + details));
    }

    @Override
    public Map<String, Object> getEnergy(String dimension, int x, int y, int z, String side) {
        remember("getEnergy", dimension, x, y, z, side, null);
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("Stored", 100);
        data.put("Max", 400);
        data.put("CanExtract", true);
        data.put("CanReceive", false);
        return data;
    }

    @Override
    public int insertEnergy(String dimension, int x, int y, int z, int amount, String side) {
        remember("insertEnergy", dimension, x, y, z, side, "amount=" + amount);
        return 7;
    }

    @Override
    public int extractEnergy(String dimension, int x, int y, int z, int amount, String side) {
        remember("extractEnergy", dimension, x, y, z, side, "amount=" + amount);
        return 3;
    }

    @Override
    public List<Map<String, Object>> getItemSlots(String dimension, int x, int y, int z, String side) {
        remember("getItemSlots", dimension, x, y, z, side, null);
        Map<String, Object> entry = new ConcurrentHashMap<>();
        entry.put("Slot", 2);
        entry.put("Id", IRON_INGOT);
        entry.put("Count", 5);
        return List.of(entry);
    }

    @Override
    public int insertItem(String dimension, int x, int y, int z, String itemId, int count, String side) {
        remember("insertItem", dimension, x, y, z, side, "item=" + itemId + " count=" + count);
        return 4;
    }

    @Override
    public Map<String, Object> extractItem(String dimension, int x, int y, int z,
                                           int slot, int count, String side) {
        remember("extractItem", dimension, x, y, z, side, "slot=" + slot + " count=" + count);
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("Slot", slot);
        data.put("Id", COAL);
        data.put("Count", 2);
        return data;
    }

    @Override
    public Map<String, Object> getFluid(String dimension, int x, int y, int z, String side) {
        remember("getFluid", dimension, x, y, z, side, null);
        Map<String, Object> data = new ConcurrentHashMap<>();
        data.put("Id", WATER);
        data.put("Amount", 500);
        data.put("Capacity", 1000);
        return data;
    }

    @Override
    public int fillFluid(String dimension, int x, int y, int z, String fluidId, int amount, String side) {
        remember("fillFluid", dimension, x, y, z, side, "fluid=" + fluidId + " amount=" + amount);
        return 250;
    }

    @Override
    public int drainFluid(String dimension, int x, int y, int z, String fluidId, int amount, String side) {
        remember("drainFluid", dimension, x, y, z, side, "fluid=" + fluidId + " amount=" + amount);
        return 125;
    }
}
