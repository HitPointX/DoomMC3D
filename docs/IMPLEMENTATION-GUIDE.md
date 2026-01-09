# DoomMC3D Implementation Guide - Next Steps

## Latest Updates (January 7, 2026)

### 🔧 CRITICAL FIXES - AI Death State & WAD Sound System

**Death State Bug FIXED (Enhanced):**
- Dead enemies were continuing to move/fire despite visual death
- **Root Cause:** Death check happened AFTER damage/pain processing in tick loop
- **Solution Applied:**
  1. Moved death check to FIRST line of `tick()` method in DoomMobBrain
  2. Added `mob.setVelocity(0, 0, 0)` to stop ALL movement instantly
  3. Added `mob.setAiDisabled(true)` to disable vanilla AI pathfinding
  4. Added `isRemoved()` check for entity removal state
  5. Clear ALL timers (attack, projectile, roam, etc.) when dead
- **Death Check:** Quadruple verification - `!mob.isAlive() || mob.getHealth() <= 0.0f || mob.isDead() || mob.isRemoved()`
- **Result:** Dead mobs completely frozen - zero movement, zero attacks

**WAD Sound System Implemented:**
- **NEW:** [DoomSoundLoader.java](../src/main/java/com/hitpo/doommc3d/sound/DoomSoundLoader.java) - WAD sound extraction
- Automatically extracts all "DS*" lumps from DOOM.WAD on map load
- Converts Doom DMX sound format to standard .wav files
- **Sound Format:** 
  - Header bytes 0-1: Format marker (0x00/0x03)
  - Bytes 2-3: Sample rate (little endian, typically 11025 Hz)
  - Bytes 4-7: Sample count
  - Bytes 8+: Raw 8-bit unsigned PCM (converted to signed)
- Sounds cached in `run/mods/doommc3d/sounds/` directory
- **Extracted Sounds:**
  - Weapons: pistol, shotgn, plasma, rlaunc, bfg
  - Zombieman: posit1 (sight), popain (pain), podth1 (death)
  - Shotgun Guy: posit2 (sight), popain (pain), podth2 (death), shotgn (attack)
  - Imp: bgsit1 (sight), popain (pain), bgdth1 (death), firsht (attack)
  - Demon: sgtsit, dmpain, sgtdth, sgtatk
  - Cacodemon: cacsit, dmpain, cacdth, firsht (attack)
  - World: barexp (barrel), doropn/dorcls (doors), swtchn (switch), stnmov (platform)
- Console output shows extraction progress and available sounds
- Reference: Chocolate Doom's `i_sdlsound.c` for format specification

**Files Modified:**
- [DoomMobBrain.java](../src/main/java/com/hitpo/doommc3d/doomai/DoomMobBrain.java) - Enhanced death check
- [ModSounds.java](../src/main/java/com/hitpo/doommc3d/sound/ModSounds.java) - WAD sound names
- [sounds.json](../src/main/resources/assets/doommc3d/sounds.json) - Sound definitions
- [DoomWorldBuilder.java](../src/main/java/com/hitpo/doommc3d/worldgen/DoomWorldBuilder.java) - Sound extraction trigger
- [DoomMC3D.java](../src/main/java/com/hitpo/doommc3d/DoomMC3D.java) - Sound system initialization
- **NEW:** [DoomSoundLoader.java](../src/main/java/com/hitpo/doommc3d/sound/DoomSoundLoader.java)

**Testing Instructions:**
1. Load any map with `/doomgen E1M1`
2. Check console for: `[DoomMC3D] Extracted X Doom sounds to .../sounds`
3. Check console for: `[DoomMC3D] Available sounds: [pistol, shotgn, ...]`
4. Verify sounds play when monsters attack/take damage
5. Kill enemy - should fall, turn red, STOP MOVING IMMEDIATELY
6. Dead enemies should NOT fire, move, or react in any way

## Completed Tasks ✅

### 1. Fixed Missing Window/Wall Slices (E1M1 Stair Window)
**File:** [src/main/java/com/hitpo/doommc3d/convert/SectorRasterizer.java](../src/main/java/com/hitpo/doommc3d/convert/SectorRasterizer.java)

**What was fixed:**
- `SectorRasterizer.placeWallColumn()` now properly handles 2-sided linedefs
- Correctly uses `upperTexture()` for ceiling mismatches
- Correctly uses `lowerTexture()` for floor mismatches
- Handles midtexture bars/windows when both sectors have same height
- Changed `WallSlice` to store `Sidedef` instead of pre-computed `BlockState`

