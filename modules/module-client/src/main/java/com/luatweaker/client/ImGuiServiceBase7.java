package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase7 extends ImGuiServiceBase6 {

    @Override
    public void endTabItem() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTabItem();
        }
    }

    @Override
    public boolean beginMenuBar() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMenuBar();
    }

    @Override
    public void endMenuBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMenuBar();
        }
    }

    @Override
    public boolean beginMenu(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginMenu(label == null ? "menu" : label);
    }

    @Override
    public void endMenu() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndMenu();
        }
    }

    @Override
    public boolean menuItem(String label, String shortcut, boolean selected, boolean enabled) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiMenuItem(label == null ? "item" : label, shortcut == null ? "" : shortcut, selected, enabled);
    }

    @Override
    public int listBox(String label, int current, String[] items) {
        IPlatformClient client = clientOrNull();
        if (client == null || items == null || items.length == 0) {
            return current;
        }
        int clamped = Math.min(Math.max(current, 1), items.length);
        return client.imguiListBox(label == null ? "list" : label, clamped, items);
    }

    @Override
    public void image(String textureId, double width, double height) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiImage(textureId == null ? "" : textureId, (float) width, (float) height);
        }
    }

    @Override
    public void imageSub(String textureId, double width, double height,
                         double u0, double v0, double u1, double v1) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiImageSub(textureId == null ? "" : textureId,
                    (float) width, (float) height, (float) u0, (float) v0, (float) u1, (float) v1);
        }
    }
}

