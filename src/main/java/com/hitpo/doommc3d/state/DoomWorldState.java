package com.hitpo.doommc3d.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

/**
 * World-scoped persistent state for DoomMC3D.
 * Stores the last played map and tracks which map is currently loaded.
 * Persists across server restarts.
 */
public class DoomWorldState extends PersistentState {
    public static final Codec<DoomWorldState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            Codec.STRING.optionalFieldOf("lastMap", "e1m1").forGetter(state -> state.lastMap),
            Codec.STRING.optionalFieldOf("currentLoadedMap", "").forGetter(state -> state.currentLoadedMap),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(state -> state.initialized)
        ).apply(instance, DoomWorldState::new)
    );
    
    private static final PersistentStateType<DoomWorldState> TYPE = new PersistentStateType<>(
        "doommc3d_state",
        DoomWorldState::new,
        CODEC,
        null
    );
    
    private String lastMap;
    private String currentLoadedMap;
    private boolean initialized;

    private DoomWorldState() {
        this("e1m1", "", false);
    }

    private DoomWorldState(String lastMap, String currentLoadedMap, boolean initialized) {
        this.lastMap = lastMap;
        this.currentLoadedMap = currentLoadedMap;
        this.initialized = initialized;
    }

    /**
     * Gets or creates the DoomWorldState for the given world.
     */
    public static DoomWorldState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(TYPE);
    }

    public String getLastMap() {
        return lastMap;
    }

    public void setLastMap(String map) {
        this.lastMap = map;
        this.markDirty();
    }

    public String getCurrentLoadedMap() {
        return currentLoadedMap;
    }

    public void setCurrentLoadedMap(String map) {
        this.currentLoadedMap = map;
        this.markDirty();
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
        this.markDirty();
    }
}
