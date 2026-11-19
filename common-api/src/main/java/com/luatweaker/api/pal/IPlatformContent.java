package com.luatweaker.api.pal;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import java.util.List;
import java.util.Set;

public interface IPlatformContent {
    IItem createItem(String itemId, int count);
    boolean itemExists(String itemId);
    boolean blockExists(String blockId);
    boolean fluidExists(String fluidId);
    boolean tagExists(String tagId);
    
    boolean isModLoaded(String modId);
    boolean isClient();
    boolean isDedicatedServer();

    /**
     * True when this process currently hosts a Minecraft server (integrated server in
     * single-player). Always false on a client joined to a remote server, so scripts can tell
     * "this VM can mutate the world" from "this VM is only a client".
     */
    default boolean hasLocalServer() { return false; }

    String getPlatformName();
    
    Set<String> getSupportedMobParents();
    List<IRecipe> getAllRecipes();
}
