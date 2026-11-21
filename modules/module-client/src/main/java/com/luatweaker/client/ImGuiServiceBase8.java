package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;
import java.util.List;

abstract class ImGuiServiceBase8 extends ImGuiServiceBase7 {

    @Override
    public List<Double> getSpriteUv(String atlasId, String spriteId) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of();
        }
        return client.imguiSpriteUv(atlasId == null ? "" : atlasId, spriteId == null ? "" : spriteId);
    }

    @Override
    public void openPopup(String id) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiOpenPopup(id == null ? "" : id);
        }
    }

    @Override
    public boolean beginPopup(String id) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginPopup(id == null ? "" : id);
    }

    @Override
    public void endPopup() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndPopup();
        }
    }

    @Override
    public boolean beginPopupModal(String title, boolean open) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return open;
        }
        return client.imguiBeginPopupModal(title == null ? "dialog" : title, open);
    }

    @Override
    public void closeCurrentPopup() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiCloseCurrentPopup();
        }
    }

    @Override
    public boolean beginPopupContextItem(String id) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginPopupContextItem(id == null ? "" : id);
    }

    @Override
    public void openPopupOnItemClick(String id) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiOpenPopupOnItemClick(id == null ? "" : id);
        }
    }

    @Override
    public void beginTooltip() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiBeginTooltip();
        }
    }
}

