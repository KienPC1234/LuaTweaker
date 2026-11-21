package com.luatweaker.command;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Recording command sender: captures every message the wrapper sends and every
 * permission level the wrapper checks, and answers permission requests from a
 * fixed granted level.
 */
class MockCommandSender implements ICommandSender {

    final List<String> messages = new ArrayList<>();
    final List<Integer> permissionChecks = new ArrayList<>();
    final boolean player;
    final int grantedLevel;

    MockCommandSender(boolean player, int grantedLevel) {
        this.player = player;
        this.grantedLevel = grantedLevel;
    }

    @Override
    public void sendMessage(String message) {
        messages.add("MSG:" + message);
    }

    @Override
    public void sendSuccess(String message) {
        messages.add("OK:" + message);
    }

    @Override
    public void sendError(String message) {
        messages.add("ERR:" + message);
    }

    @Override
    public boolean hasPermission(int level) {
        permissionChecks.add(level);
        return grantedLevel >= level;
    }

    @Override
    public boolean isPlayer() {
        return player;
    }

    @Override
    public IPlayer getPlayer() {
        return null;
    }

    @Override
    public String getName() {
        return "TestPlayer";
    }

    @Override
    public String getHeldItemId() {
        return "minecraft:diamond_sword";
    }
}
