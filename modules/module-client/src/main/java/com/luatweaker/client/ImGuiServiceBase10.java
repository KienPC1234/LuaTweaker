package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase10 extends ImGuiServiceBase9 {

    @Override
    public void textDisabled(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTextDisabled(message == null ? "" : message);
        }
    }

    @Override
    public void labelText(String label, String value) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiLabelText(label == null ? "" : label, value == null ? "" : value);
        }
    }

    @Override
    public boolean arrowButton(String id, String direction) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiArrowButton(id == null ? "arrow" : id, direction == null ? "" : direction);
    }

    @Override
    public boolean tabItemButton(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiTabItemButton(label == null ? "+" : label);
    }

    @Override
    public boolean beginMainMenuBar() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMainMenuBar();
    }

    @Override
    public void endMainMenuBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMainMenuBar();
        }
    }

    @Override
    public void pushItemWidth(double width) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPushItemWidth((float) width);
        }
    }

    @Override
    public void popItemWidth() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPopItemWidth();
        }
    }

    @Override
    public void setNextItemWidth(double width) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextItemWidth((float) width);
        }
    }
}

