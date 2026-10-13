package de.niconagel.coremod.listener;

import de.niconagel.coremod.utils.PermissionCache;
import de.niconagel.coremod.utils.PlayerManager;
import de.niconagel.coremod.utils.Tablist;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.IOException;
import java.util.UUID;

public class JoinListener {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        UUID uuid = player.getUUID();
        PlayerManager playerManager = new PlayerManager(player);

        try {
            PermissionCache.load(playerManager.getShortUUID(), playerManager.getUsername());

            Tablist tablist = new Tablist();

            if (PermissionCache.hasGroup(uuid, "chef"))
                tablist.create("chef", 5, "§cChef", "", null);

            if (PermissionCache.hasGroup(uuid, "developer"))
                tablist.create("developer", 4, "§bDeveloper", "", null);

        } catch (IOException | InterruptedException e) {
            System.err.println("[CoreMod] Fehler beim Laden der Permissions");
            System.err.println("[CoreMod] Spieler: " + playerManager.getUsername());
            System.err.println("[CoreMod] UUID: " + playerManager.getShortUUID());
            System.err.println("[CoreMod] Ursache: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[CoreMod] Unerwarteter Fehler beim Join");
            System.err.println("[CoreMod] Spieler: " + playerManager.getUsername());
            e.printStackTrace();
        }

        player.getAbilities().mayBuild = false;
        player.getAbilities().mayfly = false;
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        PermissionCache.remove(event.getEntity().getUUID());
    }
}
