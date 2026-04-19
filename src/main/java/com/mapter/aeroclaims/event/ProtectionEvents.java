package com.mapter.aeroclaims.event;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.registry.ModBlocks;
import com.mapter.aeroclaims.ship.VSShipUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Aeroclaims.MODID)
public class ProtectionEvents {

    private static final int CLAIM_MARGIN_BLOCKS = 5;

    private static Claim getClaimAtWithMargin(ServerLevel level, BlockPos pos) {
        Claim exact = ClaimManager.getClaimAt(level, pos);
        if (exact != null) return exact;
        for (int r = 1; r <= CLAIM_MARGIN_BLOCKS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    Claim c = ClaimManager.getClaimAt(level, pos.offset(dx, 0, dz));
                    if (c != null) return c;
                }
            }
        }
        return null;
    }

    private static boolean shouldSendMessage(ServerPlayer player) {
        return !(player instanceof FakePlayer);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        Claim claim = getClaimAtWithMargin(player.serverLevel(), event.getPos());
        if (claim == null) return;
        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) player.sendSystemMessage(Component.translatable("message.vsclaims.foreign_territory"));
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        Claim claimAtClicked = getClaimAtWithMargin(level, event.getPos());
        BlockPos targetPos = event.getPos().relative(event.getFace());
        Claim claimAtTarget = getClaimAtWithMargin(level, targetPos);
        Claim claim = claimAtTarget != null ? claimAtTarget : claimAtClicked;
        if (claim == null) return;
        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setUseItem(TriState.FALSE);
            event.setUseBlock(TriState.FALSE);
            if (shouldSendMessage(player)) player.sendSystemMessage(Component.translatable("message.vsclaims.no_access_use_block"));
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos clickedPos = bhr.getBlockPos();
        BlockPos targetPos = clickedPos.relative(bhr.getDirection());
        Claim claimAtClicked = getClaimAtWithMargin(level, clickedPos);
        Claim claimAtTarget = getClaimAtWithMargin(level, targetPos);
        Claim claim = claimAtTarget != null ? claimAtTarget : claimAtClicked;
        if (claim == null) return;
        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        Claim claim = getClaimAtWithMargin(level, event.getPos());
        if (claim != null && !ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) player.sendSystemMessage(Component.translatable("message.vsclaims.foreign_territory"));
            return;
        }
        if (event.getPlacedBlock().getBlock() != ModBlocks.CLAIM_BLOCK.get()) return;
        if (!VSShipUtils.isOnShip(level, event.getPos())) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) player.sendSystemMessage(Component.translatable("message.vsclaims.claim_block_only_on_ship"));
        }
    }

    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();
        Claim claim = getClaimAtWithMargin(level, event.getPos());
        if (claim == null) return;
        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) player.sendSystemMessage(Component.translatable("message.vsclaims.foreign_territory"));
        }
    }
}