package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase2 extends ImGuiServiceBase1 {

    @Override
    public void separator() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSeparator();
        }
    }

    @Override
    public void indent() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiIndent();
        }
    }

    @Override
    public void unindent() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiUnindent();
        }
    }

    @Override
    public void setCursorPosX(double x) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetCursorPosX((float) x);
        }
    }

    @Override
    public void text(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiText(message == null ? "" : message);
        }
    }

    @Override
    public void bulletText(String message) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBulletText(message == null ? "" : message);
        }
    }

    @Override
    public boolean button(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiButton(label == null ? "button" : label);
    }

    @Override
    public boolean smallButton(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSmallButton(label == null ? "button" : label);
    }

    @Override
    public boolean checkbox(String label, boolean checked) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return checked;
        }
        return client.imguiCheckbox(label == null ? "checkbox" : label, checked);
    }
}

