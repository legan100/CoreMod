package de.niconagel.coremod.mainmenu;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.api.Job;
import de.niconagel.coremod.permission.PermissionListener;
import de.niconagel.coremod.permission.PermissionManager;
import de.niconagel.coremod.tf.Abstellungen;
import de.niconagel.coremod.tf.Garnituren;
import de.niconagel.coremod.tf.Strecken;
import de.niconagel.coremod.tf.Zugeinsatz;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serverseite des Hauptmenüs. Beim Join wird der Spieler für andere unsichtbar und schwebend an die View gesetzt (damit der Client
 * dort die Welt geladen hat und sie als Hintergrund zeigen kann) und bekommt das Menü geöffnet.
 * Mit der Job-Wahl ("Spielen" → Job) bekommt er die Rechte des Jobs und kommt zurück an die Stelle, an der er vor dem Disconnect war.
 */
@EventBusSubscriber(value = Dist.DEDICATED_SERVER)
public final class MainMenu {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path VIEW_FILE = Path.of("CoreMod/mainmenu.json");

    /** In den gespeicherten Spielerdaten: wo der Spieler vor dem Menü war. Überlebt Disconnect und Absturz im Menü. */
    private static final String RETURN_KEY = "coremod_menu_return";

    /** Abfahrtszeiten und Zugnummern nach deutscher Uhrzeit, wie der Tageslauf (RealTimeMinecraftDay). */
    private static final ZoneId TIME_ZONE = ZoneId.of("Europe/Berlin");