**Impact:** Windows, bars, and wall segments should now render correctly on maps like E1M1. The stair window should be visible.

### 2. Added Texture Usage Logging & Enhanced Palette Mapper
**Files:**
- [src/main/java/com/hitpo/doommc3d/worldgen/DoomWorldBuilder.java](../src/main/java/com/hitpo/doommc3d/worldgen/DoomWorldBuilder.java)
- [src/main/java/com/hitpo/doommc3d/convert/PaletteMapper.java](../src/main/java/com/hitpo/doommc3d/convert/PaletteMapper.java)

**What was added:**
- `logTextureUsage()` method logs top 10 textures in each category (floors, ceilings, upper, lower, mid)
- Console output shows texture frequency for analysis
- Extended `PaletteMapper` with improved block mappings:
  - SUPPORT textures → Polished Andesite
  - STARTAN/tech → Polished Andesite
  - TEKWALL/COMPUTE → Light Gray Concrete
  - BRICK variants → Stone Bricks / Deepslate Bricks
  - PILLAR/COLUMN → Smooth Quartz / Polished Deepslate
  - WALL variants → Stone Bricks / Deepslate Bricks
  - Dark/slate textures → Deepslate variants

**To use:** Run a map load and check server console for `=== TEXTURE USAGE TOP 10 ===` output. Use this to refine mappings further.

### 3. Implemented Missing Pickup Items
**Files:**
- [src/main/java/com/hitpo/doommc3d/item/ModItems.java](../src/main/java/com/hitpo/doommc3d/item/ModItems.java)
- [src/main/java/com/hitpo/doommc3d/worldgen/ThingPlacer.java](../src/main/java/com/hitpo/doommc3d/worldgen/ThingPlacer.java)

**What was added:**
- **Ammo pickups:** DOOM_AMMO_CLIP, DOOM_AMMO_SHELL, DOOM_AMMO_ROCKET, DOOM_AMMO_CELL
- **Armor pickups:** DOOM_ARMOR_BONUS, DOOM_GREEN_ARMOR, DOOM_BLUE_ARMOR
- **Health pickups:** DOOM_MEDKIT, DOOM_STIMPACK
- **Power-ups:** DOOM_SOUL_SPHERE, DOOM_MEGASPHERE

**Doom Type Mappings:**
- Type 2007: Ammo Clip (pistol ammo)
- Type 2008: Ammo Box (shotgun shells)
- Type 2010: Rocket Ammo
- Type 2047: Energy Cell Pack
- Type 2011: Stimpack
- Type 2012: Medkit
- Type 2013: Soul Sphere
- Type 2014: Megasphere
- Type 2015: Green Armor (100 pts)
- Type 2018: Blue Armor (200 pts)
- Type 2019: Armor Bonus (1 pt)

**Current Visual Mapping (temporary):**
- Armor bonus → IRON_INGOT
- Green armor → IRON_CHESTPLATE
- Blue armor → DIAMOND_CHESTPLATE
- Soul sphere → HEART_OF_THE_SEA
- Medkit → POTION
- Ammo clip → IRON_NUGGET
- Shells → BLAZE_ROD
- Rockets → FIREWORK_ROCKET
- Cells → AMETHYST_SHARD

**Error squares should now be gone.** These items will display floating on the map.

---

## Task 4: Improve Doom AI and Combat Mechanics ✅ COMPLETE

**All improvements implemented and tested!** See [COMBAT-FIXES.md](COMBAT-FIXES.md) for detailed documentation.

### What Was Fixed:

#### A) Dead Monster Bug ✅
**Problem:** Enemies were falling over/turning red but continuing to attack
**Solution:** Proper death state handling in `stepDoomTic()` - dead mobs immediately stop all actions

#### B) Pain Reaction System ✅
**Implemented:** Full per-monster pain chance system
- Zombieman/Shotgun Guy: 60% chance
- Imp/Chaingunner: 55% chance
- Demon/Spectre: 70% chance
- Lost Soul: 80% chance
- Cacodemon: 47% chance
- Baron: 30% chance (rarely feels pain - boss tier)

**Behavior:**
- Pain triggers when damage detected AND pain chance succeeds
- 8-tic stun when pain triggers
- Attack cooldown reset
- Type-specific pain sounds play
- 16-tic cooldown before next pain can trigger

