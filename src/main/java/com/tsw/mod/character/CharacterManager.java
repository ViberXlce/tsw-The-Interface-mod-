package com.tsw.mod.character;

import com.tsw.mod.TSWMod;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldSavePath;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the on-disk storage of the 3 character slots per player, and the
 * logic to switch a live ServerPlayerEntity from whatever it currently has
 * loaded into a different slot's saved state (inventory, position,
 * dimension, health, hunger, xp, ...).
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
        NbtCompound data = new NbtCompound();
        // PlayerEntity#writeNbt writes inventory, xp, score, food/health,
        // position, rotation, dimension and more - the same data vanilla
        // uses for player.dat.
        player.writeNbt(data);
        profile.setPlayerData(data);
        profile.setOccupied(true);
        saveSlot(player.getUuid(), slot, profile);
    }

    /**
     * Applies a saved slot's state onto the live player entity: restores
     * inventory/health/xp via vanilla's own readNbt, then makes sure the
     * player ends up in the correct dimension/position (readNbt alone does
     * not move an already-loaded entity across dimensions).
     */
    public void applySlotToLivePlayer(ServerPlayerEntity player, CharacterProfile profile) {
        NbtCompound data = profile.getPlayerData();

        if (data.isEmpty()) {
            // Brand new character: leave the player at the default spawn
            // point with default vanilla state (fresh inventory etc).
            ServerWorld defaultWorld = server.getOverworld();
            var spawnPos = defaultWorld.getSpawnPos();
            player.teleport(defaultWorld, spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    java.util.Set.of(), 0f, 0f, false);
            return;
        }

        // Work out which dimension this slot was saved in.
        ServerWorld targetWorld = server.getOverworld();
        if (data.contains("Dimension")) {
            try {
                RegistryKey<World> dimKey = RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD,
                        net.minecraft.util.Identifier.of(data.getString("Dimension", "minecraft:overworld")));
                ServerWorld resolved = server.getWorld(dimKey);
                if (resolved != null) {
                    targetWorld = resolved;
                }
            } catch (Exception e) {
                TSWMod.LOGGER.warn("Could not resolve saved dimension for {}, defaulting to overworld", player.getUuid());
            }
        }

        double x = data.getDouble("PosX", player.getX());
        double y = data.getDouble("PosY", player.getY());
        double z = data.getDouble("PosZ", player.getZ());

        // Restore inventory / xp / health / hunger / score via vanilla's own logic.
        player.readNbt(data);

        // readNbt does not reliably relocate an already-spawned entity
        // between dimensions, so finish the move explicitly.
        player.teleport(targetWorld, x, y, z, java.util.Set.of(), player.getYaw(), player.getPitch(), false);
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

    public void setSkinUrl(UUID uuid, int slot, String skinUrl) {
        CharacterProfile profile = loadSlot(uuid, slot);
        profile.setSkinUrl(skinUrl);
        saveSlot(uuid, slot, profile);
    }
}
