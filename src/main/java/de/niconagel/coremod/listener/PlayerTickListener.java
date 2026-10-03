package de.niconagel.coremod.listener;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public class PlayerTickListener {

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Falls Fliegen nicht erlaubt ist, Flugmodus sicher deaktivieren
        if (!player.getAbilities().mayfly) {
            player.getAbilities().flying = false;
        }
    }
}
