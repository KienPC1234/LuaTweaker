package com.luatweaker.api.pal;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import java.util.List;

public interface IPlatformHelper {
    IItem createItem(String itemId, int count);
    boolean itemExists(String itemId);
    boolean tagExists(String tagId);
    List<IRecipe> getAllRecipes();
}
