package com.luatweaker.client;

import com.luatweaker.api.pal.IPlatformClient;
import java.util.List;

abstract class ImGuiServiceBase12 extends ImGuiServiceBase11 {

    @Override
    public List<Double> calcTextSize(String text) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(0.0, 0.0);
        }
        return client.imguiCalcTextSize(text == null ? "" : text);
    }

    @Override
    public List<Double> getItemRectSize() {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return List.of(0.0, 0.0);
        }
        return client.imguiGetItemRectSize();
    }

    @Override
    public boolean beginDragDropSource() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginDragDropSource();
    }

    @Override
    public boolean setDragDropPayload(String type, String data) {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiSetDragDropPayload(type == null ? "" : type, data == null ? "" : data);
    }

    @Override
    public void endDragDropSource() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDragDropSource();
        }
    }

    @Override
    public boolean beginDragDropTarget() {
        IPlatformClient client = clientOrNull();
        return client != null && client.imguiBeginDragDropTarget();
    }

    @Override
    public String acceptDragDropPayload(String type) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            return null;
        }
        return client.imguiAcceptDragDropPayload(type == null ? "" : type);
    }

    @Override
    public void endDragDropTarget() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndDragDropTarget();
        }
    }
}

