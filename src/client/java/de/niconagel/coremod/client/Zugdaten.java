package de.niconagel.coremod.client;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Garnituren und MR-Fahrzeugkatalog vom Server, mit allem, was Hauptmenü und Editoren daraus anzeigen.
 * Aufbau wie MainMenuNetwork.Zugdaten in der Server-Mod.
 *
 * @param katalog alle Fahrzeugtypen aus ModernRailroading, Loks zuerst
 */
public record Zugdaten(List<Garnitur> garnituren, List<Fahrzeugtyp> katalog) {

    /** Kategorie der Loks, wie sie der Server schickt. */
    static final String LOK = "Lok";

    static final Zugdaten NONE = new Zugdaten(List.of(), List.of());

    /** Fahrzeugtyp aus MR. Länge in m, Vmax in km/h (0 = unbekannt), Gewicht in t. */
    public record Fahrzeugtyp(String id, String name, String category, double length, double vmax, double weight) {
        static final StreamCodec<ByteBuf, Fahrzeugtyp> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Fahrzeugtyp::id,
                ByteBufCodecs.STRING_UTF8, Fahrzeugtyp::name,
                ByteBufCodecs.STRING_UTF8, Fahrzeugtyp::category,
                ByteBufCodecs.DOUBLE, Fahrzeugtyp::length,
                ByteBufCodecs.DOUBLE, Fahrzeugtyp::vmax,
                ByteBufCodecs.DOUBLE, Fahrzeugtyp::weight,
                Fahrzeugtyp::new);
    }

    /** Ein Fahrzeug einer Garnitur: MR-ID und ob es umgedreht eingereiht ist. */
    public record Fahrzeug(String type, boolean reversed) {
        static final StreamCodec<ByteBuf, Fahrzeug> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Fahrzeug::type,
                ByteBufCodecs.BOOL, Fahrzeug::reversed,
                Fahrzeug::new);
    }

    /** Global nutzbare Zuggarnitur; Routen verweisen über die feste {@code id} darauf. */
    public record Garnitur(String id, String name, List<Fahrzeug> vehicles) {
        static final StreamCodec<ByteBuf, Garnitur> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Garnitur::id,
                ByteBufCodecs.STRING_UTF8, Garnitur::name,
                Fahrzeug.CODEC.apply(ByteBufCodecs.list()), Garnitur::vehicles,
                Garnitur::new);
    }

    static final StreamCodec<ByteBuf, List<Garnitur>> GARNITUREN = Garnitur.CODEC.apply(ByteBufCodecs.list());

    static final StreamCodec<ByteBuf, Zugdaten> CODEC = StreamCodec.composite(
            GARNITUREN, Zugdaten::garnituren,
            Fahrzeugtyp.CODEC.apply(ByteBufCodecs.list()), Zugdaten::katalog,
            Zugdaten::new);

    /** @return der Typ oder null, wenn MR ihn nicht (mehr) kennt */
    Fahrzeugtyp type(String id) {
        for (Fahrzeugtyp type : katalog) {
            if (type.id().equals(id)) return type;
        }
        return null;
    }

    /** @return die Garnitur oder null bei "" bzw. gelöschter Garnitur */
    Garnitur garnitur(String id) {
        for (Garnitur garnitur : garnituren) {
            if (garnitur.id().equals(id)) return garnitur;
        }
        return null;
    }

    /** "5 Fahrzeuge · 98 m · 312 t · Vmax 100 km/h", dazu rot, was nicht stimmt (keine Lok, fehlende Fahrzeuge). */
    MutableComponent summary(Garnitur garnitur) {
        double length = 0;
        double weight = 0;
        double vmax = Double.MAX_VALUE;
        boolean locomotive = false;
        int missing = 0;
        for (Fahrzeug vehicle : garnitur.vehicles()) {
            Fahrzeugtyp type = type(vehicle.type());
            if (type == null) {
                missing++;
                continue;
            }
            length += type.length();
            weight += type.weight();
            // Das langsamste Fahrzeug bestimmt die Vmax; 0 heißt unbekannt
            if (type.vmax() > 0) vmax = Math.min(vmax, type.vmax());
            locomotive |= type.category().equals(LOK);
        }

        int count = garnitur.vehicles().size();
        MutableComponent text = Component.literal(count + (count == 1 ? " Fahrzeug" : " Fahrzeuge")
                + " · " + number(length) + " m · " + number(weight) + " t"
                + (vmax < Double.MAX_VALUE ? " · Vmax " + number(vmax) + " km/h" : "")).withStyle(ChatFormatting.GRAY);
        if (count > 0 && !locomotive) text.append(Component.literal(" · keine Lok").withStyle(ChatFormatting.RED));
        if (missing > 0) text.append(Component.literal(" · " + missing + " fehlt in MR").withStyle(ChatFormatting.RED));
        return text;
    }

    /** Reihung in kurz: "DB Baureihe 50 + 4× B4yg Umbauwagen". Gleiche Fahrzeuge hintereinander werden zusammengefasst. */
    String composition(Garnitur garnitur) {
        List<String> parts = new ArrayList<>();
        String last = null;
        int run = 0;
        for (Fahrzeug vehicle : garnitur.vehicles()) {
            Fahrzeugtyp type = type(vehicle.type());
            String name = type == null ? vehicle.type() : type.name();
            if (name.equals(last)) {
                run++;
                continue;
            }
            if (last != null) parts.add(run == 1 ? last : run + "× " + last);
            last = name;
            run = 1;
        }
        if (last != null) parts.add(run == 1 ? last : run + "× " + last);
        return parts.isEmpty() ? "leer" : String.join(" + ", parts);
    }

    static String number(double value) {
        return String.format(Locale.GERMANY, "%,.0f", value);
    }
}
