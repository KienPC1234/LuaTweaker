package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;

/**
 * An interactive button element in a container screen.
 *
 * @param id     unique button identifier passed to the click handler
 * @param x      panel x offset
 * @param y      panel y offset
 * @param width  button width
 * @param height button height
 * @param label  text displayed on the button
 */
@LuaDoc(description = "Interactive button widget in a machine container screen.")
public record MachineButtonSpec(String id, int x, int y, int width, int height, String label) {
}
