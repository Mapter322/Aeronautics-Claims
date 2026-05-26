package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.network.TransferSlotsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AeroClaimsMenuScreen extends AbstractContainerScreen<AeroClaimsMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "textures/screen/claim-menu.png");

    private static final int TEXTURE_W = 180;
    private static final int TEXTURE_H = 180;
    private static final int COLOR_TITLE   = 0x222222;
    private static final int COLOR_DIV     = 0x66888888;
    private static final int COLOR_WHITE   = 0xFFFFFF;
    private static final int COLOR_INFO_BG = 0xCC333333;
    private static final int COLOR_HOVER   = 0x44FFFFFF;
    private static final int BTN_X      = 10;
    private static final int PAD        = 4;

    private static final int LIST_Y  = 20;
    private static final int LIST_H  = 80;
    private static final int ENTRY_H = 12;

    private static final int INFO_Y    = LIST_Y + LIST_H + 8;
    private static final int INFO_H    = PAD + ENTRY_H * 3 + PAD;
    private static final int BTN_W     = 14;
    private static final int BTN_H     = 12;
    private static final int COLOR_TEXT = 0xDDDDDD;

    private Button btnPlusSlot;
    private Button btnMinusSlot;

    private int scrollOffset = 0;

    public AeroClaimsMenuScreen(AeroClaimsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 10;
    }

    @Override
    protected void init() {
        super.init();
        scrollOffset = 0;

        int boxRight  = leftPos + imageWidth - BTN_X;
        int freeLineY = topPos + INFO_Y + PAD + ENTRY_H * 2 + (ENTRY_H - BTN_H) / 2;

        btnMinusSlot = Button.builder(Component.literal("-"), b ->
                PacketDistributor.sendToServer(new TransferSlotsPacket(false)))
                .bounds(boxRight - PAD - BTN_W * 2 - 2, freeLineY, BTN_W, BTN_H)
                .build();
        btnPlusSlot = Button.builder(Component.literal("+"), b ->
                PacketDistributor.sendToServer(new TransferSlotsPacket(true)))
                .bounds(boxRight - PAD - BTN_W, freeLineY, BTN_W, BTN_H)
                .build();

        addRenderableWidget(btnPlusSlot);
        addRenderableWidget(btnMinusSlot);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);

        int bw = imageWidth - BTN_X * 2;
        g.fill(leftPos + BTN_X, topPos + LIST_Y,
                leftPos + BTN_X + bw, topPos + LIST_Y + LIST_H,
                COLOR_INFO_BG);
        g.fill(leftPos + BTN_X, topPos + INFO_Y,
                leftPos + BTN_X + bw, topPos + INFO_Y + INFO_H,
                COLOR_INFO_BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        renderShipList(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        String title = Component.translatable("screen.aeroclaims.menu.title").getString();
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);
        separator(g, 18);
        separator(g, LIST_Y + LIST_H + 1);
        renderInfoPanel(g);
    }

    private void renderInfoPanel(GuiGraphics g) {
        int x = BTN_X + PAD;
        int y = INFO_Y + PAD;
        int opacFree  = menu.getOpacFree();
        int aeroTotal = menu.getAeroTotal();
        int aeroUsed  = menu.getAeroUsed();
        int aeroFree  = aeroTotal - aeroUsed;

        String opacLine = Component.translatable("screen.aeroclaims.menu.info.opac", opacFree).getString();
        g.drawString(font, opacLine, x, y, COLOR_TEXT, false);

        String aeroLine = Component.translatable("screen.aeroclaims.menu.info.aero",
                aeroUsed, aeroTotal).getString();
        g.drawString(font, aeroLine, x, y + ENTRY_H, COLOR_TEXT, false);

        String aeroFreeLine = Component.translatable("screen.aeroclaims.menu.info.aero_free",
                aeroFree).getString();
        g.drawString(font, aeroFreeLine, x, y + ENTRY_H * 2, COLOR_TEXT, false);
    }

    private void renderShipList(GuiGraphics g, int mx, int my) {
        List<AeroClaimsMenu.ShipEntry> ships = menu.getShips();

        int listX = leftPos + BTN_X;
        int listY = topPos + LIST_Y;
        int bw = imageWidth - BTN_X * 2;
        int textX = listX + PAD;
        int textW = bw - PAD * 2 - 4;

        if (ships.isEmpty()) {
            String empty = Component.translatable("screen.aeroclaims.menu.no_ships").getString();
            g.drawString(font, empty,
                    leftPos + (imageWidth - font.width(empty)) / 2,
                    listY + LIST_H / 2 - font.lineHeight / 2,
                    COLOR_WHITE, false);
            return;
        }

        g.enableScissor(listX, listY, listX + bw, listY + LIST_H);

        int hoveredIndex = -1;

        for (int i = 0; i < ships.size(); i++) {
            int entryY = listY + PAD + i * ENTRY_H - scrollOffset;
            if (entryY + ENTRY_H < listY || entryY > listY + LIST_H) continue;

            AeroClaimsMenu.ShipEntry ship = ships.get(i);
            boolean hovered = mx >= listX && mx < listX + bw
                    && my >= Math.max(entryY, listY) && my < Math.min(entryY + ENTRY_H, listY + LIST_H);

            if (hovered) {
                hoveredIndex = i;
                g.fill(listX, entryY, listX + bw, entryY + ENTRY_H, COLOR_HOVER);
            }

            int indicatorColor = ship.active() ? 0xFF22AA22 : 0xFFCC3333;
            g.fill(textX, entryY + 3, textX + 5, entryY + 8, indicatorColor);

            String name = font.plainSubstrByWidth(ship.shipName(), textW - 8);
            g.drawString(font, name, textX + 8, entryY + 2, COLOR_WHITE, false);
        }

        g.disableScissor();

        int totalH = PAD * 2 + ships.size() * ENTRY_H;
        if (totalH > LIST_H) {
            int maxScroll = totalH - LIST_H;
            int scrollbarH = Math.max(10, LIST_H * LIST_H / totalH);
            int scrollbarY = listY + (int) ((LIST_H - scrollbarH) * ((double) scrollOffset / maxScroll));
            g.fill(listX + bw - 3, scrollbarY, listX + bw - 1, scrollbarY + scrollbarH, 0x88AAAAAA);
        }

        if (hoveredIndex >= 0) {
            List<Component> tooltip = buildTooltip(ships.get(hoveredIndex));
            g.renderTooltip(font, tooltip, Optional.empty(), mx, my);
        }
    }

    private List<Component> buildTooltip(AeroClaimsMenu.ShipEntry ship) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.id", ship.shipId())
                .withStyle(ChatFormatting.GRAY));
        String statusLabel = Component.translatable("screen.aeroclaims.menu.tooltip.status",
                Component.translatable(ship.active()
                        ? "screen.aeroclaims.claim_settings.status.active"
                        : "screen.aeroclaims.claim_settings.status.disabled").getString()).getString();
        lines.add(Component.literal(statusLabel)
                .withStyle(ship.active() ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.claims", ship.claims())
                .withStyle(ChatFormatting.GRAY));
        if (ship.blockCount() >= 0) {
            lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.blocks",
                    ship.blockCount(), ship.blockLimit())
                    .withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.blocks_unknown")
                    .withStyle(ChatFormatting.GRAY));
        }
        if (ship.hasCoords()) {
            lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.coords",
                    ship.worldX(), ship.worldY(), ship.worldZ())
                    .withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        List<AeroClaimsMenu.ShipEntry> ships = menu.getShips();
        int totalH = PAD * 2 + ships.size() * ENTRY_H;
        if (totalH > LIST_H) {
            int maxScroll = totalH - LIST_H;
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) (scrollY * ENTRY_H), maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void separator(GuiGraphics g, int y) {
        g.fill(BTN_X, y, imageWidth - BTN_X, y + 1, COLOR_DIV);
    }
}
