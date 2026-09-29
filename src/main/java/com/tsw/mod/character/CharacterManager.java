package com.tsw.mod.character;

import com.tsw.mod.TSWMod;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldSavePath;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Owns the on-disk storage of the 3 character slots per player, and the
 * logic to switch a live ServerPlayerEntity from whatever it currently has
 * loaded into a different slot's saved state.
 *
 * Every piece of state is captured/applied through ordinary, long-stable
 * public getters/setters (health, hunger, xp fields, inventory slots)
 * rather than the entity's internal NBT read/write methods, which have
 * changed shape across Minecraft versions.
 *
 * Storage layout (inside the world save folder):
 *   <world>/tsw/playerdata/<uuid>/slot0.dat
 *   <world>/tsw/playerdata/<uuid>/slot1.dat
 *   <world>/tsw/playerdata/<uuid>/slot2.dat
 */
public class CharacterManager {

    /** Which slot each currently-connected player is using, keyed by UUID. -1 = none chosen yet. */
    private final Map<UUID, Integer> activeSlot = new HashMap<>();

    private final MinecraftServer server;

    public CharacterManager(MinecraftServer server) {
        this.server = server;
    }

    private Path playerFolder(UUID uuid) {
        Path base = server.getSavePath(WorldSavePath.ROOT).resolve("tsw").resolve("playerdata").resolve(uuid.toString());
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            TSWMod.LOGGER.error("Could not create TSW playerdata folder for {}", uuid, e);
        }
        return base;
    }

    private Path slotFile(UUID uuid, int slot) {
        return playerFolder(uuid).resolve("slot" + slot + ".dat");
    }

    public int getActiveSlot(UUID uuid) {
        return activeSlot.getOrDefault(uuid, -1);
    }

    public void clearActiveSlot(UUID uuid) {
        activeSlot.remove(uuid);
    }

    /** Reads the 3 slots for this player from disk (does not touch the live entity). */
    public CharacterProfile[] loadAllSlots(UUID uuid) {
        CharacterProfile[] profiles = new CharacterProfile[CharacterProfile.SLOT_COUNT];
        for (int i = 0; i < CharacterProfile.SLOT_COUNT; i++) {
            profiles[i] = loadSlot(uuid, i);
        }
        return profiles;
    }

    public CharacterProfile loadSlot(UUID uuid, int slot) {
        File file = slotFile(uuid, slot).toFile();
        if (!file.exists()) {
            return new CharacterProfile();
        }
        try {
            NbtCompound tag = NbtIo.readCompressed(file.toPath(), net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
            return CharacterProfile.fromNbt(tag);
        } catch (IOException e) {
            TSWMod.LOGGER.error("Failed reading TSW slot {} for {}", slot, uuid, e);
            return new CharacterProfile();
        }
    }

    public void saveSlot(UUID uuid, int slot, CharacterProfile profile) {
        File file = slotFile(uuid, slot).toFile();
        try {
            NbtIo.writeCompressed(profile.toNbt(), file.toPath());
        } catch (IOException e) {
            TSWMod.LOGGER.error("Failed saving TSW slot {} for {}", slot, uuid, e);
        }
    }

    /**
     * Captures the live player entity's current state (inventory, position,
     * dimension, health, hunger, xp...) into the given slot on disk, keeping
     * whatever skin URL that slot already had.
     */
    public void captureLiveStateIntoSlot(ServerPlayerEntity player, int slot) {
        CharacterProfile profile = loadSlot(player.getUuid(), slot);

        profile.setHealth(player.getHealth());
        profile.setFoodLevel(player.getHungerManager().getFoodLevel());
        profile.setSaturation(player.getHungerManager().getSaturationLevel());
        profile.setXpLevel(player.experienceLevel);
        profile.setXpProgress(player.experienceProgress);
        profile.setTotalXp(player.totalExperience);

        profile.setPos(player.getX(), player.getY(), player.getZ());
        profile.setRotation(player.getYaw(), player.getPitch());

        World world = player.getWorld();
        String dimensionId = world.getRegistryKey().getValue().toString();
        profile.setDimension(dimensionId);

        profile.setInventory(encodeInventory(player));
        profile.setOccupied(true);

        saveSlot(player.getUuid(), slot, profile);
    }

    /**
     * Applies a saved slot's state onto the live player entity, moving them
     * to the correct dimension/position if needed.
     */
    public void applySlotToLivePlayer(ServerPlayerEntity player, CharacterProfile profile) {
        if (!profile.isOccupied()) {
            // Brand new character: leave inventory/stats at whatever the
            // freshly-joined player already has, just make sure they are
            // standing somewhere sane in the main world.
            ServerWorld overworld = server.getOverworld();
            player.teleport(overworld, player.getX(), 70.0, player.getZ(), Set.of(), player.getYaw(), player.getPitch(), false);
            return;
        }

        player.setHealth(profile.getHealth());
        player.getHungerManager().setFoodLevel(profile.getFoodLevel());
        player.getHungerManager().setSaturationLevel(profile.getSaturation());
        player.experienceLevel = profile.getXpLevel();
        player.experienceProgress = profile.getXpProgress();
        player.totalExperience = profile.getTotalXp();

        applyInventory(player, profile.getInventory());

        ServerWorld targetWorld = server.getOverworld();
        String dimension = profile.getDimension();
        if (dimension != null && !dimension.isBlank()) {
            try {
                RegistryKey<World> dimKey = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(dimension));
                ServerWorld resolved = server.getWorld(dimKey);
                if (resolved != null) {
                    targetWorld = resolved;
                }
            } catch (Exception e) {
                TSWMod.LOGGER.warn("Could not resolve saved dimension '{}' for {}, defaulting to overworld", dimension, player.getUuid());
            }
        }

        player.teleport(targetWorld, profile.getPosX(), profile.getPosY(), profile.getPosZ(),
                Set.of(), profile.getYaw(), profile.getPitch(), false);
    }

    /**
     * Full switch flow: save whatever the player currently has loaded into
     * their previous slot (if any), then load and apply the target slot.
     */
    public void switchToSlot(ServerPlayerEntity player, int newSlot) {
        UUID uuid = player.getUuid();
        int previous = getActiveSlot(uuid);
        if (previous != -1 && previous != newSlot) {
            captureLiveStateIntoSlot(player, previous);
        }

        CharacterProfile target = loadSlot(uuid, newSlot);
        applySlotToLivePlayer(player, target);
        target.setOccupied(true);
        saveSlot(uuid, newSlot, target);
        activeSlot.put(uuid, newSlot);
    }

    /** Called on disconnect: persist whatever is currently loaded back to its slot file. */
    public void persistOnDisconnect(ServerPlayerEntity player) {
        int slot = getActiveSlot(player.getUuid());
        if (slot != -1) {
            captureLiveStateIntoSlot(player, slot);
        }
        clearActiveSlot(player.getUuid());
    }

    /** Resets a slot back to a brand new, empty character. */
    public void deleteSlot(UUID uuid, int slot) {
        saveSlot(uuid, slot, new CharacterProfile());
    }

    public void setSkinUrl(UUID uuid, int slot, String skinUrl) {
        CharacterProfile profile = loadSlot(uuid, slot);
        profile.setSkinUrl(skinUrl);
        saveSlot(uuid, slot, profile);
    }

    // ------------------------------------------------------------------
    // Inventory encode/decode via ItemStack's own stable public Codec,
    // rather than any Inventory/PlayerInventory-specific NBT helper.
    // ------------------------------------------------------------------

    private NbtList encodeInventory(ServerPlayerEntity player) {
        NbtList list = new NbtList();
        var ops = server.getRegistryManager().getOps(NbtOps.INSTANCE);
        var inventory = player.getInventory();

        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            NbtElement encoded = ItemStack.CODEC.encodeStart(ops, stack).result().orElse(null);
            if (encoded == null) {
                continue;
            }
            NbtCompound entry = new NbtCompound();
            entry.putInt("Slot", slot);
            entry.put("Item", encoded);
            list.add(entry);
        }
        return list;
    }

    private void applyInventory(ServerPlayerEntity player, NbtList list) {
        var ops = server.getRegistryManager().getOps(NbtOps.INSTANCE);
        var inventory = player.getInventory();
        inventory.clear();

        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompoundOrEmpty(i);
            int slot = entry.getInt("Slot", -1);
            NbtElement itemTag = entry.get("Item");
            if (slot < 0 || itemTag == null) {
                continue;
            }
            ItemStack stack = ItemStack.CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);
            if (slot < inventory.size()) {
                inventory.setStack(slot, stack);
            }
        }
    }
}
