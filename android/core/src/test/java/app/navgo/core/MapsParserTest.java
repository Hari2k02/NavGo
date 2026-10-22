package app.navgo.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class MapsParserTest {
    private Navigation parse(String title, String text) {
        return MapsParser.parse(MapsParser.MAPS_PACKAGE, true, false, title, text, "", "");
    }

    @Test public void filtersOtherPackagesAndGroupedNotifications() {
        assertNull(MapsParser.parse("another.app", true, false, "200 m - Turn left", "", "", ""));
        assertNull(MapsParser.parse(MapsParser.MAPS_PACKAGE, true, true, "200 m - Turn left", "", "", ""));
    }

    @Test public void rejectsInactiveAndUnrelatedMapsNotifications() {
        assertNull(MapsParser.parse(MapsParser.MAPS_PACKAGE, false, false, "200 m - Turn left", "", "", ""));
        assertNull(parse("Review your recent visit", "How was your trip?"));
        assertNull(parse("Downloading offline map", "42%"));
    }

    @Test public void parsesDistanceInstructionAndSummary() {
        Navigation nav = parse("200 m - Turn left onto Main Street", "12 min \u00b7 4.2 km \u00b7 17:30");
        assertNotNull(nav);
        assertEquals("200 m", nav.distance);
        assertEquals("Turn left onto Main Street", nav.instruction);
        assertEquals(Navigation.Turn.LEFT, nav.turn);
        assertEquals(Navigation.State.NAVIGATING, nav.state);
        assertTrue(nav.summary.contains("12 min"));
    }

    @Test public void parsesImperialAndDecimalComma() {
        assertEquals("0.5 mi", parse("0.5 mi - Keep right", "").distance);
        assertEquals(Navigation.Turn.SLIGHT_RIGHT, parse("0.5 mi - Keep right", "").turn);
        assertEquals("1,2 km", parse("1,2 km - Continue straight", "").distance);
    }

    @Test public void distinguishesTurnsWithoutMatchingStreetNames() {
        assertEquals(Navigation.Turn.U_TURN, parse("100 ft - Make a U-turn", "").turn);
        assertEquals(Navigation.Turn.ROUNDABOUT, parse("100 m - At the roundabout take exit 3", "").turn);
        assertEquals(Navigation.Turn.SHARP_LEFT, parse("100 m - Turn sharp left", "").turn);
        assertEquals(Navigation.Turn.UNKNOWN, parse("100 m - Righton Avenue", "").turn);
        assertEquals(Navigation.Turn.STRAIGHT, parse("100 m - Continue onto Left Bank Road", "").turn);
        assertEquals(Navigation.Turn.UNKNOWN, parse("100 m - Left Bank Road", "").turn);
        assertEquals(Navigation.Turn.LEFT, parse("100 m - Turn left onto Sharp Road", "").turn);
        assertEquals(Navigation.Turn.RIGHT, parse("100 m - Turn right onto Keep Street", "").turn);
    }

    @Test public void preservesLocalizedTextWithoutInventingManeuver() {
        Navigation nav = parse("200 m - \u092c\u093e\u090f\u0902 \u092e\u0941\u0921\u093c\u0947\u0902", "");
        assertEquals(Navigation.Turn.UNKNOWN, nav.turn);
        assertTrue(nav.instruction.contains("\u092c\u093e\u090f\u0902"));
    }

    @Test public void arrivalAndReroutingAreExplicit() {
        assertEquals(Navigation.State.ARRIVED, parse("You've arrived", "").state);
        assertEquals(Navigation.Turn.UNKNOWN, parse("Rerouting...", "").turn);
        assertEquals(Navigation.State.NAVIGATING, parse("Finding best route", "").state);
        assertNull(parse("Saved place", "Arrived at a great restaurant yesterday"));
    }

    @Test public void fallsBackToTextForGenericTitles() {
        Navigation nav = parse("Google Maps", "Turn right onto Broadway");
        assertEquals("Turn right onto Broadway", nav.instruction);
        assertEquals(Navigation.Turn.RIGHT, nav.turn);
    }

    @Test public void normalizesControlsAndBidiMarkers() {
        Navigation nav = parse("200\u00a0m - \u202eTurn left\nonto Main", "");
        assertEquals("Turn left onto Main", nav.instruction);
    }
}
