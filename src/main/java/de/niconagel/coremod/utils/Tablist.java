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

import java.util.Arrays;
import java.util.Locale;

/**
 * Rang vor dem Namen (nur in der Tablist), Sortierung über Scoreboard-Teams (der Client sortiert
 * die Tablist nach Team-Namen), dazu Header mit Servername, Spieleranzahl und Ping.
 */
@EventBusSubscriber
public final class Tablist {

    private static final int PREFIX_WIDTH = TextWidth.commonWidth(
            Arrays.stream(Group.values()).map(Group::getPrefix).toList());

    private Tablist() {
    }

    /** Sortiert den Spieler ein und aktualisiert seinen Tab-Namen. Nach jedem Laden der Gruppe aufrufen. */
    public static void updatePlayer(ServerPlayer player) {
        Scoreboard scoreboard = player.server.getScoreboard();
        PlayerTeam team = team(scoreboard, PermissionManager.getGroup(player.getUUID()));

        if (scoreboard.getPlayersTeam(player.getScoreboardName()) != team) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }

        // Feuert onTabListName neu und schickt den Namen an alle
        player.refreshTabListName();
    }

    /** Teams nur zum Sortieren, ohne Prefix, damit Chat und Nametag normal bleiben. */
    private static PlayerTeam team(Scoreboard scoreboard, Group group) {
        // "coremod_00_chef" < "coremod_07_gast" → Chef steht oben
        String name = String.format("coremod_%02d_%s",
                Group.CHEF.getWeight() - group.getWeight(), group.name().toLowerCase(Locale.ROOT));

        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }

        // Ältere Versionen haben hier ein Prefix gesetzt, das mit der Welt gespeichert ist → leeren
        if (!team.getPlayerPrefix().getString().isEmpty()) {
            team.setPlayerPrefix(Component.empty());
        }
        return team;
    }

    /** Rang nur in der Tablist, rechtsbündig, damit die "|" untereinander stehen und direkt am Rang sitzen. */
    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        Group group = PermissionManager.getGroup(event.getEntity().getUUID());

        // Auffüllen mit normalen (4 px) und fetten (5 px) Leerzeichen
        int[] padding = TextWidth.padding(PREFIX_WIDTH - TextWidth.of(group.getPrefix()));

        event.setDisplayName(Component.empty()
                .append(" ".repeat(padding[0]))
                .append(Component.literal(" ".repeat(padding[1])).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(group.getPrefix()).withStyle(ChatFormatting.getByCode(group.getColorCode())))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
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
