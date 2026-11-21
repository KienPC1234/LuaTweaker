package com.luatweaker.api.mod;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only view of the files shipped inside the owning LuaMod (folder or .zip).
 *
 * <p>Paths are relative to the mod root and must stay inside it: {@code ..},
 * absolute paths, drive letters and symlinks that escape the root are rejected.
 * Text is UTF-8; binary files are exposed as Base64 because Lua strings in this
 * VM are text.</p>
 */
@LuaDoc(description = "Read-only file access to this LuaMod's own folder or .zip. "
        + "Paths are relative; writes are not allowed here (use mod:Exports()).")
public interface IModFiles {

    @LuaDoc(
            description = "Reads a UTF-8 text file from the mod folder. "
                    + "Throws when the file is missing, larger than the read cap, or not valid UTF-8.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "string")
    String readText(String path);

    @LuaDoc(
            description = "Reads any file from the mod folder and returns its bytes as a Base64 string. "
                    + "Use this for binary data; readText() rejects invalid UTF-8.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "string")
    String readBase64(String path);

    @LuaDoc(
            description = "Reads a JSON file from the mod folder and decodes it directly into Lua values (tables, primitives). "
                    + "Throws when the file is missing, invalid UTF-8, or malformed JSON.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "any")
    ILuaValue readJson(@NotNull String path);

    @LuaDoc(
            description = "Reads a JSON file, returning the fallback when the file is missing, "
                    + "not valid UTF-8, or malformed JSON. "
                    + "Path-security violations, directories, oversized files and I/O failures still throw. "
                    + "Use for optional config: local cfg = files:ReadJsonOr('data/config.json', {}).",
            params = {LuaDocFragments.Common.PATH_STRING, "fallback: any"},
            returnType = "any")
    ILuaValue readJsonOr(@NotNull String path, @LuaDefault("nil") @Nullable ILuaValue fallback);

    @LuaDoc(
            description = "Returns true when the file or directory exists inside the mod folder.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "boolean")
    boolean exists(String path);

    @LuaDoc(
            description = "Lists one directory level of the mod folder (sorted names, directories included). "
                    + "Symlinks are omitted. Use \"\" or \".\" for the root.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "table")
    ILuaTable list(String path);

    @LuaDoc(
            description = "Returns { size, directory, modified } for a path in the mod folder. "
                    + "modified is epoch milliseconds (-1 when unknown); size is -1 when a zip entry omits it.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "table")
    ILuaTable stat(String path);

    @LuaDoc(
            description = "Absolute filesystem path of this mod's folder or .zip (diagnostics / chat output).",
            params = {},
            returnType = "string")
    String directory();
}
