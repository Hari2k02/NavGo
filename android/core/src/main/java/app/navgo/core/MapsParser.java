package app.navgo.core;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Notification text is an unstable UI surface, not a Google navigation API. */
public final class MapsParser {
    public static final String MAPS_PACKAGE = "com.google.android.apps.maps";
    private static final Pattern DISTANCE = Pattern.compile(
        "(?iu)(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?\\s*(?:km|mi|m|ft|yd)(?!\\p{L})");
    private static final Pattern ARRIVAL = Pattern.compile(
        "(?i)\\b(?:you(?:'|\\u2019)?ve arrived|you have arrived|arrived at|you are at your destination)\\b");
    private static final Pattern DIRECTION = Pattern.compile(
        "\\b(?:turn(?: slightly| sharply| sharp)?|keep|bear|slight(?:ly)?|sharp(?:ly)?)(?: to the)?\\s+(left|right)\\b");

    private MapsParser() {}

    public static Navigation parse(String packageName, boolean ongoing, boolean groupSummary,
                                   String title, String text, String expanded, String subText) {
        if (!MAPS_PACKAGE.equals(packageName) || groupSummary) return null;
        title = clean(title);
        text = clean(text);
        expanded = clean(expanded);
        subText = clean(subText);
        if (ARRIVAL.matcher(title).find()) {
            return new Navigation(Navigation.State.ARRIVED, Navigation.Turn.UNKNOWN,
                "", "You have arrived", subText);
        }
        if (!ongoing) return null;
        Matcher distance = DISTANCE.matcher(title);
        String remaining = "";
        String instruction = title;
        if (distance.find()) {
            remaining = distance.group().trim();
            instruction = (title.substring(0, distance.start()) + " "
                + title.substring(distance.end())).replaceAll("^[\\s\\-\\u2013\\u2014:]+", "").trim();
        }
        if (instruction.isEmpty() || title.matches("(?i)Google Maps|Maps|Navigation")) {
            instruction = text.isEmpty() ? expanded : text;
        }
        String lower = instruction.toLowerCase(Locale.ROOT);
        boolean navigationEvidence = !remaining.isEmpty() || turn(lower) != Navigation.Turn.UNKNOWN
            || lower.contains("rerouting") || lower.contains("finding best route")
            || lower.contains("continue on") || lower.contains("head toward");
        if (!navigationEvidence) return null;
        if (instruction.isEmpty()) instruction = "Follow Google Maps on phone";
        String summary = subText.isEmpty() ? text : subText;
        if (summary.equals(instruction)) summary = "";
        return new Navigation(Navigation.State.NAVIGATING, turn(lower), remaining, instruction, summary);
    }

    static String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{Cc}\\p{Cf}]", " ").replaceAll("[\\s\\p{Z}]+", " ").trim();
    }

    private static Navigation.Turn turn(String text) {
        if (text.matches("^(?:continue|head|go straight)\\b.*")) return Navigation.Turn.STRAIGHT;
        if (text.matches(".*\\bu[- ]turn\\b.*")) return Navigation.Turn.U_TURN;
        if (text.matches(".*\\b(?:at|enter|take|the) (?:the )?(?:roundabout|traffic circle)\\b.*")) {
            return Navigation.Turn.ROUNDABOUT;
        }
        if (text.matches(".*\\b(?:take|use) (?:the )?(?:exit|off-ramp)\\b.*")) return Navigation.Turn.EXIT;
        if (text.matches("^merge\\b.*")) return Navigation.Turn.MERGE;
        Matcher direction = DIRECTION.matcher(text);
        if (!direction.find()) return Navigation.Turn.UNKNOWN;
        String maneuver = direction.group();
        if (direction.group(1).equals("left")) {
            if (maneuver.contains("slight") || maneuver.contains("keep") || maneuver.contains("bear")) return Navigation.Turn.SLIGHT_LEFT;
            if (maneuver.contains("sharp")) return Navigation.Turn.SHARP_LEFT;
            return Navigation.Turn.LEFT;
        }
        if (direction.group(1).equals("right")) {
            if (maneuver.contains("slight") || maneuver.contains("keep") || maneuver.contains("bear")) return Navigation.Turn.SLIGHT_RIGHT;
            if (maneuver.contains("sharp")) return Navigation.Turn.SHARP_RIGHT;
            return Navigation.Turn.RIGHT;
        }
        return Navigation.Turn.UNKNOWN;
    }
}
