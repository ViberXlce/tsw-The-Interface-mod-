package com.tsw.mod.network;

import com.tsw.mod.TSWMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import net.minecraft.util.Uuids;

import java.util.List;
import java.util.UUID;

/**
 * All custom network payloads used by TSW.
 *
 * NOTE ON MAPPINGS: this file targets the Fabric networking API (payload
 * based, introduced around 1.20.5+ and stable through 1.21.x). Class names
 * such as RegistryByteBuf / PacketCodec / CustomPayload are Yarn names for
 * 1.21.x. If your exact 1.21.11 mappings differ slightly, your IDE's
 * "quick fix" / auto-import will resolve the correct equivalent - the
 * logic below does not need to change.
 */
public final class TSWNetworking {

    private TSWNetworking() {
    }

    // ------------------------------------------------------------------
    // S2C: tell the client to open the character selection screen
    // ------------------------------------------------------------------
    public record OpenCharacterSelectS2C(List<SlotInfo> slots) implements CustomPayload {
        public static final CustomPayload.Id<OpenCharacterSelectS2C> ID =
                new CustomPayload.Id<>(Identifier.of(TSWMod.MOD_ID, "open_character_select"));

        public static final PacketCodec<RegistryByteBuf, OpenCharacterSelectS2C> CODEC =
                PacketCodec.tuple(
                        SlotInfo.CODEC.collect(PacketCodecs.toList()), OpenCharacterSelectS2C::slots,
                        OpenCharacterSelectS2C::new
                );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Small serializable summary of one of the 3 slots, sent so the GUI can
     * show whether a slot is empty and what skin URL it currently has.
     */
    public record SlotInfo(int slot, boolean occupied, String skinUrl) {
        public static final PacketCodec<RegistryByteBuf, SlotInfo> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, SlotInfo::slot,
                PacketCodecs.BOOL, SlotInfo::occupied,
                PacketCodecs.STRING, SlotInfo::skinUrl,
                SlotInfo::new
        );
    }

    // ------------------------------------------------------------------
    // C2S: the player picked / created a character in slot X
    // ------------------------------------------------------------------
    public record SelectCharacterC2S(int slot) implements CustomPayload {
        public static final CustomPayload.Id<SelectCharacterC2S> ID =
                new CustomPayload.Id<>(Identifier.of(TSWMod.MOD_ID, "select_character"));

        public static final PacketCodec<RegistryByteBuf, SelectCharacterC2S> CODEC =
                PacketCodec.tuple(PacketCodecs.VAR_INT, SelectCharacterC2S::slot, SelectCharacterC2S::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ------------------------------------------------------------------
    // C2S: update / set the skin URL for one of the player's slots
    // ------------------------------------------------------------------
    public record UpdateSkinUrlC2S(int slot, String skinUrl) implements CustomPayload {
        public static final CustomPayload.Id<UpdateSkinUrlC2S> ID =
                new CustomPayload.Id<>(Identifier.of(TSWMod.MOD_ID, "update_skin_url"));

        public static final PacketCodec<RegistryByteBuf, UpdateSkinUrlC2S> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, UpdateSkinUrlC2S::slot,
                PacketCodecs.STRING, UpdateSkinUrlC2S::skinUrl,
                UpdateSkinUrlC2S::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ------------------------------------------------------------------
    // S2C: broadcast to everyone tracking this player that their skin
    // (for the currently active character) changed, so it renders for
    // other players too, not just the owner.
    // ------------------------------------------------------------------
    public record SkinBroadcastS2C(UUID playerId, String skinUrl) implements CustomPayload {
        public static final CustomPayload.Id<SkinBroadcastS2C> ID =
                new CustomPayload.Id<>(Identifier.of(TSWMod.MOD_ID, "skin_broadcast"));

        public static final PacketCodec<RegistryByteBuf, SkinBroadcastS2C> CODEC = PacketCodec.tuple(
                Uuids.PACKET_CODEC, SkinBroadcastS2C::playerId,
                PacketCodecs.STRING, SkinBroadcastS2C::skinUrl,
                SkinBroadcastS2C::new
        );

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void registerPayloads() {
        PayloadTypeRegistry.playS2C().register(OpenCharacterSelectS2C.ID, OpenCharacterSelectS2C.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectCharacterC2S.ID, SelectCharacterC2S.CODEC);
        PayloadTypeRegistry.playC2S().register(UpdateSkinUrlC2S.ID, UpdateSkinUrlC2S.CODEC);
        PayloadTypeRegistry.playS2C().register(SkinBroadcastS2C.ID, SkinBroadcastS2C.CODEC);
    }
}
