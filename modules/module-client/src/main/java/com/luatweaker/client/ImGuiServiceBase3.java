package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase3 extends ImGuiServiceBase2 {

    @Override
    public boolean radioButton(String label, boolean active) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiRadioButton(label == null ? "radio" : label, active);
    }

    @Override
    public double sliderFloat(String label, double value, double min, double max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiSliderFloat(label == null ? "slider" : label, value, min, max);
    }

    @Override
    public int sliderInt(String label, int value, int min, int max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiSliderInt(label == null ? "slider" : label, value, min, max);
    }

    @Override
    public double dragFloat(String label, double value, double speed, double min, double max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiDragFloat(label == null ? "drag" : label, value, speed, min, max);
    }

    @Override
    public int dragInt(String label, int value, double speed, int min, int max) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiDragInt(label == null ? "drag" : label, value, speed, min, max);
    }

    @Override
    public String inputText(String label, String current) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputText(label == null ? LABEL_INPUT : label, current == null ? "" : current, false);
    }

    @Override
    public String inputTextMultiline(String label, String current) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return current == null ? "" : current;
        }
        return client.imguiInputText(label == null ? LABEL_INPUT : label, current == null ? "" : current, true);
    }

    @Override
    public int inputInt(String label, int value) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiInputInt(label == null ? LABEL_INPUT : label, value);
    }

    @Override
    public double inputFloat(String label, double value) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return value;
        }
        return client.imguiInputFloat(label == null ? LABEL_INPUT : label, value);
    }
}

