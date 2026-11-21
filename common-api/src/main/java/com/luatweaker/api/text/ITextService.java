package com.luatweaker.api.text;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaTable;
import org.jetbrains.annotations.NotNull;

/**
 * Low-level builder for Minecraft text components.
 *
 * <p>Every builder returns an immutable {@link TextSpec} handle. The handle can be passed to
 * {@code Player:SendText}, {@code Player:SendTextActionBar}, {@code Player:SendTextTitle},
 * or serialized with {@code ToJson}/{@code ToLegacy}. Styles are per node and inherit from
 * the parent, exactly like vanilla components.</p>
 */
@LuaDoc(description = "Low-level Minecraft text component builder (literal/translate/keybind/score, colors, click/hover).")
public interface ITextService {

    String TEXT_SPEC = "TextSpec";
    String NODE_PARAM = "node: TextSpec";

    @LuaDoc(description = "Creates a literal text node.",
            params = {"text: string"}, returnType = TEXT_SPEC)
    @NotNull TextSpec literal(@NotNull String text);

    @LuaDoc(description = "Creates a translatable node. args is a 1-based table of TextSpec handles "
            + "(or plain strings/numbers, which are wrapped as literals).",
            params = {"key: string", "[args: table]"}, returnType = TEXT_SPEC)
    @NotNull TextSpec translatable(@NotNull String key, @LuaDefault("") ILuaTable args);

    @LuaDoc(description = "Creates a keybind node, e.g. 'key.jump'.",
            params = {"key: string"}, returnType = TEXT_SPEC)
    @NotNull TextSpec keybind(@NotNull String key);

    @LuaDoc(description = "Creates a scoreboard value node.",
            params = {"name: string", "objective: string"}, returnType = TEXT_SPEC)
    @NotNull TextSpec score(@NotNull String name, @NotNull String objective);

    @LuaDoc(description = "Joins parts with a literal separator. parts is a 1-based table of TextSpec handles.",
            params = {"separator: string", "parts: table"}, returnType = TEXT_SPEC)
    @NotNull TextSpec concat(@NotNull String separator, @NotNull ILuaTable parts);

    @LuaDoc(description = "Applies style fields to a node without mutating it. Supported keys: "
            + "Color (hex string), Bold, Italic, Underline, Strikethrough, Obfuscated (booleans), "
            + "Font (resource id), Insertion (string).",
            params = {NODE_PARAM, "style: table"}, returnType = TEXT_SPEC)
    @NotNull TextSpec style(@NotNull TextSpec node, @NotNull ILuaTable style);

    @LuaDoc(description = "Attaches a click action. action is one of OPEN_URL, OPEN_FILE, RUN_COMMAND, "
            + "SUGGEST_COMMAND, COPY_TO_CLIPBOARD, CHANGE_PAGE.",
            params = {NODE_PARAM, "action: string", "value: string"}, returnType = TEXT_SPEC)
    @NotNull TextSpec click(@NotNull TextSpec node, @NotNull String action, @NotNull String value);

    @LuaDoc(description = "Attaches a show_text hover tooltip.",
            params = {NODE_PARAM, "tooltip: TextSpec"}, returnType = TEXT_SPEC)
    @NotNull TextSpec hover(@NotNull TextSpec node, @NotNull TextSpec tooltip);

    @LuaDoc(description = "Serializes the node to legacy section-sign text (colors/format only; "
            + "click and hover data cannot be represented).",
            params = {NODE_PARAM}, returnType = "string")
    @NotNull String toLegacy(@NotNull TextSpec node);

    @LuaDoc(description = "Serializes the node to Minecraft JSON text format (usable in tellraw/titles/data).",
            params = {NODE_PARAM, "[pretty: boolean]"}, returnType = "string")
    @NotNull String toJson(@NotNull TextSpec node, @LuaDefault("false") boolean pretty);
}
