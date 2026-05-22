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
    private static final int COLOR_TITLE   = 0x222222;
    private static final int COLOR_TEXT    = 0x555555;
    private static final int COLOR_OK      = 0x22AA22;
    private static final int COLOR_ERR     = 0xCC3333;
    private static final int COLOR_WHITE   = 0xFFFFFF;
    private static final int COLOR_DIV     = 0x66888888;
    private static final int COLOR_INFO_BG = 0xCC333333;

    private static final long REFRESH_COOLDOWN_MS = 10_000L;
    // per-block cooldown tracking; activateUsed prevents double-activate within one cooldown window
    private static final Map<BlockPos, Long>    refreshCooldowns       = new HashMap<>();
    private static final Map<BlockPos, Boolean> activateUsedInCooldown = new HashMap<>();

    // layout
    private static final int BTN_X     = 10;
    private static final int BTN_H     = 18;
    private static final int SMALL_BTN = 12;
    private static final int GAP       = 10;  // spacing between elements
    private static final int INFO_Y    = 46;  // 24 (access btn top) + 18 (BTN_H) + 4 (GAP)
    private static final int INFO_PAD  = 4;   // inner text padding inside the info panel

    // access levels for the cycling button: party < party+ally < all
    private static final int ACCESS_PARTY      = 0;
    private static final int ACCESS_PARTY_ALLY = 1;
    private static final int ACCESS_ALL        = 2;

    private Button accessButton;
    private Button refreshButton;
    private Button actionButton;
    private Button minusButton;
    private Button plusButton;

    // true while we're inside a cooldown window waiting for the user to confirm activation
    private boolean inActivateMode = false;

    public ClaimSettingsScreen(ClaimSettingsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth  = TEXTURE_W;
        imageHeight = TEXTURE_H + 30;
    }

    @Override
    protected void init() {
        super.init();

        int bw    = imageWidth - BTN_X * 2;
        int halfW = bw / 2 - 4;

        accessButton = Button.builder(accessText(), b -> {
            cycleAccess();
            b.setMessage(accessText());
            sendPermissions();
        }).bounds(leftPos + BTN_X, topPos + 24, bw, BTN_H).build();

        int rowY  = INFO_Y + infoPanelHeight() + GAP;
        // left column: [-  N claims  +] on top, 0/255 text below (drawn in renderLabels)
        minusButton = Button.builder(Component.literal("-"), b -> sendAdjust(-1))
                .bounds(leftPos + BTN_X, topPos + rowY, SMALL_BTN, BTN_H).build();
        plusButton  = Button.builder(Component.literal("+"), b -> sendAdjust(+1))
                .bounds(leftPos + BTN_X + halfW - SMALL_BTN, topPos + rowY, SMALL_BTN, BTN_H).build();

        // right column: [Refresh] on top, [Activate/Deactivate] below
        int rightX = BTN_X + halfW + 8;
        int rightW = bw - halfW - 8;
        refreshButton = Button.builder(refreshText(), b -> sendRefresh())
                .bounds(leftPos + rightX, topPos + rowY, rightW, BTN_H).build();
        actionButton  = Button.builder(activateText(), b -> sendActionButtonClick())
                .bounds(leftPos + rightX, topPos + rowY + BTN_H + 2, rightW, BTN_H).build();

        updateRefreshButton();
        updateActionButton();

        addRenderableWidget(accessButton);
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

    // called from the network handler when fresh state arrives from the server
    public void syncFromMenu() {
        accessButton.setMessage(accessText());
        updateRefreshButton();
        updateActionButton();
        refreshClaimButtons();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mx, int my) {
        g.blit(BACKGROUND, leftPos, topPos, 0, 0, TEXTURE_W, TEXTURE_H, TEXTURE_W, TEXTURE_H);

        // dark panel behind the info block — same width as the access button, 4px gap above and below
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
        int bw    = imageWidth - BTN_X * 2;
        int halfW = bw / 2 - 4;
        int divX  = BTN_X + halfW + 4;

        String title = Component.translatable("screen.aeroclaims.claim_settings.title").getString();
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, COLOR_TITLE, false);

        separator(g, 18);

        // --- info block (dark bg, white text) ---
        // text is drawn with INFO_PAD offset from the panel edges
        int textX = BTN_X + INFO_PAD;
        int textW = bw - INFO_PAD * 2;
        int y = INFO_Y + INFO_PAD;

        boolean active = menu.isClaimActive();
        String prefix = Component.translatable("screen.aeroclaims.claim_settings.privacy_prefix").getString();
        String status  = Component.translatable(active
                ? "screen.aeroclaims.claim_settings.status.active"
                : "screen.aeroclaims.claim_settings.status.disabled").getString();
        g.drawString(font, prefix, textX, y, COLOR_WHITE, false);
        g.drawString(font, status, textX + font.width(prefix), y, active ? COLOR_OK : COLOR_ERR, false);
        y += font.lineHeight + 2;

        try {
            String ownerName = Minecraft.getInstance()
                    .getConnection().getPlayerInfo(menu.getOwner())
                    .getProfile().getName();
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.owner", ownerName).getString(),
                    textX, y, COLOR_WHITE, false);
            y += font.lineHeight + 2;
        } catch (Exception ignored) {}

        if (!menu.isOnShip()) {
            g.drawString(font,
                    Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim").getString(),
                    textX, y, COLOR_ERR, false);
        } else if (menu.getShipName() != null && !menu.getShipName().isEmpty()) {
            for (FormattedCharSequence line : font.split(
                    Component.translatable("screen.aeroclaims.claim_settings.ship", menu.getShipName()),
                    textW)) {
                g.drawString(font, line, textX, y, COLOR_WHITE, false);
                y += font.lineHeight;
            }
        }

        // --- claims / blocks column ---
        int rowY = INFO_Y + infoPanelHeight() + GAP;
        separator(g, INFO_Y + infoPanelHeight() + GAP / 2);

        // vertical divider between left and right columns
        g.fill(divX, rowY, divX + 1, rowY + BTN_H * 2 + 2, COLOR_DIV);

        // left column, top: "N claims" centered between - and +
        int labelX = BTN_X + SMALL_BTN + 2;
        int labelW = halfW - SMALL_BTN * 2 - 4;
        String claimsText = menu.getClaimsForBlock() + " claims";
        g.drawString(font, claimsText,
                labelX + (labelW - font.width(claimsText)) / 2, rowY + 5,
                COLOR_TEXT, false);

        // left column, bottom: block usage — dark bg, same horizontal bounds as - and + buttons
        int blocksBoxX = BTN_X;
        int blocksBoxW = halfW;
        int blocksBoxY = rowY + BTN_H + 2;
        int blocksBoxH = BTN_H;
        g.fill(blocksBoxX, blocksBoxY, blocksBoxX + blocksBoxW, blocksBoxY + blocksBoxH, COLOR_INFO_BG);
        String blocksText  = blocksText();
        int    blocksColor = blocksOverLimit() ? COLOR_ERR : COLOR_WHITE;
        g.drawString(font, blocksText,
                blocksBoxX + (blocksBoxW - font.width(blocksText)) / 2,
                blocksBoxY + (blocksBoxH - font.lineHeight) / 2,
                blocksColor, false);

        separator(g, rowY + BTN_H + 2 + BTN_H + GAP / 2);
    }

    // calculates the pixel height of the info panel so renderBg can size it dynamically
    private int infoPanelHeight() {
        int lines = 2; // claim status + owner
        if (!menu.isOnShip()) {
            lines += 1;
        } else if (menu.getShipName() != null && !menu.getShipName().isEmpty()) {
            int textW = imageWidth - BTN_X * 2 - INFO_PAD * 2;
            lines += Math.max(1, (menu.getShipName().length() * 6) / textW + 1);
        }
        return INFO_PAD * 2 + lines * (font.lineHeight + 2);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        renderBackground(g, mx, my, partialTick);
        super.render(g, mx, my, partialTick);
        renderTooltip(g, mx, my);

        // re-enable refresh once the cooldown window closes
        if (!refreshButton.active && menu.isOnShip() && !onCooldown()) {
            updateRefreshButton();
        }

        if (menu.isOnShip()) {
            updateActionButton();
        }
    }

    private void refreshClaimButtons() {
        if (!menu.isOnShip()) {
            minusButton.active = false;
            plusButton.active  = false;
            return;
        }
        plusButton.active  = menu.getFreeSlots() >= 1;
        minusButton.active = menu.getClaimsForBlock() >= 1 && canReduceByOne();
    }

    // can't reduce below the current ship size
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

        if (onCooldown()) {
            inActivateMode = true;
            actionButton.setMessage(activateText());
            // block if already activated once this cooldown, or ship size isn't confirmed yet
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
        if (!onCooldown() || blocksOverLimit()) return;
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

    // ship size is known and fits within the allocated claims
    private boolean blocksKnownAndOk() {
        return menu.getShipBlockCount() != SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN
                && !blocksOverLimit();
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