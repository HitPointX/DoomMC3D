package com.hitpo.doommc3d.worldgen;

import com.hitpo.doommc3d.doomai.DoomMobTags;
import com.hitpo.doommc3d.doomai.DoomMobType;
import com.hitpo.doommc3d.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.item.Item;

public final class DoomMobDrops {
    private DoomMobDrops() {
    }

    public static boolean isDoomMob(MobEntity mob) {
        return mob.getCommandTags().contains(DoomMobTags.MOB);
    }

    public static void spawnDrops(ServerWorld world, MobEntity mob) {
        // Minimal Doom-like drops:
        // - Shotgun guys drop a shotgun pickup.
        // - Zombiemen/Chaingunners drop bullet ammo (placeholder: iron nuggets).
        // - Others: nothing for now.
        if (hasType(mob, DoomMobType.SHOTGUN_GUY)) {
            spawnWeaponPickup(world, mob.getX(), mob.getY(), mob.getZ(), new ItemStack(ModItems.DOOM_SHOTGUN), "doommc3d_weapon_shotgun");
            return;
        }

        if (hasType(mob, DoomMobType.ZOMBIEMAN) || hasType(mob, DoomMobType.CHAINGUNNER)) {
            spawnAmmo(world, mob.getX(), mob.getY(), mob.getZ(), new ItemStack(Items.IRON_NUGGET, 6), "doommc3d_ammo_clip");
        }
    }

    
    private static class SlideParams {
        final int ticks;
        final double drag;
        SlideParams(int ticks, double drag) { this.ticks = ticks; this.drag = drag; }
    }
    private static boolean hasType(MobEntity mob, DoomMobType type) {
        return mob.getCommandTags().contains(DoomMobTags.tagForType(type));
    }

    private static void spawnAmmo(ServerWorld world, double x, double y, double z, ItemStack stack, String ammoTag) {
        if (stack.isEmpty()) {
            return;
        }
        // Spawn an ItemDisplay so our custom pickup system can detect and consume it.
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(x, y + 0.35, z);
        display.setItemStack(stack);
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.45f);
        display.setDisplayHeight(0.45f);
        display.setViewRange(28.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.addCommandTag(DoomThingSpawner.TAG_SPAWNED);
        display.addCommandTag("doommc3d_dropped");
        if (ammoTag != null && !ammoTag.isEmpty()) display.addCommandTag(ammoTag);
        com.hitpo.doommc3d.util.DebugLogger.debugThrottled("DoomMobDrops.spawnAmmo", 500, () -> "[DoomMobDrops] spawnAmmo: pos=(" + x + "," + y + "," + z + ") tags=" + display.getCommandTags());
        world.spawnEntity(display);
    }

