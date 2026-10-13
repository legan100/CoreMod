package de.niconagel.coremod.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;

public class Tablist {

    private static final Map<String, Rank> ranks = new HashMap<>();

    public void create(String name, int rank, String prefix, String suffix, String permission) {
        ranks.put(permission, new Rank(name, rank, prefix, suffix));
    }

    public void addPlayer(ServerPlayer player) {
        Rank highestRank = null;
        for (Map.Entry<String, Rank> entry : ranks.entrySet()) {
            String group = entry.getKey();
            Rank rank = entry.getValue();
            if (!PermissionCache.hasGroup(player.getUUID(), group)) {
                continue;
            }

            if (highestRank == null || rank.rank() < highestRank.rank()) {
                highestRank = rank;
            }
        }

        if (highestRank == null) {
            return;
        }

        updatePlayer(player, highestRank);
    }

    public void update(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            addPlayer(player);
        }
    }

    private void updatePlayer(ServerPlayer player, Rank rank) {
        String teamName = rank.rank() + "_" + rank.name();
        var scoreboard = player.server.getScoreboard();
        var team = scoreboard.getPlayerTeam(teamName);
        if (team == null) {
            team = scoreboard.addPlayerTeam(teamName);
            if (rank.prefix() != null) {
                team.setPlayerPrefix(Component.literal(rank.prefix()));
            }

            if (rank.suffix() != null) {
                team.setPlayerSuffix(Component.literal(rank.suffix()));
            }
        }

        for (var existingTeam : scoreboard.getPlayerTeams()) {
            if (existingTeam.getPlayers().contains(player.getScoreboardName())) {
                scoreboard.removePlayerFromTeam(player.getScoreboardName(), existingTeam);
            }
        }

        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
    }

    public record Rank(String name, int rank, String prefix, String suffix) {}
}
