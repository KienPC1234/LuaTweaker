package com.luatweaker.common.interaction;

import com.luatweaker.api.pal.IPlatformCapability;
import com.luatweaker.api.pal.Platform;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/** Block-to-block energy/fluid/item transfers behind the Lua {@code World} service. */
final class WorldTransferOps {

    private WorldTransferOps() {
    }

    static int transferEnergy(@NotNull String dimension, int fromX, int fromY, int fromZ,
                              int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        IPlatformCapability cap = Platform.getCapability();
        if (cap == null) {
            return 0;
        }
        int extracted = cap.extractEnergy(dimension, fromX, fromY, fromZ, maxAmount, null);
        if (extracted <= 0) {
            return 0;
        }
        int accepted = cap.insertEnergy(dimension, toX, toY, toZ, extracted, null);
        if (accepted < extracted) {
            cap.insertEnergy(dimension, fromX, fromY, fromZ, extracted - accepted, null);
        }
        return accepted;
    }

    static int transferFluid(@NotNull String dimension, int fromX, int fromY, int fromZ,
                             int toX, int toY, int toZ, int maxAmount, boolean simulate) {
        if (maxAmount <= 0) {
            return 0;
        }
        IPlatformCapability cap = Platform.getCapability();
        if (cap == null) {
            return 0;
        }
        Map<String, Object> fluidInfo = cap.getFluid(dimension, fromX, fromY, fromZ, null);
        if (fluidInfo == null) {
            return 0;
        }
        String fluidId = (String) fluidInfo.get("Id");
        if (fluidId == null || fluidId.isEmpty()) {
            return 0;
        }
        int drained = cap.drainFluid(dimension, fromX, fromY, fromZ, fluidId, maxAmount, null);
        if (drained <= 0) {
            return 0;
        }
        int filled = cap.fillFluid(dimension, toX, toY, toZ, fluidId, drained, null);
        if (filled < drained) {
            cap.fillFluid(dimension, fromX, fromY, fromZ, fluidId, drained - filled, null);
        }
        return filled;
    }

    /** One item transfer request (a value object because the PAL signature is 11 parameters). */
    record Transfer(int fromX, int fromY, int fromZ, int fromSlot,
                    int toX, int toY, int toZ, int toSlot, int maxCount, boolean simulate) {
    }

    static int transferItem(@NotNull String dimension, @NotNull Transfer request) {
        if (request.maxCount() <= 0) {
            return 0;
        }
        IPlatformCapability cap = Platform.getCapability();
        if (cap == null) {
            return 0;
        }
        Map<String, Object> extracted = cap.extractItem(dimension, request.fromX(), request.fromY(), request.fromZ(),
                request.fromSlot(), request.maxCount(), null);
        if (extracted == null) {
            return 0;
        }
        String itemId = (String) extracted.get("Id");
        int count = extracted.get("Count") instanceof Number n ? n.intValue() : 0;
        if (itemId == null || count <= 0) {
            return 0;
        }
        int inserted = cap.insertItem(dimension, request.toX(), request.toY(), request.toZ(), itemId, count, null);
        if (inserted < count) {
            cap.insertItem(dimension, request.fromX(), request.fromY(), request.fromZ(), itemId, count - inserted, null);
        }
        return inserted;
    }
}
