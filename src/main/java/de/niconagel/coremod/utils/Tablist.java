package de.niconagel.coremod.utils;

import de.niconagel.coremod.api.Group;
import de.niconagel.coremod.permission.PermissionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTabListPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rang vor dem Namen über Scoreboard-Teams (Chat, Nametag; der Client sortiert die Tablist auch nach
 * Team-Namen), in der Tablist zusätzlich bündig ausgerichtet, dazu Header mit Servername, Spieleranzahl und Ping.
 */
@EventBusSubscriber
public final class Tablist {

    private Tablist() {
    }

    /** Sortiert den Spieler ein und aktualisiert die Tab-Namen. Nach jedem Laden der Gruppe aufrufen. */
    public static void updatePlayer(ServerPlayer player) {
        Scoreboard scoreboard = player.server.getScoreboard();
        PlayerTeam team = team(scoreboard, PermissionManager.getGroup(player.getUUID()));

        if (scoreboard.getPlayersTeam(player.getScoreboardName()) != team) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }

        // Ein neuer Rang kann die Ausrichtung aller ändern
        refreshAll(player.server);
    }

    /** Feuert onTabListName für alle neu; geschickt wird nur, was sich geändert hat. */
    private static void refreshAll(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(ServerPlayer::refreshTabListName);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        // Erst nach dem Entfernen aus der Spielerliste neu ausrichten
        if (server != null) server.execute(() -> refreshAll(server));
    }

    /** Das Team sortiert die Tablist und setzt den Rang vor den Namen in Chat, Nametag und Join-Nachricht. */
    private static PlayerTeam team(Scoreboard scoreboard, Group group) {
        // "coremod_00_chef" < "coremod_07_gast" → Chef steht oben
        String name = String.format("coremod_%02d_%s",
                Group.CHEF.getWeight() - group.getWeight(), group.name().toLowerCase(Locale.ROOT));

        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }

        // Immer setzen: Teams werden mit der Welt gespeichert und sollen geänderte Ränge übernehmen
        team.setPlayerPrefix(rank(group));
        return team;
    }

    /** "Dev | " in Gruppenfarbe, ohne Abstände. */
    private static Component rank(Group group) {
        return Component.empty()
                .append(Component.literal(group.getPrefix()).withStyle(ChatFormatting.getByCode(group.getColorCode())))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY));
    }

    /**
     * In der Tablist zusätzlich rechtsbündig, damit die "|" untereinander stehen.
     * Der Tab-Name ersetzt dort das Team-Prefix, der Rang erscheint also nicht doppelt.
     */
    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Group group = PermissionManager.getGroup(player.getUUID());

        // Nur an den Rängen ausrichten, die gerade online sind, sonst stünde ein einzelner "Chef"
        // wegen "FDL-Azubi" weit vom Kopf entfernt
        List<String> online = new ArrayList<>(List.of(group.getPrefix()));
        player.server.getPlayerList().getPlayers()
                .forEach(other -> online.add(PermissionManager.getGroup(other.getUUID()).getPrefix()));

        // Auffüllen mit normalen (4 px) und fetten (5 px) Leerzeichen
        int[] padding = TextWidth.padding(TextWidth.commonWidth(online) - TextWidth.of(group.getPrefix()));

        event.setDisplayName(Component.empty()
                .append(" ".repeat(padding[0]))
                .append(Component.literal(" ".repeat(padding[1])).withStyle(ChatFormatting.BOLD))
                .append(rank(group))
                .append(event.getEntity().getName()));
    }

    /** "TFs in der Blockwelt": TF rot, Rest grau, fett. */
    public static Component title() {
        return Component.empty().withStyle(ChatFormatting.BOLD)
                .append(Component.literal("TF").withStyle(ChatFormatting.RED))
                .append(Component.literal("s in der Blockwelt").withStyle(ChatFormatting.GRAY));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;

        Component title = title();
        String players = server.getPlayerCount() + "/" + server.getMaxPlayers();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Der Header ist zentriert, die Leerzeichen schieben Spieler nach links und Ping nach rechts
            Component line = Component.literal("Spieler: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(players).withStyle(ChatFormatting.WHITE))
                    .append(Component.literal("          Ping: ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(player.connection.latency() + " ms").withStyle(ChatFormatting.WHITE));

            Component header = Component.empty().append(title).append("\n").append(line);
            player.connection.send(new ClientboundTabListPacket(header, Component.empty()));
        }
    }
}
