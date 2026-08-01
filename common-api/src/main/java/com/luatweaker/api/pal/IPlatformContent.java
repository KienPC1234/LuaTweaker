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
    String getPlatformName();
    
    Set<String> getSupportedMobParents();
    List<IRecipe> getAllRecipes();
}
