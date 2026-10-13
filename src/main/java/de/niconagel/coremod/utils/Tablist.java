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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Arrays;
import java.util.Locale;

/**
 * Rang-Prefix und Sortierung über Scoreboard-Teams (der Client sortiert die Tablist nach Team-Namen),
 * dazu Header mit Servername, Spieleranzahl und Ping.
 */
@EventBusSubscriber
public final class Tablist {

    private static final int PREFIX_WIDTH = TextWidth.commonWidth(
            Arrays.stream(Group.values()).map(Group::getDisplayName).toList());

    private Tablist() {
    }

    /** Steckt den Spieler in das Team seiner Gruppe. Nach jedem Laden der Gruppe aufrufen. */
    public static void updatePlayer(ServerPlayer player) {
        Scoreboard scoreboard = player.server.getScoreboard();
        PlayerTeam team = team(scoreboard, PermissionManager.getGroup(player.getUUID()));

        if (scoreboard.getPlayersTeam(player.getScoreboardName()) != team) {
            scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        }
    }

    private static PlayerTeam team(Scoreboard scoreboard, Group group) {
        // "coremod_00_chef" < "coremod_07_gast" → Chef steht oben
        String name = String.format("coremod_%02d_%s",
                Group.CHEF.getWeight() - group.getWeight(), group.name().toLowerCase(Locale.ROOT));

        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }

        // Auffüllen mit normalen (4 px) und fetten (5 px) Leerzeichen, damit alle "|" untereinander stehen
        int[] padding = TextWidth.padding(PREFIX_WIDTH - TextWidth.of(group.getDisplayName()));

        // Immer setzen: Teams werden mit der Welt gespeichert und sollen geänderte Farben übernehmen
        team.setPlayerPrefix(Component.empty()
                .append(Component.literal(group.getDisplayName()).withStyle(ChatFormatting.getByCode(group.getColorCode())))
                .append(" ".repeat(padding[0]))
                .append(Component.literal(" ".repeat(padding[1])).withStyle(ChatFormatting.BOLD))
                .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY)));
        return team;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;

        Component title = Component.empty().withStyle(ChatFormatting.BOLD)
                .append(Component.literal("TF").withStyle(ChatFormatting.RED))
                .append(Component.literal("s in der Blockwelt").withStyle(ChatFormatting.GRAY));
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
