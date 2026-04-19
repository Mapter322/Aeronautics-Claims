package com.mapter.aeroclaims.registry;

import com.mapter.aeroclaims.block.ClaimBlock;
import com.mapter.aeroclaims.block.ClaimBlockEntity;
import com.mapter.aeroclaims.Aeroclaims;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Aeroclaims.MODID);

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Aeroclaims.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Aeroclaims.MODID);

    public static final DeferredBlock<Block> CLAIM_BLOCK =
            BLOCKS.registerBlock("claim_block",
                    ClaimBlock::new,
                    BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    public static final DeferredItem<BlockItem> CLAIM_BLOCK_ITEM =
            ITEMS.registerSimpleBlockItem("claim_block", CLAIM_BLOCK);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClaimBlockEntity>> CLAIM_BE =
            BLOCK_ENTITIES.register("claim_be",
                    () -> BlockEntityType.Builder.of(
                            ClaimBlockEntity::new,
                            CLAIM_BLOCK.get()
                    ).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}