package com.luatweaker.core.lsp;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class LspStubGenerator {

    public static void generateStubs(File targetDir) {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        File stubFile = new File(targetDir, "luatweaker-api.lua");

        try (PrintWriter writer = new PrintWriter(new FileWriter(stubFile, false))) {
            writer.println("---@meta");
            writer.println("--- LuaTweaker API Autocomplete Stubs");
            writer.println("--- Dynamically generated from @LuaDoc annotations");
            writer.println();

            // Core Global definitions
            writer.println("--- Global Mod service manager");
            writer.println("---@class Mod");
            writer.println("Mod = {}");
            writer.println();
            writer.println("--- Resolves a registered service.");
            writer.println("---@param name 'Recipes'");
            writer.println("---@return Recipes");
            writer.println("function Mod:GetService(name) end");
            writer.println();
            writer.println("--- Global game service manager (alias for Mod)");
            writer.println("---@class game : Mod");
            writer.println("game = {}");
            writer.println();
            writer.println("--- Creates an abstract item wrapper.");
            writer.println("---@param itemId string The Registry ID of the item.");
            writer.println("---@param count number? The count of the item stack (defaults to 1).");
            writer.println("---@return IItem");
            writer.println("function item(itemId, count) end");
            writer.println();
            writer.println("--- Creates an ingredient wrapper.");
            writer.println("---@param descriptor string The registry ID or tag (prefixed with #).");
            writer.println("---@return IngredientWrapper");
            writer.println("function ingredient(descriptor) end");
            writer.println();

            // Dynamic reflection generation for interfaces/classes with @LuaDoc
            generateClassStubFromReflection(writer, IItem.class, "IItem", null);
            generateClassStubFromReflection(writer, IngredientWrapper.class, "IngredientWrapper", null);
            generateClassStubFromReflection(writer, IRecipeManagerService.class, "Recipes", "recipes");

            writer.flush();
        } catch (IOException e) {
            System.err.println("[LuaTweaker] LSP generator failed to write stubs: " + e.getMessage());
        }
    }

    private static void generateClassStubFromReflection(PrintWriter writer, Class<?> clazz, String luaClassName, String instanceVarName) {
        LuaDoc classDoc = clazz.getAnnotation(LuaDoc.class);
        if (classDoc != null && !classDoc.description().isEmpty()) {
            writer.println("--- " + classDoc.description());
        }
        writer.println("---@class " + luaClassName);
        if (instanceVarName != null) {
            writer.println("local " + instanceVarName + " = {}");
        }
        writer.println();

        for (Method method : clazz.getDeclaredMethods()) {
            LuaDoc methodDoc = method.getAnnotation(LuaDoc.class);
            if (methodDoc == null) continue;

            if (!methodDoc.description().isEmpty()) {
                writer.println("--- " + methodDoc.description());
            }

            List<String> paramNames = new ArrayList<>();
            for (String param : methodDoc.params()) {
                writer.println("---@param " + param);
                String name = param.contains(":") ? param.split(":")[0].trim() : param.trim();
                paramNames.add(name);
            }

            if (!"void".equalsIgnoreCase(methodDoc.returnType())) {
                writer.println("---@return " + methodDoc.returnType());
            }

            String targetName = instanceVarName != null ? instanceVarName : luaClassName;
            String sep = instanceVarName != null ? ":" : ".";
            writer.println("function " + targetName + sep + method.getName() + "(" + String.join(", ", paramNames) + ") end");
            writer.println();
        }
    }
}
