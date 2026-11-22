package com.luatweaker.neoforge.integration.jei;

import com.luatweaker.common.integration.jei.CustomJeiRegistry;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableAnimated;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LuaCustomRecipeCategory implements IRecipeCategory<CustomJeiRegistry.CustomRecipeEntry> {

    private static final int ARROW_ANIMATION_TICKS = 40;
    private static final int FLAME_ANIMATION_TICKS = 300;

    private final CustomJeiRegistry.CustomCategorySpec spec;
    private final RecipeType<CustomJeiRegistry.CustomRecipeEntry> recipeType;
    private final IDrawable background;
    private final IDrawable icon;
    @Nullable
    private final IDrawableAnimated animatedArrow;
    @Nullable
    private final IDrawableAnimated animatedFlame;

    public LuaCustomRecipeCategory(CustomJeiRegistry.CustomCategorySpec spec, IGuiHelper guiHelper) {
        this.spec = spec;
        this.recipeType = RecipeType.create(spec.uid().getNamespace(), spec.uid().getPath(), CustomJeiRegistry.CustomRecipeEntry.class);
        this.background = createBackground(spec, guiHelper);
        this.icon = guiHelper.createDrawableItemStack(resolveIconStack(spec));
        this.animatedArrow = createAnimatedArrow(spec, guiHelper);
        this.animatedFlame = createAnimatedFlame(spec, guiHelper);
    }

    @NotNull
    public static LuaCustomRecipeCategory create(CustomJeiRegistry.CustomCategorySpec spec, IGuiHelper guiHelper) {
        return new LuaCustomRecipeCategory(spec, guiHelper);
    }

    private static IDrawable createBackground(CustomJeiRegistry.CustomCategorySpec specToDraw, IGuiHelper guiHelper) {
        String textureId = specToDraw.backgroundTexture();
        if (textureId != null && !textureId.isBlank()) {
            ResourceLocation tex = ResourceLocation.tryParse(textureId);
            if (tex != null) {
                return guiHelper.drawableBuilder(tex, 0, 0, specToDraw.width(), specToDraw.height()).build();
            }
        }
        return guiHelper.createBlankDrawable(specToDraw.width(), specToDraw.height());
    }

    @NotNull
    private static ItemStack resolveIconStack(CustomJeiRegistry.CustomCategorySpec specToDraw) {
        ItemStack iconStack = new ItemStack(Items.FURNACE);
        String itemId = specToDraw.iconItemId();
        if (itemId == null || itemId.isBlank()) {
            return iconStack;
        }
        ResourceLocation itemLoc = ResourceLocation.tryParse(itemId);
        if (itemLoc == null || !BuiltInRegistries.ITEM.containsKey(itemLoc)) {
            return iconStack;
        }
        Item item = BuiltInRegistries.ITEM.get(itemLoc);
        return new ItemStack(item);
    }

    @Nullable
    private static IDrawableAnimated createAnimatedArrow(CustomJeiRegistry.CustomCategorySpec specToDraw,
                                                          IGuiHelper guiHelper) {
        if (specToDraw.arrowX() < 0 || specToDraw.arrowY() < 0) {
            return null;
        }
        return guiHelper.createAnimatedRecipeArrow(ARROW_ANIMATION_TICKS);
    }

    @Nullable
    private static IDrawableAnimated createAnimatedFlame(CustomJeiRegistry.CustomCategorySpec specToDraw,
                                                          IGuiHelper guiHelper) {
        if (specToDraw.flameX() < 0 || specToDraw.flameY() < 0) {
            return null;
        }
        return guiHelper.createAnimatedRecipeFlame(FLAME_ANIMATION_TICKS);
    }

    @Override
    @NotNull
    public RecipeType<CustomJeiRegistry.CustomRecipeEntry> getRecipeType() {
        return recipeType;
    }

    @Override
    @NotNull
    public Component getTitle() {
        return spec.title();
    }

    @Override
    @NotNull
    public IDrawable getBackground() {
        return background;
    }

    @Override
    @NotNull
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, CustomJeiRegistry.CustomRecipeEntry recipe, IFocusGroup focuses) {
        int inIdx = 0;
        int outIdx = 0;

        for (CustomJeiRegistry.JeiSlotSpec slot : spec.slots()) {
            if (slot.isInput()) {
                var slotBuilder = builder.addInputSlot(slot.x(), slot.y());
                if (inIdx < recipe.inputs().size()) {
                    slotBuilder.addItemStack(recipe.inputs().get(inIdx));
                    inIdx++;
                }
            } else {
                var slotBuilder = builder.addOutputSlot(slot.x(), slot.y());
                if (outIdx < recipe.outputs().size()) {
                    slotBuilder.addItemStack(recipe.outputs().get(outIdx));
                    outIdx++;
                }
            }
        }
    }

    @Override
    public void draw(CustomJeiRegistry.CustomRecipeEntry recipe, IRecipeSlotsView recipeSlotsView,
                     GuiGraphics guiGraphics, double mouseX, double mouseY) {
        if (animatedArrow != null) {
            animatedArrow.draw(guiGraphics, spec.arrowX(), spec.arrowY());
        }
        if (animatedFlame != null) {
            animatedFlame.draw(guiGraphics, spec.flameX(), spec.flameY());
        }
    }
}
