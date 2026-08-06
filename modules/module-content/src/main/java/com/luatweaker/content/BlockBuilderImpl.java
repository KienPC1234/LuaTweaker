package com.luatweaker.content;

import com.luatweaker.api.content.IBlockBuilder;
import java.util.function.BiConsumer;

public class BlockBuilderImpl implements IBlockBuilder {
    private final String id;
    private float hardness = 1.5f;
    private float resistance = 6.0f;
    private int lightLevel = 0;
    private String soundType = "STONE";
    private boolean requiresTool = false;
    private String mineableWith;
    private int miningLevel = 0;
    private float friction = 0.6f;
    private String model;
    private String texture;
    private BiConsumer<Object, Object> rightClickHandler;

    public BlockBuilderImpl(String id) {
        this.id = id;
    }

    @Override
    public IBlockBuilder hardness(float hardness) {
        this.hardness = hardness;
        return this;
    }

    @Override
    public IBlockBuilder resistance(float resistance) {
        this.resistance = resistance;
        return this;
    }

    @Override
    public IBlockBuilder lightLevel(int level) {
        this.lightLevel = level;
        return this;
    }

    @Override
    public IBlockBuilder soundType(String soundType) {
        this.soundType = soundType != null ? soundType.toUpperCase() : "STONE";
        return this;
    }

    @Override
    public IBlockBuilder requiresTool(boolean requires) {
        this.requiresTool = requires;
        return this;
    }

    @Override
    public IBlockBuilder mineableWith(String toolType) {
        this.mineableWith = toolType != null ? toolType.toUpperCase() : null;
        this.requiresTool = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(int level) {
        this.miningLevel = level;
        this.requiresTool = true;
        return this;
    }

    @Override
    public IBlockBuilder miningLevel(String levelName) {
        if (levelName != null) {
            this.miningLevel = switch (levelName.toUpperCase()) {
                case "WOOD", "GOLD" -> 0;
                case "STONE" -> 1;
                case "IRON" -> 2;
                case "DIAMOND" -> 3;
                case "NETHERITE" -> 4;
                default -> {
                    try { 
                        yield Integer.parseInt(levelName); 
                    } catch (NumberFormatException e) { 
                        com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM, "Invalid mining level: " + levelName + ". Defaulting to 1.");
                        yield 1; 
                    }
                }
            };
            this.requiresTool = true;
        }
        return this;
    }

    @Override
    public IBlockBuilder friction(float friction) {
        this.friction = friction;
        return this;
    }

    private String mapColor;
    private float jumpFactor = 1.0f;
    private float speedFactor = 1.0f;
    private boolean noCollision;
    private boolean noOcclusion;
    private String pushReaction;
    private boolean replaceable;
    private boolean ignitedByLava;
    private boolean liquid;
    private String offsetType;
    private Boolean redstoneConductor;

    @Override
    public IBlockBuilder mapColor(String colorName) {
        this.mapColor = colorName;
        return this;
    }

    @Override
    public IBlockBuilder jumpFactor(float jumpFactor) {
        this.jumpFactor = jumpFactor;
        return this;
    }

    @Override
    public IBlockBuilder speedFactor(float speedFactor) {
        this.speedFactor = speedFactor;
        return this;
    }

    @Override
    public IBlockBuilder noCollision(boolean noCollision) {
        this.noCollision = noCollision;
        return this;
    }

    @Override
    public IBlockBuilder noOcclusion(boolean noOcclusion) {
        this.noOcclusion = noOcclusion;
        return this;
    }

    @Override
    public IBlockBuilder pushReaction(String pushReaction) {
        this.pushReaction = pushReaction;
        return this;
    }

    @Override
    public IBlockBuilder replaceable(boolean replaceable) {
        this.replaceable = replaceable;
        return this;
    }

    @Override
    public IBlockBuilder ignitedByLava(boolean ignitedByLava) {
        this.ignitedByLava = ignitedByLava;
        return this;
    }

    @Override
    public IBlockBuilder liquid(boolean liquid) {
        this.liquid = liquid;
        return this;
    }

