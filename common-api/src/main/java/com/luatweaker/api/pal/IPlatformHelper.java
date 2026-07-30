package com.luatweaker.api.pal;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import java.util.List;
import java.util.Set;

public interface IPlatformHelper {
    IItem createItem(String itemId, int count);
    boolean itemExists(String itemId);
    default boolean blockExists(String blockId) { return false; }
    default boolean fluidExists(String fluidId) { return false; }
    boolean tagExists(String tagId);
    default boolean isModLoaded(String modId) { return false; }
    default String getPlatformName() { return "Unknown"; }
    default Set<String> getSupportedMobParents() { return Set.of(); }
    List<IRecipe> getAllRecipes();
}
