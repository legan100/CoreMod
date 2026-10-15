package de.niconagel.coremod.tf;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Ungültige Daten aus den Editoren werden abgelehnt, bevor etwas gespeichert wird. */
class StreckenTest {

    private static Strecken.Route route(String number, String name) {
        return new Strecken.Route(number, name, "", "", List.of());
    }

    @Test
    void rejectsDuplicateRouteNumbersAcrossStrecken() {
        List<Strecken.Strecke> strecken = List.of(
                new Strecken.Strecke("A", List.of(route("384", "RE10"))),
                new Strecken.Strecke("B", List.of(route("384", "RE10"))));
        assertThrows(IllegalArgumentException.class, () -> Strecken.save(strecken));
    }

    @Test
    void rejectsBlankOrDuplicateNamesAndIncompleteRoutes() {
        assertThrows(IllegalArgumentException.class, () -> Strecken.save(List.of(new Strecken.Strecke(" ", List.of()))));
        assertThrows(IllegalArgumentException.class, () -> Strecken.save(List.of(
                new Strecken.Strecke("A", List.of()), new Strecken.Strecke("A", List.of()))));
        assertThrows(IllegalArgumentException.class, () -> Strecken.save(List.of(new Strecken.Strecke("A", List.of(route("", "RE10"))))));
        assertThrows(IllegalArgumentException.class, () -> Strecken.save(List.of(new Strecken.Strecke("A", List.of(route("384", " "))))));
    }

    @Test
    void rejectsDuplicateGarniturIdsAndBlankNames() {
        Garnituren.Garnitur a = new Garnituren.Garnitur("x1", "BR 50 + 4 Umbauwagen", List.of());
        assertThrows(IllegalArgumentException.class, () -> Garnituren.save(List.of(a, new Garnituren.Garnitur("x1", "Rheingold", List.of()))));
        assertThrows(IllegalArgumentException.class, () -> Garnituren.save(List.of(new Garnituren.Garnitur("x2", " ", List.of()))));
    }

    @Test
    void zugnummerIsRouteNumberPlusDepartureMinuteFiveMinutesLater() {
        assertEquals("38445", Strecken.zugnummer("384", LocalTime.of(13, 40)));
        // Über die volle Stunde: nur die Minute zählt, mit führender Null
        assertEquals("38403", Strecken.zugnummer("384", LocalTime.of(13, 58)));
    }
}
