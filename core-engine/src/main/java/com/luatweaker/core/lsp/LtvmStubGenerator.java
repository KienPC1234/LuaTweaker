package com.luatweaker.core.lsp;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.ILocatedItem;
import com.luatweaker.api.objects.IWorldBlock;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.stub.StubCatalog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic EmmyLua Stub Generator for LuaTweaker (LTVM Engine).
 * 
 * Uses Java reflection on @LuaDoc annotations across registered API interfaces
 * to dynamically produce type-safe EmmyLua (.luatweaker/stubs/luatweaker-api.lua) stubs.
 */
public class LtvmStubGenerator {
    // Insertion-ordered: stub output order follows registration order, so the map is
    // created as a LinkedHashMap in the constructor (single-threaded use; no concurrent access).
    private final Map<String, Class<?>> registeredModules;
    private final Set<Class<?>> processedClasses = new HashSet<>();
    private final Map<Class<?>, String> classNameByType;
    // Chunks (not one growing buffer): emission order is registration order and
    // getResult() joins once, so no StringBuilder field is retained.
    private final List<String> classStubs = new ArrayList<>();

    public LtvmStubGenerator() {
        this.registeredModules = new LinkedHashMap<>();
        this.classNameByType = new HashMap<>();
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
    public final LtvmStubGenerator registerService(String moduleName, Class<?> serviceInterface) {
        if (moduleName != null && serviceInterface != null) {
            registeredModules.put(moduleName, serviceInterface);
            generateClassStub(serviceInterface, moduleName);
        }
        return this;
    }

    public final LtvmStubGenerator registerClassStub(Class<?> clazz, String luaClassName) {
        if (clazz != null && luaClassName != null) {
            classNameByType.put(clazz, luaClassName);
            generateClassStub(clazz, luaClassName);
        }
        return this;
    }

    public final void generateClassStub(Class<?> clazz, String luaClassName) {
        if (clazz == null || processedClasses.contains(clazz)) {
            return;
        }
        processedClasses.add(clazz);
        emitClassHeader(clazz, luaClassName);
        for (Method method : clazz.getDeclaredMethods()) {
            emitMethodStub(luaClassName, method);
            scanTransitiveTypes(method);
        }
    }

    /** Emits the {@code ---doc} / {@code ---@class} / {@code local X = {}} header. */
    private void emitClassHeader(Class<?> clazz, String luaClassName) {
        // Emit parent class stubs first so `---@class X: Parent` references resolve.
        List<String> parents = new ArrayList<>();
        for (Class<?> iface : clazz.getInterfaces()) {
            if (iface.isAnnotationPresent(LuaDoc.class)) {
                String parentName = classNameByType.getOrDefault(iface, iface.getSimpleName());
                generateClassStub(iface, parentName);
                parents.add(parentName);
            }
        }
        LuaDoc typeDoc = clazz.getAnnotation(LuaDoc.class);
        if (typeDoc != null && !typeDoc.description().isEmpty()) {
            classStubs.add("--- " + typeDoc.description() + "\n");
        }
        String inheritance = parents.isEmpty() ? "" : ": " + String.join(", ", parents);
        classStubs.add("---@class " + luaClassName + inheritance + "\n");
        classStubs.add("local " + luaClassName + " = {}\n\n");
    }

    /** Emits the doc comment plus colon-call and dot-call signatures for one method. */
    private void emitMethodStub(String luaClassName, Method method) {
        LuaDoc doc = method.getAnnotation(LuaDoc.class);
        if (doc == null) {
            return;
        }
        if (!doc.description().isEmpty()) {
            classStubs.add("--- " + doc.description() + "\n");
        }
        for (String param : doc.params()) {
            classStubs.add("---@param " + param + "\n");
        }
        if (!"void".equalsIgnoreCase(doc.returnType())) {
            classStubs.add("---@return " + doc.returnType() + "\n");
        }
        String argList = luaArgList(doc.params());
        // Colon call signature (e.g. function Module:method(a, b) end)
        classStubs.add("function " + luaClassName + ":" + method.getName() + "(" + argList + ") end\n\n");
        // Dot call signature (e.g. function Module.method(a, b) end) for Static Namespace style
        classStubs.add("function " + luaClassName + "." + method.getName() + "(" + argList + ") end\n\n");
    }

    /** Renders {@code {"a: number", "b: string"}} as {@code "a, b"}. */
    private static String luaArgList(String... params) {
        List<String> names = new ArrayList<>(params.length);
        for (String param : params) {
            names.add(luaParamName(param));
        }
        return String.join(", ", names);
    }

    private static String luaParamName(String param) {
        int colonIndex = param.indexOf(':');
        if (colonIndex < 0) {
            return param.trim();
        }
        return param.substring(0, colonIndex).trim();
    }

    /** Transitive scanning: queue @LuaDoc return and parameter types for stubbing. */
    private void scanTransitiveTypes(Method method) {
        Class<?> returnType = method.getReturnType();
        if (returnType.isAnnotationPresent(LuaDoc.class) && !processedClasses.contains(returnType)) {
            generateClassStub(returnType, classNameByType.getOrDefault(returnType, returnType.getSimpleName()));
        }
        for (Class<?> paramType : method.getParameterTypes()) {
            if (paramType.isAnnotationPresent(LuaDoc.class) && !processedClasses.contains(paramType)) {
                generateClassStub(paramType, classNameByType.getOrDefault(paramType, paramType.getSimpleName()));
            }
        }
    }

    public String getResult() {
        // Merge static StubCatalog entries (registered by *LuaBinding classes)
        // in addition to instance registrations. Idempotent: processedClasses
        // guards re-generation, putIfAbsent guards duplicate names.
        var merged = new LinkedHashMap<String, Class<?>>(registeredModules);
        for (Map.Entry<String, Class<?>> entry : StubCatalog.snapshot().entrySet()) {
            merged.putIfAbsent(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, Class<?>> entry : merged.entrySet()) {
            if (!registeredModules.containsKey(entry.getKey())) {
                generateClassStub(entry.getValue(), entry.getKey());
            }
        }

        List<String> out = new ArrayList<>();
        out.add("---@meta\n-- Auto-generated by LTVM LuaTweaker Engine v1.0 (Dynamic Reflection Generator)\n\n");

        // 1. Dynamic require() Overloads for ALL Registered Modules
        out.add("--- Native Module Loader for LuaTweaker Static Namespaces\n");
        for (String modName : merged.keySet()) {
            out.add("---@overload fun(modName: 'LuaTweaker." + modName + "'): " + modName + "\n");
            out.add("---@overload fun(modName: '" + modName + "'): " + modName + "\n");
        }
        out.add("---@param modName string\n");
        out.add("---@return any\n");
        out.add("function require(modName) end\n\n");

        // 2. Legacy Mod Service Compatibility
        out.add("--- Global Mod service manager\n");
        out.add("---@class Mod\n");
        out.add("Mod = {}\n");
        out.add("---@param name string\n---@return any\nfunction Mod:GetService(name) end\n\n");

        // 3. Append all collected class stubs generated dynamically from @LuaDoc reflection
        out.add(String.join("", classStubs));

        return String.join("", out);
    }
}

