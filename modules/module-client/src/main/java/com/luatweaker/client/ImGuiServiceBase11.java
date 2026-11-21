package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase11 extends ImGuiServiceBase10 {

    @Override
    public boolean beginCombo(String label, String preview) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginCombo(label == null ? "combo" : label, preview == null ? "" : preview);
    }

    @Override
    public void endCombo() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndCombo();
        }
    }

    @Override
    public void plotLines(String label, float[] values) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPlotLines(label == null ? "" : label, values == null ? new float[0] : values);
        }
    }

    @Override
    public void plotHistogram(String label, float[] values) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPlotHistogram(label == null ? "" : label, values == null ? new float[0] : values);
        }
    }

    @Override
    public String inputTextFlags(String label, String current, String flags) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputTextFlags(label == null ? LABEL_INPUT : label,
                current == null ? "" : current, flags == null ? "" : flags);
    }

    @Override
    public boolean isItemActive() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemActive();
    }

    @Override
    public boolean isItemFocused() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemFocused();
    }

    @Override
    public boolean isItemVisible() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemVisible();
    }

    @Override
    public boolean isItemEdited() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemEdited();
    }
}

