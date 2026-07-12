package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import net.minecraft.client.Minecraft;
import com.mapter.aeroclaims.network.NavigateMenuPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
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

    private static final int LABEL_H      = 13;
    private static final int LIST_LABEL_Y  = 22;
    private static final int LIST_Y        = LIST_LABEL_Y + LABEL_H;
    private static final int LIST_H        = 78;
    private static final int ENTRY_H       = 12;

    private static final int INFO_LABEL_Y  = LIST_Y + LIST_H + 4;
    private static final int INFO_Y        = INFO_LABEL_Y + LABEL_H;
    private static final int INFO_H        = PAD + ENTRY_H * 2 + PAD + 7;
    private static final int COLOR_TEXT      = 0xDDDDDD;
    private static final int CLOSE_X = 8;
    private static final int CLOSE_Y = 7;
    private static final int CLOSE_SIZE = 10;

    private int scrollOffset = 0;

    private final ContextMenu contextMenu = new ContextMenu();

    public AeroClaimsMenuScreen(AeroClaimsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 10;
    }

    @Override
    protected void init() {
        super.init();
        CursorHelper.restoreCursor();
        scrollOffset = 0;
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
        contextMenu.render(g, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        int relMx = mouseX - leftPos;
        int relMy = mouseY - topPos;
        boolean closeHovered = relMx >= CLOSE_X && relMx < CLOSE_X + CLOSE_SIZE
                && relMy >= CLOSE_Y && relMy < CLOSE_Y + CLOSE_SIZE;
        g.drawString(font, "\u2715", CLOSE_X, CLOSE_Y, closeHovered ? 0xFFFFFF : 0xAAAAAA, false);

        String title = Component.translatable("screen.aeroclaims.menu.title").getString();
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);
        separator(g, 18);

        String listLabel = Component.translatable("screen.aeroclaims.menu.label.ships").getString();
        g.drawString(font, listLabel, BTN_X + PAD, LIST_LABEL_Y + 2, COLOR_TITLE, false);

        String infoLabel = Component.translatable("screen.aeroclaims.menu.label.info").getString();
        g.drawString(font, infoLabel, BTN_X + PAD, INFO_LABEL_Y + 2, COLOR_TITLE, false);

        renderInfoPanel(g);
    }

    private void renderInfoPanel(GuiGraphics g) {
        int x = BTN_X + PAD;
        int y = INFO_Y + PAD;

        String aeroLabel = Component.translatable("screen.aeroclaims.menu.info.aero_claims.label").getString();
        g.drawString(font, aeroLabel, x, y, COLOR_TEXT, false);
        g.drawString(font, String.valueOf(menu.getAeroUsed()), x + font.width(aeroLabel), y, COLOR_WHITE, false);

        boolean showForceloads = AeroClaimsConfig.PROVIDER_SLOTS_FORCELOAD.get();
        if (showForceloads) {
            String flLabel = Component.translatable("screen.aeroclaims.menu.info.forceloads.label").getString();
            g.drawString(font, flLabel, x, y + ENTRY_H, COLOR_TEXT, false);
            g.drawString(font, String.valueOf(menu.getForceloadUsed()),
                    x + font.width(flLabel), y + ENTRY_H, COLOR_WHITE, false);
        }

        int line2 = showForceloads ? ENTRY_H * 2 : ENTRY_H;
        String sublevelsLabel = Component.translatable("screen.aeroclaims.menu.info.sublevels.label").getString();
        g.drawString(font, sublevelsLabel, x, y + line2, COLOR_TEXT, false);
        g.drawString(font, String.valueOf(menu.getShips().size()),
                x + font.width(sublevelsLabel), y + line2, COLOR_WHITE, false);
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
                if (!contextMenu.isVisible()) {
                    g.fill(listX, entryY, listX + bw, entryY + ENTRY_H, COLOR_HOVER);
                }
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

        if (hoveredIndex >= 0 && !contextMenu.isVisible()) {
            List<Component> tooltip = buildTooltip(ships.get(hoveredIndex));
            g.renderTooltip(font, tooltip, Optional.empty(), mx, my);
        }
    }

    private List<Component> buildTooltip(AeroClaimsMenu.ShipEntry ship) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.aeroclaims.menu.tooltip.name", ship.shipName())
                .withStyle(ChatFormatting.GRAY));
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
    public boolean mouseClicked(double mx, double my, int button) {

        if (contextMenu.isVisible()) {
            return contextMenu.mouseClicked(mx, my, button);
        }

        double relMx = mx - leftPos;
        double relMy = my - topPos;


        if (relMx >= CLOSE_X && relMx < CLOSE_X + CLOSE_SIZE
                && relMy >= CLOSE_Y && relMy < CLOSE_Y + CLOSE_SIZE) {
            onClose();
            return true;
        }

        if (button == 0 || button == 1) {
            int listX = leftPos + BTN_X;
            int listY = topPos + LIST_Y;
            int bw = imageWidth - BTN_X * 2;
            if (mx >= listX && mx < listX + bw && my >= listY && my < listY + LIST_H) {
                int relY = (int) my - listY - PAD + scrollOffset;
                int index = relY / ENTRY_H;
                List<AeroClaimsMenu.ShipEntry> ships = menu.getShips();
                if (index >= 0 && index < ships.size()) {
                    AeroClaimsMenu.ShipEntry ship = ships.get(index);
                    openContextMenuForShip(ship, (int) mx, (int) my);
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, button);
    }

    private void openContextMenuForShip(AeroClaimsMenu.ShipEntry ship, int screenX, int screenY) {
        contextMenu.dismiss();
        ContextMenu menu = this.contextMenu;
        buildContextMenu(ship);
        contextMenu.open(screenX, screenY);
    }

    private void buildContextMenu(AeroClaimsMenu.ShipEntry ship) {
        contextMenu.clearItems();
        contextMenu.addItem("screen.aeroclaims.menu.context.copy_name", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(ship.shipName());
        });
        contextMenu.addItem("screen.aeroclaims.menu.context.copy_uuid", () -> {
            Minecraft.getInstance().keyboardHandler.setClipboard(ship.shipId());
        });
        if (ship.hasCoords()) {
            contextMenu.addItem("screen.aeroclaims.menu.context.copy_pos", () -> {
                String pos = ship.worldX() + " " + ship.worldY() + " " + ship.worldZ();
                Minecraft.getInstance().keyboardHandler.setClipboard(pos);
            });
        }
    }

    @Override
    public void onClose() {
        BlockPos returnPos = menu.getReturnClaimPos();
        if (returnPos != null) {
            CursorHelper.saveCursor();
            PacketDistributor.sendToServer(NavigateMenuPacket.backToClaim(returnPos));
        } else {
            super.onClose();
        }
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
