package de.niconagel.coremod.listener;

import de.niconagel.coremod.utils.PermissionCache;
import de.niconagel.coremod.utils.Tablist;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.io.IOException;
import java.util.UUID;

public class JoinListener {

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID uuid = player.getUUID();

        try {
            PermissionCache.load(uuid);

            Tablist tablist = new Tablist();

            if (PermissionCache.hasGroup(uuid, "chef")) {
                tablist.create("chef", 5, "§cChef", "", null);
            }

            if (PermissionCache.hasGroup(uuid, "developer")) {
                tablist.create("developer", 4, "§bDeveloper", "", null);
            }

        } catch (IOException | InterruptedException e) {
            System.err.println("[CoreMod] Fehler beim Laden der Permissions für " + player.getChatSession());
            e.printStackTrace();
        } catch (Exception e) {
            throw new RuntimeException(e);
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
