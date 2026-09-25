package com.tsw.mod;

import com.tsw.mod.character.CharacterManager;
import com.tsw.mod.character.CharacterProfile;
import com.tsw.mod.network.TSWNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Common (server + client run this) entrypoint. Holds the server-side
 * CharacterManager and wires up the network packet handlers plus the
 * player join/disconnect hooks.
 */
public class TSWMod implements ModInitializer {

    public static final String MOD_ID = "tsw";
    public static final Logger LOGGER = LoggerFactory.getLogger("TSW");

    /** One CharacterManager per running server (works for both integrated & dedicated). */
    private static final Map<MinecraftServer, CharacterManager> MANAGERS = new HashMap<>();

    /** Last known custom skin URL per player UUID, kept so we can re-broadcast it to newly-tracking clients. */
    public static final Map<UUID, String> ACTIVE_SKIN_URLS = new HashMap<>();

    public static CharacterManager getManager(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, CharacterManager::new);
    }

    @Override
    public void onInitialize() {
        LOGGER.info("TSW initializing...");

        TSWNetworking.registerPayloads();

        // ------------------------------------------------------------
        // On join: always show the character selection screen. The
        // player is not "in the world" for TSW purposes until they pick
        // (or create) one of the 3 slots.
        // ------------------------------------------------------------
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            CharacterManager manager = getManager(server);
            CharacterProfile[] slots = manager.loadAllSlots(player.getUuid());

            List<TSWNetworking.SlotInfo> info = new ArrayList<>();
            for (int i = 0; i < slots.length; i++) {
                info.add(new TSWNetworking.SlotInfo(i, slots[i].isOccupied(), slots[i].getSkinUrl()));
            }

            ServerPlayNetworking.send(player, new TSWNetworking.OpenCharacterSelectS2C(info));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            getManager(server).persistOnDisconnect(player);
        });

        // ------------------------------------------------------------
        // C2S: player picked/created a character in a slot.
        // ------------------------------------------------------------
        ServerPlayNetworking.registerGlobalReceiver(TSWNetworking.SelectCharacterC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            int slot = clampSlot(payload.slot());
            context.server().execute(() -> {
                CharacterManager manager = getManager(context.server());
                manager.switchToSlot(player, slot);

                CharacterProfile profile = manager.loadSlot(player.getUuid(), slot);
                String skinUrl = profile.getSkinUrl();
                if (skinUrl != null && !skinUrl.isBlank()) {
                    broadcastSkin(player, skinUrl);
                }
            });
        });

        // ------------------------------------------------------------
        // C2S: player set/changed the skin URL for one of their slots.
        // ------------------------------------------------------------
        ServerPlayNetworking.registerGlobalReceiver(TSWNetworking.UpdateSkinUrlC2S.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            int slot = clampSlot(payload.slot());
            String url = sanitizeUrl(payload.skinUrl());
            context.server().execute(() -> {
                getManager(context.server()).setSkinUrl(player.getUuid(), slot, url);

                // If they're editing the slot they're currently in, apply immediately.
                CharacterManager manager = getManager(context.server());
                if (manager.getActiveSlot(player.getUuid()) == slot) {
                    broadcastSkin(player, url);
                }
            });
        });
    }

    private static int clampSlot(int slot) {
        return Math.max(0, Math.min(CharacterProfile.SLOT_COUNT - 1, slot));
    }

    /** Very light validation - real hardening (HTTPS only, size limits) happens client-side on download. */
    private static String sanitizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        if (trimmed.length() > 512) {
            trimmed = trimmed.substring(0, 512);
        }
        return trimmed;
    }

    private static void broadcastSkin(ServerPlayerEntity player, String skinUrl) {
        ACTIVE_SKIN_URLS.put(player.getUuid(), skinUrl);
        TSWNetworking.SkinBroadcastS2C packet = new TSWNetworking.SkinBroadcastS2C(player.getUuid(), skinUrl);
        // Send to everyone (including the owner) so the preview + world render both update.
        for (ServerPlayerEntity other : PlayerLookup.all(player.getServer())) {
            ServerPlayNetworking.send(other, packet);
        }
    }
}
