package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import de.niconagel.coremod.utils.PermissionCache;
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
                        .executes(context -> {
                            ServerPlayer player =
                                    context.getSource().getPlayerOrException();

                            if (!PermissionCache.contains(player.getUUID())) {

                                player.sendSystemMessage(
                                        Component.literal(
                                                        "Deine Gruppeninformationen werden noch geladen."
                                                )
                                                .withStyle(ChatFormatting.YELLOW)
                                );

                                PermissionCache.loadAsync(
                                        player.getUUID(),
                                        player.getGameProfile().getName()
                                );

                                return 1;
                            }

                            String role =
                                    PermissionCache.getRole(player.getUUID());

                            String lpGroup =
                                    PermissionCache.getLpGroup(player.getUUID());

                            // Obere Linie
                            player.sendSystemMessage(
                                    Component.literal("--------------------")
                                            .withStyle(ChatFormatting.DARK_GRAY)
                            );

                            // Überschrift
                            player.sendSystemMessage(
                                    Component.literal("Deine Gruppeninformationen")
                                            .withStyle(ChatFormatting.GREEN)
                            );

                            // Rang
                            player.sendSystemMessage(
                                    Component.literal("Rang: ")
                                            .withStyle(ChatFormatting.GRAY)
                                            .append(
                                                    Component.literal(role)
                                                            .withStyle(ChatFormatting.WHITE)
                                            )
                            );

                            // Gruppe
                            player.sendSystemMessage(
                                    Component.literal("Gruppe: ")
                                            .withStyle(ChatFormatting.GRAY)
                                            .append(
                                                    Component.literal(lpGroup)
                                                            .withStyle(ChatFormatting.WHITE)
                                            )
                            );

                            // Untere Linie
                            player.sendSystemMessage(
                                    Component.literal("--------------------")
                                            .withStyle(ChatFormatting.DARK_GRAY)
                            );

                            return 1;
                        })
        );
    }
}
