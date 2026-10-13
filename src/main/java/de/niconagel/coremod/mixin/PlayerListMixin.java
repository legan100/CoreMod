package de.niconagel.coremod.mixin;

import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.UUID;

/**
 * OP-Level, das der Client zu sehen bekommt. Es schaltet nur Client-Oberflächen frei (F3+F4-Spielmodus-Wechsler,
 * Selektor-Vorschläge, Befehlsblock-Fenster, Operator-Tab), geprüft wird auf dem Server über die CoreMod-Rechte.
 */
@Mixin(PlayerList.class)
public abstract class PlayerListMixin {

    @ModifyArg(method = "sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;I)V"),
            index = 1)
    private int coremod$clientLevel(ServerPlayer player, int level) {
        UUID uuid = player.getUUID();
        boolean ui = PermissionManager.has(uuid, Permissions.command("gamemode"))
                || PermissionManager.has(uuid, Permissions.SELECTOR)
                || PermissionManager.has(uuid, Permissions.GAMEMASTER);
        return ui ? Commands.LEVEL_GAMEMASTERS : level;
    }
}
