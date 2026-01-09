package com.hitpo.doommc3d.client;

import com.hitpo.doommc3d.client.hud.DoomHudRenderer;
import com.hitpo.doommc3d.client.audio.DoomSfxPlayer;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientAnim;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientInput;
import com.hitpo.doommc3d.entity.ModEntities;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.render.entity.FlyingItemEntityRenderer;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

public class DoomMC3DClient implements ClientModInitializer {
    @Override
    @SuppressWarnings({"unchecked","rawtypes"})
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new DoomHudRenderer());
        DoomWeaponClientAnim.register();
        DoomWeaponClientInput.register();
        DoomSfxPlayer.register();
        DoomClientNetworking.init();

        // Prevent crashes when custom projectile entities spawn (must have renderers on client).
        EntityRendererRegistry.register(ModEntities.DOOM_ROCKET, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOOM_PLASMA, FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(ModEntities.DOOM_BFG_BALL, FlyingItemEntityRenderer::new);
        // Renderer for lift platform entity (must exist even if invisible).
        EntityRendererRegistry.register((net.minecraft.entity.EntityType)ModEntities.LIFT_PLATFORM, (EntityRendererFactory) InvisibleEntityRenderer::new);

        // Kill vanilla music when entering a world so Doom music can own the soundtrack.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (client.getMusicTracker() != null) {
                client.getMusicTracker().stop();
            }

            var musicOption = client.options.getSoundVolumeOption(SoundCategory.MUSIC);
            if (musicOption != null && musicOption.getValue() > 0.0) {
                musicOption.setValue(0.0);
                client.options.write();
            }
        });
        // end onInitializeClient
    }

    private static final class InvisibleEntityRenderer<T extends net.minecraft.entity.Entity> extends net.minecraft.client.render.entity.EntityRenderer<T, net.minecraft.client.render.entity.state.EntityRenderState> {
        protected InvisibleEntityRenderer(EntityRendererFactory.Context ctx) {
            super(ctx);
        }

        public void render(
            net.minecraft.entity.Entity entity,
            float yaw,
            float tickDelta,
            MatrixStack matrices,
            VertexConsumerProvider consumers,
            int light
        ) {
            // intentionally invisible / no-op
        }
        public Identifier getTexture(net.minecraft.entity.Entity entity) {
            return null;
        }

        @Override
        public net.minecraft.client.render.entity.state.EntityRenderState createRenderState() {
            // Provide a non-null render state to satisfy renderer pipeline.
            return new net.minecraft.client.render.entity.state.EntityRenderState();
        }

        @Override
        public void updateRenderState(T entity, net.minecraft.client.render.entity.state.EntityRenderState state, float tickDelta) {
            super.updateRenderState(entity, state, tickDelta);
            // no-op; state must be non-null to avoid client NPEs
        }
    }

}
