package de.niconagel.coremod.fdl;

import de.niconagel.coremod.fdl.Stellwerke.Bereich;
import de.niconagel.coremod.fdl.Stellwerke.Spawn;
import de.niconagel.coremod.fdl.Stellwerke.Stellwerk;
import de.niconagel.coremod.mainmenu.MainMenuNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spawn und Bereich eines Stellwerks in der Welt setzen, während der Editor zu ist. Der Spieler läuft hin und drückt
 * Enter: erst am Spawn, dann an der einen Ecke des Bereichs, dann an der gegenüberliegenden. Der Client zeigt dabei den
 * Quader an und meldet nur die Taste, die Position nimmt der Server vom Spieler selbst.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class StellwerkAuswahl {

    // Aktionen vom Client
    public static final int SET = 0;
    public static final int UNDO = 1;
    public static final int CANCEL = 2;

    // Schritte, auch für den Client (0 = aus)
    private static final int SPAWN = 1;
    private static final int CORNER_A = 2;
    private static final int CORNER_B = 3;

    /**
     * @param id    ID des Stellwerks; neu, wenn es sie noch nicht gibt
     * @param spawn null bis gesetzt
     * @param a     erste Ecke, null bis gesetzt
     */
    private record Einrichtung(String id, String name, String dimension, Spawn spawn, BlockPos a) {
        int step() {
            return spawn == null ? SPAWN : a == null ? CORNER_A : CORNER_B;
        }
    }

    private static final Map<UUID, Einrichtung> ACTIVE = new ConcurrentHashMap<>();

    private StellwerkAuswahl() {
    }

    /** Neues Stellwerk ({@code id} leer) oder Spawn und Bereich eines vorhandenen neu setzen. */
    public static void start(ServerPlayer player, String id, String name) {
        Stellwerk existing = id.isEmpty() ? null : Stellwerke.find(id);
        if (existing == null && (!id.isEmpty() || name.isBlank())) return;

        String newId = existing != null ? existing.id() : UUID.randomUUID().toString().substring(0, 8);
        String newName = existing != null ? existing.name() : name.strip();
        update(player, new Einrichtung(newId, newName, dimension(player), null, null));
    }

    public static void key(ServerPlayer player, int action) {
        Einrichtung einrichtung = ACTIVE.get(player.getUUID());
        if (einrichtung == null) return;

        switch (action) {
            case CANCEL -> finish(player);
            case UNDO -> {
                if (einrichtung.a() != null) update(player, new Einrichtung(einrichtung.id(), einrichtung.name(), einrichtung.dimension(), einrichtung.spawn(), null));
                else if (einrichtung.spawn() != null) update(player, new Einrichtung(einrichtung.id(), einrichtung.name(), einrichtung.dimension(), null, null));
            }
            default -> set(player, einrichtung);
        }
    }

    private static void set(ServerPlayer player, Einrichtung einrichtung) {
        // Ein Stellwerk liegt in einer Dimension
        if (!dimension(player).equals(einrichtung.dimension())) {
            fail(player, "Das Stellwerk liegt in " + einrichtung.dimension() + ", geh dorthin zurück.");
            return;
        }

        BlockPos here = player.blockPosition();
        switch (einrichtung.step()) {
            case SPAWN -> update(player, new Einrichtung(einrichtung.id(), einrichtung.name(), einrichtung.dimension(),
                    new Spawn(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot()), null));
            case CORNER_A -> update(player, new Einrichtung(einrichtung.id(), einrichtung.name(), einrichtung.dimension(), einrichtung.spawn(), here));
            default -> {
                BlockPos a = einrichtung.a();
                Bereich bereich = Bereich.of(a.getX(), a.getY(), a.getZ(), here.getX(), here.getY(), here.getZ());
                Spawn spawn = einrichtung.spawn();
                if (!bereich.contains(spawn.x(), spawn.y(), spawn.z())) {
                    fail(player, "Der Spawn liegt nicht im Bereich. Wähle die Ecken so, dass er drin ist (Rücktaste: Ecke neu).");
                    return;
                }
                try {
                    Stellwerke.put(new Stellwerk(einrichtung.id(), einrichtung.name(), einrichtung.dimension(), spawn, bereich));
                } catch (IOException | IllegalArgumentException e) {
                    fail(player, "Stellwerk nicht gespeichert: " + e.getMessage());
                    return;
                }
                player.displayClientMessage(Component.literal("Stellwerk \"" + einrichtung.name() + "\" eingerichtet ("
                        + (bereich.maxX() - bereich.minX() + 1) + " × " + (bereich.maxY() - bereich.minY() + 1) + " × "
                        + (bereich.maxZ() - bereich.minZ() + 1) + " Blöcke).").withStyle(ChatFormatting.GREEN), true);
                finish(player);
            }
        }
    }

    private static void update(ServerPlayer player, Einrichtung einrichtung) {
        ACTIVE.put(player.getUUID(), einrichtung);
        Spawn spawn = einrichtung.spawn();
        BlockPos a = einrichtung.a();
        PacketDistributor.sendToPlayer(player, new MainMenuNetwork.BereichState(einrichtung.step(), einrichtung.name(),
                spawn == null ? List.of() : List.of(new Vector3f((float) spawn.x(), (float) spawn.y(), (float) spawn.z())),
                a == null ? List.of() : List.of(new Vector3f(a.getX(), a.getY(), a.getZ()))));
    }

    /** Einrichten beenden und den Editor wieder öffnen. */
    private static void finish(ServerPlayer player) {
        ACTIVE.remove(player.getUUID());
        PacketDistributor.sendToPlayer(player, MainMenuNetwork.BereichState.INACTIVE);
        PacketDistributor.sendToPlayer(player, MainMenuNetwork.OpenEditor.of(MainMenuNetwork.OpenEditor.STELLWERK));
    }

    private static void fail(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message).withStyle(ChatFormatting.RED), true);
    }

    private static String dimension(ServerPlayer player) {
        return player.level().dimension().location().toString();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }
}
