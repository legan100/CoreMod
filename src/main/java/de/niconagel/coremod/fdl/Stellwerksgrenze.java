package de.niconagel.coremod.fdl;

import de.niconagel.coremod.fdl.Stellwerke.Bereich;
import de.niconagel.coremod.fdl.Stellwerke.Stellwerk;
import de.niconagel.coremod.utils.Grenze;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fahrdienstleiter bleiben in ihrem Stellwerk: Wer den Bereich verlässt, bekommt einen Stoß zurück hinein; wer weiter
 * draußen ist (Fall, Teleport), wird an die nächste Stelle im Bereich gesetzt.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class Stellwerksgrenze {

    /** Abstand zur Wand, an den zurückgestoßen wird. */
    private static final double INSET = 0.3;

    /** Spieler → ID ihres Stellwerks. */
    private static final Map<UUID, String> ACTIVE = new ConcurrentHashMap<>();

    private Stellwerksgrenze() {
    }

    /** Ab jetzt gilt der Bereich des Stellwerks für den Spieler. */
    public static void enter(ServerPlayer player, String stellwerk) {
        ACTIVE.put(player.getUUID(), stellwerk);
    }

    /** Zurück im Menü: keine Grenze mehr. */
    public static void leave(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        String id = ACTIVE.get(player.getUUID());
        if (id == null) return;
        Stellwerk stellwerk = Stellwerke.find(id);
        // Gelöschtes Stellwerk oder anderswo hingebracht (z.B. ein Admin per /tp): nicht zurückziehen
        if (stellwerk == null || !player.level().dimension().location().toString().equals(stellwerk.dimension())) return;

        Bereich b = stellwerk.bereich();
        Vec3 pos = player.position();
        if (b.contains(pos.x, pos.y, pos.z)) return;

        // Nächste Stelle im Bereich, etwas von den Wänden weg
        Vec3 inside = new Vec3(
                Mth.clamp(pos.x, b.minX() + INSET, b.maxX() + 1 - INSET),
                Mth.clamp(pos.y, b.minY(), b.maxY() + 1 - INSET),
                Mth.clamp(pos.z, b.minZ() + INSET, b.maxZ() + 1 - INSET));
        Grenze.pushBack(player, inside, "Du darfst das Stellwerk nicht verlassen.");
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
