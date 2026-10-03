package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Fly {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("fly")
                        .requires(source -> source.hasPermission(4))
                        .executes(context -> toggleFly(context.getSource()))
        );
    }

    private static int toggleFly(CommandSourceStack source) {
        ServerPlayer player;

        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return 0;
        }

        boolean canFly = player.getAbilities().mayfly;

        if (canFly) {
            player.getAbilities().mayfly = false;
            player.getAbilities().flying = false;

            player.displayClientMessage(
                    Component.literal("Flugmodus deaktiviert"),
                    true
            );
        } else {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;

            player.displayClientMessage(
                    Component.literal("Flugmodus aktiviert"),
                    true
            );
        }

        player.onUpdateAbilities();

        return 1;
    }
}
