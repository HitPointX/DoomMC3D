package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.worldgen.DoomMobDrops;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityDeathMixin {
    @Inject(method = "die(Lnet/minecraft/entity/damage/DamageSource;)V", at = @At("HEAD"))
    private void doommc3d_spawnRemains(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof MobEntity mob)) {
            return;
        }
        if (!(mob.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        if (!DoomMobDrops.isDoomMob(mob)) {
            return;
        }

        // Decide whether to spawn a full corpse entity or lightweight remains.
        // Larger monsters get a full corpse entity; smaller ones spawn skull remains.
        if (isLargeDoomMob(mob)) {
            // Spawn a non-interactive ItemDisplayEntity to represent a static corpse.
            net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity display = new net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity(net.minecraft.entity.EntityType.ITEM_DISPLAY, world);
            display.setPosition(mob.getX(), mob.getY(), mob.getZ());
            // Choose an item visual based on mob type
            display.setItemStack(pickCorpseStack(mob));
            display.setItemDisplayContext(net.minecraft.item.ItemDisplayContext.GROUND);
            display.setBillboardMode(net.minecraft.entity.decoration.DisplayEntity.BillboardMode.CENTER);
            display.setDisplayWidth(0.9f);
            display.setDisplayHeight(0.9f);
            display.setViewRange(28.0f);
            display.setShadowRadius(0.0f);
            display.addCommandTag("doommc3d_corpse");
            world.spawnEntity(display);
            // Start sliding with per-mob tuned parameters for ticks and drag
            SlideParams params = getSlideParams(mob);
            com.hitpo.doommc3d.interact.DoomCorpsePhysics.startSliding(world, display, mob.getVelocity().multiply(0.6), params.ticks, params.drag);
            // Also spawn regular Doom drops (ammo/weapon) rather than skull remains
            com.hitpo.doommc3d.worldgen.DoomMobDrops.spawnDrops(world, mob);
            // Remove the original living mob so the static corpse display is the only visible
            // representation. This prevents the living entity from continuing to animate
            // or be subject to server-client position corrections which cause jitter.
            mob.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
        } else {
            // Small mobs: spawn original Doom drops (ammo/weapon) instead of skull remains
            DoomMobDrops.spawnDrops(world, mob);
            // Small mobs also should not remain as a living model after die() - remove them
            mob.remove(net.minecraft.entity.Entity.RemovalReason.KILLED);
        }
    }

    private static class SlideParams {
        final int ticks;
        final double drag;
        SlideParams(int ticks, double drag) { this.ticks = ticks; this.drag = drag; }
    }

    private SlideParams getSlideParams(MobEntity mob) {
        // Determine DoomMobType for this mob (default to ZOMBIEMAN)
        com.hitpo.doommc3d.doomai.DoomMobType found = com.hitpo.doommc3d.doomai.DoomMobType.ZOMBIEMAN;
        for (com.hitpo.doommc3d.doomai.DoomMobType t : com.hitpo.doommc3d.doomai.DoomMobType.values()) {
            if (mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(t))) { found = t; break; }
        }

        int ticks = com.hitpo.doommc3d.interact.DoomCorpseConfig.getTicksFor(found, 50);
        double drag = com.hitpo.doommc3d.interact.DoomCorpseConfig.getDragFor(found, 0.90);
        return new SlideParams(ticks, drag);
    }

    private int getTypeVariant(MobEntity mob) {
        for (com.hitpo.doommc3d.doomai.DoomMobType t : com.hitpo.doommc3d.doomai.DoomMobType.values()) {
            if (mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(t))) return t.ordinal();
        }
        return 0;
    }

    private boolean isLargeDoomMob(MobEntity mob) {
        return mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(com.hitpo.doommc3d.doomai.DoomMobType.DEMON))
            || mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(com.hitpo.doommc3d.doomai.DoomMobType.CACODEMON))
            || mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(com.hitpo.doommc3d.doomai.DoomMobType.BARON))
            || mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(com.hitpo.doommc3d.doomai.DoomMobType.SPECTRE));
    }

    private net.minecraft.item.ItemStack pickCorpseStack(MobEntity mob) {
        for (com.hitpo.doommc3d.doomai.DoomMobType t : com.hitpo.doommc3d.doomai.DoomMobType.values()) {
            if (mob.getCommandTags().contains(com.hitpo.doommc3d.doomai.DoomMobTags.tagForType(t))) {
                switch (t) {
                    case DEMON, SPECTRE -> { return new net.minecraft.item.ItemStack(net.minecraft.item.Items.CREEPER_HEAD); }
                    case CACODEMON, BARON -> { return new net.minecraft.item.ItemStack(net.minecraft.item.Items.WITHER_SKELETON_SKULL); }
                    default -> { return new net.minecraft.item.ItemStack(net.minecraft.item.Items.SKELETON_SKULL); }
                }
            }
        }
        return new net.minecraft.item.ItemStack(net.minecraft.item.Items.SKELETON_SKULL);
    }
}
