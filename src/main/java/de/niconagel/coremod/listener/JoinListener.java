package de.niconagel.coremod.listener;

import de.niconagel.coremod.permission.PermissionManager;
import de.niconagel.coremod.utils.PermissionCache;
import de.niconagel.coremod.utils.Tablist;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.UUID;

public class JoinListener {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        // Bis die Gruppe geladen ist, gilt der Spieler als Gast und der Spielmodus wird ggf. zurückgesetzt.
        // Danach bekommt er seinen Modus wieder, wenn seine Gruppe ihn erlaubt.
        GameType joinMode = player.gameMode.getGameModeForPlayer();

        // Sofort als Gast einsortieren, sonst stünde der Spieler bis zur API-Antwort ganz oben
        Tablist.updatePlayer(player);

        // API-Abfrage im Hintergrund, bis sie fertig ist, gilt der Spieler als Gast.
        // Fehler loggt loadAsync selbst. Danach zurück auf den Server-Thread.
        PermissionCache.loadAsync(uuid).thenRunAsync(() -> {
            if (player.hasDisconnected()) {
                PermissionCache.remove(uuid);
                return;
            }

            Tablist.updatePlayer(player);

            if (player.gameMode.getGameModeForPlayer() != joinMode && PermissionManager.mayUseGameMode(uuid, joinMode)) {
                player.setGameMode(joinMode);
            }

            // OP-Level für den Client und Befehlsliste neu senden, damit sie zur geladenen Gruppe passen
            player.server.getPlayerList().sendPlayerPermissionLevel(player);
        }, player.server);
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PermissionCache.remove(event.getEntity().getUUID());
    }
}
