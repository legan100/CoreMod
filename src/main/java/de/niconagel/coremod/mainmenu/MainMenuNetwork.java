package de.niconagel.coremod.mainmenu;

import de.niconagel.coremod.Coremod;
import de.niconagel.coremod.api.Job;
import de.niconagel.coremod.api.Permissions;
import de.niconagel.coremod.permission.PermissionManager;
import de.niconagel.coremod.tf.Abstellungen;
import de.niconagel.coremod.tf.Fahrzeugkatalog;
import de.niconagel.coremod.tf.Garnituren;
import de.niconagel.coremod.tf.Gleisnetz;
import de.niconagel.coremod.tf.Strecken;
import de.niconagel.coremod.tf.Weltauswahl;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.List;

/**
 * Pakete zwischen Server und Client fürs Hauptmenü und die Editoren. Die Gegenseite steckt in der Client-Mod
 * (CoremodClient, Zugdaten), Kanäle, Version und Aufbau müssen dort gleich sein.
 * Hier sind die Kanäle Pflicht: Wer die Client-Mod nicht installiert hat, kann dem Server nicht beitreten.
 */
@EventBusSubscriber(modid = Coremod.MOD_ID)
public final class MainMenuNetwork {

    private MainMenuNetwork() {
    }

    // ---------------------------------------------------------------- Codecs (Reihenfolge zählt: jeder nutzt nur die davor)

