package de.niconagel.coremod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Admin-Meldungen wie "[Max: Spielmodus gesetzt]" gehen an alle mit dem Recht statt an OPs. */
@Mixin(CommandSourceStack.class)
public abstract class CommandSourceStackMixin {

    @WrapOperation(method = "broadcastToAdmins", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;isOp(Lcom/mojang/authlib/GameProfile;)Z"))
    private boolean coremod$notify(PlayerList playerList, GameProfile profile, Operation<Boolean> original) {
        return original.call(playerList, profile) || PermissionManager.has(profile.getId(), Permissions.NOTIFY);
    }
}
