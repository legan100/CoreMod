package de.niconagel.coremod.permission;

import com.mojang.brigadier.tree.CommandNode;
import com.mojang.logging.LogUtils;
import de.niconagel.coremod.api.Permissions;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpList;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Für Spieler gibt es kein OP: Befehle hängen nur an CoreMod-Rechten.
 * <ul>
 *     <li>Jeder Befehl braucht "command.&lt;name&gt;", egal welches OP-Level Vanilla oder die Mod verlangt.</li>
 *     <li>Unterbefehle, die nur OPs dürften, brauchen ihr eigenes Recht, z.B. "command.claim.admin".
 *     Unterbefehle ohne Sperre bleiben frei.</li>
 *     <li>Konsole, Befehlsblöcke, Funktionen und Fake-Player behalten die ursprüngliche Regel.</li>
 * </ul>
 * Minecraft stellt mit derselben Regel die Befehlsliste für die Autovervollständigung zusammen,
 * was ein Spieler vorgeschlagen bekommt, darf er also auch ausführen.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class CommandPermissions {

    private static final Logger LOGGER = LogUtils.getLogger();

    private CommandPermissions() {
    }

    /** Ohne OPs greifen auch Spawn-Schutz, Whitelist- und Voller-Server-Bypass für niemanden mehr. */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        ServerOpList ops = event.getServer().getPlayerList().getOps();
        if (ops.isEmpty()) return;

        LOGGER.info("[CoreMod] Entferne alle OPs aus der ops.json, Rechte laufen über CoreMod");
        List.copyOf(ops.getEntries()).forEach(ops::remove);
    }

    /** Zuletzt, damit die Befehle aller Mods schon registriert sind. Läuft bei jedem /reload erneut. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        try {
            // Brigadier hat keinen Setter, die Regel ist ein privates final-Feld
            Field requirement = CommandNode.class.getDeclaredField("requirement");
            requirement.setAccessible(true);

            Set<CommandNode<?>> done = Collections.newSetFromMap(new IdentityHashMap<>());
            for (CommandNode<CommandSourceStack> node : event.getDispatcher().getRoot().getChildren()) {
                replace(requirement, node, node.getName(), true, done);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Dann gilt Vanillas OP-Logik mit Level 0: Spieler dürfen nur Befehle ohne OP-Sperre
            LOGGER.error("[CoreMod] Befehlsrechte konnten nicht gesetzt werden", e);
        }
    }

    private static void replace(Field field, CommandNode<CommandSourceStack> node, String path, boolean top,
                                Set<CommandNode<?>> done) throws IllegalAccessException {
        // Manche Mods hängen denselben Knoten mehrfach ein
        if (!done.add(node)) return;

        Predicate<CommandSourceStack> original = node.getRequirement();
        String permission = Permissions.command(path);
        field.set(node, (Predicate<CommandSourceStack>) source -> {
            ServerPlayer player = source.getPlayer();
            if (player == null || player.isFakePlayer()) return original.test(source);
            if (top) return PermissionManager.has(player.getUUID(), permission);
            // Spieler haben OP-Level 0: was die alte Regel erlaubt, darf jeder mit dem Befehl
            return original.test(source) || PermissionManager.has(player.getUUID(), permission);
        });

        for (CommandNode<CommandSourceStack> child : node.getChildren()) {
            replace(field, child, path + "." + child.getName(), false, done);
        }
    }
}
