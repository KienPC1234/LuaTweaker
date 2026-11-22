package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import org.jetbrains.annotations.Nullable;
import java.util.Locale;
import java.util.function.BiConsumer;

public class BlockBuilderImpl implements IBlockBuilder {
    /** DSL defaults applied before any Lua callback runs. */
    private static final float DEFAULT_HARDNESS = 1.5f;
    private static final float DEFAULT_RESISTANCE = 6.0f;
    private static final String DEFAULT_SOUND_TYPE = "STONE";
    private static final float DEFAULT_FRICTION = 0.6f;
    private static final float DEFAULT_FACTOR = 1.0f;
    private static final String DEFAULT_FACING = "NONE";
    private static final int DEFAULT_CONTAINER_COLS = 6;
    private static final String DEFAULT_CONTAINER_DROP_MODE = "packed";
    private static final double DEFAULT_CONTAINER_USE_DISTANCE = 8.0;

    private final String id;
    private float hardnessValue = DEFAULT_HARDNESS;
    private float resistanceValue = DEFAULT_RESISTANCE;
    private int lightLevelValue;
    private String soundTypeName = DEFAULT_SOUND_TYPE;
    private boolean requiresToolEnabled;
    private String mineableWithTool;
    private int miningLevelValue;
    private float frictionValue = DEFAULT_FRICTION;
    private String modelPath;
    private String texturePath;
    private BiConsumer<Object, Object> rightClickHandler;

    public BlockBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IBlockBuilder hardness(float hardness) {
        this.hardnessValue = hardness;
        return this;
    }

    @Override
    public IBlockBuilder resistance(float resistance) {
        this.resistanceValue = resistance;
        return this;
    }

    @Override
    public IBlockBuilder lightLevel(int level) {
        this.lightLevelValue = Math.min(15, Math.max(0, level));
        return this;
    }

    @Override
    public IBlockBuilder soundType(String soundType) {
        this.soundTypeName = soundType != null ? soundType.toUpperCase(Locale.ROOT) : DEFAULT_SOUND_TYPE;
        return this;
    }

    @Override
    public IBlockBuilder requiresTool(boolean requires) {
        this.requiresToolEnabled = requires;
        return this;
    }

