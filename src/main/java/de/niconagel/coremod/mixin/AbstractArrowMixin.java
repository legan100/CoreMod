package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Pfeile und Dreizacke aufheben (dafür gibt es kein NeoForge-Event). Der Dreizack ruft diese Methode per super auf. */
@Mixin(AbstractArrow.class)
public abstract class AbstractArrowMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void coremod$checkPickup(Player player, CallbackInfo ci) {
        if (PermissionManager.isDenied(player, Permissions.ITEM_PICKUP)) {
            ci.cancel();
        }
    }
}
