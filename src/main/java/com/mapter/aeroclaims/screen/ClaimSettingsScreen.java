package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.network.ActivateClaimPacket;
import com.mapter.aeroclaims.network.AdjustBlockClaimsPacket;
import com.mapter.aeroclaims.network.DeactivateClaimPacket;
import com.mapter.aeroclaims.network.RefreshClaimPacket;
import com.mapter.aeroclaims.network.SyncClaimStatePacket;
import com.mapter.aeroclaims.network.UpdateClaimSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;

public class ClaimSettingsScreen extends AbstractContainerScreen<ClaimSettingsMenu> {

    private static final ResourceLocation BACKGROUND =
            ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "textures/screen/claim-menu.png");

    private static final int TEXTURE_W = 180;
    private static final int TEXTURE_H = 180;
    private static final int COLOR_TITLE = 0x222222;
    private static final int COLOR_TEXT  = 0x555555;
    private static final int COLOR_OK    = 0x22AA22;
    private static final int COLOR_ERR   = 0xCC3333;
    private static final int COLOR_WHITE = 0xFFFFFF;
    private static final int COLOR_DIV   = 0x66888888;

    private static final long REFRESH_COOLDOWN_MS = 30_000L;
    private static final Map<BlockPos, Long> refreshCooldowns = new HashMap<>();
    private static final Map<BlockPos, Boolean> activateUsedInCooldown = new HashMap<>();

    private static final int ROW_Y     = 116;
    private static final int SMALL_BTN = 20;
    private static final int BTN_X     = 10;
    private static final int BTN_H     = 18;

    private Button partyButton;
    private Button alliesButton;
    private Button othersButton;
    private Button refreshButton;
    private Button actionButton;
    private Button minusButton;
    private Button plusButton;


    private boolean inActivateMode = false;

    public ClaimSettingsScreen(ClaimSettingsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 30;
    }

    // --- lifecycle ---

    @Override
    protected void init() {
        super.init();

        int bw = imageWidth - BTN_X * 2;

        partyButton = Button.builder(partyText(), b -> {
            menu.setAllowParty(!menu.isAllowParty());
            b.setMessage(partyText());
            sendPermissions();
        }).bounds(leftPos + BTN_X, topPos + 24, bw, BTN_H).build();

        alliesButton = Button.builder(alliesText(), b -> {
            menu.setAllowAllies(!menu.isAllowAllies());
            b.setMessage(alliesText());
            sendPermissions();
        }).bounds(leftPos + BTN_X, topPos + 46, bw, BTN_H).build();

        othersButton = Button.builder(othersText(), b -> {
            menu.setAllowOthers(!menu.isAllowOthers());
            b.setMessage(othersText());
            sendPermissions();
        }).bounds(leftPos + BTN_X, topPos + 68, bw, BTN_H).build();

        int halfBw = (bw - 4) / 2;

        refreshButton = Button.builder(refreshText(), b -> sendRefresh())
                .bounds(leftPos + BTN_X, topPos + 90, halfBw, BTN_H).build();

        actionButton = Button.builder(activateText(), b -> sendActionButtonClick())
                .bounds(leftPos + BTN_X + halfBw + 4, topPos + 90, bw - halfBw - 4, BTN_H).build();

        updateRefreshButton();
        updateActionButton();

        int halfW = bw / 2 - 4;
        minusButton = Button.builder(Component.literal("-"), b -> sendAdjust(-1))
                .bounds(leftPos + BTN_X, topPos + ROW_Y, SMALL_BTN, BTN_H).build();
        plusButton  = Button.builder(Component.literal("+"), b -> sendAdjust(+1))
                .bounds(leftPos + BTN_X + halfW - SMALL_BTN, topPos + ROW_Y, SMALL_BTN, BTN_H).build();

        addRenderableWidget(partyButton);
        addRenderableWidget(alliesButton);
        addRenderableWidget(othersButton);
        addRenderableWidget(refreshButton);
        addRenderableWidget(actionButton);
        addRenderableWidget(minusButton);
        addRenderableWidget(plusButton);

        refreshClaimButtons();
    }

    @Override
    public void removed() {
        super.removed();
    }

    public void syncFromMenu() {
        partyButton.setMessage(partyText());
        alliesButton.setMessage(alliesText());
        othersButton.setMessage(othersText());
        updateRefreshButton();
        updateActionButton();
        refreshClaimButtons();
    }

    // rendering

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mx, int my) {
        int bw    = imageWidth - BTN_X * 2;
        int halfW = bw / 2 - 4;

        String title = Component.translatable("screen.aeroclaims.claim_settings.title").getString();
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);

        separator(g, 18);
        separator(g, 111);

        int rowTextY = ROW_Y + 5;

        int labelX = BTN_X + SMALL_BTN + 2;
        int labelW = halfW - SMALL_BTN * 2 - 4;
        String claimsText = menu.getClaimsForBlock() + " aero";
        g.drawString(font, claimsText,
                labelX + (labelW - font.width(claimsText)) / 2, rowTextY,
                COLOR_WHITE, false);

        int divX = BTN_X + halfW + 4;
        g.fill(divX, ROW_Y, divX + 1, ROW_Y + BTN_H, COLOR_DIV);

        int rightX = divX + 4;
        int rightW = bw - halfW - 8;
        String blocksText = blocksText();
        int blocksColor   = blocksOverLimit() ? COLOR_ERR : COLOR_TEXT;
        g.drawString(font, blocksText,
                rightX + (rightW - font.width(blocksText)) / 2, rowTextY,
                blocksColor, false);

        separator(g, ROW_Y + BTN_H + 3);

        int infoY = ROW_Y + BTN_H + 8;

        boolean active = menu.isClaimActive();
        String prefix = Component.translatable("screen.aeroclaims.claim_settings.privacy_prefix").getString();
        String status = Component.translatable(active
                ? "screen.aeroclaims.claim_settings.status.active"
                : "screen.aeroclaims.claim_settings.status.disabled").getString();
        g.drawString(font, prefix, BTN_X, infoY, COLOR_TEXT, false);
        g.drawString(font, status, BTN_X + font.width(prefix), infoY, active ? COLOR_OK : COLOR_ERR, false);

        try {
            String ownerName = Minecraft.getInstance()
                    .getConnection().getPlayerInfo(menu.getOwner())
                    .getProfile().getName();
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.owner", ownerName).getString(),
                    BTN_X, infoY + 12, COLOR_TEXT, false);
        } catch (Exception ignored) {}

        int nameY = infoY + 24;
        if (!menu.isOnShip()) {
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim").getString(),
                    BTN_X, nameY, COLOR_ERR, false);
        } else if (menu.getShipName() != null && !menu.getShipName().isEmpty()) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("screen.aeroclaims.claim_settings.ship", menu.getShipName()),
                    imageWidth - BTN_X * 2)) {
                g.drawString(font, line, BTN_X, nameY, COLOR_TEXT, false);
                nameY += font.lineHeight;
            }
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);

        // Restore refresh button once cooldown expires
        if (!refreshButton.active && menu.isOnShip() && !onCooldown()) {
            updateRefreshButton();
        }

        // Re-evaluate action button every frame
        if (menu.isOnShip()) {
            updateActionButton();
        }
    }

    // button state

    private void refreshClaimButtons() {
        if (!menu.isOnShip()) {
            minusButton.active = false;
            plusButton.active  = false;
            return;
        }
        plusButton.active  = menu.getFreeSlots() >= 1;
        minusButton.active = menu.getClaimsForBlock() >= 1 && canReduceByOne();
    }

    private boolean canReduceByOne() {
        if (!menu.isClaimActive()) return true;
        int count = menu.getShipBlockCount();
        if (count == SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN) return true;
        return count <= (menu.getClaimsForBlock() - 1) * menu.getBlocksPerClaim();
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

        boolean nowCooldown = onCooldown();

        if (nowCooldown) {
            if (!inActivateMode) {
                inActivateMode = true;
            }
            actionButton.setMessage(activateText());
            // Disable if already activated once this cooldown, or blocks not OK
            boolean alreadyUsed = Boolean.TRUE.equals(activateUsedInCooldown.get(menu.getCenter()));
            actionButton.active = !alreadyUsed && blocksKnownAndOk();
        } else {
            inActivateMode = false;
            actionButton.setMessage(deactivateText());
            actionButton.active = menu.isClaimActive();
        }
    }

    // actions

    private void sendPermissions() {
        PacketDistributor.sendToServer(new UpdateClaimSettingsPacket(
                menu.getCenter(), menu.isAllowParty(), menu.isAllowAllies(), menu.isAllowOthers()));
    }

    private void sendAdjust(int delta) {
        PacketDistributor.sendToServer(new AdjustBlockClaimsPacket(menu.getCenter(), delta));
        menu.setClaimsForBlock(Math.max(0, menu.getClaimsForBlock() + delta));
        menu.setFreeSlots(Math.max(0, menu.getFreeSlots() - delta));
        refreshClaimButtons();
    }

    private void sendRefresh() {
        if (onCooldown()) return;
        PacketDistributor.sendToServer(new RefreshClaimPacket(menu.getCenter()));
        refreshCooldowns.put(menu.getCenter(), System.currentTimeMillis());
        activateUsedInCooldown.put(menu.getCenter(), false); // reset for new cooldown
        refreshButton.active = false;
        refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh_wait"));
        // Immediately switch to activate mode
        inActivateMode = true;
        actionButton.setMessage(activateText());
        actionButton.active = blocksKnownAndOk();
    }

    private void sendActionButtonClick() {
        if (inActivateMode) {
            sendActivate();
        } else {
            sendDeactivate();
        }
    }

    private void sendActivate() {
        if (!onCooldown() || blocksOverLimit()) return;
        PacketDistributor.sendToServer(new ActivateClaimPacket(menu.getCenter()));
        activateUsedInCooldown.put(menu.getCenter(), true); // block until next refresh
        actionButton.active = false;
    }

    private void sendDeactivate() {
        if (onCooldown()) return;
        PacketDistributor.sendToServer(new DeactivateClaimPacket(menu.getCenter()));
        actionButton.active = false;
    }

    // helpers

    private boolean onCooldown() {
        Long last = refreshCooldowns.get(menu.getCenter());
        return last != null && System.currentTimeMillis() - last < REFRESH_COOLDOWN_MS;
    }

    private boolean blocksKnownAndOk() {
        return menu.getShipBlockCount() != SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN
                && !blocksOverLimit();
    }


    private String blocksText() {
        int count = menu.getShipBlockCount();
        if (!menu.isOnShip()) return "\u2014";
        if (count == SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN) {
            return Component.translatable("screen.aeroclaims.claim_settings.blocks_unknown").getString();
        }
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

    private Component refreshText() {
        return Component.translatable("screen.aeroclaims.claim_settings.refresh");
    }

    private Component activateText() {
        return Component.translatable("screen.aeroclaims.claim_settings.activate");
    }

    private Component deactivateText() {
        return Component.translatable("screen.aeroclaims.claim_settings.deactivate");
    }

    private Component partyText() {
        return Component.translatable(menu.isAllowParty()
                ? "screen.aeroclaims.claim_settings.party.allowed"
                : "screen.aeroclaims.claim_settings.party.denied");
    }

    private Component alliesText() {
        return Component.translatable(menu.isAllowAllies()
                ? "screen.aeroclaims.claim_settings.allies.allowed"
                : "screen.aeroclaims.claim_settings.allies.denied");
    }

    private Component othersText() {
        return Component.translatable(menu.isAllowOthers()
                ? "screen.aeroclaims.claim_settings.others.allowed"
                : "screen.aeroclaims.claim_settings.others.denied");
    }
}
