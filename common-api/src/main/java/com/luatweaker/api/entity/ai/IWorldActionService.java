package com.luatweaker.api.entity.ai;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import org.jetbrains.annotations.NotNull;
import java.util.List;

@LuaDoc(description = "Service allowing AI or players to perform physical world interactions.")
public interface IWorldActionService {
    @LuaDoc(description = "Breaks a block at the coordinates.", params = {"actor: table", "x: number", "y: number", "z: number"}, returnType = "boolean")
    boolean breakBlock(@NotNull IEntity actor, int x, int y, int z);

    @LuaDoc(description = "Places a block at the coordinates.", params = {"actor: table", "x: number", "y: number", "z: number", "blockId: string"}, returnType = "boolean")
    boolean placeBlock(@NotNull IEntity actor, int x, int y, int z, @NotNull String blockId);

    @LuaDoc(description = "Uses / interacts with a block at the coordinates.", params = {"actor: table", "x: number", "y: number", "z: number"}, returnType = "boolean")
    boolean useBlock(@NotNull IEntity actor, int x, int y, int z);

    @LuaDoc(description = "Uses the item in the specified inventory slot.", params = {"actor: table", "slot: integer"}, returnType = "boolean")
    boolean useItem(@NotNull IEntity actor, int slot);

    @LuaDoc(description = "Commands the actor to look at coordinates.", params = {"actor: table", "x: number", "y: number", "z: number"})
    void lookAt(@NotNull IEntity actor, double x, double y, double z);

    @LuaDoc(description = "Commands the actor to look at another entity.", params = {"actor: table", "target: table"})
    void lookAt(@NotNull IEntity actor, @NotNull IEntity target);

    @LuaDoc(description = "Moves items between two inventory slots.", params = {"actor: table", "fromSlot: integer", "toSlot: integer"}, returnType = "boolean")
    boolean moveInventoryItem(@NotNull IEntity actor, int fromSlot, int toSlot);

    @LuaDoc(description = "Drops items from the specified slot.", params = {"actor: table", "slot: integer", "count: integer"}, returnType = "boolean")
    boolean dropInventoryItem(@NotNull IEntity actor, int slot, int count);

    @LuaDoc(description = "Scans and retrieves blocks within a radius around the entity.", params = {"entity: table", "radius: integer"}, returnType = "table")
    @NotNull
    List<IWorldBlock> getNearbyBlocks(@NotNull IEntity entity, int radius);

    @LuaDoc(description = "Retrieves all items in the inventory of the entity.", params = {"entity: table"}, returnType = "table")
    @NotNull
    List<ILocatedItem> getInventoryItems(@NotNull IEntity entity);
}
