package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase5 extends ImGuiServiceBase4 {

    @Override
    public boolean isItemClicked() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsItemClicked();
    }

    @Override
    public boolean isWindowHovered() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiIsWindowHovered();
    }

    @Override
    public boolean wantCaptureMouse() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiWantCaptureMouse();
    }

    @Override
    public boolean wantCaptureKeyboard() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiWantCaptureKeyboard();
    }

    @Override
    public void pushStyleColor(String colorName, int argbColor) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            // Name-to-constant mapping lives in the platform (real ImGuiCol
            // constants); unknown names fail fast there with the valid list.
            client.imguiPushStyleColor(colorName == null ? "" : colorName.strip(), argbColor);
        }
    }

    @Override
    public void popStyleColor(int count) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiPopStyleColor(Math.max(count, 1));
        }
    }

    @Override
    public boolean itemIcon(String itemId, int sizePx, int count) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiItemIcon(itemId == null ? "" : itemId, Math.max(sizePx, 8), Math.max(count, 1));
    }

    @Override
    public boolean showDemoWindow(boolean open) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return open;
        }
        return client.imguiShowDemoWindow(open);
    }

    @Override
    public String getVersion() {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return "unavailable";
        }
        return client.imguiGetVersion();
    }
}