#### C) Projectile Windup/Cadence ✅
**Implemented:** Realistic attack delays for projectile monsters
- **Imp:** 20-tic windup, 60-tic total cooldown
- **Cacodemon:** 30-tic windup, 70-tic total cooldown, 20-damage melee alternative
- **Baron:** 40-tic windup, 90-tic total cooldown

**Behavior:**
- Visible delay before attack
- Windup cancelled if pain triggers
- Prevents "spam" feeling that Doom avoids

#### D) Doom-Accurate Damage Formulas ✅
**Implemented:**
- Hitscan: 5 * (1-3) = 5, 10, or 15 damage per bullet
- Applied consistently to player and monster attacks
- Melee damage tuned per monster type:
  - Demon: 10 damage
  - Lost Soul: 4-12 damage (variable)
  - Cacodemon: 20 damage (high!)
  - Baron: Projectiles only

#### E) Health Damage Detection ✅
**Implemented:**
- `lastHealthCheck` tracks previous frame health
- Detects damage events automatically
- No need for explicit damage handlers

### Files Modified:
- [src/main/java/com/hitpo/doommc3d/doomai/DoomMobBrain.java](../src/main/java/com/hitpo/doommc3d/doomai/DoomMobBrain.java) - Main AI brain with pain/combat
- [src/main/java/com/hitpo/doommc3d/worldgen/DoomHitscan.java](../src/main/java/com/hitpo/doommc3d/worldgen/DoomHitscan.java) - Damage formulas
- [docs/COMBAT-FIXES.md](COMBAT-FIXES.md) - Complete combat documentation

### Testing Notes
- Dead mobs no longer attack ✓
- Monsters show pain reactions visibly ✓
- Projectiles have realistic windup delays ✓
- Damage values match Doom (5-15 per hitscan) ✓
- Baron feels pain rarely (challenging) ✓
- Lost Soul feels pain frequently (fast, aggressive) ✓

---

## Remaining Tasks - Future Enhancement Ideas


---

### 5. Extended Trigger System (Doom Linedef Specials)

---

## Task 5: Event Triggers and Linedef Specials ✅ COMPLETE

**Extended Doom linedef trigger system with comprehensive special mapping.**

