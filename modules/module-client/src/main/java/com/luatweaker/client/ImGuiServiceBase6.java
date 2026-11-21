package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;

abstract class ImGuiServiceBase6 extends ImGuiServiceBase5 {

    @Override
    public boolean beginTable(String id, int columns, String flags) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginTable(id == null ? "table" : id, Math.max(columns, 1), flags == null ? "" : flags);
    }

    @Override
    public void endTable() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTable();
        }
    }

    @Override
    public void tableNextRow() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableNextRow();
        }
    }

    @Override
    public boolean tableNextColumn() {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiTableNextColumn();
    }

    @Override
    public void tableSetupColumn(String name, double width, String flags) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableSetupColumn(name == null ? "" : name, (float) width, flags == null ? "" : flags);
        }
    }

    @Override
    public void tableHeadersRow() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiTableHeadersRow();
        }
    }

    @Override
    public boolean beginTabBar(String id) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginTabBar(id == null ? "tabs" : id);
    }

    @Override
    public void endTabBar() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndTabBar();
        }
    }

    @Override
    public boolean beginTabItem(String label) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginTabItem(label == null ? "tab" : label);
    }
}

