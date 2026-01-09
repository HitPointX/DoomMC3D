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
            spawnAmmo(world, mob.getX(), mob.getY(), mob.getZ(), new ItemStack(Items.IRON_NUGGET, 6));
        }
    }

    private static boolean hasType(MobEntity mob, DoomMobType type) {
        return mob.getCommandTags().contains(DoomMobTags.tagForType(type));
    }

    private static void spawnAmmo(ServerWorld world, double x, double y, double z, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(world, x, y + 0.35, z, stack);
        item.setToDefaultPickupDelay();
        world.spawnEntity(item);
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
        world.spawnEntity(display);
    }
}
