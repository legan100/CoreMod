package de.niconagel.coremod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.authlib.GameProfile;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * "moved too quickly": Vanilla erlaubt pro Bewegungspaket 100 (Blöcke²) mehr als erwartet, egal wie schnell
 * jemand per /flyspeed fliegen darf. Beim Fliegen wächst die Grenze deshalb mit der Fluggeschwindigkeit
 * (quadratisch, weil Vanilla quadrierte Strecken vergleicht). Laufen und Elytra bleiben unverändert.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    /** Vanilla-Standard für Abilities.flyingSpeed. */
    private static final float DEFAULT_FLY_SPEED = 0.05f;

    @Shadow
    public ServerPlayer player;

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(floatValue = 100.0F))
    private float coremod$scaleMoveLimit(float limit) {
        if (!player.getAbilities().flying) {
            return limit;
        }

        float factor = player.getAbilities().getFlyingSpeed() / DEFAULT_FLY_SPEED;
        return limit * Math.max(1, factor * factor);
    }

    /** OPs werden nicht wegen Chat-Spam gekickt. OPs gibt es nicht mehr, das übernimmt ein Recht. */
    @WrapOperation(method = "detectRateSpam", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;isOp(Lcom/mojang/authlib/GameProfile;)Z"))
    private boolean coremod$spamBypass(PlayerList playerList, GameProfile profile, Operation<Boolean> original) {
        return original.call(playerList, profile) || PermissionManager.has(player.getUUID(), Permissions.CHAT_SPAM);
    }
}
