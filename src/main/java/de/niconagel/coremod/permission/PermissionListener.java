package de.niconagel.coremod.permission;

import com.mojang.brigadier.context.CommandContextBuilder;
import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.api.Permissions;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrownEnderpearl;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BoatItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.LeadItem;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Setzt die Minecraft-Rechte aus {@link Permissions} durch.
 * Fake-Player anderer Mods (z.B. Create-Deployer) werden nie eingeschränkt.
 */
@EventBusSubscriber
public final class PermissionListener {

    private static final Map<UUID, Vec3> FROZEN = new HashMap<>();
    private static final ResourceLocation FLY_MODIFIER = ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "fly_permission");

    private PermissionListener() {
    }

    private static boolean deniedSilently(Entity entity, String permission) {
        return PermissionManager.isDenied(entity, permission);
    }

    /** Wie {@link #deniedSilently}, meldet dem Spieler aber das Verbot. */
    private static boolean denied(Entity entity, String permission) {
        if (!deniedSilently(entity, permission)) return false;

        ((ServerPlayer) entity).displayClientMessage(
                Component.literal("Dazu hast du keine Rechte.").withStyle(ChatFormatting.RED),
                true
        );
        return true;
    }

    /** Items, die beim Benutzen ein Entity in die Welt setzen. */
    private static boolean placesEntity(ItemStack stack) {
        Item item = stack.getItem();
        return item instanceof MinecartItem || item instanceof BoatItem || item instanceof ArmorStandItem
                || item instanceof HangingEntityItem || item instanceof SpawnEggItem
                || item instanceof EndCrystalItem || item instanceof LeadItem;
    }

    // ---------------------------------------------------------------- Blöcke

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (denied(event.getPlayer(), Permissions.BLOCK_BREAK)) event.setCanceled(true);
    }

    /**
     * NeoForge feuert das auch für jeden Block, den ein Item beim Benutzen verändert
     * (Feuerzeug, Knochenmehl, Axt, Hacke, Schaufel, Honigwabe, …), nicht nur beim Setzen.
     */
    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (denied(event.getEntity(), Permissions.BLOCK_PLACE)) event.setCanceled(true);
    }

    /** Linksklick: Abbau-Beginn, Notenblöcke, Drachenei, … */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        boolean start = event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START;
        if (start ? denied(event.getEntity(), Permissions.BLOCK_BREAK) : deniedSilently(event.getEntity(), Permissions.BLOCK_BREAK)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onTrample(BlockEvent.FarmlandTrampleEvent event) {
        if (deniedSilently(event.getEntity(), Permissions.BLOCK_BREAK)) event.setCanceled(true);
    }

    /** Explosionen, die ein Spieler ausgelöst hat (TNT, Feuerkugeln, Windkugeln, …). */
    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Start event) {
        if (deniedSilently(event.getExplosion().getIndirectSourceEntity(), Permissions.BLOCK_BREAK)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- Interaktion

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (denied(event.getEntity(), Permissions.INTERACT_BLOCK)) {
            event.setUseBlock(TriState.FALSE);
        }

        // Blöcke setzen prüft onPlace, Entities setzen ENTITY_PLACE, alles andere ITEM_USE
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || stack.getItem() instanceof BlockItem) return;

        String permission = placesEntity(stack) ? Permissions.ENTITY_PLACE : Permissions.ITEM_USE;
        if (denied(event.getEntity(), permission)) {
            event.setUseItem(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getItemStack();
        String permission = stack.getItem() instanceof BucketItem ? Permissions.BLOCK_PLACE
                : placesEntity(stack) ? Permissions.ENTITY_PLACE
                : Permissions.ITEM_USE;

        if (denied(event.getEntity(), permission)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (denied(event.getEntity(), Permissions.INTERACT_ENTITY)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (denied(event.getEntity(), Permissions.INTERACT_ENTITY)) event.setCanceled(true);
    }

    /** Fängt jede GUI mit Slots ab, egal wodurch sie geöffnet wurde. */
    @SubscribeEvent
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        if (denied(event.getEntity(), Permissions.CONTAINER)) event.getEntity().closeContainer();
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting() && denied(event.getEntityMounting(), Permissions.MOUNT)) event.setCanceled(true);
    }

    // ---------------------------------------------------------------- Kampf

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        String permission = event.getTarget() instanceof Player ? Permissions.ATTACK_PLAYER : Permissions.ATTACK_ENTITY;
        if (denied(event.getEntity(), permission)) event.setCanceled(true);
    }

    /** Jeder Schaden, den ein Spieler verursacht: Schwungangriff, Projektile, Tränke, TNT, Dornen, … */
    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        Entity attacker = event.getSource().getEntity();
        LivingEntity target = event.getEntity();
        if (attacker == target) return;

        String permission = target instanceof Player ? Permissions.ATTACK_PLAYER : Permissions.ATTACK_ENTITY;
        if (deniedSilently(attacker, permission)) event.setCanceled(true);
    }

    /** Projektile ohne Recht verschwinden beim Aufprall, statt etwas auszulösen. */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        HitResult hit = event.getRayTraceResult();

        String permission;
        if (hit instanceof EntityHitResult entityHit) {
            // Auch Rahmen, Bilder, Loren, Boote, die keinen normalen Schaden bekommen
            permission = entityHit.getEntity() instanceof Player ? Permissions.ATTACK_PLAYER : Permissions.ATTACK_ENTITY;
        } else if (hit.getType() == HitResult.Type.BLOCK && !(projectile instanceof ThrownEnderpearl)) {
            // Pfeile lösen Knöpfe aus, Eier spawnen Hühner, Feuerpfeile zünden TNT, …
            permission = Permissions.BLOCK_BREAK;
        } else {
            return;
        }

        if (deniedSilently(projectile.getOwner(), permission)) {
            event.setCanceled(true);
            projectile.discard();
        }
    }

    // ---------------------------------------------------------------- Items & Chat

    @SubscribeEvent
    public static void onDrop(ItemTossEvent event) {
        if (!denied(event.getPlayer(), Permissions.ITEM_DROP)) return;

        event.setCanceled(true);
        // NeoForge hat das Item zu diesem Zeitpunkt schon aus dem Inventar genommen → zurückgeben
        event.getPlayer().getInventory().placeItemBackInInventory(event.getEntity().getItem());
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        // ohne Meldung, das Event feuert jeden Tick, solange man auf dem Item steht
        if (deniedSilently(event.getPlayer(), Permissions.ITEM_PICKUP)) event.setCanPickup(TriState.FALSE);
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        if (denied(event.getPlayer(), Permissions.CHAT)) event.setCanceled(true);
    }

    /**
     * Jeder Befehl braucht "command.&lt;name&gt;", auch Vanilla und andere Mods.
     * Verschachtelte Befehle zählen einzeln, sonst wäre "/execute run gamemode …" ein Schlupfloch.
     */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        // Die ursprüngliche Quelle, "execute as …" ändert sie erst beim Ausführen
        CommandContextBuilder<CommandSourceStack> context = event.getParseResults().getContext();
        ServerPlayer player = context.getSource().getPlayer();

        for (CommandContextBuilder<CommandSourceStack> part = context; part != null; part = part.getChild()) {
            if (part.getNodes().isEmpty()) continue;

            String name = part.getNodes().get(0).getNode().getName();
            if (deniedSilently(player, Permissions.command(name))) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("Dazu hast du keine Rechte: /" + name).withStyle(ChatFormatting.RED));
                return;
            }
        }
    }

    // ---------------------------------------------------------------- Spielmodus, Bewegung, Fliegen

    @SubscribeEvent
    public static void onGameModeChange(PlayerEvent.PlayerChangeGameModeEvent event) {
        Player player = event.getEntity();
        if (player instanceof ServerPlayer serverPlayer && !serverPlayer.isFakePlayer()
                && !PermissionManager.mayUseGameMode(player.getUUID(), event.getNewGameMode())) {
            event.setCanceled(true);
            serverPlayer.displayClientMessage(
                    Component.literal("Dazu hast du keine Rechte.").withStyle(ChatFormatting.RED), true);
        }
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isFakePlayer()) return;
        UUID uuid = player.getUUID();

        // Nicht erlaubter Spielmodus (z.B. beim Join schon Creative) → zurücksetzen
        GameType mode = player.gameMode.getGameModeForPlayer();
        if (!PermissionManager.mayUseGameMode(uuid, mode)) {
            player.setGameMode(PermissionManager.fallbackGameMode(uuid));
        }

        if (PermissionManager.has(uuid, Permissions.MOVE)) {
            FROZEN.remove(uuid);
        } else {
            if (player.isPassenger()) player.stopRiding();

            // ponytail: Zurück-Teleportieren statt echter Bewegungssperre; wer in der Luft eingefroren wird, schwebt.
            Vec3 pos = FROZEN.computeIfAbsent(uuid, id -> player.position());
            if (player.position().distanceToSqr(pos) > 0.01) {
                player.connection.teleport(pos.x, pos.y, pos.z, player.getYRot(), player.getXRot());
            }
        }

        // Fliegen über das NeoForge-Attribut; Creative und Spectator fliegen unabhängig davon
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        boolean mayFly = PermissionManager.has(uuid, Permissions.FLY);
        if (flight == null || flight.hasModifier(FLY_MODIFIER) == mayFly) return;

        if (mayFly) {
            flight.addTransientModifier(new AttributeModifier(FLY_MODIFIER, 1, AttributeModifier.Operation.ADD_VALUE));
        } else {
            flight.removeModifier(FLY_MODIFIER);
            if (!player.mayFly()) {
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
    }

    @SubscribeEvent
    public static void onEnderPearl(EntityTeleportEvent.EnderPearl event) {
        if (denied(event.getPlayer(), Permissions.MOVE)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onChorusFruit(EntityTeleportEvent.ChorusFruit event) {
        if (denied(event.getEntityLiving(), Permissions.MOVE)) event.setCanceled(true);
    }

    /** Teleport durch Admins (/tp, /spreadplayers) gilt auch für eingefrorene Spieler. */
    @SubscribeEvent
    public static void onTeleportCommand(EntityTeleportEvent.TeleportCommand event) {
        FROZEN.computeIfPresent(event.getEntity().getUUID(), (id, pos) -> event.getTarget());
    }

    @SubscribeEvent
    public static void onSpreadPlayers(EntityTeleportEvent.SpreadPlayersCommand event) {
        FROZEN.computeIfPresent(event.getEntity().getUUID(), (id, pos) -> event.getTarget());
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        FROZEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        FROZEN.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        FROZEN.remove(event.getEntity().getUUID());
    }
}
