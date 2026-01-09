package com.hitpo.doommc3d.sound;

import com.hitpo.doommc3d.DoomMC3D;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/**
 * Custom Doom sound events registered from extracted WAD audio.
 * 
 * Sound naming convention:
 * - WAD lumps use "DS" prefix (DSPISTOL, DSSHTGN, etc.)
 * - We register them without the DS prefix in lowercase
 * - sounds.json maps to extracted .wav files in sounds/ directory
 * 
 * To extract sounds from WAD:
 * 1. DoomSoundLoader.extractAllSounds() reads "DS*" lumps from DOOM.WAD
 * 2. Converts raw DMX sound data to .wav format
 * 3. Stores in run/mods/doommc3d/sounds/
 * 4. Minecraft loads via sounds.json reference
 */
public class ModSounds {
    
    // Weapon sounds (from WAD: DSPISTOL, DSSHTGN, DSPLASMA, etc.)
    public static final SoundEvent PISTOL_FIRE = register("pistol");
    public static final SoundEvent SHOTGUN_FIRE = register("shotgn");
    public static final SoundEvent CHAINGUN_FIRE = register("pistol_rapid");  // Use unique name, but same sound
    public static final SoundEvent ROCKET_FIRE = register("rlaunc");
    public static final SoundEvent PLASMA_FIRE = register("plasma");
    public static final SoundEvent BFG_FIRE = register("bfg");
    
    // Monster sounds
    // Zombieman (DSPOSIT1, DSPOPAIN, DSPODTH1-3, DSPOSACT)
    public static final SoundEvent ZOMBIEMAN_SIGHT = register("posit1");
    public static final SoundEvent ZOMBIEMAN_PAIN = register("popain");
    public static final SoundEvent ZOMBIEMAN_DEATH = register("podth1");
    public static final SoundEvent ZOMBIEMAN_ATTACK = register("zombieman_attack");
    
    // Shotgun Guy (DSPOSIT2, DSPOPAIN, DSPODTH2, DSSHTGN)
    public static final SoundEvent SHOTGUN_GUY_SIGHT = register("posit2");
    public static final SoundEvent SHOTGUN_GUY_PAIN = register("shotgunguy_pain");
    public static final SoundEvent SHOTGUN_GUY_DEATH = register("podth2");
    public static final SoundEvent SHOTGUN_GUY_ATTACK = register("shotgunguy_attack");
    
    // Imp (DSBGSIT1, DSPOPAIN, DSBGDTH1-2, DSFIRSHT)
    public static final SoundEvent IMP_SIGHT = register("bgsit1");
    public static final SoundEvent IMP_PAIN = register("imp_pain");
    public static final SoundEvent IMP_DEATH = register("bgdth1");
    public static final SoundEvent IMP_ATTACK = register("firsht");
    
    // Demon (DSSGTSIT, DSDMPAIN, DSSGTDTH)
    public static final SoundEvent DEMON_SIGHT = register("sgtsit");
    public static final SoundEvent DEMON_PAIN = register("dmpain");
    public static final SoundEvent DEMON_DEATH = register("sgtdth");
    public static final SoundEvent DEMON_ATTACK = register("sgtatk");
    
    // Cacodemon (DSCACSIT, DSDMPAIN, DSCACDTH)
    public static final SoundEvent CACODEMON_SIGHT = register("cacsit");
    public static final SoundEvent CACODEMON_PAIN = register("cacodemon_pain");
    public static final SoundEvent CACODEMON_DEATH = register("cacdth");
    public static final SoundEvent CACODEMON_ATTACK = register("cacodemon_attack");
    
    // Generic/world sounds
    public static final SoundEvent BARREL_EXPLODE = register("barexp");
    public static final SoundEvent DOOR_OPEN = register("doropn");
    public static final SoundEvent DOOR_CLOSE = register("dorcls");
    public static final SoundEvent SWITCH_USE = register("swtchn");
    public static final SoundEvent PLATFORM_MOVE = register("stnmov");
    
    private static SoundEvent register(String wadName) {
        // Register with Minecraft using our mod namespace
        // The actual sound file will be loaded from sounds.json
        Identifier id = Identifier.of(DoomMC3D.MOD_ID, wadName);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }
    
    public static void init() {
        // Static initialization - sounds are registered when class loads
        System.out.println("[DoomMC3D] Registered " + Registries.SOUND_EVENT.getIds().stream()
            .filter(id -> id.getNamespace().equals(DoomMC3D.MOD_ID))
            .count() + " custom sound events");
    }
}

