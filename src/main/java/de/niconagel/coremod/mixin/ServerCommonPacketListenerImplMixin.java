package de.niconagel.coremod.mixin;

import de.niconagel.coremod.utils.DisconnectMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Kicks im Spiel: Server geschlossen, Whitelist eingeschaltet, … */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonPacketListenerImplMixin {

    @ModifyVariable(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true)
    private Component coremod$replaceMessage(Component reason) {
        // Läuft auch beim Stoppen des Servers, vor dem Speichern der Welt: darf niemals werfen
        try {
            return DisconnectMessages.replace(reason);
        } catch (Throwable e) {
            return reason;
        }
    }
}
