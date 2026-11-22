package com.luatweaker.common.integration.geckolib;

import com.luatweaker.integration.geo.GeoModelSpec;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.model.GeoModel;

import java.util.Objects;

/**
 * Universal dynamic GeoModel driven by a {@link GeoModelSpec}.
 * Resolves Blockbench geo JSON models, PNG textures, and animation JSON files.
 *
 * <p>Supports both fully qualified resource paths (e.g. {@code "luatweaker:geo/item/scythe.geo.json"})
 * and shorthand identifiers (e.g. {@code "luatweaker:crystal_scythe"}).</p>
 */
public class LuaDynamicGeoModel<T extends GeoAnimatable> extends GeoModel<T> {
    private static final String GEO_DIR_PREFIX = "geo/";
    private static final String GEO_MODEL_EXTENSION = ".geo.json";
    private static final String TEXTURE_EXTENSION = ".png";
    private static final String ANIMATION_EXTENSION = ".animation.json";

    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final ResourceLocation animationLocation;

    public LuaDynamicGeoModel(@NotNull GeoModelSpec spec, @NotNull String defaultSubtype) {
        super();
        Objects.requireNonNull(spec, "spec");
        this.modelLocation = resolveModelPath(spec.modelPath(), defaultSubtype);
        String modelNamespace = this.modelLocation.getNamespace();
        this.textureLocation = resolveTexturePath(spec.texturePath(), defaultSubtype, this.modelLocation, modelNamespace);
        this.animationLocation = resolveAnimationPath(spec.animationPath(), defaultSubtype, this.modelLocation, modelNamespace);
    }

    @Override
    public ResourceLocation getModelResource(T animatable) {
        return this.modelLocation;
    }

    @Override
    public ResourceLocation getModelResource(T animatable, software.bernie.geckolib.renderer.GeoRenderer<T> renderer) {
        return this.modelLocation;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable) {
        return this.textureLocation;
    }

    @Override
    public ResourceLocation getTextureResource(T animatable, software.bernie.geckolib.renderer.GeoRenderer<T> renderer) {
        return this.textureLocation;
    }

    @Override
    public ResourceLocation getAnimationResource(T animatable) {
        return this.animationLocation;
    }

    private static ResourceLocation resolveModelPath(String raw, String subtype) {
        if (raw == null || raw.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("luatweaker", GEO_DIR_PREFIX + subtype + "/missing.geo.json");
        }
        ResourceLocation parsed = parseResourceLocation(raw, "luatweaker");
        String path = parsed.getPath();
        if (path.endsWith(GEO_MODEL_EXTENSION)) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + GEO_MODEL_EXTENSION);
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), GEO_DIR_PREFIX + subtype + "/" + path + GEO_MODEL_EXTENSION);
    }

    private static ResourceLocation resolveTexturePath(String raw, String subtype, ResourceLocation fallbackModel,
                                                        String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            String fallbackPath = fallbackModel.getPath();
            if (fallbackPath.startsWith(GEO_DIR_PREFIX)) {
                fallbackPath = fallbackPath.substring(GEO_DIR_PREFIX.length());
            }
            if (fallbackPath.endsWith(GEO_MODEL_EXTENSION)) {
                fallbackPath = fallbackPath.substring(0, fallbackPath.length() - GEO_MODEL_EXTENSION.length());
            }
            return ResourceLocation.fromNamespaceAndPath(fallbackModel.getNamespace(), "textures/" + fallbackPath + TEXTURE_EXTENSION);
        }
        ResourceLocation parsed = parseResourceLocation(raw, defaultNamespace);
        String path = parsed.getPath();
        if (path.endsWith(TEXTURE_EXTENSION)) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + TEXTURE_EXTENSION);
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), "textures/" + subtype + "/" + path + TEXTURE_EXTENSION);
    }

    private static ResourceLocation resolveAnimationPath(String raw, String subtype, ResourceLocation fallbackModel,
                                                          String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            String fallbackPath = fallbackModel.getPath();
            if (fallbackPath.startsWith(GEO_DIR_PREFIX)) {
                fallbackPath = fallbackPath.substring(GEO_DIR_PREFIX.length());
            }
            if (fallbackPath.endsWith(GEO_MODEL_EXTENSION)) {
                fallbackPath = fallbackPath.substring(0, fallbackPath.length() - GEO_MODEL_EXTENSION.length());
            }
            return ResourceLocation.fromNamespaceAndPath(fallbackModel.getNamespace(), "animations/" + fallbackPath + ANIMATION_EXTENSION);
        }
        ResourceLocation parsed = parseResourceLocation(raw, defaultNamespace);
        String path = parsed.getPath();
        if (path.endsWith(ANIMATION_EXTENSION)) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + ANIMATION_EXTENSION);
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), "animations/" + subtype + "/" + path + ANIMATION_EXTENSION);
    }

    private static ResourceLocation parseResourceLocation(String raw, String defaultNamespace) {
        if (raw.contains(":")) {
            return ResourceLocation.parse(raw);
        }
        return ResourceLocation.fromNamespaceAndPath(defaultNamespace, raw);
    }
}
