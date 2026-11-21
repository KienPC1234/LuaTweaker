package com.luatweaker.client;

import com.luatweaker.api.client.IImGuiService;
import com.luatweaker.api.pal.IPlatformClient;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.Platform;

abstract class ImGuiServiceBase1 implements IImGuiService {

    protected static final String LABEL_INPUT = "input";

    protected IPlatformClient clientOrNull() {
        return Platform.getClientOrNull();
    }

    @Override
    public boolean begin(String title, boolean open, String flags) {
        IPlatformClient client = clientOrNull();
        if (client == null) {
            LuaTweakerLog.get().debug(LogStage.SYSTEM, "[ImGui] Begin ignored: no client.");
            return open;
        }
        // Flag parsing lives in the platform (real ImGuiWindowFlags constants);
        // unknown names fail fast there with the valid list.
        return client.imguiBegin(title == null ? "window" : title, open, flags == null ? "" : flags);
    }

    @Override
    public void end() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEnd();
        }
    }

    @Override
    public boolean beginChild(String id, double width, double height) {
        IPlatformClient client = clientOrNull();
        return client == null || client.imguiBeginChild(id == null ? "child" : id, (float) width, (float) height);
    }

    @Override
    public void endChild() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiEndChild();
        }
    }

    @Override
    public void setNextWindowSize(double width, double height) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextWindowSize((float) width, (float) height);
        }
    }

    @Override
    public void setNextWindowPos(double x, double y) {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSetNextWindowPos((float) x, (float) y);
        }
    }

    @Override
    public void sameLine() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSameLine();
        }
    }

    @Override
    public void newLine() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiNewLine();
        }
    }

    @Override
    public void spacing() {
        IPlatformClient client = clientOrNull();
        if (client != null) {
            client.imguiSpacing();
        }
    }
}

