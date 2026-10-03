package de.niconagel.coremod;

import net.minecraft.world.level.dimension.DimensionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(DimensionType.class)
public class DimensionTypeMixin {

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            ordinal = 8
    )
    private static int niconagel$changeMinY(int minY) {
        if (minY == -64) {
            return -64;
        }

        return minY;
    }

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            ordinal = 9
    )
    private static int niconagel$changeHeight(int height) {
        if (height == 384) {
            return 1884;
        }

        return height;
    }

    @ModifyVariable(
            method = "<init>",
            at = @At("HEAD"),
            ordinal = 10
    )
    private static int niconagel$changeLogicalHeight(int logicalHeight) {
        if (logicalHeight == 384) {
            return 1884;
        }

        return logicalHeight;
    }
}
