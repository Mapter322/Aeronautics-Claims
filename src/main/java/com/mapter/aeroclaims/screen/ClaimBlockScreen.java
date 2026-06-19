package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.network.ActivateClaimPacket;
import com.mapter.aeroclaims.network.DeactivateClaimPacket;
import com.mapter.aeroclaims.network.RefreshClaimPacket;
import com.mapter.aeroclaims.network.NavigateMenuPacket;
import com.mapter.aeroclaims.network.RenameShipPacket;
import com.mapter.aeroclaims.network.SyncClaimStatePacket;
import com.mapter.aeroclaims.network.UpdateClaimSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class ClaimBlockScreen extends AbstractContainerScreen<ClaimBlockMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "textures/screen/claim-menu.png");

    private static final int TEXTURE_W = 180;
    private static final int TEXTURE_H = 180;
    private static final int COLOR_TITLE   = 0x222222;
    private static final int COLOR_TEXT    = 0x555555;
    private static final int COLOR_OK      = 0x22AA22;
    private static final int COLOR_ERR     = 0xCC3333;
    private static final int COLOR_WHITE   = 0xFFFFFF;
    private static final int COLOR_DIV     = 0x66888888;
    private static final int COLOR_INFO_BG = 0xCC333333;
    private static final int CLOSE_X = 8;
    private static final int CLOSE_Y = 7;
    private static final int CLOSE_SIZE = 10;

    private static final long REFRESH_COOLDOWN_MS = 10_000L;
    private static final Map<BlockPos, Long>    refreshCooldowns       = new HashMap<>();
    private static final Map<BlockPos, Boolean> activateUsedInCooldown = new HashMap<>();

    // layout
    private static final int BTN_X    = 10;
    private static final int BTN_H    = 18;
    private static final int GAP      = 10;
    private static final int INFO_Y   = 46;
    private static final int INFO_PAD = 4;

    private static final int ACCESS_PARTY      = 0;
    private static final int ACCESS_PARTY_ALLY = 1;
    private static final int ACCESS_ALL        = 2;

    private Button accessButton;
    private Button refreshButton;
    private Button actionButton;

    private EditBox renameBox;
    private boolean editing;
    private String editOriginal;
    private int shipNameY, shipNameH;

    private boolean inActivateMode = false;

    public ClaimBlockScreen(ClaimBlockMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 10;
    }

    @Override
    protected void init() {
        super.init();
        CursorHelper.restoreCursor();

        int bw    = imageWidth - BTN_X * 2;
        int halfW = bw / 2 - 2;

        accessButton = Button.builder(accessText(), b -> {
            cycleAccess();
            b.setMessage(accessText());
            sendPermissions();
        }).bounds(leftPos + BTN_X, topPos + 24, bw, BTN_H).build();

        int rowY = INFO_Y + infoPanelHeight() + GAP;

        refreshButton = Button.builder(refreshText(), b -> sendRefresh())
                .bounds(leftPos + BTN_X, topPos + rowY, halfW, BTN_H).build();
        actionButton  = Button.builder(activateText(), b -> sendActionButtonClick())
                .bounds(leftPos + BTN_X + halfW + 4, topPos + rowY, halfW, BTN_H).build();

        updateRefreshButton();
        updateActionButton();

        int aeroMenuBtnY = rowY + BTN_H + GAP + 3;
        Button aeroMenuButton = Button.builder(
                Component.translatable("screen.aeroclaims.menu.title"),
                b -> {
                    CursorHelper.saveCursor();
                    PacketDistributor.sendToServer(NavigateMenuPacket.toAeroMenu());
                })
                .bounds(leftPos + BTN_X, topPos + aeroMenuBtnY, bw, BTN_H).build();

        addRenderableWidget(accessButton);
        addRenderableWidget(refreshButton);
        addRenderableWidget(actionButton);
        addRenderableWidget(aeroMenuButton);

        renameBox = new EditBox(font, leftPos + BTN_X + INFO_PAD, topPos + INFO_Y, imageWidth - BTN_X * 2 - INFO_PAD * 2, font.lineHeight + 2, Component.empty());
        renameBox.setMaxLength(48);
        renameBox.setBordered(false);
        renameBox.setTextColor(COLOR_WHITE);
        renameBox.visible = false;
        addWidget(renameBox);
    }

    public void syncFromMenu() {
        accessButton.setMessage(accessText());
        updateRefreshButton();
        updateActionButton();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);

        int panelH = infoPanelHeight();
        int bw = imageWidth - BTN_X * 2;
        g.fill(leftPos + BTN_X,
                topPos  + INFO_Y,
                leftPos + BTN_X + bw,
                topPos  + INFO_Y + panelH,
                COLOR_INFO_BG);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int bw   = imageWidth - BTN_X * 2;

        int relMx = mx - leftPos;
        int relMy = my - topPos;
        boolean closeHovered = relMx >= CLOSE_X && relMx < CLOSE_X + CLOSE_SIZE
                && relMy >= CLOSE_Y && relMy < CLOSE_Y + CLOSE_SIZE;
        g.drawString(font, "\u2715", CLOSE_X, CLOSE_Y, closeHovered ? 0xFFFFFF : 0xAAAAAA, false);

        String title = Component.translatable("screen.aeroclaims.claim_settings.title").getString();
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);

        separator(g, 18);

        // --- info block ---
        int textX = BTN_X + INFO_PAD;
        int textW = bw - INFO_PAD * 2;
        int y = INFO_Y + INFO_PAD;

        // Status
        boolean active = menu.isClaimActive();
        String prefix = Component.translatable("screen.aeroclaims.claim_settings.privacy_prefix").getString();
        String status  = Component.translatable(active
                ? "screen.aeroclaims.claim_settings.status.active"
                : "screen.aeroclaims.claim_settings.status.disabled").getString();
        g.drawString(font, prefix, textX, y, COLOR_WHITE, false);
        g.drawString(font, status, textX + font.width(prefix), y, active ? COLOR_OK : COLOR_ERR, false);
        y += font.lineHeight + 2;

        // Owner
        try {
            String ownerName = Minecraft.getInstance()
                    .getConnection().getPlayerInfo(menu.getOwner())
                    .getProfile().getName();
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.owner", ownerName).getString(),
                    textX, y, COLOR_WHITE, false);
            y += font.lineHeight + 2;
        } catch (Exception ignored) {}

        // Ship name
        if (!menu.isOnShip()) {
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim").getString(),
                    textX, y, COLOR_ERR, false);
            y += font.lineHeight + 2;
        } else if (menu.getShipName() != null && !menu.getShipName().isEmpty()) {
            shipNameY = y;
            if (!editing) {
                for (FormattedCharSequence line : font.split(
                        Component.translatable("screen.aeroclaims.claim_settings.ship", menu.getShipName()),
                        textW)) {
                    g.drawString(font, line, textX, y, COLOR_WHITE, false);
                    y += font.lineHeight;
                }
                shipNameH = y - shipNameY;
            } else {
                String namePrefix = Component.translatable("screen.aeroclaims.claim_settings.ship", "").getString();
                g.drawString(font, namePrefix, textX, y, COLOR_WHITE, false);
                y += font.lineHeight;
                shipNameH = font.lineHeight;
            }
            y += 2;
        }

        // Claims used / needed
        int usedClaims   = menu.getClaimsForBlock();
        int neededClaims = neededClaimsCount();
        int claimsColor  = (neededClaims > usedClaims) ? COLOR_ERR : COLOR_OK;
        String claimsLabel  = Component.translatable("screen.aeroclaims.claim_settings.claims_label").getString();
        String claimsValues = usedClaims + " / " + neededClaims;
        g.drawString(font, claimsLabel, textX, y, COLOR_WHITE, false);
        g.drawString(font, claimsValues, textX + font.width(claimsLabel), y, claimsColor, false);
        y += font.lineHeight + 2;

        // Block count
        String blocksLine = blocksText();
        int blocksColor = blocksOverLimit() ? COLOR_ERR : COLOR_WHITE;
        g.drawString(font, blocksLine, textX, y, blocksColor, false);

        // separator above buttons
        int rowY = INFO_Y + infoPanelHeight() + GAP;
        separator(g, INFO_Y + infoPanelHeight() + GAP / 2);
        separator(g, rowY + BTN_H + GAP / 2);
    }

    private int infoPanelHeight() {
        int lines = 2; // status + owner
        if (!menu.isOnShip()) {
            lines += 1; // not on ship
        } else if (menu.getShipName() != null && !menu.getShipName().isEmpty()) {
            int textW = imageWidth - BTN_X * 2 - INFO_PAD * 2;
            lines += Math.max(1, (menu.getShipName().length() * 6) / textW + 1);
        }
        lines += 2; // claims used/needed + block count
        return INFO_PAD * 2 + lines * (font.lineHeight + 2);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);

        if (editing) {
            renameBox.setY(topPos + shipNameY);
            renameBox.renderWidget(g, mx, my, partialTick);
        }

        if (!refreshButton.active && menu.isOnShip() && !onCooldown()) {
            updateRefreshButton();
        }
        if (menu.isOnShip()) {
            updateActionButton();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double relMx = mx - leftPos;
        double relMy = my - topPos;
        if (relMx >= CLOSE_X && relMx < CLOSE_X + CLOSE_SIZE
                && relMy >= CLOSE_Y && relMy < CLOSE_Y + CLOSE_SIZE) {
            onClose();
            return true;
        }
        if (editing) {
            if (!renameBox.isMouseOver(mx, my)) { confirmRename(); return true; }
            return renameBox.mouseClicked(mx, my, button);
        }
        if (isOverShipName(mx, my)) { startEditing(); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editing) {
            if (keyCode == 256) { stopEditing(); return true; }
            if (keyCode == 257 || keyCode == 335) { confirmRename(); return true; }
            renameBox.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return editing ? renameBox.charTyped(c, modifiers) : super.charTyped(c, modifiers);
    }

    private boolean isOverShipName(double mx, double my) {
        if (!menu.isOnShip() || menu.getShipName() == null || menu.getShipName().isEmpty()) return false;
        int x0 = leftPos + BTN_X + INFO_PAD, y0 = topPos + shipNameY;
        return mx >= x0 && mx <= x0 + imageWidth - BTN_X * 2 - INFO_PAD * 2 && my >= y0 && my <= y0 + shipNameH;
    }

    private void startEditing() {
        editing = true;
        editOriginal = menu.getShipName();
        int prefixW = font.width(Component.translatable("screen.aeroclaims.claim_settings.ship", "").getString());
        renameBox.setValue(editOriginal);
        renameBox.setX(leftPos + BTN_X + INFO_PAD + prefixW);
        renameBox.setY(topPos + shipNameY);
        renameBox.setWidth(imageWidth - BTN_X * 2 - INFO_PAD * 2 - prefixW);
        renameBox.visible = true;
        renameBox.setFocused(true);
        setFocused(renameBox);
    }

    private void confirmRename() {
        String newName = renameBox.getValue().trim();
        if (!newName.isEmpty() && !newName.equals(editOriginal)) {
            menu.setShipName(newName);
            PacketDistributor.sendToServer(new RenameShipPacket(menu.getCenter(), newName));
        }
        stopEditing();
    }

    private void stopEditing() {
        editing = false;
        renameBox.visible = false;
        renameBox.setFocused(false);
        setFocused(null);
    }

    private void updateRefreshButton() {
        if (!menu.isOnShip()) {
            refreshButton.active = false;
            refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim"));
        } else if (onCooldown()) {
            refreshButton.active = false;
            refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh_wait"));
        } else {
            refreshButton.active = true;
            refreshButton.setMessage(refreshText());
        }
    }

    private void updateActionButton() {
        if (!menu.isOnShip()) {
            inActivateMode = false;
            actionButton.active = false;
            actionButton.setMessage(activateText());
            return;
        }

        if (onCooldown()) {
            inActivateMode = true;
            actionButton.setMessage(activateText());
            boolean alreadyUsed = Boolean.TRUE.equals(activateUsedInCooldown.get(menu.getCenter()));
            actionButton.active = !alreadyUsed && blocksKnownAndOk();
        } else {
            inActivateMode = false;
            actionButton.setMessage(deactivateText());
            actionButton.active = menu.isClaimActive();
        }
    }

    private int currentAccessLevel() {
        if (menu.isAllowOthers()) return ACCESS_ALL;
        if (menu.isAllowAllies()) return ACCESS_PARTY_ALLY;
        return ACCESS_PARTY;
    }

    private void cycleAccess() {
        int next = (currentAccessLevel() + 1) % 3;
        menu.setAllowParty(true);
        menu.setAllowAllies(next >= ACCESS_PARTY_ALLY);
        menu.setAllowOthers(next == ACCESS_ALL);
    }

    private Component accessText() {
        String levelKey = switch (currentAccessLevel()) {
            case ACCESS_PARTY_ALLY -> "screen.aeroclaims.claim_settings.access.party_ally";
            case ACCESS_ALL        -> "screen.aeroclaims.claim_settings.access.all";
            default                -> "screen.aeroclaims.claim_settings.access.party";
        };
        return Component.translatable("screen.aeroclaims.claim_settings.access_label",
                Component.translatable(levelKey).getString());
    }

    private void sendPermissions() {
        PacketDistributor.sendToServer(new UpdateClaimSettingsPacket(
                menu.getCenter(), menu.isAllowParty(), menu.isAllowAllies(), menu.isAllowOthers()));
    }

    private void sendRefresh() {
        if (onCooldown()) return;
        PacketDistributor.sendToServer(new RefreshClaimPacket(menu.getCenter()));
        refreshCooldowns.put(menu.getCenter(), System.currentTimeMillis());
        activateUsedInCooldown.put(menu.getCenter(), false);
        refreshButton.active = false;
        refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh_wait"));
        inActivateMode = true;
        actionButton.setMessage(activateText());
        actionButton.active = blocksKnownAndOk();
    }

    private void sendActionButtonClick() {
        if (inActivateMode) sendActivate();
        else                sendDeactivate();
    }

    private void sendActivate() {
        if (!onCooldown()) return;
        PacketDistributor.sendToServer(new ActivateClaimPacket(menu.getCenter()));
        activateUsedInCooldown.put(menu.getCenter(), true);
        actionButton.active = false;
    }

    private void sendDeactivate() {
        if (onCooldown()) return;
        PacketDistributor.sendToServer(new DeactivateClaimPacket(menu.getCenter()));
        actionButton.active = false;
    }

    private boolean onCooldown() {
        Long last = refreshCooldowns.get(menu.getCenter());
        return last != null && System.currentTimeMillis() - last < REFRESH_COOLDOWN_MS;
    }

    private boolean blocksKnownAndOk() {
        return menu.getShipBlockCount() != SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN;
    }

    private int neededClaimsCount() {
        int count = menu.getShipBlockCount();
        if (count == SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN || count <= 0) return 0;
        int bpc = menu.getBlocksPerClaim();
        if (bpc <= 0) return 0;
        return (count + bpc - 1) / bpc;
    }

    private String blocksText() {
        int count = menu.getShipBlockCount();
        if (!menu.isOnShip()) return "\u2014";
        if (count == SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN)
            return Component.translatable("screen.aeroclaims.claim_settings.blocks_unknown").getString();
        int limit = menu.getBlockLimit();
        return Component.translatable("screen.aeroclaims.claim_settings.blocks_usage", count, limit).getString();
    }

    private boolean blocksOverLimit() {
        int count = menu.getShipBlockCount();
        int limit = menu.getBlockLimit();
        return menu.isOnShip()
                && count != SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN
                && limit > 0
                && count > limit;
    }

    private void separator(GuiGraphics g, int y) {
        g.fill(BTN_X, y, imageWidth - BTN_X, y + 1, COLOR_DIV);
    }

    private Component refreshText()    { return Component.translatable("screen.aeroclaims.claim_settings.refresh"); }
    private Component activateText()   { return Component.translatable("screen.aeroclaims.claim_settings.activate"); }
    private Component deactivateText() { return Component.translatable("screen.aeroclaims.claim_settings.deactivate"); }
}
