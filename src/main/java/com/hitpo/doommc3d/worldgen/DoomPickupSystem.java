package com.hitpo.doommc3d.worldgen;

import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;

import com.hitpo.doommc3d.player.DoomAmmoAccess;
import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.player.DoomAmmoType;

/**
 * Handles health, armor, ammo, and power-up pickups (non-weapon, non-key).
 */
public final class DoomPickupSystem {
    private static final Set<String> HEALTH_TAGS = Set.of(
        "doommc3d_health_bonus",
        "doommc3d_stimpack",
        "doommc3d_medkit",
        "doommc3d_soul_sphere",
        "doommc3d_megasphere"
    );

    private static final Set<String> ARMOR_TAGS = Set.of(
        "doommc3d_armor_bonus",
        "doommc3d_green_armor",
        "doommc3d_blue_armor"
    );

    private static final Set<String> AMMO_TAGS = Set.of(
        "doommc3d_ammo_clip",
        "doommc3d_ammo_box_bullets",
        "doommc3d_ammo_shell",
        "doommc3d_ammo_box_shells",
        "doommc3d_ammo_rocket",
        "doommc3d_ammo_box_rockets",
        "doommc3d_ammo_cell",
        "doommc3d_ammo_cell_pack",
        "doommc3d_backpack"
    );

