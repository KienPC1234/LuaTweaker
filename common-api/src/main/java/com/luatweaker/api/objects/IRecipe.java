package com.luatweaker.api.objects;

import com.luatweaker.api.wrapper.IngredientWrapper;
import java.util.List;

public interface IRecipe {
    String getId();
    String getType();
    List<IngredientWrapper> getIngredients();
    IItem getResult();
    Object getRawRecipe();
}
