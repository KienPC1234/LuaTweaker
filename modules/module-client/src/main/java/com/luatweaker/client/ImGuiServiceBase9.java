package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase9 extends ImGuiServiceBase8 {

    @Override
    public void endTooltip() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTooltip();
        }
    }

    @Override
    public void setTooltip(String text) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetTooltip(text == null ? "" : text);
        }
    }

    @Override
    public void beginDisabled(boolean disabled) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBeginDisabled(disabled);
        }
    }

    @Override
    public void endDisabled() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDisabled();
        }
    }

    @Override
    public void setScrollHereY() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetScrollHereY();
        }
    }

    @Override
    public void setKeyboardFocusHere() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetKeyboardFocusHere();
        }
    }

    @Override
    public void setNextItemOpen(boolean open) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextItemOpen(open);
        }
    }

    @Override
    public void textColored(String text, double r, double g, double b) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextColored((float) r, (float) g, (float) b, text == null ? "" : text);
        }
    }

    @Override
    public void textWrapped(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextWrapped(message == null ? "" : message);
        }
    }
}

