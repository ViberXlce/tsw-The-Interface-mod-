package com.tsw.mod.client.skin;

import com.tsw.mod.TSWMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Downloads a skin PNG from a URL on a background thread, uploads it as a
 * GPU texture on the render thread, and caches the resulting Identifier so
 * repeated renders (or the same skin used by several players) do not
 * re-download every frame.
 *
 * Registered identifiers live under "tsw:skins/<hash>".
 */
public final class SkinCache {

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private static final long MAX_BYTES = 2L * 1024 * 1024; // 2 MB safety cap

    /** url -> resolved texture identifier, once downloaded & uploaded. */
    private static final Map<String, Identifier> READY = new ConcurrentHashMap<>();
    /** url currently being downloaded, to avoid duplicate requests. */
    private static final Map<String, Boolean> IN_FLIGHT = new ConcurrentHashMap<>();

    /** last skin URL we know about per player, set from the S2C broadcast packet. */
    public static final Map<UUID, String> PLAYER_SKIN_URLS = new ConcurrentHashMap<>();

    private SkinCache() {
    }

    /** Returns the texture id if already loaded, otherwise kicks off a download and returns null for now. */
    public static Identifier getOrRequest(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        Identifier cached = READY.get(url);
        if (cached != null) {
            return cached;
        }
        if (IN_FLIGHT.putIfAbsent(url, true) == null) {
            downloadAsync(url);
        }
        return null;
    }

    private static void downloadAsync(String url) {
        Thread thread = new Thread(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());

                if (response.statusCode() != 200) {
                    TSWMod.LOGGER.warn("TSW skin download failed ({}) for {}", response.statusCode(), url);
                    IN_FLIGHT.remove(url);
                    return;
                }

                byte[] bytes = response.body().readNBytes((int) MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES) {
                    TSWMod.LOGGER.warn("TSW skin at {} is larger than the 2MB safety cap, skipping", url);
                    IN_FLIGHT.remove(url);
                    return;
                }

                NativeImage image = NativeImage.read(bytes);

                MinecraftClient.getInstance().execute(() -> {
                    try {
                        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "tsw_skin", image);
                        String hash = Integer.toHexString(url.hashCode());
                        Identifier id = Identifier.of(TSWMod.MOD_ID, "skins/" + hash);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(id, texture);
                        READY.put(url, id);
                    } finally {
                        IN_FLIGHT.remove(url);
                    }
                });
            } catch (IOException | InterruptedException e) {
                TSWMod.LOGGER.warn("TSW could not download skin from {}: {}", url, e.toString());
                IN_FLIGHT.remove(url);
            } catch (Exception e) {
                TSWMod.LOGGER.warn("TSW skin at {} could not be decoded as an image", url);
                IN_FLIGHT.remove(url);
            }
        }, "TSW-Skin-Download");
        thread.setDaemon(true);
        thread.start();
    }
}
