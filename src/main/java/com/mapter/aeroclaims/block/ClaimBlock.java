package com.mapter.aeroclaims.block;

import com.mapter.aeroclaims.claim.AeroClaimManager;
import com.mapter.aeroclaims.config.AeroClaimsConfig;
import com.mapter.aeroclaims.claim.AeroClaimSavedData;
import com.mapter.aeroclaims.claim.Claim;
import com.mapter.aeroclaims.claim.ClaimManager;
import com.mapter.aeroclaims.network.SyncClaimStatePacket;
import com.mapter.aeroclaims.screen.ClaimSettingsMenu;
import com.mapter.aeroclaims.sublevel.RegisteredSublevelManager;
import com.mapter.aeroclaims.sublevel.SableShipUtils;
import com.mapter.aeroclaims.sublevel.UnregisteredSublevelManager;
import com.mojang.serialization.MapCodec;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class ClaimBlock extends BaseEntityBlock {

    public static final MapCodec<ClaimBlock> CODEC = simpleCodec(ClaimBlock::new);
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public ClaimBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH)
                .setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(BlockStateProperties.HORIZONTAL_FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, context.getHorizontalDirection().getOpposite())
                .setValue(OPEN, false);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ClaimBlockEntity(pos, state);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return true; }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable net.minecraft.world.entity.LivingEntity placer,
                            net.minecraft.world.item.ItemStack stack) {
        if (!level.isClientSide && placer instanceof Player player) {
            ClaimManager.addClaim((ServerLevel) level, pos, player.getUUID());
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos,
                         BlockState newState, boolean moving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            ServerLevel serverLevel = (ServerLevel) level;

            SubLevel ship = SableShipUtils.getShipAt(serverLevel, pos);
            String shipId = SableShipUtils.getShipId(ship);
            if (shipId != null) {
                String shipName = SableShipUtils.getShipName(ship);
                RegisteredSublevelManager.unregisterShip(shipId);
                UnregisteredSublevelManager.addShip(shipId, shipName);
            }

            Claim claim = ClaimManager.getClaimByCenter(serverLevel, pos);
            if (claim != null) {
                AeroClaimManager.releaseAllClaimsForBlock(serverLevel, claim.getOwner(), pos);
            }

            ClaimManager.removeClaim(serverLevel, pos);


            AeroClaimSavedData data = AeroClaimSavedData.get(serverLevel);
            data.clearCachedShipBlockCount(pos);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        ServerLevel serverLevel = serverPlayer.serverLevel();
        Claim claim = ClaimManager.getClaimByCenter(serverLevel, pos);
        if (claim == null) return InteractionResult.PASS;

        if (!serverPlayer.getUUID().equals(claim.getOwner())) {
            serverPlayer.sendSystemMessage(Component.translatable("message.aeroclaims.only_owner_can_configure"));
            return InteractionResult.CONSUME;
        }

        if (state.hasProperty(OPEN) && !state.getValue(OPEN)) {
            level.setBlock(pos, state.setValue(OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0F, 1.0F);
        }

        SubLevel ship = SableShipUtils.getShipAt(serverLevel, pos);
        boolean onShip = ship != null;
        String shipName = SableShipUtils.getShipName(ship);

        AeroClaimSavedData data = AeroClaimSavedData.get(serverLevel);
        int claimsForBlock = data.getClaimsForBlock(pos);
        int freeSlots = data.getFreeSlots(serverPlayer.getUUID());

        Integer cachedCount = data.getCachedShipBlockCount(pos);
        int initialBlockCount = (cachedCount != null) ? cachedCount : SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN;

        serverPlayer.openMenu(getMenuProvider(state, level, pos), buf -> {
            buf.writeBlockPos(pos);
            buf.writeUUID(claim.getOwner());
            buf.writeUtf(shipName != null ? shipName : "");
            buf.writeBoolean(onShip);
            buf.writeBoolean(claim.isActive());
            buf.writeBoolean(claim.isAllowParty());
            buf.writeBoolean(claim.isAllowAllies());
            buf.writeBoolean(claim.isAllowOthers());
            buf.writeInt(claimsForBlock);
            buf.writeInt(freeSlots);
            buf.writeInt(AeroClaimsConfig.BLOCKS_PER_CLAIM.get());
            buf.writeInt(initialBlockCount);
        });

        return InteractionResult.CONSUME;
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        Claim claim = level instanceof ServerLevel serverLevel
                ? ClaimManager.getClaimByCenter(serverLevel, pos) : null;
        if (claim == null) return null;
        return new SimpleMenuProvider(
                (containerId, inv, p) -> new ClaimSettingsMenu(
                        containerId, inv, pos, claim.getOwner(), "",
                        false, claim.isActive(),
                        claim.isAllowParty(), claim.isAllowAllies(), claim.isAllowOthers(),
                        0, 0, 0, SyncClaimStatePacket.SHIP_BLOCK_COUNT_UNKNOWN),
                Component.translatable("screen.aeroclaims.claim_settings.title")
        );
    }
}
