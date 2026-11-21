package com.luatweaker.network;

import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Path-scoped circular-reference guard for table encoding.
 *
 * <p>Owns the active-path set for one encode call: {@link #enter} records a
 * table, {@link #exit} releases it when its subtree is done. A table that
 * appears twice on the <em>path</em> is a true cycle and fails fast; a shared
 * subtable referenced from two siblings encodes twice with no complaint.</p>
 */
final class CycleGuard {

    private final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Records a table on the active path.
     *
     * @return the identity token to hand back to {@link #exit}
     * @throws IllegalArgumentException when the table is already on the path (a cycle)
     */
    Object enter(@NotNull ILuaTable table) {
        // asTable() allocates a fresh wrapper per call, so identity must be taken from
        // the underlying table object or a cycle looks like two different tables.
        Object identity = table.toJavaObject();
        if (identity == null) {
            identity = table;
        }
        if (!active.add(identity)) {
            throw new IllegalArgumentException(
                    "Network payload contains a circular table reference; flatten it before sending");
        }
        return identity;
    }

    /** Releases a table recorded by {@link #enter} once its subtree is encoded. */
    void exit(Object identity) {
        active.remove(identity);
    }
}
