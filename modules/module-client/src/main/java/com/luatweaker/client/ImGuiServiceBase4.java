package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;
import java.util.List;

abstract class ImGuiServiceBase4 extends ImGuiServiceBase3 {

    @Override
    public int combo(String label, int current, String[] items) {
        IPlatformClient client = clientOrNull();
        if (client == null || items == null || items.length == 0) {
            return current;
        }
        int clamped = Math.min(Math.max(current, 1), items.length);
        return client.imguiCombo(label == null ? "combo" : label, clamped, items);
    }

    @Override
    public List<Double> colorEdit3(String label, double r, double g, double b) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(r, g, b, 1.0);
        }
        return client.imguiColorEdit(label == null ? "color" : label, r, g, b, 1.0);
    }

    @Override
    public List<Double> colorEdit4(String label, double r, double g, double b, double a) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(r, g, b, a);
        }
        return client.imguiColorEdit(label == null ? "color" : label, r, g, b, a);
    }

    @Override
    public void progressBar(double fraction, String label) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            double clamped = Math.min(Math.max(fraction, 0.0), 1.0);
            client.imguiProgressBar(clamped, label == null ? "" : label);
        }
    }

    @Override
    public boolean collapsingHeader(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiCollapsingHeader(label == null ? "header" : label);
    }

    @Override
    public boolean treeNode(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiTreeNode(label == null ? "node" : label);
    }

    @Override
    public void treePop() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTreePop();
        }
    }

    @Override
    public boolean selectable(String label, boolean selected) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSelectable(label == null ? "selectable" : label, selected);
    }

    @Override
    public boolean isItemHovered() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemHovered();
    }
}