    @Override
    public IBlockBuilder mineableWith(String toolType) {
        this.mineableWithTool = upperOrNull(toolType);
        this.requiresToolEnabled = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(int level) {
        this.miningLevelValue = level;
        this.requiresToolEnabled = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(String levelName) {
        if (levelName != null) {
            this.miningLevelValue = MiningLevels.parse(levelName);
            this.requiresToolEnabled = true;
        }
        return this;
    }

    @Override
    public IBlockBuilder friction(float friction) {
        this.frictionValue = friction;
        return this;
    }

    private String mapColorName;
    private float jumpFactorValue = DEFAULT_FACTOR;
    private float speedFactorValue = DEFAULT_FACTOR;
    private boolean noCollisionEnabled;
    private boolean noOcclusionEnabled;
    private String pushReactionName;
    private boolean replaceableEnabled;
    private boolean ignitedByLavaEnabled;
    private boolean liquidEnabled;
    private String offsetTypeName;
    private Boolean redstoneConductorValue;

    @Override
    public IBlockBuilder mapColor(String colorName) {
        this.mapColorName = colorName;
        return this;
    }

    @Override
    public IBlockBuilder jumpFactor(float jumpFactor) {
        this.jumpFactorValue = jumpFactor;
        return this;
    }

    @Override
    public IBlockBuilder speedFactor(float speedFactor) {
        this.speedFactorValue = speedFactor;
        return this;
    }

    @Override
    public IBlockBuilder noCollision(boolean noCollision) {
        this.noCollisionEnabled = noCollision;
        return this;
    }

    @Override
    public IBlockBuilder noOcclusion(boolean noOcclusion) {
        this.noOcclusionEnabled = noOcclusion;
        return this;
    }

    @Override
    public IBlockBuilder pushReaction(String pushReaction) {
        this.pushReactionName = pushReaction;
        return this;
    }

    @Override
    public IBlockBuilder replaceable(boolean replaceable) {
        this.replaceableEnabled = replaceable;
        return this;
    }

    @Override
    public IBlockBuilder ignitedByLava(boolean ignitedByLava) {
        this.ignitedByLavaEnabled = ignitedByLava;
        return this;
    }

    @Override
    public IBlockBuilder liquid(boolean liquid) {
        this.liquidEnabled = liquid;
        return this;
    }

    @Override
    public IBlockBuilder offsetType(String offsetType) {
        this.offsetTypeName = offsetType;
        return this;
    }

    @Override
    public IBlockBuilder redstoneConductor(boolean redstoneConductor) {
        this.redstoneConductorValue = redstoneConductor;
        return this;
    }

    private String shapeName;
    private String baseBlockRef;

    @Override
    public IBlockBuilder shape(String shapeName) {
        this.shapeName = upperOrNull(shapeName);
        return this;
    }

    @Override
    public IBlockBuilder baseBlock(String baseBlockId) {
        this.baseBlockRef = blankToNull(baseBlockId);
        return this;
    }

    @Override
    public IBlockBuilder model(String modelPath) {
        this.modelPath = modelPath;
        return this;
    }

    @Override
    public IBlockBuilder texture(String texturePath) {
        this.texturePath = texturePath;
        return this;
    }

    private final java.util.List<String> tags = new java.util.ArrayList<>();
    private String creativeTabId;

    @Override
    public IBlockBuilder onRightClick(BiConsumer<Object, Object> handler) {
        this.rightClickHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder tag(String tagId) {
        if (tagId != null && !tagId.isBlank()) {
            this.tags.add(tagId);
        }
        return this;
    }

    @Override
    public IBlockBuilder creativeTab(String tabId) {
        this.creativeTabId = tabId;
        return this;
    }

    private String dropItemId;
    private int minDropCount = 1;
    private int maxDropCount = 1;
    private int minExp;
    private int maxExp;

    @Override
    public IBlockBuilder drop(String itemId, int minCount, int maxCount) {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("Block drop itemId must not be blank");
        }
        this.dropItemId = itemId;
        this.minDropCount = Math.max(MIN_DROP_COUNT, minCount);
        this.maxDropCount = Math.max(this.minDropCount, maxCount);
        return this;
    }

    @Override
    public IBlockBuilder dropExperience(int minExp, int maxExp) {
        this.minExp = Math.max(0, minExp);
        this.maxExp = Math.max(this.minExp, maxExp);
        return this;
    }

    private int containerRows;
    private int containerCols = DEFAULT_CONTAINER_COLS;
    private String containerDropMode = DEFAULT_CONTAINER_DROP_MODE;
    private double containerUseDistanceValue = DEFAULT_CONTAINER_USE_DISTANCE;

    /** Container grid bounds (the vanilla chest GUI supports 1..6 rows, 1..9 cols). */
    private static final int MAX_CONTAINER_ROWS = 6;
    private static final int MAX_CONTAINER_COLS = 9;
    private static final int MIN_CONTAINER_DIM = 1;
    private static final double MIN_CONTAINER_USE_DISTANCE = 1.0;
    private static final int MIN_DROP_COUNT = 1;
    /** Block-state property names must be lowercase letters/digits/underscores. */
    private static final String VALID_PROPERTY_NAME = "[a-z0-9_]+";
    /** State property names reserved by facing/connection/wall mechanics; collisions crash registration. */
    private static final java.util.Set<String> RESERVED_PROPERTY_NAMES = java.util.Set.of(
            "facing", "half", "shape", "waterlogged", "up", "down",
            "north", "south", "east", "west", "open", "powered", "lit",
            "hinge", "layers", "age", "moisture", "snowy");

    @Override
    public IBlockBuilder container(int rows, int cols, String dropMode) {
        this.containerRows = Math.max(MIN_CONTAINER_DIM, Math.min(MAX_CONTAINER_ROWS, rows));
        this.containerCols = Math.max(MIN_CONTAINER_DIM, Math.min(MAX_CONTAINER_COLS, cols));
        String mode = dropMode != null ? dropMode.toLowerCase(Locale.ROOT) : DEFAULT_CONTAINER_DROP_MODE;
        this.containerDropMode = switch (mode) {
            case "spill", "none" -> mode;
            default -> DEFAULT_CONTAINER_DROP_MODE;
        };
        return this;
    }

    @Override
    public IBlockBuilder containerUseDistance(double distance) {
        this.containerUseDistanceValue = Math.max(MIN_CONTAINER_USE_DISTANCE, distance);
        return this;
    }

    private java.util.function.BiFunction<Object, Object, Boolean> itemFilterHandler;

    @Override
    public IBlockBuilder itemFilter(java.util.function.BiFunction<Object, Object, Boolean> handler) {
        this.itemFilterHandler = handler;
        return this;
    }

    private String containerTexturePath;
    private String containerTitleText;

    @Override
    public IBlockBuilder containerTexture(String texturePath) {
        this.containerTexturePath = texturePath;
        return this;
    }

    @Override
    public IBlockBuilder containerTitle(String title) {
        this.containerTitleText = title;
        return this;
    }

    private final java.util.Map<Integer, int[]> slotPositions = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Set<Integer> lockedSlots = new java.util.LinkedHashSet<>();
    private String slotTexturePath;
    private boolean manualSlotsEnabled;

    @Override
    public IBlockBuilder manualSlots() {
        this.manualSlotsEnabled = true;
        return this;
    }

    @Override
    public IBlockBuilder slotPosition(int slotIndex, int x, int y) {
        if (slotIndex >= 0) {
            this.slotPositions.put(slotIndex, new int[]{x, y});
        }
        return this;
    }

    @Override
    public IBlockBuilder slotTexture(String texturePath) {
        this.slotTexturePath = texturePath;
        return this;
    }

    @Override
    public IBlockBuilder lockSlot(int slotIndex, boolean locked) {
        if (locked) {
            this.lockedSlots.add(slotIndex);
        } else {
            this.lockedSlots.remove(slotIndex);
        }
        return this;
    }

    private int energyCapacity;
    private int energyMaxReceive;
    private int energyMaxExtract;
    private int fluidCapacity;
    private final java.util.List<com.luatweaker.api.content.MachineBarSpec> guiBars = new java.util.ArrayList<>();
    private com.luatweaker.api.content.BooleanStateSpec booleanStateSpec;
    private boolean connectionStateEnabled;
    private BiConsumer<Object, Object> tickHandler;

    @Override
    public IBlockBuilder energyStorage(int capacity, int maxReceive, int maxExtract) {
        this.energyCapacity = Math.max(0, capacity);
        this.energyMaxReceive = Math.max(0, maxReceive);
        this.energyMaxExtract = Math.max(0, maxExtract);
        return this;
    }

    @Override
    public IBlockBuilder fluidStorage(int capacityMB) {
        this.fluidCapacity = Math.max(0, capacityMB);
        return this;
    }

    @Override
    public IBlockBuilder guiBar(String id, int x, int y, int width, int height, String source, int color) {
        return guiBar(id, x, y, width, height, source, color, "topleft");
    }

    @Override
    public IBlockBuilder guiBar(String id, int x, int y, int width, int height, String source, int color, String anchor) {
        if (id != null && source != null && width > 0 && height > 0) {
            this.guiBars.add(new com.luatweaker.api.content.MachineBarSpec(
                    id, x, y, width, height, source.toLowerCase(Locale.ROOT), color,
                    null, null, 0, 0, null, null, anchor));
        }
        return this;
    }

    @Override
    public IBlockBuilder customBar(String id, int x, int y, int width, int height, String currentKey, String maxKey, int color, @Nullable String tooltip) {
        return customBar(id, x, y, width, height, currentKey, maxKey, color, tooltip, "topleft");
    }

    @Override
    public IBlockBuilder customBar(String id, int x, int y, int width, int height, String currentKey, String maxKey, int color, @Nullable String tooltip, String anchor) {
        if (id != null && currentKey != null && maxKey != null && width > 0 && height > 0) {
            this.guiBars.add(new com.luatweaker.api.content.MachineBarSpec(
                    id, x, y, width, height, "custom", color, tooltip, null, 0, 0, currentKey, maxKey, anchor));
        }
        return this;
    }

    @Override
    public IBlockBuilder booleanState(String property, String offTexture, String onTexture) {
        if (property != null && property.matches(VALID_PROPERTY_NAME) && offTexture != null && onTexture != null) {
            if (RESERVED_PROPERTY_NAMES.contains(property.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Block '" + id + "' booleanState property '" + property
                        + "' is reserved by facing/connection mechanics and would crash registration");
            }
            this.booleanStateSpec = new com.luatweaker.api.content.BooleanStateSpec(property, offTexture, onTexture);
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "Invalid booleanState for block '" + id + "' (property must be [a-z0-9_]+, textures must be set)");
        }
        return this;
    }

    @Override
    public IBlockBuilder connectionState(boolean connections) {
        this.connectionStateEnabled = connections;
        return this;
    }

    @Override
    public IBlockBuilder onTick(BiConsumer<Object, Object> handler) {
        this.tickHandler = handler;
        return this;
    }

    private String displayNameText;
    private final java.util.List<com.luatweaker.api.content.BoxSpec> boxes = new java.util.ArrayList<>();
    private String facingName = DEFAULT_FACING;
    private BiConsumer<Object, Object> renderHandler;
    private final java.util.List<com.luatweaker.api.content.MachineButtonSpec> buttons = new java.util.ArrayList<>();
    private final java.util.List<com.luatweaker.api.content.MachineLabelSpec> labels = new java.util.ArrayList<>();
    private BiConsumer<Object, String> buttonClickHandler;
    private BiConsumer<Object, Object> neighborChangedHandler;

    @Override
    public IBlockBuilder box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isNaN(minX) || Double.isNaN(minY) || Double.isNaN(minZ)
                || Double.isNaN(maxX) || Double.isNaN(maxY) || Double.isNaN(maxZ)
                || Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ)
                || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            throw new IllegalArgumentException("Block box coordinates must be finite numbers");
        }
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("Block box min must not exceed max, got: ("
                    + minX + ", " + minY + ", " + minZ + ") to (" + maxX + ", " + maxY + ", " + maxZ + ")");
        }
        this.boxes.add(new com.luatweaker.api.content.BoxSpec(minX, minY, minZ, maxX, maxY, maxZ));
        return this;
    }

    @Override
    public IBlockBuilder facing(String facing) {
        this.facingName = facing != null ? facing.toUpperCase(Locale.ROOT) : DEFAULT_FACING;
        return this;
    }

    @Override
    public IBlockBuilder onRender(BiConsumer<Object, Object> handler) {
        this.renderHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder button(String id, int x, int y, int width, int height, String label) {
        return button(id, x, y, width, height, label, "topleft");
    }

    @Override
    public IBlockBuilder button(String id, int x, int y, int width, int height, String label, String anchor) {
        if (id != null && label != null) {
            this.buttons.add(new com.luatweaker.api.content.MachineButtonSpec(id, x, y, width, height, label, anchor));
        }
        return this;
    }

    @Override
    public IBlockBuilder label(int x, int y, String textOrVar, int color) {
        return label(x, y, textOrVar, color, "left", 1.0, false);
    }

    @Override
    public IBlockBuilder label(int x, int y, String textOrVar, int color, String align, double scale, boolean shadow) {
        if (textOrVar != null) {
            this.labels.add(new com.luatweaker.api.content.MachineLabelSpec(x, y, textOrVar, color,
                    align, (float) Math.min(3.0, Math.max(0.5, scale)), shadow));
        }
        return this;
    }

    @Override
    public IBlockBuilder onButtonClick(BiConsumer<Object, String> handler) {
        this.buttonClickHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder onNeighborChanged(BiConsumer<Object, Object> handler) {
        this.neighborChangedHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder displayName(String name) {
        this.displayNameText = name;
        return this;
    }

    /** Normalizes an optional uppercase token; absent input stays absent. */
    private static String upperOrNull(String value) {
        return value != null ? value.toUpperCase(Locale.ROOT) : null;
    }

    /** Normalizes an optional id; blank input stays absent. */
    private static String blankToNull(String value) {
        return value != null && !value.isBlank() ? value : null;
    }

    @Override public String getId() { return id; }
    @Override public float getHardness() { return hardnessValue; }
    @Override public float getResistance() { return resistanceValue; }
    @Override public int getLightLevel() { return lightLevelValue; }
    @Override public String getSoundType() { return soundTypeName; }
    @Override public boolean getRequiresTool() { return requiresToolEnabled; }
    @Override public String getMineableWith() { return mineableWithTool; }
    @Override public int getMiningLevel() { return miningLevelValue; }
    @Override public float getFriction() { return frictionValue; }
    @Override public String getMapColor() { return mapColorName; }
    @Override public float getJumpFactor() { return jumpFactorValue; }
    @Override public float getSpeedFactor() { return speedFactorValue; }
    @Override public boolean isNoCollision() { return noCollisionEnabled; }
    @Override public boolean isNoOcclusion() { return noOcclusionEnabled; }
    @Override public String getPushReaction() { return pushReactionName; }
    @Override public boolean isReplaceable() { return replaceableEnabled; }
    @Override public boolean isIgnitedByLava() { return ignitedByLavaEnabled; }
    @Override public boolean isLiquid() { return liquidEnabled; }
    @Override public String getOffsetType() { return offsetTypeName; }
    @Override public Boolean getRedstoneConductor() { return redstoneConductorValue; }
    @Override public String getShape() { return shapeName; }
    @Override public String getBaseBlockId() { return baseBlockRef; }
    @Override public String getModel() { return modelPath; }
    @Override public String getTexture() { return texturePath; }
    @Override public java.util.List<String> getTags() { return java.util.List.copyOf(tags); }
    @Override public String getCreativeTab() { return creativeTabId; }
    @Override public BiConsumer<Object, Object> getRightClickHandler() { return rightClickHandler; }
    @Override public String getDropItemId() { return dropItemId; }
    @Override public int getMinDropCount() { return minDropCount; }
    @Override public int getMaxDropCount() { return maxDropCount; }
    @Override public int getMinExp() { return minExp; }
    @Override public int getMaxExp() { return maxExp; }
    @Override public boolean isContainer() { return containerRows > 0; }
    @Override public int getContainerRows() { return containerRows; }
    @Override public int getContainerCols() { return containerCols; }
    @Override public String getContainerDropMode() { return containerDropMode; }
    @Override public double getContainerUseDistance() { return containerUseDistanceValue; }
    @Override public java.util.function.BiFunction<Object, Object, Boolean> getItemFilter() { return itemFilterHandler; }
    @Override public String getContainerTexture() { return containerTexturePath; }
    @Override public String getContainerTitle() { return containerTitleText; }
    @Override public java.util.Map<Integer, int[]> getSlotPositions() { return java.util.Map.copyOf(slotPositions); }
    @Override public java.util.Set<Integer> getLockedSlots() { return java.util.Set.copyOf(lockedSlots); }
    @Override public String getSlotTexture() { return slotTexturePath; }
    @Override public boolean isManualSlots() { return manualSlotsEnabled; }
    @Override public int getEnergyCapacity() { return energyCapacity; }
    @Override public int getEnergyMaxReceive() { return energyMaxReceive; }
    @Override public int getEnergyMaxExtract() { return energyMaxExtract; }
    @Override public int getFluidCapacity() { return fluidCapacity; }
    @Override public java.util.List<com.luatweaker.api.content.MachineBarSpec> getGuiBars() { return java.util.List.copyOf(guiBars); }
    @Override public com.luatweaker.api.content.BooleanStateSpec getBooleanState() { return booleanStateSpec; }
    @Override public boolean isConnectionState() { return connectionStateEnabled; }
    @Override public BiConsumer<Object, Object> getTickHandler() { return tickHandler; }
    @Override public String getDisplayName() { return displayNameText; }
    @Override public java.util.List<com.luatweaker.api.content.BoxSpec> getBoxes() { return java.util.List.copyOf(boxes); }
    @Override public String getFacing() { return facingName; }
    @Override public BiConsumer<Object, Object> getRenderHandler() { return renderHandler; }
    @Override public java.util.List<com.luatweaker.api.content.MachineButtonSpec> getButtons() { return java.util.List.copyOf(buttons); }
    @Override public java.util.List<com.luatweaker.api.content.MachineLabelSpec> getLabels() { return java.util.List.copyOf(labels); }
    private com.luatweaker.integration.geo.GeoModelSpec geoModelSpec;

    @Override
    public IBlockBuilder geoModel(String modelPath, String texturePath, String animationPath, @Nullable String defaultAnimation) {
        this.geoModelSpec = new com.luatweaker.integration.geo.GeoModelSpec(modelPath, texturePath, animationPath, defaultAnimation);
        return this;
    }

    @Override public BiConsumer<Object, String> getButtonClickHandler() { return buttonClickHandler; }
    @Override public BiConsumer<Object, Object> getNeighborChangedHandler() { return neighborChangedHandler; }
    @Override @Nullable public com.luatweaker.integration.geo.GeoModelSpec getGeoModel() { return geoModelSpec; }
}
