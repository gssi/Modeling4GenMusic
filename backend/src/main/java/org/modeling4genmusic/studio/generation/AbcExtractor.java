package org.modeling4genmusic.studio.generation;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Turns the text written by a language model into a tune of a given number of bars.
 * It only tidies the notation: chord symbols, dynamics, ornaments and repeat signs go away, a pickup
 * bar is dropped and the tune stops after the requested bars. The notes that remain are never changed.
 */
public final class AbcExtractor {

    private static final Pattern QUOTED = Pattern.compile("\"[^\"\n]*\"");
    private static final Pattern DECORATION = Pattern.compile("![^!\n]*!");
    private static final Pattern GRACE = Pattern.compile("\\{[^}\n]*\\}");
    /** Repeat signs, double bars and numbered endings, all reduced to a plain bar line. */
    private static final Pattern BAR_LINE = Pattern.compile("\\[\\||\\|\\]|:\\|\\|:|::|\\|:|:\\||\\|\\||\\|");
    private static final Pattern ENDING = Pattern.compile("^\\s*\\[?[1-9]\\b");
    private static final int BARS_PER_LINE = 4;

    private AbcExtractor() {
    }

    /**
     * @param header   header lines of the tune, ending with the {@code K:} field and a line break
     * @param response what the model wrote after that header
     * @param bars     number of bars to keep; 0 keeps them all
     * @param barUnits length of a full bar in {@link AbcTune.Event#units()}, or 0 when the meter is unknown
     */
    public static String tune(String header, String response, int bars, int barUnits) {
        List<String> all = bars(body(response));
        if (!all.isEmpty() && barUnits > 0 && all.size() > 1 && length(header, all.get(0)) < barUnits) {
            all.remove(0); // pickup bar
        }
        List<String> kept = bars > 0 && all.size() > bars ? all.subList(0, bars) : all;
        StringBuilder text = new StringBuilder(header);
        for (int i = 0; i < kept.size(); i++) {
            boolean last = i == kept.size() - 1;
            text.append(kept.get(i)).append(last ? " |]" : " |").append(last || (i + 1) % BARS_PER_LINE == 0 ? "\n" : " ");
        }
        return text.toString();
    }

    /** The music lines of the answer: up to a blank line, a new tune or a new turn of the dialogue. */
    static String body(String response) {
        StringBuilder body = new StringBuilder();
        boolean started = false;
        for (String raw : response.replace("```", "\n").split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                if (started) {
                    break;
                }
                continue;
            }
            if (line.startsWith("Human:") || line.startsWith("Assistant:") || line.startsWith("</s>")) {
                break;
            }
            if (line.length() >= 2 && line.charAt(1) == ':' && Character.isLetter(line.charAt(0))) {
                if (started && line.charAt(0) == 'X') {
                    break; // a second tune
                }
                continue; // header fields are fixed by the request, not by the model
            }
            if (line.startsWith("%")) {
                continue;
            }
            started = true;
            body.append(line).append(' ');
        }
        return body.toString();
    }

    static List<String> bars(String body) {
        String clean = GRACE.matcher(DECORATION.matcher(QUOTED.matcher(body).replaceAll("")).replaceAll("")).replaceAll("")
                .replace("\\", " ");
        List<String> bars = new ArrayList<>();
        for (String part : BAR_LINE.split(clean)) {
            String bar = ENDING.matcher(part).replaceFirst("").strip().replaceAll("\\s+", " ");
            if (bar.chars().anyMatch(c -> "ABCDEFGabcdefgzx".indexOf(c) >= 0)) {
                bars.add(bar);
            }
        }
        return bars;
    }

    /** Length of one bar, or the largest value when it cannot be read: such a bar is then never dropped. */
    private static int length(String header, String bar) {
        try {
            return AbcTune.parse(header + bar).bars().stream().findFirst()
                    .map(events -> events.stream().mapToInt(AbcTune.Event::units).sum()).orElse(Integer.MAX_VALUE);
        } catch (IllegalArgumentException e) {
            return Integer.MAX_VALUE;
        }
    }
}
