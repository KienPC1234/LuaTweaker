package com.luatweaker.platform.gecko;

import com.luatweaker.api.content.GeoModelSpec;
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

    private final ResourceLocation modelLocation;
    private final ResourceLocation textureLocation;
    private final ResourceLocation animationLocation;

    public LuaDynamicGeoModel(@NotNull GeoModelSpec spec, @NotNull String defaultSubtype) {
        Objects.requireNonNull(spec, "spec");
        this.modelLocation = resolveModelPath(spec.modelPath(), defaultSubtype);
        this.textureLocation = resolveTexturePath(spec.texturePath(), defaultSubtype, this.modelLocation);
        this.animationLocation = resolveAnimationPath(spec.animationPath(), defaultSubtype, this.modelLocation);
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
            return ResourceLocation.fromNamespaceAndPath("luatweaker", "geo/" + subtype + "/missing.geo.json");
        }
        ResourceLocation parsed = parseResourceLocation(raw);
        String path = parsed.getPath();
        if (path.endsWith(".geo.json")) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + ".geo.json");
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), "geo/" + subtype + "/" + path + ".geo.json");
    }

    private static ResourceLocation resolveTexturePath(String raw, String subtype, ResourceLocation fallbackModel) {
        if (raw == null || raw.isBlank()) {
            String fallbackPath = fallbackModel.getPath();
            if (fallbackPath.startsWith("geo/")) {
                fallbackPath = fallbackPath.substring("geo/".length());
            }
            if (fallbackPath.endsWith(".geo.json")) {
                fallbackPath = fallbackPath.substring(0, fallbackPath.length() - ".geo.json".length());
            }
            return ResourceLocation.fromNamespaceAndPath(fallbackModel.getNamespace(), "textures/" + fallbackPath + ".png");
        }
        ResourceLocation parsed = parseResourceLocation(raw);
        String path = parsed.getPath();
        if (path.endsWith(".png")) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + ".png");
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), "textures/" + subtype + "/" + path + ".png");
    }

    private static ResourceLocation resolveAnimationPath(String raw, String subtype, ResourceLocation fallbackModel) {
        if (raw == null || raw.isBlank()) {
            String fallbackPath = fallbackModel.getPath();
            if (fallbackPath.startsWith("geo/")) {
                fallbackPath = fallbackPath.substring("geo/".length());
            }
            if (fallbackPath.endsWith(".geo.json")) {
                fallbackPath = fallbackPath.substring(0, fallbackPath.length() - ".geo.json".length());
            }
            return ResourceLocation.fromNamespaceAndPath(fallbackModel.getNamespace(), "animations/" + fallbackPath + ".animation.json");
        }
        ResourceLocation parsed = parseResourceLocation(raw);
        String path = parsed.getPath();
        if (path.endsWith(".animation.json")) {
            return parsed;
        }
        if (path.contains("/")) {
            return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), path + ".animation.json");
        }
        return ResourceLocation.fromNamespaceAndPath(parsed.getNamespace(), "animations/" + subtype + "/" + path + ".animation.json");
    }

    private static ResourceLocation parseResourceLocation(String raw) {
        if (raw.contains(":")) {
            return ResourceLocation.parse(raw);
        }
        return ResourceLocation.fromNamespaceAndPath("luatweaker", raw);
    }
}
