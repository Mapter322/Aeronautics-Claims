package com.mapter.aeroclaims.mixin;

import com.mapter.aeroclaims.protect.IPlacerTracked;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = KineticBlockEntity.class, remap = false)
public class MixinKineticBlockEntityPlacer implements IPlacerTracked {

    @Unique
    private UUID aeroclaims$placerUUID;

    @Override
    public UUID aeroclaims$getPlacerUUID() {
        return aeroclaims$placerUUID;
    }

    @Override
    public void aeroclaims$setPlacerUUID(UUID uuid) {
        this.aeroclaims$placerUUID = uuid;
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void aeroclaims$onWrite(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (aeroclaims$placerUUID != null) {
            compound.putUUID("aeroclaims:Placer", aeroclaims$placerUUID);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void aeroclaims$onRead(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (compound.hasUUID("aeroclaims:Placer")) {
            aeroclaims$placerUUID = compound.getUUID("aeroclaims:Placer");
        }
    }
}