    private static final StreamCodec<ByteBuf, Abstellungen.Gleispunkt> GLEISPUNKT = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Abstellungen.Gleispunkt::segment,
            ByteBufCodecs.DOUBLE, Abstellungen.Gleispunkt::distance,
            ByteBufCodecs.BOOL, Abstellungen.Gleispunkt::facing,
            ByteBufCodecs.DOUBLE, Abstellungen.Gleispunkt::x,
            ByteBufCodecs.DOUBLE, Abstellungen.Gleispunkt::y,
            ByteBufCodecs.DOUBLE, Abstellungen.Gleispunkt::z,
            Abstellungen.Gleispunkt::new);

    private static final StreamCodec<ByteBuf, Strecken.Haltepunkt> HALTEPUNKT = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Strecken.Haltepunkt::name,
            ByteBufCodecs.STRING_UTF8, Strecken.Haltepunkt::dimension,
            GLEISPUNKT, Strecken.Haltepunkt::punkt,
            Strecken.Haltepunkt::new);

    private static final StreamCodec<ByteBuf, Strecken.Route> ROUTE = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Strecken.Route::number,
            ByteBufCodecs.STRING_UTF8, Strecken.Route::name,
            ByteBufCodecs.STRING_UTF8, Strecken.Route::garnitur,
            ByteBufCodecs.STRING_UTF8, Strecken.Route::abstellung,
            HALTEPUNKT.apply(ByteBufCodecs.list()), Strecken.Route::stops,
            Strecken.Route::new);

    private static final StreamCodec<ByteBuf, List<Strecken.Strecke>> STRECKEN = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Strecken.Strecke::name,
            ROUTE.apply(ByteBufCodecs.list()), Strecken.Strecke::routes,
            Strecken.Strecke::new).apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, Garnituren.Fahrzeug> FAHRZEUG = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Garnituren.Fahrzeug::type,
            ByteBufCodecs.BOOL, Garnituren.Fahrzeug::reversed,
            Garnituren.Fahrzeug::new);

    private static final StreamCodec<ByteBuf, List<Garnituren.Garnitur>> GARNITUREN = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Garnituren.Garnitur::id,
            ByteBufCodecs.STRING_UTF8, Garnituren.Garnitur::name,
            FAHRZEUG.apply(ByteBufCodecs.list()), Garnituren.Garnitur::vehicles,
            Garnituren.Garnitur::new).apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, Fahrzeugkatalog.Fahrzeugtyp> FAHRZEUGTYP = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Fahrzeugkatalog.Fahrzeugtyp::id,
            ByteBufCodecs.STRING_UTF8, Fahrzeugkatalog.Fahrzeugtyp::name,
            ByteBufCodecs.STRING_UTF8, Fahrzeugkatalog.Fahrzeugtyp::category,
            ByteBufCodecs.DOUBLE, Fahrzeugkatalog.Fahrzeugtyp::length,
            ByteBufCodecs.DOUBLE, Fahrzeugkatalog.Fahrzeugtyp::vmax,
            ByteBufCodecs.DOUBLE, Fahrzeugkatalog.Fahrzeugtyp::weight,
            Fahrzeugkatalog.Fahrzeugtyp::new);

    private static final StreamCodec<ByteBuf, Abstellungen.Gleis> GLEIS = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Abstellungen.Gleis::id,
            ByteBufCodecs.STRING_UTF8, Abstellungen.Gleis::name,
            ByteBufCodecs.STRING_UTF8, Abstellungen.Gleis::dimension,
            GLEISPUNKT, Abstellungen.Gleis::start,
            GLEISPUNKT, Abstellungen.Gleis::end,
            ByteBufCodecs.DOUBLE, Abstellungen.Gleis::length,
            Abstellungen.Gleis::new);

    private static final StreamCodec<ByteBuf, List<Abstellungen.Abstellung>> ABSTELLUNGEN = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Abstellungen.Abstellung::id,
            ByteBufCodecs.STRING_UTF8, Abstellungen.Abstellung::name,
            GLEIS.apply(ByteBufCodecs.list()), Abstellungen.Abstellung::gleise,
            Abstellungen.Abstellung::new).apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, List<Vector3f>> LINE = ByteBufCodecs.VECTOR3F.apply(ByteBufCodecs.list());

    // ---------------------------------------------------------------- Hauptmenü

    /** Ein wählbarer Job, als Text: die Client-Mod kennt das Job-Enum nicht. */
    public record JobEntry(String id, String name, String description) {
        static final StreamCodec<ByteBuf, JobEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, JobEntry::id,
                ByteBufCodecs.STRING_UTF8, JobEntry::name,
                ByteBufCodecs.STRING_UTF8, JobEntry::description,
                JobEntry::new);

        static JobEntry of(Job job) {
            return new JobEntry(job.name(), job.getDisplayName(), job.getDescription());
        }
    }

    /** Garnituren samt MR-Fahrzeugkatalog: Der Client braucht beides, um Länge, Gewicht und Vmax anzuzeigen. */
    public record Zugdaten(List<Garnituren.Garnitur> garnituren, List<Fahrzeugkatalog.Fahrzeugtyp> katalog) {
        static final StreamCodec<ByteBuf, Zugdaten> CODEC = StreamCodec.composite(
                GARNITUREN, Zugdaten::garnituren,
                FAHRZEUGTYP.apply(ByteBufCodecs.list()), Zugdaten::katalog,
                Zugdaten::new);

        static final Zugdaten NONE = new Zugdaten(List.of(), List.of());

        static Zugdaten current() {
            return new Zugdaten(Garnituren.all(), Fahrzeugkatalog.all());
        }
    }

    /**
     * Server → Client: Hauptmenü öffnen bzw. die wählbaren Jobs aktualisieren.
     * Strecken, Zugdaten und Abstellungen nur, wenn der Spieler Triebfahrzeugführer wählen darf, sonst leer.
     */
    public record OpenMenu(List<JobEntry> jobs, List<Strecken.Strecke> strecken, Zugdaten zugdaten,
                           List<Abstellungen.Abstellung> abstellungen) implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "open_main_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.composite(
                JobEntry.CODEC.apply(ByteBufCodecs.list()), OpenMenu::jobs,
                STRECKEN, OpenMenu::strecken,
                Zugdaten.CODEC, OpenMenu::zugdaten,
                ABSTELLUNGEN, OpenMenu::abstellungen,
                OpenMenu::new);

        static OpenMenu of(List<JobEntry> jobs, boolean tf) {
            return tf ? new OpenMenu(jobs, Strecken.all(), Zugdaten.current(), Abstellungen.all())
                    : new OpenMenu(jobs, List.of(), Zugdaten.NONE, List.of());
        }

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Client → Server: Job gewählt ({@link JobEntry#id()}), beim Triebfahrzeugführer mit TrainOS-Routennummer, sonst "". */
    public record Play(String job, String route) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Play::job,
                ByteBufCodecs.STRING_UTF8, Play::route,
                Play::new);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** Server → Client: Fahrt zu Ende, abblenden; der Server holt den Spieler zurück ins Menü, wenn es schwarz ist. */
    public record FadeToMenu() implements CustomPacketPayload {
        public static final FadeToMenu INSTANCE = new FadeToMenu();
        public static final Type<FadeToMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "fade_to_menu"));
        public static final StreamCodec<ByteBuf, FadeToMenu> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<FadeToMenu> type() {
            return TYPE;
        }
    }

    /**
     * Server → Client: Halte der laufenden Fahrt, die beim Fahrer leuchten; {@code done} = dort hat der Zug schon
     * gestanden. Keine Halte = keine Fahrt (mehr).
     */
    public record FahrtState(List<Marker> stops, List<Boolean> done) implements CustomPacketPayload {
        public static final FahrtState NONE = new FahrtState(List.of(), List.of());
        public static final Type<FahrtState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "fahrt_state"));
        public static final StreamCodec<ByteBuf, FahrtState> CODEC = StreamCodec.composite(
                Marker.CODEC.apply(ByteBufCodecs.list()), FahrtState::stops,
                ByteBufCodecs.BOOL.apply(ByteBufCodecs.list()), FahrtState::done,
                FahrtState::new);

        @Override
        public Type<FahrtState> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- Editoren

    /**
     * Server → Client: einen der Editoren öffnen ({@link #ROUTES}, {@link #TRAINS}, {@link #SPAWNS}). Alle bekommen alles:
     * der Routen-Editor zeigt Garnituren und Abstellungen an, der Zug-Editor warnt beim Löschen vor Routen, die sie nutzen, usw.
     *
     * @param broken IDs der Gleise, deren Spline es nicht mehr gibt (umgebaut oder abgerissen)
     */
    public record OpenEditor(int editor, List<Strecken.Strecke> strecken, Zugdaten zugdaten,
                             List<Abstellungen.Abstellung> abstellungen, List<String> broken) implements CustomPacketPayload {
        public static final int ROUTES = 0;
        public static final int TRAINS = 1;
        public static final int SPAWNS = 2;

        public static final Type<OpenEditor> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "open_editor"));
        public static final StreamCodec<ByteBuf, OpenEditor> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenEditor::editor,
                STRECKEN, OpenEditor::strecken,
                Zugdaten.CODEC, OpenEditor::zugdaten,
                ABSTELLUNGEN, OpenEditor::abstellungen,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenEditor::broken,
                OpenEditor::new);

        public static OpenEditor of(int editor) {
            List<String> broken = Abstellungen.all().stream().flatMap(abstellung -> abstellung.gleise().stream())
                    .filter(gleis -> !Gleisnetz.exists(gleis.dimension(), gleis.start()) || !Gleisnetz.exists(gleis.dimension(), gleis.end()))
                    .map(Abstellungen.Gleis::id)
                    .toList();
            return new OpenEditor(editor, Strecken.all(), Zugdaten.current(), Abstellungen.all(), broken);
        }

        @Override
        public Type<OpenEditor> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Strecken nach einer Änderung im Routen-Editor. */
    public record SaveStrecken(List<Strecken.Strecke> strecken) implements CustomPacketPayload {
        public static final Type<SaveStrecken> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "save_strecken"));
        public static final StreamCodec<ByteBuf, SaveStrecken> CODEC = STRECKEN.map(SaveStrecken::new, SaveStrecken::strecken);

        @Override
        public Type<SaveStrecken> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Garnituren nach einer Änderung im Zug-Editor. */
    public record SaveGarnituren(List<Garnituren.Garnitur> garnituren) implements CustomPacketPayload {
        public static final Type<SaveGarnituren> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "save_garnituren"));
        public static final StreamCodec<ByteBuf, SaveGarnituren> CODEC = GARNITUREN.map(SaveGarnituren::new, SaveGarnituren::garnituren);

        @Override
        public Type<SaveGarnituren> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Abstellungen nach einer Änderung im Abstellungs-Editor. */
    public record SaveAbstellungen(List<Abstellungen.Abstellung> abstellungen) implements CustomPacketPayload {
        public static final Type<SaveAbstellungen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "save_abstellungen"));
        public static final StreamCodec<ByteBuf, SaveAbstellungen> CODEC = ABSTELLUNGEN.map(SaveAbstellungen::new, SaveAbstellungen::abstellungen);

        @Override
        public Type<SaveAbstellungen> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- Punkte in der Welt setzen (Weltauswahl)

    /** Client → Server: neues Gleis für die Abstellung per Rechtsklick setzen (der Editor ist dann zu). */
    public record StartGleis(String abstellung, String name) implements CustomPacketPayload {
        public static final Type<StartGleis> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "start_gleis"));
        public static final StreamCodec<ByteBuf, StartGleis> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StartGleis::abstellung,
                ByteBufCodecs.STRING_UTF8, StartGleis::name,
                StartGleis::new);

        @Override
        public Type<StartGleis> type() {
            return TYPE;
        }
    }

    /** Client → Server: neue Route auf der Strecke; Haltepunkte und Abstellung folgen in der Welt. */
    public record StartRoute(String strecke, String name, String number, String garnitur) implements CustomPacketPayload {
        public static final Type<StartRoute> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "start_route"));
        public static final StreamCodec<ByteBuf, StartRoute> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, StartRoute::strecke,
                ByteBufCodecs.STRING_UTF8, StartRoute::name,
                ByteBufCodecs.STRING_UTF8, StartRoute::number,
                ByteBufCodecs.STRING_UTF8, StartRoute::garnitur,
                StartRoute::new);

        @Override
        public Type<StartRoute> type() {
            return TYPE;
        }
    }

    /**
     * Client → Server: Klick in der Welt. {@code action} siehe {@link Weltauswahl#SET}, {@link Weltauswahl#DONE},
     * {@link Weltauswahl#UNDO}; {@code name} = Name des Haltepunkts, sonst "".
     */
    public record AuswahlClick(Vector3f point, int action, String name) implements CustomPacketPayload {
        public static final Type<AuswahlClick> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "auswahl_click"));
        public static final StreamCodec<ByteBuf, AuswahlClick> CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, AuswahlClick::point,
                ByteBufCodecs.VAR_INT, AuswahlClick::action,
                ByteBufCodecs.STRING_UTF8, AuswahlClick::name,
                AuswahlClick::new);

        @Override
        public Type<AuswahlClick> type() {
            return TYPE;
        }
    }

    /** Beschrifteter Punkt in der Welt, z.B. der gesetzte Anfang eines Gleises oder ein Haltepunkt. */
    public record Marker(Vector3f position, String label) {
        static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, Marker::position,
                ByteBufCodecs.STRING_UTF8, Marker::label,
                Marker::new);
    }

    /**
     * Server → Client: Stand der Weltauswahl. {@code mode} = {@link Weltauswahl#GLEIS}/{@link Weltauswahl#HALTE}, 0 beendet sie.
     * {@code network} = Splines in der Nähe (je Segment eine Linie), {@code own} = Gleise der Abstellung,
     * {@code others} = Gleise anderer Abstellungen (belegt).
     */
    public record AuswahlState(int mode, String title, List<List<Vector3f>> network, List<List<Vector3f>> own,
                               List<List<Vector3f>> others, List<Marker> markers) implements CustomPacketPayload {
        public static final AuswahlState INACTIVE = new AuswahlState(0, "", List.of(), List.of(), List.of(), List.of());

        public static final Type<AuswahlState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "auswahl_state"));
        public static final StreamCodec<ByteBuf, AuswahlState> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, AuswahlState::mode,
                ByteBufCodecs.STRING_UTF8, AuswahlState::title,
                LINE.apply(ByteBufCodecs.list()), AuswahlState::network,
                LINE.apply(ByteBufCodecs.list()), AuswahlState::own,
                LINE.apply(ByteBufCodecs.list()), AuswahlState::others,
                Marker.CODEC.apply(ByteBufCodecs.list()), AuswahlState::markers,
                AuswahlState::new);

        @Override
        public Type<AuswahlState> type() {
            return TYPE;
        }
    }

    /** Eintrag einer Auswahlliste. */
    public record Choice(String id, String name, String detail) {
        static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Choice::id,
                ByteBufCodecs.STRING_UTF8, Choice::name,
                ByteBufCodecs.STRING_UTF8, Choice::detail,
                Choice::new);
    }

    /** Server → Client: Abstellung für die neue Route wählen; nur die, von denen der Zug den ersten Halt erreicht. */
    public record ChooseAbstellung(List<Choice> choices) implements CustomPacketPayload {
        public static final Type<ChooseAbstellung> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "choose_abstellung"));
        public static final StreamCodec<ByteBuf, ChooseAbstellung> CODEC = Choice.CODEC.apply(ByteBufCodecs.list())
                .map(ChooseAbstellung::new, ChooseAbstellung::choices);

        @Override
        public Type<ChooseAbstellung> type() {
            return TYPE;
        }
    }

    /** Client → Server: gewählte Abstellung für die neue Route. */
    public record AbstellungChosen(String id) implements CustomPacketPayload {
        public static final Type<AbstellungChosen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Coremod.MOD_ID, "abstellung_chosen"));
        public static final StreamCodec<ByteBuf, AbstellungChosen> CODEC = ByteBufCodecs.STRING_UTF8.map(AbstellungChosen::new, AbstellungChosen::id);

        @Override
        public Type<AbstellungChosen> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- Empfangen

    private interface IoAction {
        void run() throws IOException;
    }

    private static boolean mayEdit(ServerPlayer player) {
        // Dasselbe Recht wie für /mainmenu: der Client könnte die Pakete auch ohne den Befehl schicken
        return PermissionManager.has(player.getUUID(), Permissions.COMMAND_MAINMENU);
    }

    /** Speichert Änderungen aus einem Editor. */
    private static void save(ServerPlayer player, String what, IoAction action) {
        if (!mayEdit(player)) return;

        try {
            action.run();
        } catch (IOException | IllegalArgumentException e) {
            player.sendSystemMessage(Component.literal(what + " nicht gespeichert: " + e.getMessage()).withStyle(ChatFormatting.RED));
            return;
        }
        // Wer gerade im Hauptmenü ist, bekommt die Änderungen sofort
        player.server.getPlayerList().getPlayers().forEach(MainMenu::sendMenu);
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("10");

        registrar.playToClient(FadeToMenu.TYPE, FadeToMenu.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
        registrar.playToClient(FahrtState.TYPE, FahrtState.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });

        // Hauptmenü
        registrar.playToClient(OpenMenu.TYPE, OpenMenu.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
        registrar.playToServer(Play.TYPE, Play.CODEC, (payload, context) -> {
            Job job = Job.byName(payload.job());
            if (job != null) MainMenu.play((ServerPlayer) context.player(), job, payload.route());
        });

        // Editoren
        registrar.playToClient(OpenEditor.TYPE, OpenEditor.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
        registrar.playToServer(SaveStrecken.TYPE, SaveStrecken.CODEC,
                (payload, context) -> save((ServerPlayer) context.player(), "Strecken", () -> Strecken.save(payload.strecken())));
        registrar.playToServer(SaveGarnituren.TYPE, SaveGarnituren.CODEC,
                (payload, context) -> save((ServerPlayer) context.player(), "Garnituren", () -> {
                    Garnituren.save(payload.garnituren());
                    // Routen mit gelöschter Garnitur stehen danach auf "keine Garnitur"
                    Strecken.unlinkMissingGarnituren(Garnituren.ids());
                }));
        registrar.playToServer(SaveAbstellungen.TYPE, SaveAbstellungen.CODEC,
                (payload, context) -> save((ServerPlayer) context.player(), "Abstellungen", () -> Abstellungen.save(payload.abstellungen())));

        // Weltauswahl
        registrar.playToServer(StartGleis.TYPE, StartGleis.CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (mayEdit(player)) Weltauswahl.startGleis(player, payload.abstellung(), payload.name());
        });
        registrar.playToServer(StartRoute.TYPE, StartRoute.CODEC, (payload, context) -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (mayEdit(player)) Weltauswahl.startRoute(player, payload.strecke(), payload.name(), payload.number(), payload.garnitur());
        });
        registrar.playToServer(AuswahlClick.TYPE, AuswahlClick.CODEC,
                (payload, context) -> Weltauswahl.click((ServerPlayer) context.player(), payload.point(), payload.action(), payload.name()));
        registrar.playToServer(AbstellungChosen.TYPE, AbstellungChosen.CODEC,
                (payload, context) -> Weltauswahl.chooseAbstellung((ServerPlayer) context.player(), payload.id()));
        registrar.playToClient(AuswahlState.TYPE, AuswahlState.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
        registrar.playToClient(ChooseAbstellung.TYPE, ChooseAbstellung.CODEC, (payload, context) -> {
            // kommt nur beim Client an
        });
    }
}