    private static void spawnWeaponPickup(ServerWorld world, double x, double y, double z, ItemStack stack, String weaponTag) {
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(x, y + 0.35, z);
        display.setItemStack(stack);
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.75f);
        display.setDisplayHeight(0.75f);
        display.setViewRange(28.0f);
        display.setShadowRadius(0.0f);
        display.setShadowStrength(0.0f);
        display.addCommandTag(DoomThingSpawner.TAG_SPAWNED);
        display.addCommandTag("doommc3d_weapon_pickup");
        display.addCommandTag("doommc3d_dropped");
        display.addCommandTag(weaponTag);
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomMobDrops.spawnWeapon", () -> "[DoomMobDrops] spawnWeaponPickup: pos=(" + x + "," + y + "," + z + ") tags=" + display.getCommandTags());
        world.spawnEntity(display);
    }

    public static void spawnRemains(ServerWorld world, MobEntity mob, DamageSource cause) {
        // Choose remains variant based on DoomMobType tags
        DoomMobType[] types = DoomMobType.values();
        ItemStack stack = new ItemStack(Items.SKELETON_SKULL);
        for (DoomMobType t : types) {
            if (hasType(mob, t)) {
                switch (t) {
                    case ZOMBIEMAN, SHOTGUN_GUY, CHAINGUNNER -> stack = new ItemStack(Items.SKELETON_SKULL);
                    case IMP, LOST_SOUL -> stack = new ItemStack(Items.ZOMBIE_HEAD);
                    case DEMON, SPECTRE -> stack = new ItemStack(Items.CREEPER_HEAD);
                    case CACODEMON, BARON -> stack = new ItemStack(Items.WITHER_SKELETON_SKULL);
                }
                break;
            }
        }

        int count = 1;
        try {
            if (cause != null && cause.getName() != null && cause.getName().toLowerCase().contains("explosion")) count = 3;
        } catch (Exception ignored) {
        }
        for (int i = 0; i < count; i++) {
            ItemEntity item = new ItemEntity(world, mob.getX(), mob.getY(), mob.getZ(), stack.copy());
            item.setToDefaultPickupDelay();
            double vx = (mob.getRandom().nextDouble() - 0.5) * 0.25;
            double vz = (mob.getRandom().nextDouble() - 0.5) * 0.25;
            item.setVelocity(vx, 0.12, vz);
            world.spawnEntity(item);
        }
    }

    public static void spawnRemainsForType(ServerWorld world, com.hitpo.doommc3d.doomai.DoomMobType t, double x, double y, double z, DamageSource cause) {
        ItemStack stack = new ItemStack(Items.SKELETON_SKULL);
        switch (t) {
            case ZOMBIEMAN, SHOTGUN_GUY, CHAINGUNNER -> stack = new ItemStack(Items.SKELETON_SKULL);
            case IMP, LOST_SOUL -> stack = new ItemStack(Items.ZOMBIE_HEAD);
            case DEMON, SPECTRE -> stack = new ItemStack(Items.CREEPER_HEAD);
            case CACODEMON, BARON -> stack = new ItemStack(Items.WITHER_SKELETON_SKULL);
        }

        int count = 1;
        try {
            if (cause != null && cause.getName() != null && cause.getName().toLowerCase().contains("explosion")) count = 3;
        } catch (Exception ignored) {}

        for (int i = 0; i < count; i++) {
            ItemEntity item = new ItemEntity(world, x, y, z, stack.copy());
            item.setToDefaultPickupDelay();
            double vx = (world.getRandom().nextDouble() - 0.5) * 0.25;
            double vz = (world.getRandom().nextDouble() - 0.5) * 0.25;
            item.setVelocity(vx, 0.12, vz);
            world.spawnEntity(item);
        }
    }

    /**
     * Spawn a static ItemDisplay corpse for a living mob. Used as a fallback when the
     * normal mixin injection path didn't run (some death codepaths can bypass die()).
     * This mirrors the behavior in the LivingEntityDeathMixin.
     */
    public static void spawnCorpseDisplay(ServerWorld world, MobEntity mob) {
        if (world == null || mob == null) return;
        DisplayEntity.ItemDisplayEntity display = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        display.setPosition(mob.getX(), mob.getY(), mob.getZ());
        display.setItemStack(pickCorpseStack(mob));
        display.setItemDisplayContext(ItemDisplayContext.GROUND);
        display.setBillboardMode(DisplayEntity.BillboardMode.CENTER);
        display.setDisplayWidth(0.9f);
        display.setDisplayHeight(0.9f);
        display.setViewRange(28.0f);
        display.setShadowRadius(0.0f);
        display.addCommandTag("doommc3d_corpse");
        com.hitpo.doommc3d.util.DebugLogger.debug("DoomMobDrops.spawnCorpse", () -> "[DoomMobDrops] spawnCorpseDisplay: mob=" + mob + " pos=(" + mob.getX() + "," + mob.getY() + "," + mob.getZ() + ") tags=" + display.getCommandTags());
        world.spawnEntity(display);
        SlideParams params = new SlideParams(50, 0.90);
        com.hitpo.doommc3d.interact.DoomCorpsePhysics.startSliding(world, display, mob.getVelocity().multiply(0.6), params.ticks, params.drag);
        // Also spawn regular Doom drops (ammo/weapon) rather than skull remains
        spawnDrops(world, mob);
    }

    public static void spawnDropsForType(ServerWorld world, com.hitpo.doommc3d.doomai.DoomMobType t, double x, double y, double z) {
        // Mirror spawnDrops but target a specific DoomMobType and coordinates
        switch (t) {
            case SHOTGUN_GUY -> spawnWeaponPickup(world, x, y, z, new ItemStack(ModItems.DOOM_SHOTGUN), "doommc3d_weapon_shotgun");
            case ZOMBIEMAN, CHAINGUNNER -> spawnAmmo(world, x, y, z, new ItemStack(Items.IRON_NUGGET, 6), "doommc3d_ammo_clip");
            default -> {
                // No default drops for other types for now
            }
        }
    }

    private static net.minecraft.item.ItemStack pickCorpseStack(MobEntity mob) {
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
