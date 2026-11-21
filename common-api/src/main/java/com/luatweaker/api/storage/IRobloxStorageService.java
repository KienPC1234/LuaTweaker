package com.luatweaker.api.storage;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;

@LuaDoc(description = "Storage service for Roblox-style World, Player, and Session data stores.")
public interface IRobloxStorageService {
    
    interface IDataStore {
        @LuaDoc(description = "Retrieves a stored value.", params = {LuaDocFragments.Common.KEY_STRING}, returnType = "any")
        Object GetAsync(String key);

        @LuaDoc(description = "Sets a value. Nil removes the key.", params = {LuaDocFragments.Common.KEY_STRING, "value: any"})
        void SetAsync(String key, Object value);

        @LuaDoc(
            description = "Removes a stored key and returns whether it existed.",
            params = {LuaDocFragments.Common.KEY_STRING},
            returnType = "boolean"
        )
        boolean RemoveAsync(String key);

        @LuaDoc(description = "Lists every stored key, sorted.", returnType = "table")
        java.util.List<String> GetKeys();

        @LuaDoc(
            description = "Atomically adds delta to a numeric value (missing key starts at 0) and returns the new value. Fails loudly when the stored value is not a number.",
            params = {LuaDocFragments.Common.KEY_STRING, "delta: number"},
            returnType = "number"
        )
        double IncrementAsync(String key, double delta);

        @LuaDoc(description = "Flushes any pending debounced writes in this store to disk synchronously.")
        default void Flush() {}
    }

    @LuaDoc(description = "Returns the World storage data store.", returnType = "IDataStore")
    IDataStore GetWorldStorage();

    @LuaDoc(description = "Returns the Player storage data store for a specific player.", params = {"playerUuid: string"}, returnType = "IDataStore")
    IDataStore GetPlayerStorage(String playerUuid);

    @LuaDoc(description = "Returns the Session in-memory storage data store.", returnType = "IDataStore")
    IDataStore GetSessionStorage();

    @LuaDoc(description = "Flushes all pending debounced writes across all stores to disk synchronously.")
    default void Flush() {}
}
