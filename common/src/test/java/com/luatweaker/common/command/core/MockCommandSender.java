package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.entity.IPlayer;

import java.util.ArrayList;
import java.util.List;

/** Test-only sender that records every message with its channel prefix. */
final class MockCommandSender implements ICommandSender {

    private static final String ERROR_PREFIX = "ERR:";

    final List<String> messages = new ArrayList<>();

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
        messages.add(ERROR_PREFIX + message);
    }

    @Override
    public boolean hasPermission(int level) {
        return true;
    }

    @Override
    public boolean isPlayer() {
        return false;
    }

    @Override
    public IPlayer getPlayer() {
        return null;
    }

    @Override
    public String getName() {
        return "Console";
    }

    @Override
    public String getHeldItemId() {
        return "";
    }

    boolean failedLoudly() {
        return messages.stream().anyMatch(message -> message.startsWith(ERROR_PREFIX));
    }
}
