package com.luatweaker.client;

import com.luatweaker.api.objects.IItem;
import com.luatweaker.api.objects.IRecipe;
import com.luatweaker.api.pal.IPlatformContent;

import java.util.List;
import java.util.Set;

/**
 * Minimal PAL content double exposing only the process flags the update/client tests read.
 */
record FakeContent(boolean dedicated, boolean client, boolean localServer)
        implements IPlatformContent {

    @Override
    public IItem createItem(String itemId, int count) {
        return null;
    }

    @Override
    public boolean itemExists(String itemId) {
        return false;
    }

    @Override
    public boolean blockExists(String blockId) {
        return false;
    }

    @Override
    public boolean fluidExists(String fluidId) {
        return false;
    }

    @Override
    public boolean tagExists(String tagId) {
        return false;
    }

    @Override
    public boolean isModLoaded(String modId) {
        return false;
    }

    @Override
    public boolean isClient() {
        return client;
    }

    @Override
    public boolean isDedicatedServer() {
        return dedicated;
    }

    @Override
    public boolean hasLocalServer() {
        return localServer;
    }

    @Override
    public String getPlatformName() {
        return "Fake";
    }

    @Override
    public Set<String> getSupportedMobParents() {
        return Set.of();
    }

    @Override
    public List<IRecipe> getAllRecipes() {
        return List.of();
    }
}
