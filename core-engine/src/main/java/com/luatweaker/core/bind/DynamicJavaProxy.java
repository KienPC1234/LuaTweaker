package com.luatweaker.core.bind;

import com.luatweaker.core.remap.RuntimeRemapper;
import org.squiddev.cobalt.*;
import org.squiddev.cobalt.function.LuaFunction;
import org.squiddev.cobalt.function.VarArgFunction;
import org.squiddev.cobalt.lib.CoreLibraries;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Proxy for Java Objects in LuaTweaker.
 * Intercepts property accesses (e.g. `event.amount`) and maps them to Java getters (`event.getAmount()`),
 * and maps assignments (`event.canceled = true`) to Java setters (`event.setCanceled(true)`).
 */
public class DynamicJavaProxy {
    
    // Method cache: Class -> (MethodName -> Method)
    private static final Map<Class<?>, Map<String, Method>> METHOD_CACHE = new ConcurrentHashMap<>();
    
    // Security Blacklist: Block dangerous packages (RCE, Data Exfiltration, DoS, Reflection Escape)
    private static final java.util.Set<String> PACKAGE_BLACKLIST = java.util.Set.of(
        "java.lang.Runtime",
        "java.lang.Process",
        "java.lang.ProcessBuilder",
        "java.lang.System",
        "java.lang.Thread",
        "java.lang.Class",
        "java.lang.ClassLoader",
        "java.lang.reflect.",
        "java.lang.invoke.",
        "java.io.",
        "java.nio.",
        "java.net.",
        "javax.net.",
        "sun.",
        "com.sun.",
        "jdk."
    );
    
    // Security Blacklist: Prevent dangerous Java reflection calls
    private static final java.util.Set<String> METHOD_BLACKLIST = java.util.Set.of(
        "getClass", "wait", "notify", "notifyAll", "clone", "finalize", "hashCode"
    );

    public static LuaUserdata create(LuaState state, Object javaObject) {
        LuaUserdata userdata = new LuaUserdata(javaObject);
        Class<?> clazz = javaObject.getClass();
        
        LuaTable metatable = new LuaTable();
        
        try {
            // __index interceptor
            metatable.rawset(Constants.INDEX, new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    if (args.arg(2).isNil()) return Constants.NIL;
                    String key = args.arg(2).checkString();

                    // 1. Exact method name => method call intent (obj:getX() / obj:setX(...)).
                    // Only an exact name match returns a wrapped function; a heuristic match
                    // ("Entity" -> getEntity) must NOT become a function, or property access
                    // like `event.Entity` yields a callable instead of the entity value.
                    Method exactMethod = findMethodCached(clazz, key);
                    if (exactMethod != null && exactMethod.getName().equals(key)) {
                        return wrapMethod(state, javaObject, exactMethod);
                    }

                    // 2. Property to Getter translation (e.g., "Health" or "health" -> "getHealth" / "isHealth")
                    String capitalizedKey = key.substring(0, 1).toUpperCase() + key.substring(1);
                    Method getter = findMethodCached(clazz, "get" + capitalizedKey);
                    if (getter == null) {
                        getter = findMethodCached(clazz, "is" + capitalizedKey);
                    }

                    if (getter != null && getter.getParameterCount() == 0) {
                        try {
                            Object result = getter.invoke(javaObject);
                            if (result == null) return Constants.NIL;
                            return wrapResult(state, result);
                        } catch (Exception e) {
                            throw new LuaError("Error invoking getter for " + key + ": " + e.getMessage());
                        }
                    }

                    return Constants.NIL;
                }
            });
    
