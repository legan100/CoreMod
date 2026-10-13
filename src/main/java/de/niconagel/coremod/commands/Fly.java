package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
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
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

        boolean canFly = player.getAbilities().mayfly;

        player.getAbilities().mayfly = !canFly;
        player.getAbilities().flying = !canFly;

        player.displayClientMessage(
                Component.literal(canFly ? "Flugmodus deaktiviert" : "Flugmodus aktiviert"),
                true
        );

        player.onUpdateAbilities();
        return 1;
    }
}
