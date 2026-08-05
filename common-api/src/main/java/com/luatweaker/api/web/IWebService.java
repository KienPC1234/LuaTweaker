package com.luatweaker.api.web;

import com.luatweaker.api.annotation.LuaDefault;
import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.vm.ILuaValue;

/**
 * Web Service - permission-gated HTTP GET access.
 *
 * <p>Free HTTP access is DISABLED by default. The {@code net.http} manifest
 * permission unlocks it for the whole session when ANY loaded mod declares it
 * (the VM cannot attribute runtime calls to a specific mod, so the grant is
 * per installation); the granting mods are listed in a loud warning in chat
 * and logs so admins can audit their modpacks. With no granting mod loaded,
 * every call is rejected loudly.</p>
 *
 * <p>Results are returned synchronously as a table:
 * {Success = boolean, StatusCode = int, Body = string, Json = table|nil, Error = string|nil}.
 * When the response body is valid JSON it is also exposed parsed as {@code Json}.</p>
 */
@LuaDoc(description = "Web Service - HTTP GET access gated behind the 'net.http' manifest permission.")
public interface IWebService {

    @LuaDoc(
        description = "Performs a synchronous HTTP GET request. Requires at least one loaded mod to declare the 'net.http' permission in manifest.json (per-installation grant, granting mods are warned about); otherwise the call is rejected. Returns {Success, StatusCode, Body, Json, Error}.",
        params = {"url: string", "timeoutSeconds: number (optional, default 5.0, clamped to 1-60)"},
        returnType = "table"
    )
    ILuaValue HttpGet(String url, @LuaDefault("5.0") double timeoutSeconds);
}
