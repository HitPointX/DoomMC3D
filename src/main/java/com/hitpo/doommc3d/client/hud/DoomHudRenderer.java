package com.hitpo.doommc3d.client.hud;

import com.hitpo.doommc3d.client.doomgfx.DoomWadSpriteCache;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientAnim;
import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadRepository;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;

public final class DoomHudRenderer implements HudRenderCallback {
    private static final int HUD_HEIGHT = 40;
    private static final DoomWadSpriteCache SPRITES = new DoomWadSpriteCache();
    private static volatile WadFile cachedWad;
    // Pickup display state (client-side)
    private static volatile String pickupText = null;
    private static volatile int pickupColor = 0xFFFFFFFF;
    private static volatile int pickupTicksRemaining = 0;
    // Smoothed effective light to reduce abrupt band flicker when crossing sector boundaries
    private static double smoothedEffective = -1.0;

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) {
            return;
        }

        // Doom-style quantized fog overlay based on averaged block light + extralight
        // Increase averaging to a 5x5 area to further smooth sector boundary differences.
        BlockPos basePos = client.player.getBlockPos();
        int sum = 0;
        int count = 0;
        final int R = 2; // radius -> (2*R+1)^2 samples (5x5)
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                BlockPos p = basePos.add(dx, 0, dz);
                sum += client.world.getLightLevel(p);
                count++;
            }
        }
        int blockLightAvg = count > 0 ? (sum / count) : client.world.getLightLevel(basePos);
        int rawBlock = client.world.getLightLevel(basePos);
        int extra = com.hitpo.doommc3d.client.lighting.ClientExtralightManager.scaledExtra(3);
        int rawEffective = Math.max(0, Math.min(15, blockLightAvg + extra));

        // Temporal smoothing: use adaptive lerp rates so brightening can feel responsive
        // while darkening (entering new darker sector) is slightly slower to avoid popping.
        if (smoothedEffective < 0.0) smoothedEffective = rawEffective;
        final double LERP_UP = 0.35;   // when raw > smoothed: faster
        final double LERP_DOWN = 0.12; // when raw <= smoothed: slower
        double lerp = rawEffective > smoothedEffective ? LERP_UP : LERP_DOWN;
        smoothedEffective = smoothedEffective * (1.0 - lerp) + rawEffective * lerp;
        double effectiveForDarkness = smoothedEffective;

        // Ambient baseline to avoid complete black in interiors.
        // Raise baseline to brighten overall look (closer to vanilla Doom brightness).
        final int AMBIENT_BASE = 4;
        double darkness = 1.0 - ((effectiveForDarkness + AMBIENT_BASE) / 17.0);
        // Use 16 bands matching Chocolate Doom LIGHTLEVELS for a closer feel, but
        // softly blend between adjacent bands to reduce visual stepping.
        int bands = 16;
        double scaled = darkness * bands;
        double bandFloor = Math.floor(scaled);
        double frac = scaled - bandFloor; // 0..1 fraction inside the band
        // blendFactor controls how much of the fractional part bleeds into the next band
        final double BLEND_FACTOR = 0.80;
        double banded = (bandFloor + frac * BLEND_FACTOR) / (double) bands;
        // reduce overlay multiplier so darkness isn't too heavy overall
        float alpha = (float) (banded * 0.65f);
        if (alpha > 0f) {
            int a = (int) (alpha * 255) & 0xFF;
            int color = (a << 24);
            int ww = drawContext.getScaledWindowWidth();
            int hh = drawContext.getScaledWindowHeight();
            drawContext.fill(0, 0, ww, hh, color);
        }

        // Debug overlay: small panel with raw/avg/smoothed/extralight values for tuning
        int dbgX = 6;
        int dbgY = 6;
        TextRenderer trDbg = client.textRenderer;
        String[] dbgLines = new String[] {
            String.format("rawBlock=%d avg=%d extra=%d", rawBlock, blockLightAvg, extra),
            String.format("rawEff=%d smoothed=%.2f", rawEffective, smoothedEffective),
            String.format("bands=%d frac=%.2f alpha=%.3f", bands, frac, alpha),
            String.format("extraTicks=%d", com.hitpo.doommc3d.client.lighting.ClientExtralightManager.getTicks())
        };
        // small translucent background for debug text
        int dbgW = 220;
        int dbgH = dbgLines.length * 10 + 6;
        drawContext.fill(dbgX - 4, dbgY - 4, dbgX + dbgW, dbgY + dbgH, 0x77000000);
        for (int i = 0; i < dbgLines.length; i++) {
            drawContext.drawTextWithShadow(trDbg, dbgLines[i], dbgX, dbgY + i * 10, 0xFFFFFFFF);
        }

        int w = drawContext.getScaledWindowWidth();
        int h = drawContext.getScaledWindowHeight();
        int y0 = h - HUD_HEIGHT;

        drawContext.fill(0, y0, w, h, 0xCC000000);
        drawContext.fill(0, y0, w, y0 + 1, 0xFF3A3A3A);

        PlayerEntity player = client.player;
        TextRenderer tr = client.textRenderer;

        // Draw pickup text at top-left (Doom-style)
        if (pickupTicksRemaining > 0 && pickupText != null && !pickupText.isEmpty()) {
            drawContext.drawTextWithShadow(tr, pickupText, 8, 8, pickupColor);
            pickupTicksRemaining--;
            if (pickupTicksRemaining <= 0) {
                pickupText = null;
            }
        }

        int health = Math.max(0, (int) Math.ceil(player.getHealth()));
        int armor = player.getArmor();
        int ammo = DoomAmmo.getAmmoForHeldWeapon(player);

        drawContext.drawTextWithShadow(tr, "HP " + health, 8, y0 + 14, 0xFFCC4444);
        drawContext.drawTextWithShadow(tr, "AMMO " + ammo, 8, y0 + 26, 0xFFCCCC44);
        drawContext.drawTextWithShadow(tr, "AR " + armor, w - 8 - tr.getWidth("AR " + armor), y0 + 14, 0xFF44CCCC);

        // Keycard indicators (show when player has key command tags)
        String keysLabel = "";
        boolean hasRed = player.getCommandTags().contains("doommc3d_key_red");
        boolean hasYellow = player.getCommandTags().contains("doommc3d_key_yellow");
        boolean hasBlue = player.getCommandTags().contains("doommc3d_key_blue");
        if (hasRed) keysLabel += "[R] ";
        if (hasYellow) keysLabel += "[Y] ";
        if (hasBlue) keysLabel += "[B] ";
        if (!keysLabel.isEmpty()) {
            int keyX = w - 8 - tr.getWidth(keysLabel);
            drawContext.drawTextWithShadow(tr, keysLabel, keyX, y0 + 26, 0xFFFFFFFF);
        }

        renderFace(drawContext, player, w / 2 - 16, y0 + 4, 32, 32);
        renderWeapon(drawContext, client, w, h, y0);
        renderPainOverlay(drawContext, player, 0, 0, w, h);
    }

    // Called from networking handler to show pickup text
    public static void showPickup(String text, int color, int durationTicks) {
        pickupText = text;
        pickupColor = color;
        pickupTicksRemaining = Math.max(1, durationTicks);
    }

    private static void renderFace(DrawContext dc, PlayerEntity player, int x, int y, int size, int sizeY) {
        if (!(player instanceof AbstractClientPlayerEntity clientPlayer)) {
            return;
        }
        Identifier skin = clientPlayer.getSkin().comp_1626().comp_3627();
        if (skin == null) {
            return;
        }
        float scale = size / 8.0f;
        dc.getMatrices().pushMatrix();
        dc.getMatrices().translate(x, y);
        dc.getMatrices().scale(scale, scale);
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 8, 8, 8, 8, 64, 64);
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 40, 8, 8, 8, 64, 64);
        dc.getMatrices().popMatrix();
    }

    private static void renderPainOverlay(DrawContext dc, PlayerEntity player, int x1, int y1, int x2, int y2) {
        float hurt = Math.min(1.0f, player.hurtTime / 10.0f);
        float lowHealth = player.getHealth() <= 6.0f ? 0.25f : 0.0f;
        float intensity = Math.max(hurt, lowHealth);
        if (intensity <= 0.0f) {
            return;
        }
        int alpha = Math.min(160, Math.max(0, (int) (intensity * 160)));
        int color = (alpha << 24) | 0x00AA0000;
        dc.fill(x1, y1, x2, y2, color);
    }

    private static void renderWeapon(DrawContext dc, MinecraftClient client, int w, int h, int hudY0) {
        if (client.player == null) {
            return;
        }
        if (!(client.player.getMainHandStack().isOf(ModItems.DOOM_PISTOL)
            || client.player.getMainHandStack().isOf(ModItems.DOOM_SHOTGUN)
            || client.player.getMainHandStack().isOf(ModItems.DOOM_CHAINGUN)
            || client.player.getMainHandStack().isOf(ModItems.DOOM_ROCKET_LAUNCHER)
            || client.player.getMainHandStack().isOf(ModItems.DOOM_PLASMA_RIFLE)
            || client.player.getMainHandStack().isOf(ModItems.DOOM_BFG))) {
            return;
        }

        String lump = DoomWeaponClientAnim.getWeaponLump(client);
        String flash = DoomWeaponClientAnim.getFlashLump(client);
        WadFile wad = getWad();
        DoomWadSpriteCache.DoomWadSprite sprite = (wad != null && lump != null) ? SPRITES.getOrLoad(wad, lump) : null;
        if (sprite == null) {
            return;
        }

        DoomWadSpriteCache.DoomWadSprite flashSprite = (wad != null && flash != null) ? SPRITES.getOrLoad(wad, flash) : null;

        float scale = Math.max(1.0f, Math.min(w / 320.0f, (h - HUD_HEIGHT) / 200.0f));
        int x0 = Math.round((w - sprite.width() * scale) / 2.0f);
        int y0 = Math.round(hudY0 - sprite.height() * scale);
        int originX = Math.round(x0 + sprite.leftOffset() * scale);
        int originY = Math.round(y0 + sprite.topOffset() * scale);

        dc.getMatrices().pushMatrix();
        dc.getMatrices().translate(originX, originY);
        dc.getMatrices().scale(scale, scale);
        dc.drawTexture(RenderPipelines.GUI_TEXTURED, sprite.texture(), -sprite.leftOffset(), -sprite.topOffset(), 0, 0, sprite.width(), sprite.height(), sprite.width(), sprite.height());
        if (flashSprite != null) {
            dc.drawTexture(RenderPipelines.GUI_TEXTURED, flashSprite.texture(), -flashSprite.leftOffset(), -flashSprite.topOffset(), 0, 0, flashSprite.width(), flashSprite.height(), flashSprite.width(), flashSprite.height());
        }
        dc.getMatrices().popMatrix();
    }

    private static WadFile getWad() {
        WadFile wad = cachedWad;
        if (wad != null) {
            return wad;
        }
        try {
            wad = WadRepository.getOrLoad(null);
            cachedWad = wad;
            return wad;
        } catch (Exception e) {
            return null;
        }
    }
}
