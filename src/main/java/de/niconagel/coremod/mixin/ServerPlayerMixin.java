package de.niconagel.coremod.mixin;

import de.niconagel.coremod.mainmenu.MainMenu;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Spieler im Hauptmenü werden anderen gar nicht erst geschickt: keine Rüstung, kein Nametag, auch nicht
 * für Teamkollegen (Ränge sind Teams, und Teams sehen Unsichtbare halb durchsichtig).
 * Neu geprüft wird beim Chunk-Wechsel oder per ServerChunkCache.move().
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Inject(method = "broadcastToPlayer", at = @At("HEAD"), cancellable = true)
    private void coremod$hideInMenu(ServerPlayer viewer, CallbackInfoReturnable<Boolean> cir) {
        if (MainMenu.isInMenu((ServerPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