    @Override
    public IBlockBuilder offsetType(String offsetType) {
        this.offsetType = offsetType;
        return this;
    }

    @Override
    public IBlockBuilder redstoneConductor(boolean redstoneConductor) {
        this.redstoneConductor = redstoneConductor;
        return this;
    }

    @Override
    public IBlockBuilder model(String modelPath) {
        this.model = modelPath;
        return this;
    }

    @Override
    public IBlockBuilder texture(String texturePath) {
        this.texture = texturePath;
        return this;
    }

    private final java.util.List<String> tags = new java.util.ArrayList<>();
    private String creativeTab;

    @Override
    public IBlockBuilder onRightClick(BiConsumer<Object, Object> handler) {
        this.rightClickHandler = handler;
        return this;
    }

    @Override
    public IBlockBuilder tag(String tagId) {
        if (tagId != null && !tagId.isBlank()) this.tags.add(tagId);
        return this;
    }

    @Override
    public IBlockBuilder creativeTab(String tabId) {
        this.creativeTab = tabId;
        return this;
    }

    private String dropItemId;
    private int minDropCount = 1;
    private int maxDropCount = 1;
    private int minExp = 0;
    private int maxExp = 0;

    @Override
    public IBlockBuilder drop(String itemId, int minCount, int maxCount) {
        this.dropItemId = itemId;
        this.minDropCount = Math.max(1, minCount);
        this.maxDropCount = Math.max(this.minDropCount, maxCount);
        return this;
    }

    @Override
    public IBlockBuilder dropExperience(int minExp, int maxExp) {
        this.minExp = Math.max(0, minExp);
        this.maxExp = Math.max(this.minExp, maxExp);
        return this;
    }

    private int containerRows = 0;
    private int containerCols = 6;
    private String containerDropMode = "packed";
    private double containerUseDistance = 8.0;

    /** Container grid bounds (the vanilla chest GUI supports 1..6 rows, 1..9 cols). */
    private static final int MAX_CONTAINER_ROWS = 6;
    private static final int MAX_CONTAINER_COLS = 9;
    /** Block-state property names must be lowercase letters/digits/underscores. */
    private static final String VALID_PROPERTY_NAME = "[a-z0-9_]+";

    @Override
    public IBlockBuilder container(int rows, int cols, String dropMode) {
        this.containerRows = Math.max(1, Math.min(MAX_CONTAINER_ROWS, rows));
        this.containerCols = Math.max(1, Math.min(MAX_CONTAINER_COLS, cols));
        String mode = dropMode != null ? dropMode.toLowerCase() : "packed";
        this.containerDropMode = switch (mode) {
            case "spill", "none" -> mode;
            default -> "packed";
        };
        return this;
    }

    private java.util.function.BiFunction<Object, Object, Boolean> itemFilter;

    @Override
    public IBlockBuilder containerUseDistance(double distance) {
        this.containerUseDistance = Math.max(1.0, distance);
        return this;
    }

    @Override
    public IBlockBuilder itemFilter(java.util.function.BiFunction<Object, Object, Boolean> handler) {
        this.itemFilter = handler;
        return this;
    }

    private String containerTexture;
    private String containerTitle;

    @Override
    public IBlockBuilder containerTexture(String texturePath) {
        this.containerTexture = texturePath;
        return this;
    }

    @Override
    public IBlockBuilder containerTitle(String title) {
        this.containerTitle = title;
        return this;
    }

    private final java.util.LinkedHashMap<Integer, int[]> slotPositions = new java.util.LinkedHashMap<>();
    private final java.util.Set<Integer> lockedSlots = new java.util.LinkedHashSet<>();
    private String slotTexture;

    @Override
    public IBlockBuilder slotPosition(int slotIndex, int x, int y) {
        if (slotIndex >= 0) {
            this.slotPositions.put(slotIndex, new int[]{x, y});
        }
        return this;
    }