            // __newindex interceptor
            metatable.rawset(Constants.NEWINDEX, new VarArgFunction() {
                @Override
                public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                    String key = args.arg(2).checkString();
                    LuaValue value = args.arg(3);
                    
                    String capitalizedKey = key.substring(0, 1).toUpperCase() + key.substring(1);
                    String setterName = "set" + capitalizedKey;
                    
                    Method setter = findMethodCached(clazz, setterName);
                    if (setter != null && setter.getParameterCount() == 1) {
                        try {
                            Object javaArg = coerceLuaToJava(value, setter.getParameterTypes()[0]);
                            setter.invoke(javaObject, javaArg);
                        } catch (Exception e) {
                            throw new LuaError("Error invoking setter for " + key + ": " + e.getMessage());
                        }
                    }
                    return Constants.NONE;
                }
            });
            
            userdata.setMetatable(state, metatable);
        } catch (LuaError e) {
            throw new RuntimeException("Failed to bind proxy metatable", e);
        }
        return userdata;
    }

    private static boolean isClassBlocked(Class<?> clazz) {
        String className = clazz.getName();
        for (String blocked : PACKAGE_BLACKLIST) {
            if (className.startsWith(blocked)) {
                return true;
            }
        }
        return false;
    }

    private static Method findMethodCached(Class<?> clazz, String methodName) {
        if (isClassBlocked(clazz)) return null;
        if (METHOD_BLACKLIST.contains(methodName)) return null;
        
        Map<String, Method> classCache = METHOD_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        
        if (classCache.containsKey(methodName)) {
            Method m = classCache.get(methodName);
            return m; 
        }

        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                classCache.put(methodName, m);
                return m;
            }
        }
        
        Method remapped = RuntimeRemapper.getInstance().resolveMethod(clazz, methodName);
        if (remapped != null) {
            classCache.put(methodName, remapped);
            return remapped;
        }
        
        return null;
    }
    
    private static LuaFunction wrapMethod(LuaState state, Object instance, Method method) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(LuaState state, Varargs args) throws LuaError {
                try {
                    int paramCount = method.getParameterCount();
                    Object[] javaArgs = new Object[paramCount];
                    int luaArgIndex = 1;
                    if (args.count() >= 1 && args.arg(1) instanceof LuaUserdata && ((LuaUserdata)args.arg(1)).instance == instance) {
                        luaArgIndex = 2; // Skip self
                    }
                    
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < paramCount; i++) {
                        javaArgs[i] = coerceLuaToJava(args.arg(luaArgIndex + i), paramTypes[i]);
                    }
                    
                    Object result = method.invoke(instance, javaArgs);
                    return wrapResult(state, result);
                } catch (Exception e) {
                    throw new LuaError("Error invoking Java method " + method.getName() + ": " + e.getMessage());
                }
            }
        };
    }
    
    public static LuaValue wrapResult(LuaState state, Object javaObject) {
        if (javaObject == null) return Constants.NIL;
        if (javaObject instanceof String) return ValueFactory.valueOf((String)javaObject);
        if (javaObject instanceof Number) return ValueFactory.valueOf(((Number)javaObject).doubleValue());
        if (javaObject instanceof Boolean) return ValueFactory.valueOf((Boolean)javaObject);
        if (javaObject instanceof com.luatweaker.api.vm.ILuaValue) return ((com.luatweaker.core.vm.CobaltLuaValue)javaObject).getCobaltValue();
        
        // Wrap complex objects dynamically
        return create(state, javaObject);
    }
    
    private static Object coerceLuaToJava(LuaValue luaVal, Class<?> targetType) throws LuaError {
        if (luaVal == null || luaVal.isNil()) return null;
        if (targetType == String.class) return luaVal.checkString();
        if (targetType == int.class || targetType == Integer.class) return luaVal.checkInteger();
        if (targetType == double.class || targetType == Double.class) return luaVal.checkDouble();
        if (targetType == float.class || targetType == Float.class) return (float) luaVal.checkDouble();
        if (targetType == boolean.class || targetType == Boolean.class) return luaVal.checkBoolean();
        if (luaVal instanceof LuaUserdata u) return u.instance;
        
        return null;
    }
}
