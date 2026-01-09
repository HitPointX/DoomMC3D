package com.hitpo.doommc3d.item;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModItems {
    public static final Identifier DOOM_PISTOL_ID = Identifier.of(DoomMC3D.MOD_ID, "doom_pistol");
    public static final RegistryKey<Item> DOOM_PISTOL_KEY = RegistryKey.of(RegistryKeys.ITEM, DOOM_PISTOL_ID);
    public static final Item DOOM_PISTOL = new DoomPistolItem(new Item.Settings().registryKey(DOOM_PISTOL_KEY).maxCount(1));

    public static final Item DOOM_SHOTGUN = simpleItem("doom_shotgun");
    public static final Item DOOM_CHAINGUN = simpleItem("doom_chaingun");
    public static final Item DOOM_ROCKET_LAUNCHER = simpleItem("doom_rocket_launcher");
    public static final Item DOOM_PLASMA_RIFLE = simpleItem("doom_plasma_rifle");
    public static final Item DOOM_BFG = simpleItem("doom_bfg");

    public static final Item DOOM_KEY_RED = simpleItem("doom_key_red");
    public static final Item DOOM_KEY_YELLOW = simpleItem("doom_key_yellow");
    public static final Item DOOM_KEY_BLUE = simpleItem("doom_key_blue");

    // Pickup items (temporary mappings to vanilla items for visuals)
    public static final Item DOOM_AMMO_CLIP = simpleItem("doom_ammo_clip");
    public static final Item DOOM_AMMO_SHELL = simpleItem("doom_ammo_shell");
    public static final Item DOOM_AMMO_ROCKET = simpleItem("doom_ammo_rocket");
    public static final Item DOOM_AMMO_CELL = simpleItem("doom_ammo_cell");

    public static final Item DOOM_ARMOR_BONUS = simpleItem("doom_armor_bonus");
    public static final Item DOOM_GREEN_ARMOR = simpleItem("doom_green_armor");
    public static final Item DOOM_BLUE_ARMOR = simpleItem("doom_blue_armor");

    public static final Item DOOM_MEDKIT = simpleItem("doom_medkit");
    public static final Item DOOM_STIMPACK = simpleItem("doom_stimpack");
    public static final Item DOOM_SOUL_SPHERE = simpleItem("doom_soul_sphere");
    public static final Item DOOM_MEGASPHERE = simpleItem("doom_megasphere");

    private ModItems() {
    }

    public static void init() {
        Registry.register(Registries.ITEM, DOOM_PISTOL_ID, DOOM_PISTOL);
        register("doom_shotgun", DOOM_SHOTGUN);
        register("doom_chaingun", DOOM_CHAINGUN);
        register("doom_rocket_launcher", DOOM_ROCKET_LAUNCHER);
        register("doom_plasma_rifle", DOOM_PLASMA_RIFLE);
        register("doom_bfg", DOOM_BFG);
        register("doom_key_red", DOOM_KEY_RED);
        register("doom_key_yellow", DOOM_KEY_YELLOW);
        register("doom_key_blue", DOOM_KEY_BLUE);

        // Register pickup items
        register("doom_ammo_clip", DOOM_AMMO_CLIP);
        register("doom_ammo_shell", DOOM_AMMO_SHELL);
        register("doom_ammo_rocket", DOOM_AMMO_ROCKET);
        register("doom_ammo_cell", DOOM_AMMO_CELL);
        register("doom_armor_bonus", DOOM_ARMOR_BONUS);
        register("doom_green_armor", DOOM_GREEN_ARMOR);
        register("doom_blue_armor", DOOM_BLUE_ARMOR);
        register("doom_medkit", DOOM_MEDKIT);
        register("doom_stimpack", DOOM_STIMPACK);
        register("doom_soul_sphere", DOOM_SOUL_SPHERE);
        register("doom_megasphere", DOOM_MEGASPHERE);
    }

    private static Item simpleItem(String path) {
        Identifier id = Identifier.of(DoomMC3D.MOD_ID, path);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        return new Item(new Item.Settings().registryKey(key).maxCount(1));
    }

    private static void register(String path, Item item) {
        Registry.register(Registries.ITEM, Identifier.of(DoomMC3D.MOD_ID, path), item);
    }
}
