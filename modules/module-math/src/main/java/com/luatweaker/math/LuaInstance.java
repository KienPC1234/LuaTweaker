package com.luatweaker.math;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LuaInstance {
    private final String className;
    private String name;
    private LuaInstance parent;
    private final List<LuaInstance> children = new ArrayList<>();

    public LuaInstance(@NotNull String className) {
        this.className = className;
        this.name = className;
    }

    public LuaInstance(@NotNull String className, @Nullable LuaInstance parent) {
        this.className = className;
        this.name = className;
        setParent(parent);
    }

    @NotNull
    public String getClassName() {
        return className;
    }

    @NotNull
    public String getName() {
        return name;
    }

    public void setName(@NotNull String name) {
        this.name = name;
    }

    @Nullable
    public LuaInstance getParent() {
        return parent;
    }

    public void setParent(@Nullable LuaInstance parent) {
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        this.parent = parent;
        if (parent != null) {
            parent.children.add(this);
        }
    }

    @NotNull
    public List<LuaInstance> getChildren() {
        return new ArrayList<>(children);
    }

    @Nullable
    public LuaInstance findFirstChild(@NotNull String childName) {
        for (LuaInstance child : children) {
            if (child.getName().equals(childName)) {
                return child;
            }
        }
        return null;
    }

    @NotNull
    public LuaInstance cloneInstance() {
        LuaInstance copy = new LuaInstance(this.className);
        copy.setName(this.name);
        for (LuaInstance child : children) {
            LuaInstance childCopy = child.cloneInstance();
            childCopy.setParent(copy);
        }
        return copy;
    }

    public void destroy() {
        setParent(null);
        List<LuaInstance> kids = new ArrayList<>(children);
        for (LuaInstance child : kids) {
            child.destroy();
        }
        children.clear();
    }
}
