package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TurtleEggBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Schildkröteneier durch Drauftreten oder -fallen zerstören zählt als Abbauen. */
@Mixin(TurtleEggBlock.class)
public abstract class TurtleEggBlockMixin {

    @Inject(method = "canDestroyEgg", at = @At("HEAD"), cancellable = true)
    private void coremod$checkTrample(Level level, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (PermissionManager.isDenied(entity, Permissions.BLOCK_BREAK)) {
            cir.setReturnValue(false);
        }
    }
}