    private DoomPickupSystem() {
    }

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(DoomPickupSystem::tickWorld);
    }

    private static void tickWorld(ServerWorld world) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            tryPickup(world, player);
        }
    }

    private static void tryPickup(ServerWorld world, ServerPlayerEntity player) {
        Box box = player.getBoundingBox().expand(1.2, 1.0, 1.2);
        var pickups = world.getEntitiesByType(EntityType.ITEM_DISPLAY, box, entity -> entity.getCommandTags().contains("doommc3d_weapon_pickup"));
        if (pickups.isEmpty()) {
            return;
        }

        for (DisplayEntity.ItemDisplayEntity display : pickups) {
            if (!display.isAlive() || display.squaredDistanceTo(player) > (1.2 * 1.2)) {
                continue;
            }

            var tags = display.getCommandTags();
            boolean consumed = false;

            if (containsAny(tags, HEALTH_TAGS)) {
                consumed = applyHealthPickup(player, tags);
            } else if (containsAny(tags, ARMOR_TAGS)) {
                consumed = applyArmorPickup(player, tags);
            } else if (containsAny(tags, AMMO_TAGS)) {
                consumed = applyAmmoPickup(player, tags);
            }

            if (consumed) {
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ITEM_PICKUP, SoundCategory.PLAYERS, 0.9f, 1.0f);
                display.discard();
                break;
            }
        }
    }

    private static boolean applyHealthPickup(ServerPlayerEntity player, Set<String> tags) {
        float current = player.getHealth();
        float max = player.getMaxHealth();

        if (tags.contains("doommc3d_health_bonus")) {
            if (current >= 200.0f) return false;
            ensureHealthCap(player, 200.0f);
            float target = Math.min(current + 1.0f, 200.0f);
            if (target <= current) return false;
            player.setHealth(target);
            player.sendMessage(Text.literal("Health Bonus"), true);
            return true;
        }

        if (tags.contains("doommc3d_stimpack")) {
            if (current >= 100.0f) return false;
            float target = Math.min(current + 10.0f, Math.min(max, 100.0f));
            player.setHealth(target);
            player.sendMessage(Text.literal("Picked up a Stimpack"), true);
            return true;
        }

        if (tags.contains("doommc3d_medkit")) {
            if (current >= 100.0f) return false;
            float target = Math.min(current + 25.0f, Math.min(max, 100.0f));
            player.setHealth(target);
            player.sendMessage(Text.literal("Picked up a Medikit"), true);
            return true;
        }

        if (tags.contains("doommc3d_soul_sphere")) {
            float target = Math.min(current + 100.0f, 200.0f);
            if (target <= current) return false;
            ensureHealthCap(player, 200.0f);
            player.setHealth(target);
            player.sendMessage(Text.literal("Soul Sphere!"), true);
            return true;
        }

        if (tags.contains("doommc3d_megasphere")) {
            ensureHealthCap(player, 200.0f);
            player.setHealth(200.0f);
            setArmor(player, 200.0);
            player.sendMessage(Text.literal("Megasphere!"), true);
            return true;
        }

        return false;
    }

    private static boolean applyArmorPickup(ServerPlayerEntity player, Set<String> tags) {
        double armor = getArmor(player);

        if (tags.contains("doommc3d_armor_bonus")) {
            double target = Math.min(armor + 1.0, 200.0);
            if (target <= armor) return false;
            setArmor(player, target);
            player.sendMessage(Text.literal("Armor Bonus"), true);
            return true;
        }

        if (tags.contains("doommc3d_green_armor")) {
            if (armor >= 100.0) return false;
            setArmor(player, 100.0);
            player.sendMessage(Text.literal("Green Armor"), true);
            return true;
        }

        if (tags.contains("doommc3d_blue_armor")) {
            if (armor >= 200.0) return false;
            setArmor(player, 200.0);
            player.sendMessage(Text.literal("Blue Armor"), true);
            return true;
        }

        return false;
    }

    private static boolean applyAmmoPickup(ServerPlayerEntity player, Set<String> tags) {
        if (!(player instanceof DoomAmmoAccess ammo)) {
            return false;
        }
        boolean changed = false;

        if (tags.contains("doommc3d_backpack")) {
            // Doom backpack: doubles max ammo (only once) and gives +1 clip of each ammo type.
            if (!ammo.hasDoomBackpack()) {
                ammo.setDoomBackpack(true);
                changed = true;
            }

            int beforeB = ammo.getDoomAmmo(DoomAmmoType.BULLET);
            int beforeS = ammo.getDoomAmmo(DoomAmmoType.SHELL);
            int beforeR = ammo.getDoomAmmo(DoomAmmoType.ROCKET);
            int beforeC = ammo.getDoomAmmo(DoomAmmoType.CELL);

            int afterB = ammo.addDoomAmmo(DoomAmmoType.BULLET, DoomAmmo.getClipAmount(DoomAmmoType.BULLET));
            int afterS = ammo.addDoomAmmo(DoomAmmoType.SHELL, DoomAmmo.getClipAmount(DoomAmmoType.SHELL));
            int afterR = ammo.addDoomAmmo(DoomAmmoType.ROCKET, DoomAmmo.getClipAmount(DoomAmmoType.ROCKET));
            int afterC = ammo.addDoomAmmo(DoomAmmoType.CELL, DoomAmmo.getClipAmount(DoomAmmoType.CELL));
            changed |= (afterB != beforeB) || (afterS != beforeS) || (afterR != beforeR) || (afterC != beforeC);

            if (changed) player.sendMessage(Text.literal("Backpack full of ammo!"), true);
            return changed;
        }

        if (tags.contains("doommc3d_ammo_clip")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.BULLET);
            int after = ammo.addDoomAmmo(DoomAmmoType.BULLET, DoomAmmo.getClipAmount(DoomAmmoType.BULLET));
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Bullets +10"), true);
        } else if (tags.contains("doommc3d_ammo_box_bullets")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.BULLET);
            int after = ammo.addDoomAmmo(DoomAmmoType.BULLET, DoomAmmo.getClipAmount(DoomAmmoType.BULLET) * 5);
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Bullets +50"), true);
        } else if (tags.contains("doommc3d_ammo_shell")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.SHELL);
            int after = ammo.addDoomAmmo(DoomAmmoType.SHELL, DoomAmmo.getClipAmount(DoomAmmoType.SHELL));
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Shells +4"), true);
        } else if (tags.contains("doommc3d_ammo_box_shells")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.SHELL);
            int after = ammo.addDoomAmmo(DoomAmmoType.SHELL, DoomAmmo.getClipAmount(DoomAmmoType.SHELL) * 5);
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Shells +20"), true);
        } else if (tags.contains("doommc3d_ammo_rocket")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.ROCKET);
            int after = ammo.addDoomAmmo(DoomAmmoType.ROCKET, DoomAmmo.getClipAmount(DoomAmmoType.ROCKET));
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Rockets +1"), true);
        } else if (tags.contains("doommc3d_ammo_box_rockets")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.ROCKET);
            int after = ammo.addDoomAmmo(DoomAmmoType.ROCKET, DoomAmmo.getClipAmount(DoomAmmoType.ROCKET) * 5);
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Rockets +5"), true);
        } else if (tags.contains("doommc3d_ammo_cell")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.CELL);
            int after = ammo.addDoomAmmo(DoomAmmoType.CELL, DoomAmmo.getClipAmount(DoomAmmoType.CELL));
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Cells +20"), true);
        } else if (tags.contains("doommc3d_ammo_cell_pack")) {
            int before = ammo.getDoomAmmo(DoomAmmoType.CELL);
            int after = ammo.addDoomAmmo(DoomAmmoType.CELL, DoomAmmo.getClipAmount(DoomAmmoType.CELL) * 5);
            changed |= after != before;
            if (changed) player.sendMessage(Text.literal("Cells +100"), true);
        }

        return changed;
    }

    private static void ensureHealthCap(ServerPlayerEntity player, double cap) {
        var attr = player.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (attr != null && attr.getBaseValue() < cap) {
            attr.setBaseValue(cap);
        }
    }

    private static void setArmor(ServerPlayerEntity player, double value) {
        EntityAttributeInstance armorAttr = player.getAttributeInstance(EntityAttributes.ARMOR);
        if (armorAttr != null) {
            armorAttr.setBaseValue(value);
        }
    }

    private static double getArmor(ServerPlayerEntity player) {
        EntityAttributeInstance armorAttr = player.getAttributeInstance(EntityAttributes.ARMOR);
        return armorAttr != null ? armorAttr.getBaseValue() : 0.0;
    }

    private static boolean containsAny(Set<String> haystack, Set<String> needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

}
