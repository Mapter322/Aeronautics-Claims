package com.mapter.aeroclaims.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;


public class ContextMenu {

    private static final int BG_COLOR      = 0xF0111111;
    private static final int BORDER_COLOR  = 0xFF555555;
    private static final int HOVER_COLOR   = 0x55FFFFFF;
    private static final int TEXT_COLOR    = 0xFFDDDDDD;
    private static final int PAD_X         = 6;
    private static final int PAD_Y         = 3;
    private static final int ITEM_H        = 12;

    public record MenuItem(String labelKey, Runnable action) {}

    private final List<MenuItem> items = new ArrayList<>();
    private boolean visible = false;
    private int menuX;
    private int menuY;

    private int menuW;
    private int menuH;

    public void addItem(String translationKey, Runnable action) {
        items.add(new MenuItem(translationKey, action));
    }

    public void clearItems() {
        items.clear();
    }

    public void open(int x, int y) {
        this.visible = true;
        Font font = Minecraft.getInstance().font;
        int maxW = 0;
        for (MenuItem item : items) {
            maxW = Math.max(maxW, font.width(Component.translatable(item.labelKey()).getString()));
        }
        menuW = maxW + PAD_X * 2;
        menuH = PAD_Y * 2 + items.size() * ITEM_H;

        this.menuX = x - menuW;
        this.menuY = y;
    }

    public void dismiss() {
        visible = false;
    }

    public boolean isVisible() {
        return visible;
    }



    public void render(GuiGraphics g, int mouseX, int mouseY) {
        if (!visible) return;
        Font font = Minecraft.getInstance().font;

        g.fill(menuX - 1, menuY - 1, menuX + menuW + 1, menuY + menuH + 1, BORDER_COLOR);
        g.fill(menuX, menuY, menuX + menuW, menuY + menuH, BG_COLOR);

        for (int i = 0; i < items.size(); i++) {
            int itemY = menuY + PAD_Y + i * ITEM_H;
            boolean hovered = mouseX >= menuX && mouseX < menuX + menuW
                    && mouseY >= itemY && mouseY < itemY + ITEM_H;
            if (hovered) {
                g.fill(menuX, itemY, menuX + menuW, itemY + ITEM_H, HOVER_COLOR);
            }
            String label = Component.translatable(items.get(i).labelKey()).getString();
            g.drawString(font, label, menuX + PAD_X, itemY + 2, TEXT_COLOR, false);
        }
    }


    public boolean mouseClicked(double mx, double my, int button) {
        if (!visible) return false;

        if (mx < menuX || mx >= menuX + menuW || my < menuY || my >= menuY + menuH) {
            dismiss();
            return true;
        }

        if (button == 0) {
            int relY = (int) my - menuY - PAD_Y;
            int index = relY / ITEM_H;
            if (index >= 0 && index < items.size()) {
                items.get(index).action().run();
            }
            dismiss();
            return true;
        }
        return true;
    }
}
