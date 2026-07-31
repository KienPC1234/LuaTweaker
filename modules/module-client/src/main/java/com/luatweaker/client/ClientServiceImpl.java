package com.luatweaker.client;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ClientServiceImpl {
    private final Set<Integer> pressedKeys = ConcurrentHashMap.newKeySet();

    public void onKeyPress(int keyCode, boolean isPressed) {
        if (isPressed) {
            pressedKeys.add(keyCode);
        } else {
            pressedKeys.remove(keyCode);
        }
    }

    public boolean isKeyDown(int keyCode) {
        return pressedKeys.contains(keyCode);
    }
}
