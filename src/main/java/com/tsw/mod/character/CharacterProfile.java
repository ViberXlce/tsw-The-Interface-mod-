package com.tsw.mod.character;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;

/**
 * Everything TSW needs to remember about ONE character slot.
 *
 * Rather than dumping the entity's raw NBT (Minecraft's internal
 * read/write methods for that have changed shape across versions and
 * are not a stable target to compile against), TSW stores each piece
 * of state explicitly through ordinary public getters/setters, which
 * are far less likely to be renamed between Minecraft versions.
 */
public class CharacterProfile {

    public static final int SLOT_COUNT = 3;

    private String skinUrl = "";
    private boolean occupied = false;

    private float health = 20.0f;
    private int foodLevel = 20;
    private float saturation = 5.0f;
    private int xpLevel = 0;
    private float xpProgress = 0.0f;
    private int totalXp = 0;

    private double posX = 0.0;
    private double posY = 0.0;
    private double posZ = 0.0;
    private float yaw = 0.0f;
    private float pitch = 0.0f;
    /** Dimension identifier, e.g. "minecraft:overworld". Empty = use server default. */
    private String dimension = "";

    /** One entry per non-empty inventory slot: {"Slot": int, "Item": <encoded ItemStack>}. */
    private NbtList inventory = new NbtList();

    public CharacterProfile() {
    }

    public String getSkinUrl() {
        return skinUrl;
    }

    public void setSkinUrl(String skinUrl) {
        this.skinUrl = skinUrl == null ? "" : skinUrl;
    }

    public boolean isOccupied() {
        return occupied;
    }

    public void setOccupied(boolean occupied) {
        this.occupied = occupied;
    }

    public float getHealth() {
        return health;
    }

    public void setHealth(float health) {
        this.health = health;
    }

    public int getFoodLevel() {
        return foodLevel;
    }

    public void setFoodLevel(int foodLevel) {
        this.foodLevel = foodLevel;
    }

    public float getSaturation() {
        return saturation;
    }

    public void setSaturation(float saturation) {
        this.saturation = saturation;
    }

    public int getXpLevel() {
        return xpLevel;
    }

    public void setXpLevel(int xpLevel) {
        this.xpLevel = xpLevel;
    }

    public float getXpProgress() {
        return xpProgress;
    }

    public void setXpProgress(float xpProgress) {
        this.xpProgress = xpProgress;
    }

    public int getTotalXp() {
        return totalXp;
    }

    public void setTotalXp(int totalXp) {
        this.totalXp = totalXp;
    }

    public double getPosX() {
        return posX;
    }

    public double getPosY() {
        return posY;
    }

    public double getPosZ() {
        return posZ;
    }

    public void setPos(double x, double y, double z) {
        this.posX = x;
        this.posY = y;
        this.posZ = z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setRotation(float yaw, float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getDimension() {
        return dimension;
    }

    public void setDimension(String dimension) {
        this.dimension = dimension == null ? "" : dimension;
    }

    public NbtList getInventory() {
        return inventory;
    }

    public void setInventory(NbtList inventory) {
        this.inventory = inventory;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("SkinUrl", skinUrl);
        tag.putBoolean("Occupied", occupied);
        tag.putFloat("Health", health);
        tag.putInt("FoodLevel", foodLevel);
        tag.putFloat("Saturation", saturation);
        tag.putInt("XpLevel", xpLevel);
        tag.putFloat("XpProgress", xpProgress);
        tag.putInt("TotalXp", totalXp);
        tag.putDouble("PosX", posX);
        tag.putDouble("PosY", posY);
        tag.putDouble("PosZ", posZ);
        tag.putFloat("Yaw", yaw);
        tag.putFloat("Pitch", pitch);
        tag.putString("Dimension", dimension);
        tag.put("Inventory", inventory);
        return tag;
    }

    public static CharacterProfile fromNbt(NbtCompound tag) {
        CharacterProfile profile = new CharacterProfile();
        profile.skinUrl = tag.getString("SkinUrl", "");
        profile.occupied = tag.getBoolean("Occupied", false);
        profile.health = tag.getFloat("Health", 20.0f);
        profile.foodLevel = tag.getInt("FoodLevel", 20);
        profile.saturation = tag.getFloat("Saturation", 5.0f);
        profile.xpLevel = tag.getInt("XpLevel", 0);
        profile.xpProgress = tag.getFloat("XpProgress", 0.0f);
        profile.totalXp = tag.getInt("TotalXp", 0);
        profile.posX = tag.getDouble("PosX", 0.0);
        profile.posY = tag.getDouble("PosY", 0.0);
        profile.posZ = tag.getDouble("PosZ", 0.0);
        profile.yaw = tag.getFloat("Yaw", 0.0f);
        profile.pitch = tag.getFloat("Pitch", 0.0f);
        profile.dimension = tag.getString("Dimension", "");
        profile.inventory = tag.getListOrEmpty("Inventory");
        return profile;
    }
}
