package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Befehlsblöcke, Strukturblöcke, Jigsaw und Items mit OP-NBT: Creative plus Recht statt Creative plus OP. */
@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "canUseGameMasterBlocks", at = @At("HEAD"), cancellable = true)
    private void coremod$gameMaster(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayer player && !player.isFakePlayer()) {
            cir.setReturnValue(player.getAbilities().instabuild && PermissionManager.has(player.getUUID(), Permissions.GAMEMASTER));
        }
    }
}
