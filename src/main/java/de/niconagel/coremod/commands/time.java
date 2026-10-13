package de.niconagel.coremod.commands;

import com.mojang.brigadier.CommandDispatcher;
import de.niconagel.coremod.utils.InternalMethods;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class time {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("uhrzeit")
                        .requires(source -> source.getEntity() instanceof ServerPlayer player && player.hasPermissions(4))
                        .executes(context -> showTime(context.getSource()))
        );
    }

    private static int showTime(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) return 0;

        player.sendSystemMessage(
                Component.literal("Aktuelle Uhrzeit: " + InternalMethods.getTime())
        );

        return 1;
    }
}
