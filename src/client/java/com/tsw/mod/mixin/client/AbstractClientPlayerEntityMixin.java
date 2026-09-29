package com.tsw.mod.mixin.client;

import com.tsw.mod.client.skin.SkinCache;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Overrides the skin texture used for rendering when TSW has a custom
 * skin URL registered for this player's UUID.
 *
 * MAPPING NOTE: the target method here is written as "getSkinTextures"
 * matching current 1.21.x Yarn mappings for AbstractClientPlayerEntity.
 * If Gradle reports this method/signature does not exist on your exact
 * 1.21.11 mappings, open AbstractClientPlayerEntity in your IDE and look
 * for the method returning `SkinTextures` (it may be named `getSkin()`
 * on some versions) - update the `method` value below to match; the
 * body does not need to change.
 */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class AbstractClientPlayerEntityMixin {

    @Inject(method = "getSkinTextures", at = @At("RETURN"), cancellable = true)
    private void tsw$overrideSkin(CallbackInfoReturnable<SkinTextures> cir) {
        AbstractClientPlayerEntity self = (AbstractClientPlayerEntity) (Object) this;
        java.util.UUID uuid = self.getUuid();

        String url = SkinCache.PLAYER_SKIN_URLS.get(uuid);
        if (url == null || url.isBlank()) {
            return;
        }

        Identifier customTexture = SkinCache.getOrRequest(url);
        if (customTexture == null) {
            // Still downloading - fall back to whatever vanilla returned this call.
            return;
        }

        SkinTextures original = cir.getReturnValue();
        SkinTextures replaced = new SkinTextures(
                customTexture,
                null,
                original.capeTexture(),
                original.elytraTexture(),
                original.model(),
                original.secure()
        );
        cir.setReturnValue(replaced);
    }
}
