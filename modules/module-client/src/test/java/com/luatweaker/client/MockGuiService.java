package com.luatweaker.client;

import com.luatweaker.api.client.IGuiService;
import com.luatweaker.api.vm.ILuaTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Recording {@link IGuiService} double: every call is appended as a readable string so tests can
 * assert exactly what the Lua binding dispatched. Split by drawing concern so each level stays
 * small.
 */
abstract class MockGuiServiceBase implements IGuiService {

    private static final int SCREEN_WIDTH = 1920;
    private static final int SCREEN_HEIGHT = 1080;

    final List<String> calls = new ArrayList<>();

    protected MockGuiServiceBase() {
        // Base of the fake only; instantiate MockGuiService.
    }

    @Override
    public void drawRect(int x, int y, int width, int height, int color) {
        calls.add("rect " + x + "," + y + " " + width + "x" + height);
    }

    @Override
    public void drawOutline(int x, int y, int width, int height, int color) {
        calls.add("outline " + x + "," + y + " " + width + "x" + height);
    }

    @Override
    public void drawProgressBar(int x, int y, int width, int height, double fillPercent,
                                int backgroundColor, int fillColor) {
        calls.add("progress " + fillPercent);
    }

    @Override
    public void drawTexture(String textureId, int x, int y, int width, int height) {
        calls.add("texture " + textureId);
    }

    @Override
    public void pushClip(int x, int y, int width, int height) {
        calls.add("clipPush " + x + "," + y + " " + width + "x" + height);
    }

    @Override
    public void popClip() {
        calls.add("clipPop");
    }

    @Override
    public void drawLine(int x1, int y1, int x2, int y2, int color, int thickness) {
        calls.add("line " + x1 + "," + y1 + " -> " + x2 + "," + y2 + " t" + thickness);
    }

    @Override
    public void drawItem(String itemId, int x, int y, int count) {
        calls.add("item " + itemId + "x" + count + " @" + x + "," + y);
    }

    @Override
    public int[] getScreenSize() {
        return new int[] { SCREEN_WIDTH, SCREEN_HEIGHT };
    }
}

/** Text/sprite half of the recording GUI double. */
abstract class MockGuiServiceText extends MockGuiServiceBase {

    protected MockGuiServiceText() {
        super();
    }

    @Override
    public void drawText(String text, int x, int y, int color, boolean dropShadow) {
        calls.add("text " + text);
    }

    @Override
    public void drawTextCentered(String text, int centerX, int y, int color, boolean dropShadow) {
        calls.add("textCentered " + text + " @" + centerX + "," + y);
    }

    @Override
    public int[] measureText(String text) {
        calls.add("measure " + text);
        // Deterministic fake: 6px per character, 9px line height.
        return new int[] { text == null ? 0 : text.length() * 6, 9 };
    }

    @Override
    public void drawGradient(int x, int y, int width, int height, int topColor, int bottomColor) {
        calls.add("gradient " + x + "," + y + " " + width + "x" + height);
    }

    @Override
    public void drawSprite(String spriteId, int x, int y, int width, int height) {
        calls.add("sprite " + spriteId);
    }

    @Override
    public void drawTooltip(String text, int x, int y) {
        calls.add("tooltip " + text);
    }

    @Override
    public void pushPose() {
        calls.add("posePush");
    }
}

/** Pose/screen half of the recording GUI double. */
final class MockGuiService extends MockGuiServiceText {

    @Override
    public void popPose() {
        calls.add("posePop");
    }

    @Override
    public void translate(double x, double y, double z) {
        calls.add("translate " + x + "," + y + "," + z);
    }

    @Override
    public void scale(double x, double y, double z) {
        calls.add("scale " + x + "," + y + "," + z);
    }

    @Override
    public void registerScreen(String screenId, ILuaTable screenTable) {
        calls.add("register " + screenId);
    }

    @Override
    public void openScreen(String screenId, String title, boolean pauseGame) {
        calls.add("open " + screenId + " (" + title + ")");
    }

    @Override
    public void closeScreen() {
        calls.add("close");
    }

    @Override
    public boolean isScreenOpen() {
        return calls.contains("open");
    }
}
