package com.luatweaker.math;

import com.luatweaker.api.vm.*;
import org.jetbrains.annotations.NotNull;

public class MathLuaBinding {
    private static ILuaValue wrapFunction(ILuaEngine engine, ILuaFunction function) {
        ILuaTable temp = engine.createTable();
        temp.rawset("fn", function);
        return temp.rawget("fn");
    }

    private static boolean isNumber(ILuaValue val) {
        return val != null && val.toJavaObject() instanceof Number;
    }

    public static void registerBindings(@NotNull ILuaEngine engine) {
        // 1. Vector3 Constructor
        ILuaTable v3Table = engine.createTable();
        v3Table.rawset("new", args -> {
            double x = args.length >= 2 ? args[1].asDouble() : 0;
            double y = args.length >= 3 ? args[2].asDouble() : 0;
            double z = args.length >= 4 ? args[3].asDouble() : 0;
            return createVector3(engine, x, y, z);
        });
        v3Table.rawset("zero", createVector3(engine, 0, 0, 0));
        v3Table.rawset("one", createVector3(engine, 1, 1, 1));
        engine.registerGlobal("Vector3", v3Table);

        // 2. Vector2 Constructor
        ILuaTable v2Table = engine.createTable();
        v2Table.rawset("new", args -> {
            double x = args.length >= 2 ? args[1].asDouble() : 0;
            double y = args.length >= 3 ? args[2].asDouble() : 0;
            return createVector2(engine, x, y);
        });
        v2Table.rawset("zero", createVector2(engine, 0, 0));
        v2Table.rawset("one", createVector2(engine, 1, 1));
        engine.registerGlobal("Vector2", v2Table);

        // 3. Color3 Constructor
        ILuaTable c3Table = engine.createTable();
        c3Table.rawset("new", args -> {
            double r = args.length >= 2 ? args[1].asDouble() : 0;
            double g = args.length >= 3 ? args[2].asDouble() : 0;
            double b = args.length >= 4 ? args[3].asDouble() : 0;
            return createColor3(engine, r, g, b);
        });
        c3Table.rawset("fromRGB", args -> {
            double r = args.length >= 2 ? args[1].asDouble() : 0;
            double g = args.length >= 3 ? args[2].asDouble() : 0;
            double b = args.length >= 4 ? args[3].asDouble() : 0;
            return createColor3(engine, r / 255.0, g / 255.0, b / 255.0);
        });
        c3Table.rawset("fromHex", args -> {
            if (args.length < 2) return createColor3(engine, 0, 0, 0);
            Color3 color = Color3.fromHex(args[1].asString());
            return createColor3(engine, color.r(), color.g(), color.b());
        });
        c3Table.rawset("black", createColor3(engine, 0, 0, 0));
        c3Table.rawset("white", createColor3(engine, 1, 1, 1));
        engine.registerGlobal("Color3", c3Table);

        // Register bootstrapped Roblox task and Signal as services
        ILuaValue taskService = engine.getGlobalEnvironment().rawget("task");
        if (taskService != null && !taskService.isNil()) {
            engine.registerService("Task", taskService);
        }
        ILuaValue sigService = engine.getGlobalEnvironment().rawget("Signal");
        if (sigService != null && !sigService.isNil()) {
            engine.registerService("Signal", sigService);
        }

        // 5. Instance Constructor
        ILuaTable instConstructor = engine.createTable();
        instConstructor.rawset("new", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Instance.new requires a className string");
            }
            String className = args[1].asString();
            LuaInstance parent = null;
            if (args.length >= 3 && args[2] instanceof ILuaTable pTable) {
                ILuaValue rawInst = pTable.rawget("__instance");
                if (rawInst != null && !rawInst.isNil()) {
                    Object ud = rawInst.toJavaObject();
                    if (ud instanceof LuaInstance parentInst) {
                        parent = parentInst;
                    }
                }
            }
            LuaInstance inst = new LuaInstance(className, parent);
            return createInstance(engine, inst);
        });
        engine.registerGlobal("Instance", instConstructor);

        // 6. Native High-Performance Java Math Extensions
        ILuaValue globalMath = engine.getGlobalEnvironment().rawget("math");
        if (globalMath != null && globalMath.isTable()) {
            ILuaTable mathTbl = globalMath.asTable();
            mathTbl.rawset("clamp", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 3) return engine.wrapNumber(0);
                double val = args[off].asDouble();
                double min = args[off + 1].asDouble();
                double max = args[off + 2].asDouble();
                return engine.wrapNumber(Math.clamp(val, min, max));
            });
            mathTbl.rawset("sign", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) return engine.wrapNumber(0);
                return engine.wrapNumber(Math.signum(args[off].asDouble()));
            });
            mathTbl.rawset("round", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) return engine.wrapNumber(0);
                return engine.wrapNumber((double) Math.round(args[off].asDouble()));
            });
            mathTbl.rawset("lerp", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 3) return engine.wrapNumber(0);
                double a = args[off].asDouble();
                double b = args[off + 1].asDouble();
                double t = args[off + 2].asDouble();
                return engine.wrapNumber(a + (b - a) * t);
            });
        }

        // 7. Native High-Performance Java String Extensions
        ILuaValue globalString = engine.getGlobalEnvironment().rawget("string");
        if (globalString != null && globalString.isTable()) {
            ILuaTable strTbl = globalString.asTable();
            strTbl.rawset("split", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) return engine.createTable();
                String input = args[off].asString();
                String sep = args.length - off >= 2 ? args[off + 1].asString() : ",";
                String[] parts = input.split(java.util.regex.Pattern.quote(sep));
                ILuaTable result = engine.createTable();
                for (int i = 0; i < parts.length; i++) {
                    result.rawset(i + 1, engine.wrapString(parts[i]));
                }
                return result;
            });
            strTbl.rawset("trim", args -> {
                int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
                if (args.length - off < 1) return engine.wrapString("");
                return engine.wrapString(args[off].asString().trim());
            });
        }
    }

    public static ILuaTable createVector3(ILuaEngine engine, double x, double y, double z) {
        ILuaTable vecTable = engine.createTable();
        vecTable.rawset("X", x);
        vecTable.rawset("Y", y);
        vecTable.rawset("Z", z);
        vecTable.rawset("x", x);
        vecTable.rawset("y", y);
        vecTable.rawset("z", z);

        double mag = Math.sqrt(x * x + y * y + z * z);
        vecTable.rawset("Magnitude", mag);
        vecTable.rawset("magnitude", mag);

        double uX = mag == 0 ? 0 : x / mag;
        double uY = mag == 0 ? 0 : y / mag;
        double uZ = mag == 0 ? 0 : z / mag;
        
        ILuaTable unitTable = engine.createTable();
        unitTable.rawset("X", uX); unitTable.rawset("Y", uY); unitTable.rawset("Z", uZ);
        unitTable.rawset("Magnitude", 1.0);
        vecTable.rawset("Unit", unitTable);
        vecTable.rawset("unit", unitTable);

        // Methods
        vecTable.rawset("Dot", args -> {
            ILuaTable other = args[1].asTable();
            double dot = x * other.rawget("X").asDouble() +
                         y * other.rawget("Y").asDouble() +
                         z * other.rawget("Z").asDouble();
            return engine.wrapNumber(dot);
        });

        vecTable.rawset("Cross", args -> {
            ILuaTable other = args[1].asTable();
            double oX = other.rawget("X").asDouble();
            double oY = other.rawget("Y").asDouble();
            double oZ = other.rawget("Z").asDouble();
            return createVector3(engine,
                y * oZ - z * oY,
                z * oX - x * oZ,
                x * oY - y * oX
            );
        });

        vecTable.rawset("Lerp", args -> {
            ILuaTable other = args[1].asTable();
            double alpha = args[2].asDouble();
            double oX = other.rawget("X").asDouble();
            double oY = other.rawget("Y").asDouble();
            double oZ = other.rawget("Z").asDouble();
            return createVector3(engine,
                x + (oX - x) * alpha,
                y + (oY - y) * alpha,
                z + (oZ - z) * alpha
            );
        });

        vecTable.rawset("Angle", args -> {
            ILuaTable other = args[1].asTable();
            double oX = other.rawget("X").asDouble();
            double oY = other.rawget("Y").asDouble();
            double oZ = other.rawget("Z").asDouble();
            double mag2 = Math.sqrt(oX * oX + oY * oY + oZ * oZ);
            if (mag == 0 || mag2 == 0) return engine.wrapNumber(0);
            double dot = x * oX + y * oY + z * oZ;
            double cos = dot / (mag * mag2);
            return engine.wrapNumber(Math.acos(Math.clamp(cos, -1.0, 1.0)));
        });

        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", vecTable);
        metaTable.rawset("__add", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return createVector3(engine,
                a.rawget("X").asDouble() + bVal.rawget("X").asDouble(),
                a.rawget("Y").asDouble() + bVal.rawget("Y").asDouble(),
                a.rawget("Z").asDouble() + bVal.rawget("Z").asDouble()
            );
        });
        metaTable.rawset("__sub", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return createVector3(engine,
                a.rawget("X").asDouble() - bVal.rawget("X").asDouble(),
                a.rawget("Y").asDouble() - bVal.rawget("Y").asDouble(),
                a.rawget("Z").asDouble() - bVal.rawget("Z").asDouble()
            );
        });
        metaTable.rawset("__mul", args -> {
            ILuaValue first = args[0];
            ILuaValue second = args[1];
            if (first instanceof ILuaTable && isNumber(second)) {
                ILuaTable a = first.asTable();
                double factor = second.asDouble();
                return createVector3(engine,
                    a.rawget("X").asDouble() * factor,
                    a.rawget("Y").asDouble() * factor,
                    a.rawget("Z").asDouble() * factor
                );
            } else if (isNumber(first) && second instanceof ILuaTable) {
                double factor = first.asDouble();
                ILuaTable a = second.asTable();
                return createVector3(engine,
                    a.rawget("X").asDouble() * factor,
                    a.rawget("Y").asDouble() * factor,
                    a.rawget("Z").asDouble() * factor
                );
            }
            throw new IllegalArgumentException("Vector3 multiplication requires a vector and a scalar number");
        });
        metaTable.rawset("__div", args -> {
            ILuaTable a = args[0].asTable();
            double factor = args[1].asDouble();
            if (factor == 0) return createVector3(engine, 0, 0, 0);
            return createVector3(engine,
                a.rawget("X").asDouble() / factor,
                a.rawget("Y").asDouble() / factor,
                a.rawget("Z").asDouble() / factor
            );
        });
        metaTable.rawset("__unm", args -> {
            ILuaTable a = args[0].asTable();
            return createVector3(engine,
                -a.rawget("X").asDouble(),
                -a.rawget("Y").asDouble(),
                -a.rawget("Z").asDouble()
            );
        });
        metaTable.rawset("__eq", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            boolean eq = a.rawget("X").asDouble() == bVal.rawget("X").asDouble() &&
                         a.rawget("Y").asDouble() == bVal.rawget("Y").asDouble() &&
                         a.rawget("Z").asDouble() == bVal.rawget("Z").asDouble();
            return engine.wrapBoolean(eq);
        });
        metaTable.rawset("__tostring", args -> {
            ILuaTable self = args[0].asTable();
            return engine.wrapString(
                self.rawget("X").asDouble() + ", " +
                self.rawget("Y").asDouble() + ", " +
                self.rawget("Z").asDouble()
            );
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Vector3 properties are read-only");
        });

        vecTable.setMetatable(metaTable);
        unitTable.setMetatable(metaTable);
        return vecTable;
    }

    public static ILuaTable createVector2(ILuaEngine engine, double x, double y) {
        ILuaTable vecTable = engine.createTable();
        vecTable.rawset("X", x);
        vecTable.rawset("Y", y);
        vecTable.rawset("x", x);
        vecTable.rawset("y", y);

        double mag = Math.sqrt(x * x + y * y);
        vecTable.rawset("Magnitude", mag);

        double uX = mag == 0 ? 0 : x / mag;
        double uY = mag == 0 ? 0 : y / mag;
        ILuaTable unitTable = engine.createTable();
        unitTable.rawset("X", uX); unitTable.rawset("Y", uY);
        unitTable.rawset("Magnitude", 1.0);
        vecTable.rawset("Unit", unitTable);

        vecTable.rawset("Dot", args -> {
            ILuaTable other = args[1].asTable();
            double dot = x * other.rawget("X").asDouble() + y * other.rawget("Y").asDouble();
            return engine.wrapNumber(dot);
        });

        vecTable.rawset("Lerp", args -> {
            ILuaTable other = args[1].asTable();
            double alpha = args[2].asDouble();
            double oX = other.rawget("X").asDouble();
            double oY = other.rawget("Y").asDouble();
            return createVector2(engine, x + (oX - x) * alpha, y + (oY - y) * alpha);
        });

        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", vecTable);
        metaTable.rawset("__add", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return createVector2(engine,
                a.rawget("X").asDouble() + bVal.rawget("X").asDouble(),
                a.rawget("Y").asDouble() + bVal.rawget("Y").asDouble()
            );
        });
        metaTable.rawset("__sub", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return createVector2(engine,
                a.rawget("X").asDouble() - bVal.rawget("X").asDouble(),
                a.rawget("Y").asDouble() - bVal.rawget("Y").asDouble()
            );
        });
        metaTable.rawset("__mul", args -> {
            ILuaValue first = args[0];
            ILuaValue second = args[1];
            if (first instanceof ILuaTable && isNumber(second)) {
                ILuaTable a = first.asTable();
                double factor = second.asDouble();
                return createVector2(engine, a.rawget("X").asDouble() * factor, a.rawget("Y").asDouble() * factor);
            } else if (isNumber(first) && second instanceof ILuaTable) {
                double factor = first.asDouble();
                ILuaTable a = second.asTable();
                return createVector2(engine, a.rawget("X").asDouble() * factor, a.rawget("Y").asDouble() * factor);
            }
            throw new IllegalArgumentException("Vector2 multiplication requires a vector and a scalar number");
        });
        metaTable.rawset("__div", args -> {
            ILuaTable a = args[0].asTable();
            double factor = args[1].asDouble();
            if (factor == 0) return createVector2(engine, 0, 0);
            return createVector2(engine, a.rawget("X").asDouble() / factor, a.rawget("Y").asDouble() / factor);
        });
        metaTable.rawset("__unm", args -> {
            ILuaTable a = args[0].asTable();
            return createVector2(engine, -a.rawget("X").asDouble(), -a.rawget("Y").asDouble());
        });
        metaTable.rawset("__eq", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return engine.wrapBoolean(a.rawget("X").asDouble() == bVal.rawget("X").asDouble() &&
                                      a.rawget("Y").asDouble() == bVal.rawget("Y").asDouble());
        });
        metaTable.rawset("__tostring", args -> {
            ILuaTable self = args[0].asTable();
            return engine.wrapString(self.rawget("X").asDouble() + ", " + self.rawget("Y").asDouble());
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Vector2 properties are read-only");
        });

        vecTable.setMetatable(metaTable);
        unitTable.setMetatable(metaTable);
        return vecTable;
    }

    public static ILuaTable createColor3(ILuaEngine engine, double r, double g, double b) {
        ILuaTable colorTable = engine.createTable();
        colorTable.rawset("R", r);
        colorTable.rawset("G", g);
        colorTable.rawset("B", b);
        colorTable.rawset("r", r);
        colorTable.rawset("g", g);
        colorTable.rawset("b", b);

        colorTable.rawset("toRGBInt", args -> {
            int rInt = (int) Math.clamp(r * 255.0, 0, 255);
            int gInt = (int) Math.clamp(g * 255.0, 0, 255);
            int bInt = (int) Math.clamp(b * 255.0, 0, 255);
            return engine.wrapNumber((rInt << 16) | (gInt << 8) | bInt);
        });

        colorTable.rawset("Lerp", args -> {
            ILuaTable other = args[1].asTable();
            double alpha = args[2].asDouble();
            double oR = other.rawget("R").asDouble();
            double oG = other.rawget("G").asDouble();
            double oB = other.rawget("B").asDouble();
            return createColor3(engine, r + (oR - r) * alpha, g + (oG - g) * alpha, b + (oB - b) * alpha);
        });

        ILuaTable metaTable = engine.createTable();
        metaTable.rawset("__index", colorTable);
        metaTable.rawset("__eq", args -> {
            ILuaTable a = args[0].asTable();
            ILuaTable bVal = args[1].asTable();
            return engine.wrapBoolean(a.rawget("R").asDouble() == bVal.rawget("R").asDouble() &&
                                      a.rawget("G").asDouble() == bVal.rawget("G").asDouble() &&
                                      a.rawget("B").asDouble() == bVal.rawget("B").asDouble());
        });
        metaTable.rawset("__tostring", args -> {
            ILuaTable self = args[0].asTable();
            return engine.wrapString(self.rawget("R").asDouble() + ", " + self.rawget("G").asDouble() + ", " + self.rawget("B").asDouble());
        });
        metaTable.rawset("__newindex", args -> {
            throw new IllegalArgumentException("Color3 properties are read-only");
        });

        colorTable.setMetatable(metaTable);
        return colorTable;
    }


    public static ILuaTable createInstance(ILuaEngine engine, LuaInstance instance) {
        ILuaTable instTable = engine.createTable();

        ILuaTable meta = engine.createTable();
        meta.rawset("__index", args -> {
            String key = args[1].asString();

            if (key.equals("Name") || key.equals("name")) {
                return engine.wrapString(instance.getName());
            }
            if (key.equals("ClassName") || key.equals("className")) {
                return engine.wrapString(instance.getClassName());
            }
            if (key.equals("Parent") || key.equals("parent")) {
                if (instance.getParent() == null) return engine.nilValue();
                return createInstance(engine, instance.getParent());
            }

            if (key.equals("GetChildren")) {
                return wrapFunction(engine, getChildrenArgs -> {
                    ILuaTable list = engine.createTable();
                    int idx = 1;
                    for (LuaInstance child : instance.getChildren()) {
                        list.rawset(idx++, createInstance(engine, child));
                    }
                    return list;
                });
            }
            if (key.equals("FindFirstChild")) {
                return wrapFunction(engine, findArgs -> {
                    if (findArgs.length < 2) return engine.nilValue();
                    String childName = findArgs[1].asString();
                    LuaInstance child = instance.findFirstChild(childName);
                    if (child == null) return engine.nilValue();
                    return createInstance(engine, child);
                });
            }
            if (key.equals("Clone")) {
                return wrapFunction(engine, cloneArgs -> {
                    LuaInstance copy = instance.cloneInstance();
                    return createInstance(engine, copy);
                });
            }
            if (key.equals("Destroy")) {
                return wrapFunction(engine, destroyArgs -> {
                    instance.destroy();
                    return null;
                });
            }

            return engine.nilValue();
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];

            if (key.equals("Name") || key.equals("name")) {
                instance.setName(val.asString());
            } else if (key.equals("Parent") || key.equals("parent")) {
                if (val.isNil()) {
                    instance.setParent(null);
                } else if (val instanceof ILuaTable pTable) {
                    ILuaValue rawInst = pTable.rawget("__instance");
                    if (rawInst != null && !rawInst.isNil()) {
                        Object ud = rawInst.toJavaObject();
                        if (ud instanceof LuaInstance parentInst) {
                            instance.setParent(parentInst);
                        }
                    }
                }
            }
            return null;
        });

        instTable.setMetatable(meta);
        instTable.rawset("__instance", engine.wrapUserdata(instance));
        return instTable;
    }
}
