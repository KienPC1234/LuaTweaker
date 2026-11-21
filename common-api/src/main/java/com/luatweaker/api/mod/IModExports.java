package com.luatweaker.api.mod;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Per-mod writable folder: {@code <gameDir>/luatweaker/exports/<mod_id>/}.
 *
 * <p>Writes never touch the mod's own folder (updates overwrite it, zip mods are
 * read-only, and self-modifying code would be unauditable). Every path is
 * contained in this mod's export folder; other mods' exports are unreachable.
 * Exports are local files: they are never synced to the server or to clients.</p>
 */
@LuaDoc(description = "Writable per-mod export folder (gameDir/luatweaker/exports/<mod_id>). "
        + "Paths are relative and cannot escape this folder or reach the mod's own files.")
public interface IModExports {

    @LuaDoc(
            description = "Reads a UTF-8 text file previously written to this mod's export folder.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "string")
    String readText(String path);

    @LuaDoc(
            description = "Reads a file from the export folder as Base64 (binary-safe).",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "string")
    String readBase64(String path);

    @LuaDoc(
            description = "Reads a JSON file from the export folder and decodes it directly into Lua values (tables, primitives).",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "any")
    ILuaValue readJson(String path);

    @LuaDoc(
            description = "Encodes a Lua value to JSON and writes it to the export folder (atomic replace). Parent directories are created.",
            params = {LuaDocFragments.Common.PATH_STRING, "value: any", "pretty: boolean"})
    void writeJson(String path, ILuaValue value, @LuaDefault("false") boolean pretty);

    @LuaDoc(
            description = "Writes a UTF-8 text file (atomic replace). Parent directories are created. "
                    + "Writes outside the export folder are rejected.",
            params = {LuaDocFragments.Common.PATH_STRING, "text: string"})
    void writeText(String path, String text);

    @LuaDoc(
            description = "Writes Base64-decoded bytes to the export folder (binary-safe, atomic replace). "
                    + "Throws when the Base64 payload is malformed.",
            params = {LuaDocFragments.Common.PATH_STRING, "base64: string"})
    void writeBase64(String path, String base64);

    @LuaDoc(
            description = "Appends UTF-8 text to a file in the export folder, creating it when missing. "
                    + "The file is rejected once it would exceed the size cap.",
            params = {LuaDocFragments.Common.PATH_STRING, "text: string"})
    void appendText(String path, String text);

    @LuaDoc(
            description = "Returns true when the path exists in this mod's export folder.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "boolean")
    boolean exists(String path);

    @LuaDoc(
            description = "Lists one directory level of the export folder (sorted names). "
                    + "A missing folder yields an empty table.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "table")
    ILuaTable list(String path);

    @LuaDoc(
            description = "Returns { size, directory, modified } for a path in the export folder.",
            params = {LuaDocFragments.Common.PATH_STRING},
            returnType = "table")
    ILuaTable stat(String path);

    @LuaDoc(
            description = "Deletes a file or an empty directory inside the export folder. "
                    + "Non-empty directories and missing paths raise an error.",
            params = {LuaDocFragments.Common.PATH_STRING})
    void delete(String path);

    @LuaDoc(
            description = "Creates a directory (and parents) inside the export folder. Idempotent.",
            params = {LuaDocFragments.Common.PATH_STRING})
    void mkdir(String path);

    @LuaDoc(
            description = "Absolute filesystem path of this mod's export folder (created on first call).",
            params = {},
            returnType = "string")
    String directory();
}
