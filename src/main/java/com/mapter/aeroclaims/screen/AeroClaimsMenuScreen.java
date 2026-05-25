package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class AeroClaimsMenuScreen extends AbstractContainerScreen<AeroClaimsMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "textures/screen/claim-menu.png");

    private static final int TEXTURE_W = 180;
    private static final int TEXTURE_H = 180;
    private static final int COLOR_TITLE = 0x222222;
    private static final int COLOR_DIV   = 0x66888888;
    private static final int BTN_X      = 10;

    public AeroClaimsMenuScreen(AeroClaimsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 10;
    }

    @Override
    protected void init() {
        super.init();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String title = Component.translatable("screen.aeroclaims.menu.title").getString();
        graphics.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);
        separator(graphics, 18);
    }

    private void separator(GuiGraphics g, int y) {
        g.fill(BTN_X, y, imageWidth - BTN_X, y + 1, COLOR_DIV);
    }
}