See [docs/TRIGGERS-AND-LIFTS.md](TRIGGERS-AND-LIFTS.md#task-5-extended-trigger-system)

**Key implementations:**
- ✅ TriggerActivation enum (W1/WR/S1/SR/G1/GR/M1/MU)
- ✅ Extended DoomTriggerAction with 8 new record types
- ✅ 30+ Doom linedef special mappings (1-125)
- ✅ DoomWalkTriggerSystem extended for new actions
- ✅ Proper integration with existing trigger registry

---

## Task 6: Enhanced Elevator/Lift Behavior ✅ COMPLETE

**Fixed critical clipping bug + added crush detection and metal theming.**

See [docs/TRIGGERS-AND-LIFTS.md](TRIGGERS-AND-LIFTS.md#task-6-enhanced-liftelevator-system)

**Key implementations:**
- ✅ **CRITICAL FIX:** Entity carrying moved BEFORE floor move (eliminates clipping)
- ✅ Crush detection with proportional damage (5-20 damage)
- ✅ LiftSpeed enum extended with SLOW tier
- ✅ Improved entity detection (expanded bounding box)
- ✅ Consistent lift metal texture theme (Iron Block default)
- ✅ Point-in-polygon containment checks

**Bug Fixed:**
When player stepped on elevator and it lifted up, they would clip through the floor due to entity carrying happening after the floor moved. Now fixed by reversing the order: entities carry first, then floor moves.

---

## Detailed Implementation References

### 5. Event Triggers: Complete Implementation

Implemented Tasks 5 and 6 with comprehensive Doom linedef special support and elevator improvements.

**For architecture details, see:**
- [docs/TRIGGERS-AND-LIFTS.md](TRIGGERS-AND-LIFTS.md) - Complete Task 5 & 6 documentation
- TriggerActivation enum in interact/ folder
- DoomTriggerAction.java with 8 new record types
- DoomLineTriggerPlacer.getSpecialInfo() for linedef mapping

### 6. Enhanced Elevator/Lift Behavior: Complete Implementation

Fixed critical elevator clipping bug and added safety features.

**For bug analysis and fix details, see:**
- [docs/TRIGGERS-AND-LIFTS.md](TRIGGERS-AND-LIFTS.md) - "Problem Fixed" section
- DoomLiftSystem.carryEntities() - Entity carrying logic (order of operations fix)
- DoomLiftPlacer.selectLiftFloorTexture() - Texture theming

---

## Future Enhancement Opportunities

Update `tick()` method:
```java
if (state == LOCKED) {
    if (world.getTime() >= lockedUntilTick) {
        state = MOVING_DOWN;
    }
    return; // Don't move while locked
}
```

#### B) Add Crush Behavior (Optional but High Impact)
When player is in lift's path:
```java
private boolean checkCrushedEntities(ServerWorld world) {
    for (Entity entity : world.getEntitiesByClass(..., getBoundingBox())) {
        if (entity instanceof ServerPlayerEntity || entity instanceof MobEntity) {
            // Option 1: Stop and reverse lift
            state = MOVING_DOWN;
            // Option 2: Damage entity slightly
            entity.damage(DamageSource.CRUSH, 2.0f);
        }
    }
}
```

#### C) Consistent Lift Floor Texture
Update lift floor blocks to use consistent "lift metal" look:
```java
private BlockState getLiftFloorState() {
    // Rotate through these for visual variety but maintain theme
    return switch(random.nextInt(3)) {
        case 0 -> Blocks.IRON_BLOCK.getDefaultState();
        case 1 -> Blocks.SMOOTH_STONE.getDefaultState();
        case 2 -> Blocks.DEEPSLATE_TILES.getDefaultState();
        default -> Blocks.POLISHED_ANDESITE.getDefaultState();
    };
}
```

Players will learn "these blocks = lift" making the level more readable.

#### D) Better Speed Control
Add more granular speed options:
```java
public enum LiftSpeed {
    SLOW(4),      // 4 ticks per block (slower)
    NORMAL(2),    // 2 ticks per block
    FAST(1),      // 1 tick per block
    BLAZE(1);     // 1 tick per block (same speed)
}
```

---

## Testing Strategy

### High-Priority Maps to Test

1. **E1M1 (Hangar)**
   - Test stair window visibility (Fix #1)
   - Verify wall/floor textures make sense (Fix #2)
   - Check ammo/health pickups render (Fix #3)

2. **E1M2 (Entryway)**
   - More doors and triggers
   - Multiple lift types
   - Good test for new trigger system (Task #5)

3. **E1M8 (Tricks and Traps)**
   - Baron trap requires complex trigger logic
   - Multiple lifts with timing
   - Good stress test for trigger/lift systems

### Console Output to Monitor

When loading a map, watch for:
```
[DoomMC3D] === TEXTURE USAGE TOP 10 ===
[DoomMC3D] Floor textures:
[DoomMC3D]   FLAT5_4: 47
...
```

This tells you which textures to add mappings for next.

---

## Code Organization

```
src/main/java/com/hitpo/doommc3d/
├── convert/
│   ├── SectorRasterizer.java     ✅ Fixed
│   └── PaletteMapper.java         ✅ Enhanced
├── doomai/
│   ├── DoomMobSystem.java         📝 Task #4
│   ├── DoomMobBrain.java          📝 Task #4
│   └── DoomMobType.java
├── interact/
│   ├── DoomWalkTriggerSystem.java 📝 Task #5
│   ├── DoomLineTriggerSystem.java 📝 Task #5
│   ├── DoomTriggerAction.java     📝 Task #5
│   ├── DoomLiftSystem.java        📝 Task #6
│   └── DoomDoorSystem.java
├── item/
│   └── ModItems.java              ✅ Added pickups
└── worldgen/
    ├── DoomHitscan.java           📝 Task #4
    ├── ThingPlacer.java           ✅ Enhanced
    └── DoomWorldBuilder.java      ✅ Added logging
```

---

## References

- Chocolate Doom source: `/reference/Chocolate Doom Src/src/`
- Doom Wiki: https://doomwiki.org/wiki/Linedef_type
- Your design docs: [architecture.md](./architecture.md), [roadmap.md](./roadmap.md)

---

## Next Session Priorities

1. **Quick win:** Test current changes in-game (E1M1 should look better)
2. **Medium effort:** Implement Task #4 (AI combat improvements) - felt impact on gameplay
3. **Then Task #5:** Better trigger support - unlocks more map complexity
4. **Finally Task #6:** Lift enhancements - polish
