package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import de.niconagel.coremod.utils.PermissionCache;
import de.niconagel.coremod.utils.PlayerManager;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class Group {

    private Group() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("group")
                        .executes(context -> execute(context.getSource()))
        );
    }

    private static int execute(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

        PlayerManager playerManager = new PlayerManager(player);
        String shortUuid = playerManager.getShortUUID();

        PermissionCache.PlayerPermission permission =
                PermissionCache.getPermission(player.getUUID());

        if (permission == null) {
            player.sendSystemMessage(
                    Component.literal("Deine Gruppeninformationen werden geladen. Bitte warte einen Moment...")
                            .withStyle(ChatFormatting.YELLOW)
            );

            PermissionCache.loadAsync(shortUuid, playerManager.getUsername())
                    .thenRun(() -> {
                        if (player.getServer() == null) return;

                        player.getServer().execute(() -> {
                            if (player.getServer().getPlayerList().getPlayer(player.getUUID()) == null) return;

                            player.sendSystemMessage(
                                    Component.literal("Deine Gruppeninformationen wurden erfolgreich geladen! Nutze /group erneut.")
                                            .withStyle(ChatFormatting.GREEN)
                            );
                        });
                    });

            return 1;
        }

        String role = permission.role();
        String lpGroup = permission.lpGroup();

        if (role == null || role.isBlank()) role = "Keine";
        if (lpGroup == null || lpGroup.isBlank()) lpGroup = "Keine";

        player.sendSystemMessage(
                Component.literal("---------------------------------------------")
                        .withStyle(ChatFormatting.DARK_GRAY)
        );
        player.sendSystemMessage(
                Component.literal("Deine Gruppeninformationen")
                        .withStyle(ChatFormatting.GREEN)
        );
        player.sendSystemMessage(
                Component.literal("Rang: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(role).withStyle(ChatFormatting.WHITE))
        );
        player.sendSystemMessage(
                Component.literal("Gruppe: ")
                        .withStyle(ChatFormatting.GRAY)
                        .append(Component.literal(lpGroup).withStyle(ChatFormatting.WHITE))
        );
        player.sendSystemMessage(
                Component.literal("---------------------------------------------")
                        .withStyle(ChatFormatting.DARK_GRAY)
        );

        return 1;
    }
}
