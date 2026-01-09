package com.hitpo.doommc3d.client.hud;

import com.hitpo.doommc3d.client.doomgfx.DoomWadSpriteCache;
import com.hitpo.doommc3d.client.weapon.DoomWeaponClientAnim;
import com.hitpo.doommc3d.item.ModItems;
import com.hitpo.doommc3d.player.DoomAmmo;
import com.hitpo.doommc3d.wad.WadFile;
import com.hitpo.doommc3d.wad.WadRepository;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
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

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) {
            return;
        }

        int w = drawContext.getScaledWindowWidth();
        int h = drawContext.getScaledWindowHeight();
        int y0 = h - HUD_HEIGHT;

        drawContext.fill(0, y0, w, h, 0xCC000000);
        drawContext.fill(0, y0, w, y0 + 1, 0xFF3A3A3A);

        PlayerEntity player = client.player;
        TextRenderer tr = client.textRenderer;

        int health = Math.max(0, (int) Math.ceil(player.getHealth()));
        int armor = player.getArmor();
        int ammo = DoomAmmo.getAmmoForHeldWeapon(player);

        drawContext.drawTextWithShadow(tr, "HP " + health, 8, y0 + 14, 0xFFCC4444);
        drawContext.drawTextWithShadow(tr, "AMMO " + ammo, 8, y0 + 26, 0xFFCCCC44);
        drawContext.drawTextWithShadow(tr, "AR " + armor, w - 8 - tr.getWidth("AR " + armor), y0 + 14, 0xFF44CCCC);

        renderFace(drawContext, player, w / 2 - 16, y0 + 4, 32, 32);
        renderWeapon(drawContext, client, w, h, y0);
        renderPainOverlay(drawContext, player, 0, 0, w, h);
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
