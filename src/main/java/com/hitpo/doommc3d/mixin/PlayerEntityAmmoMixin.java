package com.hitpo.doommc3d.mixin;

import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmoType;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityAmmoMixin extends LivingEntity implements DoomAmmoAccess {
    @Unique private static final TrackedData<Integer> DOOM_AMMO_BULLET = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> DOOM_AMMO_SHELL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> DOOM_AMMO_ROCKET = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Integer> DOOM_AMMO_CELL = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    @Unique private static final TrackedData<Boolean> DOOM_HAS_BACKPACK = DataTracker.registerData(PlayerEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    protected PlayerEntityAmmoMixin(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "initDataTracker", at = @At("TAIL"))
    private void doommc3d$initDataTracker(DataTracker.Builder builder, CallbackInfo ci) {
        builder.add(DOOM_AMMO_BULLET, 0);
        builder.add(DOOM_AMMO_SHELL, 0);
        builder.add(DOOM_AMMO_ROCKET, 0);
        builder.add(DOOM_AMMO_CELL, 0);
        builder.add(DOOM_HAS_BACKPACK, false);
    }

    @Override
    public int getDoomAmmo(DoomAmmoType type) {
        return this.dataTracker.get(tracked(type));
    }

    @Override
    public boolean hasDoomBackpack() {
        return this.dataTracker.get(DOOM_HAS_BACKPACK);
    }

    @Override
    public void setDoomBackpack(boolean hasBackpack) {
        this.dataTracker.set(DOOM_HAS_BACKPACK, hasBackpack);
    }

    @Override
    public void setDoomAmmo(DoomAmmoType type, int amount) {
        int max = DoomAmmo.getMax(type, hasDoomBackpack());
        int clamped = Math.max(0, Math.min(max, amount));
        this.dataTracker.set(tracked(type), clamped);
    }

    @Override
    public int addDoomAmmo(DoomAmmoType type, int delta) {
        int current = getDoomAmmo(type);
        int max = DoomAmmo.getMax(type, hasDoomBackpack());
        int target = Math.max(0, Math.min(max, current + delta));
        if (target != current) {
            this.dataTracker.set(tracked(type), target);
        }
        return target;
    }

    @Override
    public boolean consumeDoomAmmo(DoomAmmoType type, int amount) {
        if (amount <= 0) {
            return true;
        }
        int current = getDoomAmmo(type);
        if (current < amount) {
            return false;
        }
        this.dataTracker.set(tracked(type), current - amount);
        return true;
    }

    @Unique
    private static TrackedData<Integer> tracked(DoomAmmoType type) {
        return switch (type) {
            case BULLET -> DOOM_AMMO_BULLET;
            case SHELL -> DOOM_AMMO_SHELL;
            case ROCKET -> DOOM_AMMO_ROCKET;
            case CELL -> DOOM_AMMO_CELL;
        };
    }
}
