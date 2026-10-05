package de.niconagel.coremod.commands;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Build {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("build").requires(source -> source.hasPermission(4)).executes(context -> toggleBuild(context.getSource())));
    }

    private static int toggleBuild(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException e) {
            return 0;
        }

        boolean canBuild = player.getAbilities().mayBuild;
        player.getAbilities().mayBuild = !canBuild;
        player.displayClientMessage(Component.literal(player.getAbilities().mayBuild ? "Bauen aktiviert" : "Bauen deaktiviert"),true);
        player.onUpdateAbilities();
        return 1;
    }
}
