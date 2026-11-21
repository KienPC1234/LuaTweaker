package com.luatweaker.core.bind;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaUserdata;
import org.luava.runtime.LuaValue;

/**
 * Rates how well a Lua value fits one Java parameter type.
 *
 * <p>Overload resolution sums these scores per candidate: 0 is an exact fit,
 * positive values are lossy-but-usable fits and negative means the value cannot
 * convert at all. Kept apart from {@link LuaCoercion} so the conversion path and
 * the scoring policy can change independently.</p>
 */
final class LuaCompatibilityScorer {
    private LuaCompatibilityScorer() {}

    /** Exact type match. */
    private static final int SCORE_EXACT = 0;
    /** Lua nil fits any reference type, but no primitive. */
    private static final int SCORE_NIL_FOR_REFERENCE = 4;
    /** Anything fits {@code Object}. */
    private static final int SCORE_ANY_REFERENCE = 10;
    /** Value converts, but precision can be lost (e.g. Lua float into a double slot). */
    private static final int SCORE_LOSSY = 1;
    /** Value converts through a widening numeric conversion. */
    private static final int SCORE_WIDENED = 2;
    /** Value cannot convert to the requested type. */
    private static final int SCORE_INCOMPATIBLE = -1;

    /**
     * @return {@link #SCORE_EXACT} for an exact fit, higher for lossy-but-usable
     *         fits, {@link #SCORE_INCOMPATIBLE} when the value cannot convert
     */
    static int compatibilityScore(@Nullable LuaValue luaVal, @NotNull Class<?> targetType) {
        if (luaVal == null || luaVal.isNil()) {
            return targetType.isPrimitive() ? SCORE_INCOMPATIBLE : SCORE_NIL_FOR_REFERENCE;
        }
        if (targetType == Object.class) {
            return SCORE_ANY_REFERENCE;
        }
        if (isNumericType(targetType)) {
            return scoreNumeric(luaVal, targetType);
        }
        return scoreNonNumeric(luaVal, targetType);
    }

    private static int scoreNonNumeric(@NotNull LuaValue luaVal, @NotNull Class<?> targetType) {
        if (targetType == String.class) {
            return luaVal.isString() ? SCORE_EXACT : SCORE_INCOMPATIBLE;
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            return luaVal.isBoolean() ? SCORE_EXACT : SCORE_INCOMPATIBLE;
        }
        return scoreUserdata(luaVal, targetType);
    }

    private static int scoreUserdata(@NotNull LuaValue luaVal, @NotNull Class<?> targetType) {
        if (luaVal instanceof LuaUserdata userdata) {
            return targetType.isInstance(userdata.getJavaInstance()) ? SCORE_EXACT : SCORE_INCOMPATIBLE;
        }
        return SCORE_INCOMPATIBLE;
    }

    private static boolean isNumericType(@NotNull Class<?> targetType) {
        return targetType == int.class || targetType == Integer.class
            || targetType == long.class || targetType == Long.class
            || targetType == double.class || targetType == Double.class
            || targetType == float.class || targetType == Float.class;
    }

    private static int scoreNumeric(@NotNull LuaValue luaVal, @NotNull Class<?> targetType) {
        if (!luaVal.isNumber()) {
            return SCORE_INCOMPATIBLE;
        }
        boolean integral = isIntegralNumber(luaVal);
        if (targetType == int.class || targetType == Integer.class) {
            return integral ? SCORE_EXACT : SCORE_INCOMPATIBLE;
        }
        if (targetType == double.class || targetType == Double.class) {
            return integral ? SCORE_LOSSY : SCORE_EXACT;
        }
        return SCORE_WIDENED;
    }

    private static boolean isIntegralNumber(@NotNull LuaValue luaVal) {
        return luaVal.isNumber() && Math.rint(luaVal.toDouble()) == luaVal.toDouble();
    }

    /**
     * Scores one overload candidate against the actual arguments.
     *
     * @return total score, or {@link #SCORE_INCOMPATIBLE} when any argument is incompatible
     */
    static int scoreArguments(@NotNull Class<?>[] paramTypes, @NotNull LuaValue... args) {
        int score = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            int argScore = compatibilityScore(args[i], paramTypes[i]);
            if (argScore < 0) {
                return SCORE_INCOMPATIBLE;
            }
            score += argScore;
        }
        return score;
    }
}
