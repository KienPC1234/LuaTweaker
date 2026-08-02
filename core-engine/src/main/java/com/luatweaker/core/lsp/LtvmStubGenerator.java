package com.luatweaker.core.lsp;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Dynamic EmmyLua Stub Generator for LuaTweaker (LTVM Engine).
 * 
 * Uses Java reflection on @LuaDoc annotations across registered API interfaces
 * to dynamically produce type-safe EmmyLua (.luatweaker/stubs/luatweaker-api.lua) stubs.
 */
public class LtvmStubGenerator {
    private final Map<String, Class<?>> registeredModules = new LinkedHashMap<>();
    private final Set<Class<?>> processedClasses = new HashSet<>();
    private final Map<Class<?>, String> classNameByType = new HashMap<>();
    private final StringBuilder classStubs = new StringBuilder();

    public LtvmStubGenerator() {
        // Automatically register standard core API object wrappers
        registerClassStub(IItem.class, "IItem");
        registerClassStub(ILocatedItem.class, "ILocatedItem");
        registerClassStub(IWorldBlock.class, "IWorldBlock");
        registerClassStub(IngredientWrapper.class, "IngredientWrapper");
        registerClassStub(ItemCount.class, "ItemCount");
    }

    /**
     * Registers a module or service interface for dynamic stub generation.
     *
     * @param moduleName       The module name used in require("LuaTweaker.ModuleName")
     * @param serviceInterface The Java interface or class annotated with @LuaDoc
     * @return This generator instance for chaining
     */
    public LtvmStubGenerator registerService(String moduleName, Class<?> serviceInterface) {
        if (moduleName != null && serviceInterface != null) {
            registeredModules.put(moduleName, serviceInterface);
            generateClassStub(serviceInterface, moduleName);
        }
        return this;
    }

    public LtvmStubGenerator registerClassStub(Class<?> clazz, String luaClassName) {
        if (clazz != null && luaClassName != null) {
            classNameByType.put(clazz, luaClassName);
            generateClassStub(clazz, luaClassName);
        }
        return this;
    }

    public void generateClassStub(Class<?> clazz, String luaClassName) {
        if (clazz == null || processedClasses.contains(clazz)) {
            return;
        }
        processedClasses.add(clazz);

        // Emit parent class stubs first so `---@class X: Parent` references resolve.
        StringBuilder inheritance = new StringBuilder();
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.isAnnotationPresent(LuaDoc.class)) {
                String parentName = classNameByType.getOrDefault(iface, iface.getSimpleName());
                generateClassStub(iface, parentName);
                if (inheritance.length() > 0) {
                    inheritance.append(", ");
                }
                inheritance.append(parentName);
            }
        }

        LuaDoc typeDoc = clazz.getAnnotation(LuaDoc.class);
        if (typeDoc != null && !typeDoc.description().isEmpty()) {
            classStubs.append("--- ").append(typeDoc.description()).append("\n");
        }
        classStubs.append("---@class ").append(luaClassName)
                .append(inheritance.length() > 0 ? ": " + inheritance : "")
                .append("\n");
        classStubs.append("local ").append(luaClassName).append(" = {}\n\n");

        for (Method method : clazz.getDeclaredMethods()) {
            LuaDoc doc = method.getAnnotation(LuaDoc.class);
            if (doc != null) {
                if (!doc.description().isEmpty()) {
                    classStubs.append("--- ").append(doc.description()).append("\n");
                }
                for (String param : doc.params()) {
                    classStubs.append("---@param ").append(param).append("\n");
                }
                if (!"void".equalsIgnoreCase(doc.returnType())) {
                    classStubs.append("---@return ").append(doc.returnType()).append("\n");
                }

                // Colon call signature (e.g. function Module:method(a, b) end)
                classStubs.append("function ").append(luaClassName).append(":")
                    .append(method.getName()).append("(");

                String[] params = doc.params();
                for (int i = 0; i < params.length; i++) {
                    String p = params[i];
                    int colonIndex = p.indexOf(':');
                    String pName = colonIndex != -1 ? p.substring(0, colonIndex).trim() : p.trim();
                    classStubs.append(pName);
                    if (i < params.length - 1) {
                        classStubs.append(", ");
                    }
                }
                classStubs.append(") end\n\n");

                // Dot call signature (e.g. function Module.method(a, b) end) for Static Namespace style
                classStubs.append("function ").append(luaClassName).append(".")
                    .append(method.getName()).append("(");
                for (int i = 0; i < params.length; i++) {
                    String p = params[i];
                    int colonIndex = p.indexOf(':');
                    String pName = colonIndex != -1 ? p.substring(0, colonIndex).trim() : p.trim();
                    classStubs.append(pName);
                    if (i < params.length - 1) {
                        classStubs.append(", ");
                    }
                }
                classStubs.append(") end\n\n");
            }

            // Transitive scanning: check return type for @LuaDoc
            Class<?> returnType = method.getReturnType();
            if (returnType.isAnnotationPresent(LuaDoc.class) && !processedClasses.contains(returnType)) {
                generateClassStub(returnType, classNameByType.getOrDefault(returnType, returnType.getSimpleName()));
            }
            // Transitive scanning: check parameter types for @LuaDoc
            for (Class<?> paramType : method.getParameterTypes()) {
                if (paramType.isAnnotationPresent(LuaDoc.class) && !processedClasses.contains(paramType)) {
                    generateClassStub(paramType, classNameByType.getOrDefault(paramType, paramType.getSimpleName()));
                }
            }
        }
    }

    public String getResult() {
        StringBuilder stub = new StringBuilder("---@meta\n-- Auto-generated by LTVM LuaTweaker Engine v1.0 (Dynamic Reflection Generator)\n\n");

        // 1. Dynamic require() Overloads for ALL Registered Modules
        stub.append("--- Native Module Loader for LuaTweaker Static Namespaces\n");
        for (String modName : registeredModules.keySet()) {
            stub.append("---@overload fun(modName: 'LuaTweaker.").append(modName).append("'): ").append(modName).append("\n");
            stub.append("---@overload fun(modName: '").append(modName).append("'): ").append(modName).append("\n");
        }
        stub.append("---@param modName string\n");
        stub.append("---@return any\n");
        stub.append("function require(modName) end\n\n");

        // 2. Legacy Mod Service Compatibility
        stub.append("--- Global Mod service manager\n");
        stub.append("---@class Mod\n");
        stub.append("Mod = {}\n");
        stub.append("---@param name string\n---@return any\nfunction Mod:GetService(name) end\n\n");

        // 3. Append all collected class stubs generated dynamically from @LuaDoc reflection
        stub.append(classStubs);

        return stub.toString();
    }
}