    @Override
    public IBlockBuilder slotTexture(String texturePath) {
        this.slotTexture = texturePath;
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
    private com.luatweaker.api.content.BooleanStateSpec booleanState;
    private boolean connectionState;
    private java.util.function.BiConsumer<Object, Object> tickHandler;

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
        if (id != null && source != null && width > 0 && height > 0) {
            this.guiBars.add(new com.luatweaker.api.content.MachineBarSpec(
                    id, x, y, width, height, source.toLowerCase(), color));
        }
        return this;
    }

    @Override
    public IBlockBuilder booleanState(String property, String offTexture, String onTexture) {
        if (property != null && property.matches(VALID_PROPERTY_NAME) && offTexture != null && onTexture != null) {
            this.booleanState = new com.luatweaker.api.content.BooleanStateSpec(property, offTexture, onTexture);
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "Invalid booleanState for block '" + id + "' (property must be [a-z0-9_]+, textures must be set)");
        }
        return this;
    }

    @Override
    public IBlockBuilder connectionState(boolean connections) {
        this.connectionState = connections;
        return this;
    }

    @Override
    public IBlockBuilder onTick(java.util.function.BiConsumer<Object, Object> handler) {
        this.tickHandler = handler;
        return this;
    }

    private String displayName;

    @Override
    public IBlockBuilder displayName(String name) {
        this.displayName = name;
        return this;
    }

    @Override public String getId() { return id; }
    @Override public float getHardness() { return hardness; }
    @Override public float getResistance() { return resistance; }
    @Override public int getLightLevel() { return lightLevel; }
    @Override public String getSoundType() { return soundType; }
    @Override public boolean getRequiresTool() { return requiresTool; }
    @Override public String getMineableWith() { return mineableWith; }
    @Override public int getMiningLevel() { return miningLevel; }
    @Override public float getFriction() { return friction; }
    @Override public String getMapColor() { return mapColor; }
    @Override public float getJumpFactor() { return jumpFactor; }
    @Override public float getSpeedFactor() { return speedFactor; }
    @Override public boolean isNoCollision() { return noCollision; }
    @Override public boolean isNoOcclusion() { return noOcclusion; }
    @Override public String getPushReaction() { return pushReaction; }
    @Override public boolean isReplaceable() { return replaceable; }
    @Override public boolean isIgnitedByLava() { return ignitedByLava; }
    @Override public boolean isLiquid() { return liquid; }
    @Override public String getOffsetType() { return offsetType; }
    @Override public Boolean getRedstoneConductor() { return redstoneConductor; }
    @Override public String getModel() { return model; }
    @Override public String getTexture() { return texture; }
    @Override public java.util.List<String> getTags() { return tags; }
    @Override public String getCreativeTab() { return creativeTab; }
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
    @Override public double getContainerUseDistance() { return containerUseDistance; }
    @Override public java.util.function.BiFunction<Object, Object, Boolean> getItemFilter() { return itemFilter; }
    @Override public String getContainerTexture() { return containerTexture; }
    @Override public String getContainerTitle() { return containerTitle; }
    @Override public java.util.Map<Integer, int[]> getSlotPositions() { return java.util.Map.copyOf(slotPositions); }
    @Override public java.util.Set<Integer> getLockedSlots() { return java.util.Set.copyOf(lockedSlots); }
    @Override public String getSlotTexture() { return slotTexture; }
    @Override public int getEnergyCapacity() { return energyCapacity; }
    @Override public int getEnergyMaxReceive() { return energyMaxReceive; }
    @Override public int getEnergyMaxExtract() { return energyMaxExtract; }
    @Override public int getFluidCapacity() { return fluidCapacity; }
    @Override public java.util.List<com.luatweaker.api.content.MachineBarSpec> getGuiBars() { return java.util.List.copyOf(guiBars); }
    @Override public com.luatweaker.api.content.BooleanStateSpec getBooleanState() { return booleanState; }
    @Override public boolean isConnectionState() { return connectionState; }
    @Override public java.util.function.BiConsumer<Object, Object> getTickHandler() { return tickHandler; }
    @Override public String getDisplayName() { return displayName; }
}



