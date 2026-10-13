package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Druckplatten und Stolperdrähte (die einzigen Nutzer der Methode) ignorieren Spieler ohne Interaktionsrecht. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "isIgnoringBlockTriggers", at = @At("HEAD"), cancellable = true)
    private void coremod$ignoreBlockTriggers(CallbackInfoReturnable<Boolean> cir) {
        if (PermissionManager.isDenied((Entity) (Object) this, Permissions.INTERACT_BLOCK)) {
            cir.setReturnValue(true);
        }
    }
}