    private static final ResourceLocation MENU_FLIGHT = ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "main_menu_flight");

    private static final Set<UUID> IN_MENU = ConcurrentHashMap.newKeySet();
    /** Nur für diese Sitzung: nach jedem Join wird im Menü neu gewählt. */
    private static final Map<UUID, Job> JOBS = new ConcurrentHashMap<>();
    /** Gewählte Route der Triebfahrzeugführer, ebenfalls nur für diese Sitzung. */
    private static final Map<UUID, Strecken.Route> ROUTES = new ConcurrentHashMap<>();
    private static volatile View view;

    private MainMenu() {
    }

    /** Kameraposition (Augenhöhe) und Blickrichtung des Menü-Hintergrunds. */
    public record View(String dimension, double x, double y, double z, float yaw, float pitch) {
        static final View DEFAULT = new View("minecraft:overworld", 0, 0, 0, 0, 0);
    }

    /** Spieler im Menü dürfen nichts tun und nehmen keinen Schaden. */
    public static boolean isInMenu(Entity entity) {
        return IN_MENU.contains(entity.getUUID());
    }

    /** @return die gestartete Route eines Triebfahrzeugführers, sonst null */
    public static Strecken.Route getRoute(UUID player) {
        return ROUTES.get(player);
    }

    /** @return der im Menü gewählte Job, null solange der Spieler im Menü oder offline ist */
    public static Job getJob(UUID player) {
        return JOBS.get(player);
    }

    public static View getView() {
        if (view == null) {
            view = loadView();
        }
        return view;
    }

    public static void setView(View newView) throws IOException {
        Files.writeString(VIEW_FILE, GSON.toJson(newView), StandardCharsets.UTF_8);
        view = newView;
    }

    private static View loadView() {
        try {
            if (Files.exists(VIEW_FILE)) {
                View loaded = GSON.fromJson(Files.readString(VIEW_FILE, StandardCharsets.UTF_8), View.class);
                if (loaded != null && loaded.dimension() != null) return loaded;
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.warn("[CoreMod] {} konnte nicht gelesen werden, nutze 0,0,0", VIEW_FILE, e);
        }
        return View.DEFAULT;
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Nur merken, wenn noch nichts gemerkt ist: Wer beim letzten Mal im Menü gegangen ist, steht jetzt an der View
        CompoundTag data = player.getPersistentData();
        if (!data.contains(RETURN_KEY)) {
            data.put(RETURN_KEY, position(player));
        }

        IN_MENU.add(player.getUUID());
        // Ohne Flugrecht fiele der Spieler (und Vanilla kickt fürs Schweben), der Flugmodus hält ihn an der View
        hover(player);

        View target = getView();
        player.teleportTo(level(player.server, target.dimension()),
                target.x(), target.y() - player.getEyeHeight(), target.z(), target.yaw(), target.pitch());

        // Sichtbarkeit für alle anderen neu prüfen, siehe ServerPlayerMixin
        player.serverLevel().getChunkSource().move(player);
        sendMenu(player);
    }

    /**
     * Öffnet das Menü bzw. aktualisiert die wählbaren Jobs. Beim Join gilt der Spieler bis zur API-Antwort als Gast,
     * deshalb schickt der JoinListener die Jobs nach dem Laden der Rolle noch einmal.
     */
    public static void sendMenu(ServerPlayer player) {
        if (!isInMenu(player)) return;
        List<MainMenuNetwork.JobEntry> jobs = Arrays.stream(Job.values())
                .filter(job -> PermissionManager.has(player.getUUID(), job.getPermission()))
                .map(MainMenuNetwork.JobEntry::of)
                .toList();
        boolean tf = PermissionManager.has(player.getUUID(), Job.TF.getPermission());
        PacketDistributor.sendToPlayer(player, MainMenuNetwork.OpenMenu.of(jobs, tf));
    }

    /**
     * Job im Menü gewählt: ab jetzt gelten seine Rechte. Triebfahrzeugführer starten eine Route: Der Zug wird in deren
     * Abstellung eingesetzt und der Spieler daneben gestellt; klappt das nicht, bleibt er im Menü.
     */
    static void play(ServerPlayer player, Job job, String routeNumber) {
        UUID uuid = player.getUUID();
        // Im Menü hat der Spieler noch keinen Job, has() prüft hier also nur die Rolle
        if (!isInMenu(player) || !PermissionManager.has(uuid, job.getPermission())) return;

        Strecken.Route route = null;
        Zugeinsatz.Eingesetzt train = null;
        LocalTime departure = null;
        String zugnummer = null;
        if (job == Job.TF) {
            route = Strecken.route(routeNumber);
            if (route == null) return;
            LocalTime now = LocalTime.now(TIME_ZONE);
            departure = now.plusMinutes(5);
            zugnummer = Strecken.zugnummer(route.number(), now);
            try {
                train = einsetzen(player, route, zugnummer);
            } catch (IllegalStateException e) {
                player.sendSystemMessage(Component.literal("Route " + route.name() + " kann nicht starten: " + e.getMessage())
                        .withStyle(ChatFormatting.RED));
                // Der Client hat das Menü schon verlassen, er bekommt es wieder
                sendMenu(player);
                return;
            }
        }

        IN_MENU.remove(uuid);
        JOBS.put(uuid, job);
        if (route != null) ROUTES.put(uuid, route);

        // ponytail: FDL kommt vorerst auch an die gemerkte Stelle, eigener Einstieg folgt
        CompoundTag back = player.getPersistentData().getCompound(RETURN_KEY);
        player.getPersistentData().remove(RETURN_KEY);

        // Fähigkeiten an den Job anpassen, Fliegen nur wenn vorher geflogen und weiterhin erlaubt
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null) flight.removeModifier(MENU_FLIGHT);
        PermissionListener.applyAbilities(player);
        Abilities abilities = player.getAbilities();
        abilities.flying = false;
        player.gameMode.getGameModeForPlayer().updatePlayerAbilities(abilities);
        abilities.flying |= back.getBoolean("flying") && player.mayFly();
        player.onUpdateAbilities();
        player.resetFallDistance();

        if (train != null) {
            // Neben die Lok, mit Blick in Fahrtrichtung
            Vec3 direction = train.direction();
            Vec3 at = train.front().add(-direction.z * 2.5, 0, direction.x * 2.5);
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            player.teleportTo(level(player.server, train.gleis().dimension()), at.x, at.y, at.z, yaw, 0);
        } else if (!back.isEmpty()) {
            player.teleportTo(level(player.server, back.getString("dimension")),
                    back.getDouble("x"), back.getDouble("y"), back.getDouble("z"),
                    back.getFloat("yaw"), back.getFloat("pitch"));
        }
        player.serverLevel().getChunkSource().move(player);

        // OP-Level für den Client (F3+F4) und Befehlsliste passend zum Job
        player.server.getPlayerList().sendPlayerPermissionLevel(player);

        if (train != null) sendEbula(player, zugnummer, departure, train);
    }

    /**
     * Setzt den Zug der Route ein.
     *
     * @throws IllegalStateException mit dem Grund für den Spieler
     */
    private static Zugeinsatz.Eingesetzt einsetzen(ServerPlayer player, Strecken.Route route, String zugnummer) {
        if (!route.number().matches("\\d{3}")) throw new IllegalStateException("Die Route hat keine dreistellige TrainOS-Nummer.");
        if (route.stops().isEmpty()) throw new IllegalStateException("Die Route hat keine Haltepunkte.");
        Garnituren.Garnitur garnitur = Garnituren.all().stream().filter(g -> g.id().equals(route.garnitur())).findFirst()
                .orElseThrow(() -> new IllegalStateException("Die Route hat keine Garnitur."));
        if (garnitur.vehicles().isEmpty()) throw new IllegalStateException("Die Garnitur \"" + garnitur.name() + "\" ist leer.");
        Abstellungen.Abstellung abstellung = Abstellungen.find(route.abstellung());
        if (abstellung == null) throw new IllegalStateException("Die Route hat keine Abstellung.");

        Strecken.Haltepunkt first = route.stops().getFirst();
        return Zugeinsatz.einsetzen(level(player.server, first.dimension()), abstellung, garnitur.vehicles(), first.punkt(), zugnummer);
    }

    /** Zugnummer, Abfahrt und der EBuLa-Link (als anklickbarer Text, die Adresse selbst steht nicht im Chat). */
    private static void sendEbula(ServerPlayer player, String zugnummer, LocalTime departure, Zugeinsatz.Eingesetzt train) {
        String url = "https://tfs-in-der-blockwelt.themineattack.de/ebula/" + zugnummer + "?path=default&variant=RW";
        Component link = Component.literal("[EBuLa öffnen]").withStyle(style -> style
                .withColor(ChatFormatting.AQUA)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, url))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Buchfahrplan für Zug " + zugnummer + " öffnen"))));

        player.sendSystemMessage(Component.literal("Zug ").withStyle(ChatFormatting.GRAY)
                .append(Component.literal(zugnummer).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
                .append(Component.literal(" steht auf \"" + train.gleis().name() + "\" bereit, Abfahrt "
                        + departure.format(DateTimeFormatter.ofPattern("HH:mm")) + ". ").withStyle(ChatFormatting.GRAY))
                .append(link));
    }

    /**
     * Spielmodus-Wechsel (z.B. wenn die Gruppe nachgeladen ist) setzen die Fähigkeiten zurück,
     * deshalb jeden Tick prüfen.
     */
    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && isInMenu(player)) hover(player);
    }

    private static void hover(ServerPlayer player) {
        AttributeInstance flight = player.getAttribute(NeoForgeMod.CREATIVE_FLIGHT);
        if (flight != null && !flight.hasModifier(MENU_FLIGHT)) {
            flight.addTransientModifier(new AttributeModifier(MENU_FLIGHT, 1, AttributeModifier.Operation.ADD_VALUE));
        }
        if (!player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        // Die gemerkte Position bleibt in den Spielerdaten, beim nächsten Join geht es dorthin zurück
        IN_MENU.remove(event.getEntity().getUUID());
        JOBS.remove(event.getEntity().getUUID());
        ROUTES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (isInMenu(event.getEntity())) event.setCanceled(true);
    }

    private static CompoundTag position(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", player.level().dimension().location().toString());
        tag.putDouble("x", player.getX());
        tag.putDouble("y", player.getY());
        tag.putDouble("z", player.getZ());
        tag.putFloat("yaw", player.getYRot());
        tag.putFloat("pitch", player.getXRot());
        tag.putBoolean("flying", player.getAbilities().flying);
        return tag;
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        ResourceLocation id = ResourceLocation.tryParse(dimension);
        ServerLevel level = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        return level != null ? level : server.overworld();
    }
}
