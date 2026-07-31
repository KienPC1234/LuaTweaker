package com.luatweaker.api.storage;

import com.luatweaker.api.annotation.LuaDoc;

@LuaDoc(description = "Storage service for Roblox-style World, Player, and Session data stores.")
public interface IRobloxStorageService {
    
    interface IDataStore {
        @LuaDoc(description = "Retrieves a stored value.", params = {"key: string"}, returnType = "any")
        Object GetAsync(String key);

        @LuaDoc(description = "Sets a value.", params = {"key: string", "value: any"})
        void SetAsync(String key, Object value);
    }

    @LuaDoc(description = "Returns the World storage data store.", returnType = "IDataStore")
    IDataStore GetWorldStorage();

    @LuaDoc(description = "Returns the Player storage data store for a specific player.", params = {"playerUuid: string"}, returnType = "IDataStore")
    IDataStore GetPlayerStorage(String playerUuid);

    @LuaDoc(description = "Returns the Session in-memory storage data store.", returnType = "IDataStore")
    IDataStore GetSessionStorage();
}
