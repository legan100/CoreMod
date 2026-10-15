package de.niconagel.coremod.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.joml.Vector3f;

import java.util.List;

/**
 * Client-Teil von CoreMod: Hauptmenü und Editoren. Alles öffnet sich ausschließlich auf Anweisung des Servers,
 * auf Servern ohne CoreMod (und im Einzelspieler) macht die Mod also nichts.
 */
@Mod(value = CoremodClient.MOD_ID, dist = Dist.CLIENT)
public final class CoremodClient {

    public static final String MOD_ID = "coremod_client";

    public CoremodClient(IEventBus modBus) {
        modBus.addListener(CoremodClient::registerPayloads);
    }

    // Kanäle, Version und Aufbau müssen zu MainMenuNetwork in der Server-Mod passen

    private static final StreamCodec<ByteBuf, List<Vector3f>> LINE = ByteBufCodecs.VECTOR3F.apply(ByteBufCodecs.list());

    /** Ein wählbarer Job. {@code id} geht beim Wählen an den Server zurück. */
    public record JobEntry(String id, String name, String description) {
        static final StreamCodec<ByteBuf, JobEntry> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, JobEntry::id,
                ByteBufCodecs.STRING_UTF8, JobEntry::name,
                ByteBufCodecs.STRING_UTF8, JobEntry::description,
                JobEntry::new);
    }

    /** Punkt auf einer MR-Spline; die Koordinaten sind zur Anzeige. */
    public record Gleispunkt(String segment, double distance, boolean facing, double x, double y, double z) {
        static final StreamCodec<ByteBuf, Gleispunkt> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Gleispunkt::segment,
                ByteBufCodecs.DOUBLE, Gleispunkt::distance,
                ByteBufCodecs.BOOL, Gleispunkt::facing,
                ByteBufCodecs.DOUBLE, Gleispunkt::x,
                ByteBufCodecs.DOUBLE, Gleispunkt::y,
                ByteBufCodecs.DOUBLE, Gleispunkt::z,
                Gleispunkt::new);
    }

    /** Halt einer Route, in der Welt gesetzt. */
    public record Haltepunkt(String name, String dimension, Gleispunkt punkt) {
        static final StreamCodec<ByteBuf, Haltepunkt> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Haltepunkt::name,
                ByteBufCodecs.STRING_UTF8, Haltepunkt::dimension,
                Gleispunkt.CODEC, Haltepunkt::punkt,
                Haltepunkt::new);
    }

    /**
     * Fahrt eines Triebfahrzeugführers.
     *
     * @param number     TrainOS-Routennummer, dreistellig
     * @param name       z.B. "RE10"
     * @param garnitur   ID der Garnitur, "" = keine
     * @param abstellung ID der Abstellung, in der der Zug eingesetzt wird, "" = keine
     * @param stops      Haltepunkte in Fahrtreihenfolge
     */
    public record Route(String number, String name, String garnitur, String abstellung, List<Haltepunkt> stops) {
        static final StreamCodec<ByteBuf, Route> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Route::number,
                ByteBufCodecs.STRING_UTF8, Route::name,
                ByteBufCodecs.STRING_UTF8, Route::garnitur,
                ByteBufCodecs.STRING_UTF8, Route::abstellung,
                Haltepunkt.CODEC.apply(ByteBufCodecs.list()), Route::stops,
                Route::new);

        /** "Nürnberg Hbf → Würzburg Hbf", der erste und letzte Halt. */
        String fromTo() {
            return stops.isEmpty() ? "keine Haltepunkte" : stops.getFirst().name() + " → " + stops.getLast().name();
        }
    }

    public record Strecke(String name, List<Route> routes) {
        static final StreamCodec<ByteBuf, List<Strecke>> LIST = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Strecke::name,
                Route.CODEC.apply(ByteBufCodecs.list()), Strecke::routes,
                Strecke::new).apply(ByteBufCodecs.list());
    }

    /** Gleis einer Abstellung: Stück Spline von start nach end, {@code length} in Metern. */
    public record Gleis(String id, String name, String dimension, Gleispunkt start, Gleispunkt end, double length) {
        static final StreamCodec<ByteBuf, Gleis> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Gleis::id,
                ByteBufCodecs.STRING_UTF8, Gleis::name,
                ByteBufCodecs.STRING_UTF8, Gleis::dimension,
                Gleispunkt.CODEC, Gleis::start,
                Gleispunkt.CODEC, Gleis::end,
                ByteBufCodecs.DOUBLE, Gleis::length,
                Gleis::new);
    }

    /** Bereich mit Gleisen, auf denen Züge eingesetzt werden können. */
    public record Abstellung(String id, String name, List<Gleis> gleise) {
        static final StreamCodec<ByteBuf, List<Abstellung>> LIST = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Abstellung::id,
                ByteBufCodecs.STRING_UTF8, Abstellung::name,
                Gleis.CODEC.apply(ByteBufCodecs.list()), Abstellung::gleise,
                Abstellung::new).apply(ByteBufCodecs.list());

        /** @return der Name oder null, wenn es die Abstellung nicht (mehr) gibt */
        static String name(List<Abstellung> abstellungen, String id) {
            return abstellungen.stream().filter(abstellung -> abstellung.id().equals(id)).map(Abstellung::name).findFirst().orElse(null);
        }
    }

    // ---------------------------------------------------------------- Hauptmenü

    /** Server → Client: Hauptmenü öffnen bzw. die wählbaren Jobs aktualisieren. Strecken usw. nur für Triebfahrzeugführer. */
    public record OpenMenu(List<JobEntry> jobs, List<Strecke> strecken, Zugdaten zugdaten, List<Abstellung> abstellungen)
            implements CustomPacketPayload {
        public static final Type<OpenMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "open_main_menu"));
        public static final StreamCodec<ByteBuf, OpenMenu> CODEC = StreamCodec.composite(
                JobEntry.CODEC.apply(ByteBufCodecs.list()), OpenMenu::jobs,
                Strecke.LIST, OpenMenu::strecken,
                Zugdaten.CODEC, OpenMenu::zugdaten,
                Abstellung.LIST, OpenMenu::abstellungen,
                OpenMenu::new);

        @Override
        public Type<OpenMenu> type() {
            return TYPE;
        }
    }

    /** Client → Server: Job gewählt, beim Triebfahrzeugführer mit TrainOS-Routennummer, sonst "". */
    public record Play(String job, String route) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "play"));
        public static final StreamCodec<ByteBuf, Play> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Play::job,
                ByteBufCodecs.STRING_UTF8, Play::route,
                Play::new);

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    /** Server → Client: Fahrt zu Ende, abblenden; zurück ins Menü holt der Server, siehe {@link Abblende}. */
    public record FadeToMenu() implements CustomPacketPayload {
        public static final FadeToMenu INSTANCE = new FadeToMenu();
        public static final Type<FadeToMenu> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "fade_to_menu"));
        public static final StreamCodec<ByteBuf, FadeToMenu> CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<FadeToMenu> type() {
            return TYPE;
        }
    }

    /** Server → Client: Halte der laufenden Fahrt, siehe {@link Haltanzeige}. Keine Halte = keine Fahrt (mehr). */
    public record FahrtState(List<Marker> stops, List<Boolean> done) implements CustomPacketPayload {
        static final FahrtState NONE = new FahrtState(List.of(), List.of());
        public static final Type<FahrtState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "fahrt_state"));
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
     * Server → Client: einen der Editoren öffnen (/mainmenu routes, … trains, … spawns).
     *
     * @param broken IDs der Gleise, deren Spline es nicht mehr gibt
     */
    public record OpenEditor(int editor, List<Strecke> strecken, Zugdaten zugdaten, List<Abstellung> abstellungen,
                             List<String> broken) implements CustomPacketPayload {
        static final int ROUTES = 0;
        static final int TRAINS = 1;

        public static final Type<OpenEditor> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "open_editor"));
        public static final StreamCodec<ByteBuf, OpenEditor> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, OpenEditor::editor,
                Strecke.LIST, OpenEditor::strecken,
                Zugdaten.CODEC, OpenEditor::zugdaten,
                Abstellung.LIST, OpenEditor::abstellungen,
                ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), OpenEditor::broken,
                OpenEditor::new);

        @Override
        public Type<OpenEditor> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Strecken nach einer Änderung im Routen-Editor. */
    public record SaveStrecken(List<Strecke> strecken) implements CustomPacketPayload {
        public static final Type<SaveStrecken> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "save_strecken"));
        public static final StreamCodec<ByteBuf, SaveStrecken> CODEC = Strecke.LIST.map(SaveStrecken::new, SaveStrecken::strecken);

        @Override
        public Type<SaveStrecken> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Garnituren nach einer Änderung im Zug-Editor. */
    public record SaveGarnituren(List<Zugdaten.Garnitur> garnituren) implements CustomPacketPayload {
        public static final Type<SaveGarnituren> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "save_garnituren"));
        public static final StreamCodec<ByteBuf, SaveGarnituren> CODEC = Zugdaten.GARNITUREN.map(SaveGarnituren::new, SaveGarnituren::garnituren);

        @Override
        public Type<SaveGarnituren> type() {
            return TYPE;
        }
    }

    /** Client → Server: alle Abstellungen nach einer Änderung im Abstellungs-Editor. */
    public record SaveAbstellungen(List<Abstellung> abstellungen) implements CustomPacketPayload {
        public static final Type<SaveAbstellungen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "save_abstellungen"));
        public static final StreamCodec<ByteBuf, SaveAbstellungen> CODEC = Abstellung.LIST.map(SaveAbstellungen::new, SaveAbstellungen::abstellungen);

        @Override
        public Type<SaveAbstellungen> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- Punkte in der Welt setzen (Weltauswahl)

    /** Client → Server: neues Gleis für die Abstellung per Rechtsklick setzen. */
    public record StartGleis(String abstellung, String name) implements CustomPacketPayload {
        public static final Type<StartGleis> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "start_gleis"));
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
        public static final Type<StartRoute> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "start_route"));
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

    /** Client → Server: Klick in der Welt, siehe {@link Weltauswahl}; {@code name} = Name des Haltepunkts, sonst "". */
    public record AuswahlClick(Vector3f point, int action, String name) implements CustomPacketPayload {
        public static final Type<AuswahlClick> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "auswahl_click"));
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

    /** Beschrifteter Punkt in der Welt. */
    public record Marker(Vector3f position, String label) {
        static final StreamCodec<ByteBuf, Marker> CODEC = StreamCodec.composite(
                ByteBufCodecs.VECTOR3F, Marker::position,
                ByteBufCodecs.STRING_UTF8, Marker::label,
                Marker::new);
    }

    /** Server → Client: Stand der Weltauswahl, siehe {@link Weltauswahl}. {@code mode} 0 beendet sie. */
    public record AuswahlState(int mode, String title, List<List<Vector3f>> network, List<List<Vector3f>> own,
                               List<List<Vector3f>> others, List<Marker> markers) implements CustomPacketPayload {
        static final AuswahlState INACTIVE = new AuswahlState(0, "", List.of(), List.of(), List.of(), List.of());

        public static final Type<AuswahlState> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "auswahl_state"));
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

    /** Eintrag einer Auswahlliste vom Server. */
    public record Choice(String id, String name, String detail) {
        static final StreamCodec<ByteBuf, Choice> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Choice::id,
                ByteBufCodecs.STRING_UTF8, Choice::name,
                ByteBufCodecs.STRING_UTF8, Choice::detail,
                Choice::new);
    }

    /** Server → Client: Abstellung für die neue Route wählen; nur die, von denen der Zug den ersten Halt erreicht. */
    public record ChooseAbstellung(List<Choice> choices) implements CustomPacketPayload {
        public static final Type<ChooseAbstellung> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "choose_abstellung"));
        public static final StreamCodec<ByteBuf, ChooseAbstellung> CODEC = Choice.CODEC.apply(ByteBufCodecs.list())
                .map(ChooseAbstellung::new, ChooseAbstellung::choices);

        @Override
        public Type<ChooseAbstellung> type() {
            return TYPE;
        }
    }

    /** Client → Server: gewählte Abstellung für die neue Route. */
    public record AbstellungChosen(String id) implements CustomPacketPayload {
        public static final Type<AbstellungChosen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("coremod", "abstellung_chosen"));
        public static final StreamCodec<ByteBuf, AbstellungChosen> CODEC = ByteBufCodecs.STRING_UTF8.map(AbstellungChosen::new, AbstellungChosen::id);

        @Override
        public Type<AbstellungChosen> type() {
            return TYPE;
        }
    }

    // ---------------------------------------------------------------- Empfangen

    /** Abbrechen bleibt in der Welt bei den Haltepunkten, der Server wartet weiter. */
    private static void chooseAbstellung(List<Choice> choices) {
        List<PickerScreen.Option> options = choices.stream()
                .map(choice -> new PickerScreen.Option(choice.id(), Component.literal(choice.name()),
                        TreeEditorScreen.gray(choice.detail()), ""))
                .toList();
        Minecraft.getInstance().setScreen(new PickerScreen(null, "Abstellung für den Zug wählen", options, "Auswählen", false,
                id -> PacketDistributor.sendToServer(new AbstellungChosen(id))));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: Server ohne CoreMod lassen sich trotzdem betreten
        PayloadRegistrar registrar = event.registrar("10").optional();

        registrar.playToClient(FadeToMenu.TYPE, FadeToMenu.CODEC, (payload, context) -> Abblende.start());
        registrar.playToClient(FahrtState.TYPE, FahrtState.CODEC, (payload, context) -> Haltanzeige.update(payload));

        // Hauptmenü
        registrar.playToClient(OpenMenu.TYPE, OpenMenu.CODEC,
                (payload, context) -> MainMenuClient.open(payload.jobs(), payload.strecken(), payload.zugdaten(), payload.abstellungen()));
        registrar.playToServer(Play.TYPE, Play.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });

        // Editoren
        registrar.playToClient(OpenEditor.TYPE, OpenEditor.CODEC, (payload, context) -> Minecraft.getInstance().setScreen(switch (payload.editor()) {
            case OpenEditor.ROUTES -> new RouteEditorScreen(payload.strecken(), payload.zugdaten(), payload.abstellungen());
            case OpenEditor.TRAINS -> new TrainEditorScreen(payload.strecken(), payload.zugdaten());
            default -> new SpawnEditorScreen(payload.abstellungen(), payload.broken());
        }));
        registrar.playToServer(SaveStrecken.TYPE, SaveStrecken.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToServer(SaveGarnituren.TYPE, SaveGarnituren.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToServer(SaveAbstellungen.TYPE, SaveAbstellungen.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });

        // Weltauswahl
        registrar.playToServer(StartGleis.TYPE, StartGleis.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToServer(StartRoute.TYPE, StartRoute.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToServer(AuswahlClick.TYPE, AuswahlClick.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToServer(AbstellungChosen.TYPE, AbstellungChosen.CODEC, (payload, context) -> {
            // kommt nur beim Server an
        });
        registrar.playToClient(AuswahlState.TYPE, AuswahlState.CODEC, (payload, context) -> Weltauswahl.update(payload));
        registrar.playToClient(ChooseAbstellung.TYPE, ChooseAbstellung.CODEC, (payload, context) -> chooseAbstellung(payload.choices()));
    }
}
