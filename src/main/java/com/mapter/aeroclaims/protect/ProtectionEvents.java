package com.mapter.aeroclaims.protect;

import com.mapter.aeroclaims.Aeroclaims;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
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
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Forge event listeners protecting claimed blocks from unauthorized access.
// Each event checks if affected position is in an active claim, then verifies player permissions via PERMISSION_RESOLVER.
// Spam messages limited by MESSAGE_COOLDOWN_MS cooldown per player.
@EventBusSubscriber(modid = Aeroclaims.MODID)
public class ProtectionEvents {

    private static final long MESSAGE_COOLDOWN_MS = 15_000;
    private static final Map<UUID, Long> lastMessageTime = new ConcurrentHashMap<>();

    // Claim lookup with margin

    // Searches for claim at position with claimMarginBlocks buffer.
    // Checks exact position first, then rings r=1..margin.
    private static Claim getClaimAtWithMargin(ServerLevel level, BlockPos pos) {
        Claim exact = ClaimManager.getClaimAt(level, pos);
        if (exact != null) return exact;

        int margin = AeroClaimsConfig.CLAIM_MARGIN_BLOCKS.get();
        for (int r = 1; r <= margin; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue; // ring perimeter only
                    Claim c = ClaimManager.getClaimAt(level, pos.offset(dx, 0, dz));
                    if (c != null) return c;
                }
            }
        }
        return null;
    }

    // Anti-spam

    private static boolean shouldSendMessage(ServerPlayer player) {
        if (player instanceof FakePlayer) return false;
        long now = System.currentTimeMillis();
        Long last = lastMessageTime.get(player.getUUID());
        if (last != null && now - last < MESSAGE_COOLDOWN_MS) return false;
        lastMessageTime.put(player.getUUID(), now);
        return true;
    }

    // Event listeners

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        Claim claim = getClaimAtWithMargin(player.serverLevel(), event.getPos());
        if (claim == null) return;

        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.foreign_territory"));
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        // Check both clicked block and target block (where new block could be placed)
        BlockPos clickedPos = event.getPos();
        BlockPos targetPos  = clickedPos.relative(event.getFace());
        Claim claim = firstNonNull(
                getClaimAtWithMargin(level, targetPos),
                getClaimAtWithMargin(level, clickedPos)
        );
        if (claim == null) return;

        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            event.setUseItem(TriState.FALSE);
            event.setUseBlock(TriState.FALSE);
            if (shouldSendMessage(player)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.no_access_use_block"));
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        HitResult hit = player.pick(5.0, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult bhr = (BlockHitResult) hit;
        BlockPos clickedPos = bhr.getBlockPos();
        BlockPos targetPos  = clickedPos.relative(bhr.getDirection());
        Claim claim = firstNonNull(
                getClaimAtWithMargin(level, targetPos),
                getClaimAtWithMargin(level, clickedPos)
        );
        if (claim == null) return;

        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Claim claim = getClaimAtWithMargin(player.serverLevel(), event.getPos());
        if (claim == null) return;

        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.foreign_territory"));
            }
        }
    }

    @SubscribeEvent
    public static void onBlockMultiPlace(BlockEvent.EntityMultiPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        Claim claim = getClaimAtWithMargin(player.serverLevel(), event.getPos());
        if (claim == null) return;

        if (!ClaimManager.PERMISSION_RESOLVER.canAccess(player, claim)) {
            event.setCanceled(true);
            if (shouldSendMessage(player)) {
                player.sendSystemMessage(Component.translatable("message.aeroclaims.foreign_territory"));
            }
        }
    }

    // Utilities

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
