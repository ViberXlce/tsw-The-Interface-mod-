package com.tsw.mod.character;

import net.minecraft.nbt.NbtCompound;

/**
 * Everything TSW needs to remember about ONE character slot:
 * skin URL + the full saved player state (inventory, position,
 * health, hunger, xp, game mode...) stored as a raw NBT blob taken
 * straight from the player entity (same tags vanilla itself uses
 * to write player.dat), so nothing is lost between switches.
 */
public class CharacterProfile {

    public static final int SLOT_COUNT = 3;

    private String skinUrl = "";
    private boolean occupied = false;
    /** Raw vanilla player NBT (inventory, pos, health, xp, dimension, etc). */
    private NbtCompound playerData = new NbtCompound();

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

    public NbtCompound getPlayerData() {
        return playerData;
    }

    public void setPlayerData(NbtCompound playerData) {
        this.playerData = playerData;
    }

    public NbtCompound toNbt() {
        NbtCompound tag = new NbtCompound();
        tag.putString("SkinUrl", skinUrl);
        tag.putBoolean("Occupied", occupied);
        tag.put("PlayerData", playerData);
        return tag;
    }

    public static CharacterProfile fromNbt(NbtCompound tag) {
        CharacterProfile profile = new CharacterProfile();
        profile.skinUrl = tag.getString("SkinUrl", "");
        profile.occupied = tag.getBoolean("Occupied", false);
        profile.playerData = tag.getCompoundOrEmpty("PlayerData");
        return profile;
    }
}
