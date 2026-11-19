package com.luatweaker.core.bind;

import com.luatweaker.core.remap.RuntimeRemapper;
import org.luava.binding.JavaAccessPolicy;
import org.luava.binding.LuaDataConverter;
import org.luava.runtime.LuaException;
import org.luava.runtime.LuaFunction;
import org.luava.runtime.LuaNil;
import org.luava.runtime.LuaState;
import org.luava.runtime.LuaTable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Proxy for Java Objects in LuaTweaker.
 * Intercepts property accesses (e.g. {@code event.amount}) and maps them to Java getters
 * ({@code event.getAmount()}), and assignments ({@code event.canceled = true}) to Java setters
 * ({@code event.setCanceled(true)}). Method names resolve exact matches first, then score
 * overloads by argument compatibility.
 */
public class DynamicJavaProxy {

    // Method cache: Class -> (MethodName -> single best Method, or empty when it does not exist).
    // The empty entry is the negative cache: a missing property must not re-scan reflection on
    // every access (event handlers read the same absent keys every tick).
    private static final Map<Class<?>, Map<String, java.util.Optional<Method>>> METHOD_CACHE =
            new ConcurrentHashMap<>();
    // Overload cache: Class -> (MethodName -> all candidates, including remapped ones)
    private static final Map<Class<?>, Map<String, List<Method>>> OVERLOAD_CACHE = new ConcurrentHashMap<>();
    // Declared public method names per class, computed once.
    private static final Map<Class<?>, java.util.Set<String>> PUBLIC_METHOD_NAMES = new ConcurrentHashMap<>();
    // Package blacklist verdict per class (GC-safe, no strong class references).
    private static final ClassValue<Boolean> PACKAGE_BLOCKED = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            String name = type.getName();
            for (String blocked : PACKAGE_BLACKLIST) {
                if (name.startsWith(blocked)) return true;
            }
            return false;
        }
    };

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

    /**
     * Creates a dynamic bridge userdata backed by luava. Property reads are translated to
     * getters ({@code Health -> getHealth()}, {@code isAlive -> isAlive()}), writes to setters,
     * and exact method names become invokable luava functions. Reflection is blocked by the
     * project package blacklist plus the active {@link JavaAccessPolicy}.
     *
     * @param state      active luava state (used to consult its Java access policy)
     * @param javaObject object to expose, must not be {@code null}
     * @return userdata with {@code __index}/{@code __newindex} metatable handlers
     */
    public static LuaUserdata createLuava(LuaState state, Object javaObject) {
        LuaUserdata userdata = new LuaUserdata(javaObject);
        Class<?> clazz = javaObject.getClass();
        LuaTable metatable = new LuaTable();

        metatable.rawset(LuaValue.Meta.INDEX, LuaFunction.of(args -> {
            if (args.length < 2 || args[1] == null || args[1].isNil()) return LuaNil.NIL;
            if (isClassBlocked(state, clazz)) return LuaNil.NIL;
            String key = args[1].toLuaString();
            if (key.isEmpty()) return LuaNil.NIL;

            if (key.startsWith("is") && key.length() > 2) {
                Method booleanGetter = findMethodCached(clazz, key);
                if (booleanGetter != null && booleanGetter.getParameterCount() == 0
                        && (booleanGetter.getReturnType() == boolean.class
                            || booleanGetter.getReturnType() == Boolean.class)) {
                    try {
                        return wrapResultLuava(state, booleanGetter.invoke(javaObject));
                    } catch (ReflectiveOperationException e) {
                        throw LuaFunction.hostError(
                                "Error invoking getter for " + key + ": " + e.getMessage(), e);
                    }
                }
            }

            // 1. Exact method name => method call intent (obj:getX() / obj:setX(...)).
            // Only an exact name match returns a wrapped function; a heuristic match
            // ("Entity" -> getEntity) must NOT become a function, or property access
            // like `event.Entity` yields a callable instead of the entity value.
            if (hasPublicMethodNamed(clazz, key)) {
                return wrapMethod(state, javaObject, key);
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
                    return result == null ? LuaNil.NIL : wrapResultLuava(state, result);
                } catch (ReflectiveOperationException e) {
                    throw LuaFunction.hostError(
                            "Error invoking getter for " + key + ": " + e.getMessage(), e);
                }
            }

            return LuaNil.NIL;
        }));

        metatable.rawset(LuaValue.Meta.NEWINDEX, LuaFunction.of(args -> {
            if (args.length < 3 || args[1] == null || args[1].isNil()) return LuaNil.NIL;
            if (isClassBlocked(state, clazz)) return LuaNil.NIL;
            String key = args[1].toLuaString();
            if (key.isEmpty()) return LuaNil.NIL;

            String capitalizedKey = key.substring(0, 1).toUpperCase() + key.substring(1);
            String setterName = "set" + capitalizedKey;
            Method setter = selectMethod(clazz, setterName, new LuaValue[]{args[2]});
            if (setter == null) {
                // Fail loudly: a silent no-op made `entity.Owner = x` disappear with no trace.
                throw new LuaException("No Java setter for property '" + key + "' on "
                        + clazz.getSimpleName());
            }
            try {
                Object javaArg = coerceLuaToJava(args[2], setter.getParameterTypes()[0]);
                setter.invoke(javaObject, javaArg);
            } catch (LuaException e) {
                throw e;
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw LuaFunction.hostError(
                        "Error invoking setter for " + key + ": " + e.getMessage(), e);
            }
            return LuaNil.NIL;
        }));

        userdata.setMetatable(metatable);
        return userdata;
    }

    private static boolean isClassBlocked(LuaState state, Class<?> clazz) {
        if (PACKAGE_BLOCKED.get(clazz)) return true;
        JavaAccessPolicy policy = (state != null && state.getJavaAccessPolicy() != null)
                ? state.getJavaAccessPolicy()
                : JavaAccessPolicy.active();
        return policy.isDenied(clazz.getName());
    }

    private static Method findMethodCached(Class<?> clazz, String methodName) {
        if (isClassBlocked(null, clazz)) return null;
        if (METHOD_BLACKLIST.contains(methodName)) return null;

        return METHOD_CACHE
                .computeIfAbsent(clazz, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(methodName, name -> java.util.Optional.ofNullable(resolveMethod(clazz, name)))
                .orElse(null);
    }

    /** Host-side lookup (reflection + remapper) behind the cached entry. */
    private static Method resolveMethod(Class<?> clazz, String methodName) {
        // Prefer the overload with the fewest parameters (getters/setters are
        // the common case); getMethods() order is not guaranteed, so caching
        // the first hit would be non-deterministic across overloads.
        Method best = null;
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(methodName)) {
                if (best == null || m.getParameterCount() < best.getParameterCount()) {
                    best = m;
                }
            }
        }
        if (best != null) {
            return best;
        }
        return RuntimeRemapper.getInstance().resolveMethod(clazz, methodName);
    }

    private static List<Method> findMethodsCached(Class<?> clazz, String methodName) {
        if (isClassBlocked(null, clazz)) return List.of();
        if (METHOD_BLACKLIST.contains(methodName)) return List.of();

        Map<String, List<Method>> classCache = OVERLOAD_CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>());
        return classCache.computeIfAbsent(methodName, name -> {
            List<Method> methods = new ArrayList<>();
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    methods.add(m);
                }
            }
            Method remapped = RuntimeRemapper.getInstance().resolveMethod(clazz, name);
            if (remapped != null && remapped.getName().equals(name)
                    && methods.stream().noneMatch(m -> sameSignature(m, remapped))) {
                methods.add(remapped);
            }
            return List.copyOf(methods);
        });
    }

    private static boolean hasPublicMethodNamed(Class<?> clazz, String methodName) {
        if (isClassBlocked(null, clazz)) return false;
        if (METHOD_BLACKLIST.contains(methodName)) return false;
        return PUBLIC_METHOD_NAMES
                .computeIfAbsent(clazz, k -> {
                    java.util.Set<String> names = new java.util.HashSet<>();
                    for (Method m : k.getMethods()) {
                        names.add(m.getName());
                    }
                    return java.util.Set.copyOf(names);
                })
                .contains(methodName);
    }

    private static boolean sameSignature(Method left, Method right) {
        return left.getName().equals(right.getName())
                && Arrays.equals(left.getParameterTypes(), right.getParameterTypes());
    }

    private static Method selectMethod(Class<?> clazz, String methodName, LuaValue[] args) {
        Method best = null;
        int bestScore = Integer.MAX_VALUE;
        boolean ambiguous = false;

        for (Method method : findMethodsCached(clazz, methodName)) {
            Class<?>[] paramTypes = method.getParameterTypes();
            if (paramTypes.length != args.length) continue;

            int score = 0;
            boolean compatible = true;
            for (int i = 0; i < paramTypes.length; i++) {
                int argScore = compatibilityScore(args[i], paramTypes[i]);
                if (argScore < 0) {
                    compatible = false;
                    break;
                }
                score += argScore;
            }
            if (!compatible) continue;

            if (score < bestScore) {
                best = method;
                bestScore = score;
                ambiguous = false;
            } else if (score == bestScore) {
                ambiguous = true;
            }
        }

        if (ambiguous) {
            throw new LuaException("Ambiguous Java overload for method " + methodName + " with "
                    + args.length + " argument(s)");
        }
        return best;
    }

    private static LuaFunction wrapMethod(LuaState state, Object instance, String methodName) {
        return LuaFunction.of(args -> {
            int luaArgIndex = 0;
            if (args.length >= 1 && args[0] instanceof LuaUserdata userdata
                    && userdata.getJavaInstance() == instance) {
                luaArgIndex = 1; // Skip self on colon calls.
            }
            int luaArgCount = args.length - luaArgIndex;
            LuaValue[] luaArgs = new LuaValue[luaArgCount];
            System.arraycopy(args, luaArgIndex, luaArgs, 0, luaArgCount);

            Method method = selectMethod(instance.getClass(), methodName, luaArgs);
            if (method == null) {
                throw new LuaException("No matching Java overload for method " + methodName + " with "
                        + luaArgCount + " argument(s)");
            }

            int paramCount = method.getParameterCount();
            Object[] javaArgs = new Object[paramCount];
            Class<?>[] paramTypes = method.getParameterTypes();
            for (int i = 0; i < paramCount; i++) {
                javaArgs[i] = coerceLuaToJava(luaArgs[i], paramTypes[i]);
            }

            try {
                Object result = method.invoke(instance, javaArgs);
                return wrapResultLuava(state, result);
            } catch (ReflectiveOperationException e) {
                throw LuaFunction.hostError(
                        "Error invoking Java method " + methodName + ": " + e.getMessage(), e);
            }
        });
    }

    /**
     * Converts a Java return value into a luava value.
     *
     * @param state      active luava state
     * @param javaObject value to wrap, may be {@code null}
     * @return the equivalent luava value; complex objects become dynamic proxies
     */
    public static LuaValue wrapResultLuava(LuaState state, Object javaObject) {
        if (javaObject == null) return LuaNil.NIL;
        if (javaObject instanceof String text) return LuaValue.valueOf(text);
        if (javaObject instanceof Number number) return LuaValue.valueOf(number.doubleValue());
        if (javaObject instanceof Boolean flag) return LuaValue.valueOf(flag);
        if (javaObject instanceof LuaValue luavaValue) return luavaValue;
        // Complex objects (including foreign ILuaValue implementations) stay usable
        // through the dynamic bridge instead of throwing a ClassCastException.
        return createLuava(state, javaObject);
    }

    private static Object coerceLuaToJava(LuaValue luaVal, Class<?> targetType) {
        if (luaVal == null || luaVal.isNil()) return null;
        if (targetType == String.class) return luaVal.toLuaString();
        if (targetType == int.class || targetType == Integer.class) return (int) luaVal.toLong();
        if (targetType == double.class || targetType == Double.class) return luaVal.toDouble();
        if (targetType == float.class || targetType == Float.class) return (float) luaVal.toDouble();
        if (targetType == boolean.class || targetType == Boolean.class) return luaVal.toBoolean();
        if (luaVal instanceof LuaUserdata userdata) return userdata.getJavaInstance();
        return LuaDataConverter.toJava(luaVal, targetType);
    }

    private static int compatibilityScore(LuaValue luaVal, Class<?> targetType) {
        if (luaVal == null || luaVal.isNil()) return targetType.isPrimitive() ? -1 : 4;
        if (targetType == String.class) return luaVal.isString() ? 0 : -1;
        if (targetType == boolean.class || targetType == Boolean.class) return luaVal.isBoolean() ? 0 : -1;
        if (targetType == int.class || targetType == Integer.class) {
            return luaVal.isNumber() && Math.rint(luaVal.toDouble()) == luaVal.toDouble() ? 0 : -1;
        }
        if (targetType == double.class || targetType == Double.class) {
            return luaVal.isNumber() ? (Math.rint(luaVal.toDouble()) == luaVal.toDouble() ? 1 : 0) : -1;
        }
        if (targetType == float.class || targetType == Float.class) return luaVal.isNumber() ? 2 : -1;
        if (luaVal instanceof LuaUserdata userdata) {
            return targetType.isInstance(userdata.getJavaInstance()) ? 0 : -1;
        }
        return -1;
    }
}
