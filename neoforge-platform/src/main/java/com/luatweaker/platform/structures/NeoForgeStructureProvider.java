package com.luatweaker.platform.structures;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.structures.NbtStructureLoader;
import com.luatweaker.structures.StructureServiceImpl;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Materializes every Lua-registered structure into virtual datapack JSON.
 *
 * <p>Per registration four files are emitted through {@link IDatapackService} (all
 * shapes verified against the vanilla 1.21.1 datapack in the game jar):
 * <ul>
 *   <li>{@code <ns>/worldgen/structure/<path>.json} - a {@code minecraft:jigsaw}
 *       structure pointing at a generated single-element pool (verified fields:
 *       {@code type/biomes/start_pool/size/start_height/project_start_to_heightmap/
 *       max_distance_from_center/use_expansion_hack/terrain_adaptation/spawn_overrides/step}).</li>
 *   <li>{@code <ns>/worldgen/template_pool/<path>.json} - one
 *       {@code minecraft:single_pool_element} referencing the template location with
 *       inline empty processors (verified: {@code fallback/elements/weight/element/
 *       element_type/location/processors/projection}; the inline
 *       {@code {"processors": []}} form is verbatim vanilla).</li>
 *   <li>{@code <ns>/worldgen/structure_set/<path>.json} - {@code random_spread}
 *       placement with the registered spacing/separation (verified fields:
 *       {@code placement/type/salt/separation/spacing, structures/structure/weight}).</li>
 *   <li>{@code <ns>/tags/worldgen/biome/has_structure/<path>.json} - the biome tag
 *       the structure points at (verified path + {@code values} format).</li>
 * </ul>
 *
 * <p>A {@code minecraft:jigsaw} structure is the only vanilla mechanism whose JSON
 * can reference an arbitrary NBT template (verified: no vanilla structure JSON
 * references a template directly; pools do via {@code element.location}, which maps
 * to {@code data/<ns>/structure/<path>.nbt}). The MVP therefore places each template
 * as a single-element pool; no multi-piece jigsaw assembly API is exposed.
 *
 * <p>The template NBT itself is served by the virtual pack's physical-file fallback
 * from {@code luamods/<mod_id>/data/<ns>/structure/<path>.nbt}; when it is absent
 * under every supplied data root the registration is skipped with an error log so a
 * missing template can never crash the datapack load.
 */
public class NeoForgeStructureProvider {

    private final StructureServiceImpl structureService;
    private final IDatapackService datapackService;
    private final List<File> dataRoots;

    public NeoForgeStructureProvider(@NotNull StructureServiceImpl structureService,
                                     @NotNull IDatapackService datapackService,
                                     @NotNull List<File> dataRoots) {
        this.structureService = structureService;
        this.datapackService = datapackService;
        this.dataRoots = List.copyOf(dataRoots);
    }

    public void applyAll() {
        int emitted = 0;
        int skipped = 0;
        for (String structureId : structureService.getStructureIds()) {
            StructureServiceImpl.StructureEntry entry = structureService.getEntry(structureId);
            if (entry == null) {
                continue;
            }
            Optional<File> template = NbtStructureLoader.findTemplateFile(dataRoots, entry.template());
            if (template.isEmpty()) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "[StructureProvider] Skipped structure '" + structureId + "': template '"
                                + entry.template() + "' not found under any data root"
                                + " (expected data/<namespace>/structure/<path>.nbt)");
                skipped++;
                continue;
            }
            emit(entry);
            emitted++;
        }

        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "[StructureProvider] Applied: " + emitted + " structures, " + skipped + " skipped (missing template)");
    }

    private void emit(StructureServiceImpl.StructureEntry entry) {
        String[] parts = splitId(entry.structureId());
        String namespace = parts[0];
        String path = parts[1];
        String templateLocation = NbtStructureLoader.templateLocation(entry.template());
        String tagReference = "#" + namespace + ":has_structure/" + path;

        datapackService.addData(namespace + "/worldgen/structure/" + path + ".json",
                buildStructureJson(tagReference, namespace + ":" + path));
        datapackService.addData(namespace + "/worldgen/template_pool/" + path + ".json",
                buildPoolJson(templateLocation));
        datapackService.addData(namespace + "/worldgen/structure_set/" + path + ".json",
                buildStructureSetJson(entry));
        datapackService.addData(namespace + "/tags/worldgen/biome/has_structure/" + path + ".json",
                buildBiomeTagJson(entry));
    }

    // ===== JSON builders (field names verified against the vanilla 1.21.1 datapack) =====

    private String buildStructureJson(String tagReference, String poolReference) {
        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:jigsaw");
        json.addProperty("biomes", tagReference);
        json.addProperty("start_pool", poolReference);
        json.addProperty("size", 1);
        JsonObject startHeight = new JsonObject();
        startHeight.addProperty("absolute", 0);
        json.add("start_height", startHeight);
        json.addProperty("project_start_to_heightmap", "WORLD_SURFACE_WG");
        json.addProperty("max_distance_from_center", 80);
        json.addProperty("use_expansion_hack", false);
        json.addProperty("terrain_adaptation", "beard_thin");
        json.add("spawn_overrides", new JsonObject());
        json.addProperty("step", "surface_structures");
        return json.toString();
    }

    private String buildPoolJson(String templateLocation) {
        JsonObject element = new JsonObject();
        element.addProperty("element_type", "minecraft:single_pool_element");
        element.addProperty("location", templateLocation);
        JsonObject processors = new JsonObject();
        processors.add("processors", new JsonArray());
        element.add("processors", processors);
        element.addProperty("projection", "rigid");

        JsonObject wrapper = new JsonObject();
        wrapper.add("element", element);
        wrapper.addProperty("weight", 1);

        JsonArray elements = new JsonArray();
        elements.add(wrapper);

        JsonObject json = new JsonObject();
        json.add("elements", elements);
        json.addProperty("fallback", "minecraft:empty");
        return json.toString();
    }

    private String buildStructureSetJson(StructureServiceImpl.StructureEntry entry) {
        JsonObject placement = new JsonObject();
        placement.addProperty("type", "minecraft:random_spread");
        placement.addProperty("salt", entry.structureId().hashCode() & 0x7fffffff);
        placement.addProperty("separation", entry.separation());
        placement.addProperty("spacing", entry.spacing());

        JsonObject structureRef = new JsonObject();
        structureRef.addProperty("structure", entry.structureId());
        structureRef.addProperty("weight", 1);
        JsonArray structures = new JsonArray();
        structures.add(structureRef);

        JsonObject json = new JsonObject();
        json.add("placement", placement);
        json.add("structures", structures);
        return json.toString();
    }

    private String buildBiomeTagJson(StructureServiceImpl.StructureEntry entry) {
        JsonArray values = new JsonArray();
        for (String biome : entry.spawnBiomes()) {
            values.add(biome);
        }
        JsonObject json = new JsonObject();
        json.add("values", values);
        return json.toString();
    }

    /** Splits "namespace:path" -&gt; [namespace, path]; defaults the namespace to "luatweaker". */
    private static String[] splitId(String id) {
        int colon = id.indexOf(':');
        if (colon > 0) {
            return new String[]{id.substring(0, colon), id.substring(colon + 1)};
        }
        return new String[]{"luatweaker", id};
    }
}
