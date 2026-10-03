package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

public class Color {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("color")
                        .executes(context -> {

                            ServerPlayer player = context.getSource().getPlayerOrException();

                            player.sendSystemMessage(
                                    Component.literal("§8§m-----------------------------")
                            );

                            player.sendSystemMessage(
                                    Component.literal("§6§lMinecraft Farbcodes")
                            );

                            player.sendSystemMessage(
                                    Component.literal("§8§m-----------------------------")
                            );

                            player.sendSystemMessage(createColor("&0", ChatFormatting.BLACK));
                            player.sendSystemMessage(createColor("&1", ChatFormatting.DARK_BLUE));
                            player.sendSystemMessage(createColor("&2", ChatFormatting.DARK_GREEN));
                            player.sendSystemMessage(createColor("&3", ChatFormatting.DARK_AQUA));
                            player.sendSystemMessage(createColor("&4", ChatFormatting.DARK_RED));
                            player.sendSystemMessage(createColor("&5", ChatFormatting.DARK_PURPLE));
                            player.sendSystemMessage(createColor("&6", ChatFormatting.GOLD));
                            player.sendSystemMessage(createColor("&7", ChatFormatting.GRAY));
                            player.sendSystemMessage(createColor("&8", ChatFormatting.DARK_GRAY));
                            player.sendSystemMessage(createColor("&9", ChatFormatting.BLUE));
                            player.sendSystemMessage(createColor("&a", ChatFormatting.GREEN));
                            player.sendSystemMessage(createColor("&b", ChatFormatting.AQUA));
                            player.sendSystemMessage(createColor("&c", ChatFormatting.RED));
                            player.sendSystemMessage(createColor("&d", ChatFormatting.LIGHT_PURPLE));
                            player.sendSystemMessage(createColor("&e", ChatFormatting.YELLOW));
                            player.sendSystemMessage(createColor("&f", ChatFormatting.WHITE));

                            player.sendSystemMessage(
                                    Component.literal("§8§m-----------------------------")
                            );

                            player.sendSystemMessage(
                                    Component.literal("§7Beispiel: §a&aHallo Welt")
                            );

                            return 1;
                        })
        );
    }

    private static MutableComponent createColor(
            String code,
            ChatFormatting formatting
    ) {
        return Component.empty()
                .append(
                        Component.literal(code + "  ")
                                .withStyle(ChatFormatting.GRAY)
                )
                .append(
                        Component.literal("Beispiel Text")
                                .withStyle(formatting)
                );
    }
}
