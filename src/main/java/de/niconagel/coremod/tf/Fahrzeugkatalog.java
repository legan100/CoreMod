package de.niconagel.coremod.tf;

import com.blockwelt.modernrailroading.platform.neoforge_1_21_1.ForgePlatformBootstrap;
import com.blockwelt.modernrailroading.rollingstock.RollingStockModule;
import com.blockwelt.modernrailroading.rollingstock.model.RollingStockCategory;
import com.blockwelt.modernrailroading.rollingstock.model.RollingStockDefinition;
import com.blockwelt.modernrailroading.rollingstock.model.VehicleShape;

import java.util.Comparator;
import java.util.List;

/**
 * Die Fahrzeuge aus ModernRailroading (Loks und Wagen aller geladenen Content-Packs).
 * Zusammen mit {@link Gleisnetz} die einzige Stelle in CoreMod, die MR anspricht: MR hat keine Java-API, ändert sich
 * dort etwas, bricht nur hier etwas.
 */
public final class Fahrzeugkatalog {

    private Fahrzeugkatalog() {
    }

    /**
     * Ein Fahrzeugtyp aus MR.
     *
     * @param id       MR-ID, z.B. "locomotives/br50"
     * @param category "Lok", "Personenwagen", … (für die Reiter in der Auswahl)
     * @param length   Meter, Kupplung bis Kupplung
     * @param vmax     km/h, 0 = unbekannt
     * @param weight   Tonnen
     */
    public record Fahrzeugtyp(String id, String name, String category, double length, double vmax, double weight) {
    }

    /** Content-Packs ändern sich nur mit einem Neustart. */
    private static volatile List<Fahrzeugtyp> cache;

    /**
     * Alle Fahrzeugtypen, Loks zuerst, dann nach Name.
     * ponytail: der erste Aufruf lässt MR alle Modelle für die Längen einlesen (einmaliges Ruckeln); ggf. beim Serverstart vorladen
     */
    public static List<Fahrzeugtyp> all() {
        if (cache == null) cache = load();
        return cache;
    }

    private static List<Fahrzeugtyp> load() {
        RollingStockModule module = ForgePlatformBootstrap.bootstrap().rollingStockModule();
        return module.catalog().allDefinitions().stream()
                .sorted(Comparator.comparing(RollingStockDefinition::category).thenComparing(RollingStockDefinition::displayName))
                .map(definition -> {
                    VehicleShape shape = module.shape(definition);
                    // wie TrainSimulation.length() in MR
                    double length = shape.frontReach(definition) + shape.rearReach(definition);
                    return new Fahrzeugtyp(definition.id(), definition.displayName(), category(definition.category()),
                            length, definition.maxSpeedKmh(), definition.weightKg() / 1000);
                })
                .toList();
    }

    private static String category(RollingStockCategory category) {
        return switch (category) {
            case LOCOMOTIVE -> "Lok";
            case PASSENGER_COACH -> "Personenwagen";
            case FREIGHT_WAGON -> "Güterwagen";
            case TANK_WAGON -> "Kesselwagen";
            case TENDER -> "Tender";
        };
    }
}
