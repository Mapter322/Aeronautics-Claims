package com.mapter.aeroclaims.screen;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.network.RefreshClaimPacket;
import com.mapter.aeroclaims.network.UpdateClaimSettingsPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class ClaimSettingsScreen extends AbstractContainerScreen<ClaimSettingsMenu> {

    private static final ResourceLocation BACKGROUND_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Aeroclaims.MODID, "textures/screen/claim-menu.png");

    private static final int TEXTURE_SIZE = 180;

    private static final int COLOR_TITLE = 0x222222;
    private static final int COLOR_LABEL = 0x555555;

    private static final java.util.Map<net.minecraft.core.BlockPos, Long> REFRESH_COOLDOWNS =
            new java.util.HashMap<>();
    private static final long COOLDOWN_MS = 30_000L;

    private Button partyButton;
    private Button alliesButton;
    private Button othersButton;
    private Button refreshButton;
    private Object ship;

    public ClaimSettingsScreen(ClaimSettingsMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth  = TEXTURE_SIZE;
        this.imageHeight = TEXTURE_SIZE;
    }

    @Override
    protected void init() {
        super.init();

        if (Minecraft.getInstance().level != null) {
            try {
                Class<?> utilsClass = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
                this.ship = utilsClass
                        .getMethod("getShipManagingPos",
                                net.minecraft.world.level.Level.class,
                                net.minecraft.core.BlockPos.class)
                        .invoke(null, Minecraft.getInstance().level, menu.getCenter());
            } catch (Exception ignored) {}
        }

        int btnX = this.leftPos + 10;
        int btnW = this.imageWidth - 20;

        this.partyButton = Button.builder(getPartyText(), btn -> {
            this.menu.setAllowParty(!this.menu.isAllowParty());
            btn.setMessage(getPartyText());
            sendUpdate();
        }).bounds(btnX, this.topPos + 24, btnW, 18).build();

        this.alliesButton = Button.builder(getAlliesText(), btn -> {
            this.menu.setAllowAllies(!this.menu.isAllowAllies());
            btn.setMessage(getAlliesText());
            sendUpdate();
        }).bounds(btnX, this.topPos + 46, btnW, 18).build();

        this.othersButton = Button.builder(getOthersText(), btn -> {
            this.menu.setAllowOthers(!this.menu.isAllowOthers());
            btn.setMessage(getOthersText());
            sendUpdate();
        }).bounds(btnX, this.topPos + 68, btnW, 18).build();

        this.refreshButton = Button.builder(Component.translatable("screen.aeroclaims.claim_settings.refresh"), btn -> sendRefresh())
                .bounds(btnX, this.topPos + 90, btnW, 18).build();

        if (!menu.isOnShip()) {
            refreshButton.active = false;
            refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim"));
        } else if (isOnCooldown()) {
            refreshButton.active = false;
            refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh_wait"));
        }

        this.addRenderableWidget(this.partyButton);
        this.addRenderableWidget(this.alliesButton);
        this.addRenderableWidget(this.othersButton);
        this.addRenderableWidget(this.refreshButton);
    }

    public void syncFromMenu() {
        if (partyButton != null) partyButton.setMessage(getPartyText());
        if (alliesButton != null) alliesButton.setMessage(getAlliesText());
        if (othersButton != null) othersButton.setMessage(getOthersText());
    }

    private Component getPartyText() {
        return Component.translatable(this.menu.isAllowParty()
                ? "screen.aeroclaims.claim_settings.party.allowed"
                : "screen.aeroclaims.claim_settings.party.denied");
    }

    private Component getAlliesText() {
        return Component.translatable(this.menu.isAllowAllies()
                ? "screen.aeroclaims.claim_settings.allies.allowed"
                : "screen.aeroclaims.claim_settings.allies.denied");
    }

    private Component getOthersText() {
        return Component.translatable(this.menu.isAllowOthers()
                ? "screen.aeroclaims.claim_settings.others.allowed"
                : "screen.aeroclaims.claim_settings.others.denied");
    }

    private void sendUpdate() {
        PacketDistributor.sendToServer(new UpdateClaimSettingsPacket(
                this.menu.getCenter(),
                this.menu.isAllowParty(),
                this.menu.isAllowAllies(),
                this.menu.isAllowOthers()
        ));
    }

    private boolean isOnCooldown() {
        Long last = REFRESH_COOLDOWNS.get(menu.getCenter());
        return last != null && System.currentTimeMillis() - last < COOLDOWN_MS;
    }

    private void sendRefresh() {
        if (isOnCooldown()) return;
        PacketDistributor.sendToServer(new RefreshClaimPacket(this.menu.getCenter()));
        REFRESH_COOLDOWNS.put(menu.getCenter(), System.currentTimeMillis());
        refreshButton.active = false;
        refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh_wait"));
    }

    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics g, float partialTick, int mx, int my) {
        g.blit(BACKGROUND_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, TEXTURE_SIZE, TEXTURE_SIZE);
    }

    @Override
    protected void renderLabels(net.minecraft.client.gui.GuiGraphics g, int mx, int my) {
        String title = Component.translatable("screen.aeroclaims.claim_settings.title").getString();
        g.drawString(this.font, title,
                (this.imageWidth - this.font.width(title)) / 2, 7, COLOR_TITLE, false);

        g.fill(10, 18, this.imageWidth - 10, 19, 0x66888888);
        g.fill(10, 116, this.imageWidth - 10, 117, 0x66888888);

        String statusText = Component.translatable(this.menu.isClaimActive()
                ? "screen.aeroclaims.claim_settings.status.active"
                : "screen.aeroclaims.claim_settings.status.disabled").getString();
        int statusColor = this.menu.isClaimActive() ? 0x22AA22 : 0xCC3333;
        String privacyPrefix = Component.translatable("screen.aeroclaims.claim_settings.privacy_prefix").getString();
        int prefixWidth = this.font.width(privacyPrefix);
        g.drawString(this.font, privacyPrefix, 10, 120, COLOR_LABEL, false);
        g.drawString(this.font, statusText, 10 + prefixWidth, 120, statusColor, false);

        try {
            String name = Minecraft.getInstance()
                    .getConnection()
                    .getPlayerInfo(menu.getOwner())
                    .getProfile()
                    .getName();
            g.drawString(this.font, Component.translatable("screen.aeroclaims.claim_settings.owner", name).getString(), 10, 132, COLOR_LABEL, false);
        } catch (Exception ignored) {}

        int shipY = 146;
        if (!this.menu.isOnShip()) {
            Component notOnShip = Component.translatable("screen.aeroclaims.claim_settings.not_on_subclaim");
            g.drawString(this.font, notOnShip.getString(), 10, shipY, 0xCC3333, false);
        } else {
            String shipName = this.menu.getShipName();
            if (shipName != null && !shipName.isEmpty()) {
                Component shipText = Component.translatable("screen.aeroclaims.claim_settings.ship", shipName);
                int maxWidth = this.imageWidth - 20;
                int line = 0;
                for (FormattedCharSequence seq : this.font.split(shipText, maxWidth)) {
                    g.drawString(this.font, seq, 10, shipY + (line * this.font.lineHeight), COLOR_LABEL, false);
                    line++;
                }
            }
        }
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics g, int mx, int my, float partialTick) {
        this.renderBackground(g, mx, my, partialTick);
        super.render(g, mx, my, partialTick);
        this.renderTooltip(g, mx, my);

        if (!refreshButton.active && menu.isOnShip() && !isOnCooldown()) {
            REFRESH_COOLDOWNS.remove(menu.getCenter());
            refreshButton.active = true;
            refreshButton.setMessage(Component.translatable("screen.aeroclaims.claim_settings.refresh"));
        }
    }
}
