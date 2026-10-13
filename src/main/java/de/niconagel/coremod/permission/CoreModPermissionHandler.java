package de.niconagel.coremod.permission;

import de.niconagel.coremod.Coremod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.DefaultPermissionHandler;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * Rechte-Handler für NeoForges Permission-API: Mods, die ihre Rechte darüber abfragen, hängen an der
 * permissions.json statt am OP-Level. Ein Ja/Nein-Recht gilt, wenn die Mod es standardmäßig erlaubt oder es
 * per Name (z.B. "neoforge.use_entity_selectors") in der permissions.json steht. Zahlen und Texte bleiben beim
 * Standard der Mod, die lassen sich in der permissions.json nicht ausdrücken.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class CoreModPermissionHandler implements IPermissionHandler {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "permissions");

    private final Set<PermissionNode<?>> nodes;

    private CoreModPermissionHandler(Collection<PermissionNode<?>> nodes) {
        this.nodes = Set.copyOf(nodes);
    }

    /** Wird automatisch aktiv, solange in neoforge-server.toml noch der Standard-Handler eingetragen ist. */
    @SubscribeEvent
    public static void onGatherHandlers(PermissionGatherEvent.Handler event) {
        event.addPermissionHandler(ID, CoreModPermissionHandler::new);

        ModConfigSpec.ConfigValue<String> selected = NeoForgeConfig.SERVER.permissionHandler;
        if (selected.get().equals(DefaultPermissionHandler.IDENTIFIER.toString())) {
            selected.set(ID.toString());
        }
    }

    @Override
    public ResourceLocation getIdentifier() {
        return ID;
    }

    @Override
    public Set<PermissionNode<?>> getRegisteredNodes() {
        return nodes;
    }

    @Override
    public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return resolve(player, player.getUUID(), node, context);
    }

    @Override
    public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        return resolve(null, player, node, context);
    }

    @SuppressWarnings("unchecked")
    private static <T> T resolve(@Nullable ServerPlayer player, UUID uuid, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
        T standard = node.getDefaultResolver().resolve(player, uuid, context);
        if (node.getType() != PermissionTypes.BOOLEAN || Boolean.TRUE.equals(standard)
                || player != null && player.isFakePlayer()) {
            return standard;
        }
        return (T) (Boolean) PermissionManager.has(uuid, node.getNodeName());
    }
}
