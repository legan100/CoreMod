package de.niconagel.coremod.mixin;

import de.niconagel.coremod.utils.DisconnectMessages;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Kicks beim Einloggen, z.B. "nicht auf der Whitelist". */
@Mixin(ServerLoginPacketListenerImpl.class)
public abstract class ServerLoginPacketListenerImplMixin {

    @ModifyVariable(method = "disconnect(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), argsOnly = true)
    private Component coremod$replaceMessage(Component reason) {
        // Ein Fehler hier darf den Kick nicht verhindern
        try {
            return DisconnectMessages.replace(reason);
        } catch (Throwable e) {
            return reason;
        }
    }
}
