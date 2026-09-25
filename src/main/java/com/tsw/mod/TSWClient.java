package com.tsw.mod;

import com.tsw.mod.client.gui.CharacterSelectScreen;
import com.tsw.mod.client.skin.SkinCache;
import com.tsw.mod.network.TSWNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Client-only entrypoint. Listens for the server telling us to open the
 * character select screen, and for skin-url broadcasts for any player.
 */
public class TSWClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(TSWNetworking.OpenCharacterSelectS2C.ID, (payload, context) -> {
            context.client().execute(() -> {
                MinecraftClient client = context.client();
                client.setScreen(new CharacterSelectScreen(payload.slots()));
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(TSWNetworking.SkinBroadcastS2C.ID, (payload, context) ->
                context.client().execute(() -> {
                    SkinCache.PLAYER_SKIN_URLS.put(payload.playerId(), payload.skinUrl());
                    // Pre-warm the download so the texture is ready by the time
                    // the mixin asks for it during rendering.
                    SkinCache.getOrRequest(payload.skinUrl());
                })
        );
    }
}
