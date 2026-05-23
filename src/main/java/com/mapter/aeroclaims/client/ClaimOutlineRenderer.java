package com.mapter.aeroclaims.client;

import com.mapter.aeroclaims.Aeroclaims;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = Aeroclaims.MODID, value = Dist.CLIENT)
public class ClaimOutlineRenderer {

    private static final int COLOR = 0x33D9FF;
    private static final int DURATION_TICKS = 80;
    private static final float LINE_WIDTH = 1 / 16f;
    private static final Object OUTLINE_SLOT = new Object();

    private static int remainingTicks = 0;

    public static void setOutline(List<BlockPos> blocks) {
        if (blocks.isEmpty()) return;
        Level level = Minecraft.getInstance().level;
        Set<BlockPos> blockSet = new HashSet<>(blocks.size());
        for (BlockPos pos : blocks) {
            if (level != null) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || !state.getFluidState().isEmpty() || !state.isSolid()) continue;
            }
            blockSet.add(pos);
        }
        if (blockSet.isEmpty()) return;
        Outliner.getInstance().showCluster(OUTLINE_SLOT, blockSet)
                .colored(COLOR)
                .disableLineNormals()
                .lineWidth(LINE_WIDTH);
        remainingTicks = DURATION_TICKS;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (remainingTicks <= 0) return;
        remainingTicks--;
        if (remainingTicks > 0) {
            Outliner.getInstance().keep(OUTLINE_SLOT);
        }
    }
}
