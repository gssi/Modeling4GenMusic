package org.modeling4genmusic.studio.generation;

import org.modeling4genmusic.studio.rhythm.RhythmPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reader for the subset of ABC notation the studio produces: header fields (M, L, K, Q, T), single
 * notes with accidentals, octave marks and lengths, rests, broken rhythm ({@code >} and {@code <}) and
 * bar lines. Chords and common tuplets are read and grace notes skipped; inline fields are reported as errors.
 */
public final class AbcTune {

    /** A whole note in the unit of {@link Event#units()}, the same unit used for rhythm patterns. */
    public static final int WHOLE = RhythmPattern.WHOLE;
    private static final String SHARP_ORDER = "FCGDAEB";
    private static final String FLAT_ORDER = "BEADGCF";
    private static final Map<String, Integer> MAJOR_KEYS = Map.ofEntries(Map.entry("C", 0), Map.entry("G", 1),
            Map.entry("D", 2), Map.entry("A", 3), Map.entry("E", 4), Map.entry("B", 5), Map.entry("F#", 6),
            Map.entry("C#", 7), Map.entry("F", -1), Map.entry("Bb", -2), Map.entry("Eb", -3), Map.entry("Ab", -4),
            Map.entry("Db", -5), Map.entry("Gb", -6), Map.entry("Cb", -7));
    private static final Map<String, Integer> MINOR_KEYS = Map.ofEntries(Map.entry("A", 0), Map.entry("E", 1),
            Map.entry("B", 2), Map.entry("F#", 3), Map.entry("C#", 4), Map.entry("G#", 5), Map.entry("D#", 6),
            Map.entry("A#", 7), Map.entry("D", -1), Map.entry("G", -2), Map.entry("C", -3), Map.entry("F", -4),
            Map.entry("Bb", -5), Map.entry("Eb", -6), Map.entry("Ab", -7));
    private static final int[] SEMITONES = { 9, 11, 0, 2, 4, 5, 7 }; // A B C D E F G

    /**
     * A note, a chord or a rest.
     *
     * @param midi    MIDI pitch (the highest one of a chord), or -1 for a rest
     * @param units   length in units of 1/192 of a whole note
     * @param pitches every pitch that sounds together, lowest first; empty for a rest
     */
    public record Event(int midi, int units, List<Integer> pitches) {

        public Event(int midi, int units) {
            this(midi, units, midi < 0 ? List.of() : List.of(midi));
        }

        public boolean isRest() {
            return midi < 0;
        }

        /** Semitones between the lowest and the highest note played together: 0 for a single note. */
        public int span() {
            return pitches.size() < 2 ? 0 : pitches.get(pitches.size() - 1) - pitches.get(0);
        }

        Event withUnits(int newUnits) {
            return new Event(midi, newUnits, pitches);
        }
    }

    private final Map<String, String> headers;
    private final List<List<Event>> bars;

    private AbcTune(Map<String, String> headers, List<List<Event>> bars) {
        this.headers = headers;
        this.bars = bars;
    }

    /** Header fields by letter, for example {@code M -> 4/4}. */
    public Map<String, String> headers() {
        return headers;
    }

    public List<List<Event>> bars() {
        return bars;
    }

    public List<Event> notes() {
        return bars.stream().flatMap(List::stream).filter(e -> !e.isRest()).toList();
    }

    /** Every pitch of the tune, the notes of chords included. */
    public List<Integer> pitches() {
        return notes().stream().flatMap(e -> e.pitches().stream()).toList();
    }

    /** Parses a tune or throws {@link IllegalArgumentException} with a reason for the user. */
    public static AbcTune parse(String abc) {
        if (abc == null || abc.isBlank()) {
            throw new IllegalArgumentException("l'ABC è vuoto");
        }
        Map<String, String> headers = new LinkedHashMap<>();
        StringBuilder body = new StringBuilder();
        for (String raw : abc.split("\\R")) {
            String line = raw.contains("%") ? raw.substring(0, raw.indexOf('%')) : raw;
            line = line.strip();
            if (line.length() >= 2 && line.charAt(1) == ':' && Character.isLetter(line.charAt(0))
                    && line.charAt(0) != '|') {
                headers.put(line.substring(0, 1), line.substring(2).strip());
            } else {
                body.append(line).append('\n');
            }
        }
        if (!headers.containsKey("K")) {
            throw new IllegalArgumentException("manca la tonalità (campo K:)");
        }
        int unit = unitLength(headers.get("L"));
        int[] signature = keySignature(headers.get("K"));
        return new AbcTune(headers, new BodyReader(body.toString(), unit, signature).read());
    }

    private static int unitLength(String field) {
        if (field == null) {
            return WHOLE / 8;
        }
        String[] parts = field.split("/");
        try {
            int units = WHOLE * Integer.parseInt(parts[0].strip()) / Integer.parseInt(parts[1].strip());
            if (units > 0) {
                return units;
            }
        } catch (RuntimeException e) {
            // reported below
        }
        throw new IllegalArgumentException("lunghezza di base non valida (campo L: " + field + ")");
    }

    /** Alteration in semitones of each letter A..G for a key such as {@code G}, {@code Bb} or {@code F#m}. */
    private static int[] keySignature(String field) {
        String key = field.strip().split("\\s+")[0];
        boolean minor = key.length() > 1 && key.substring(1).replace("#", "").replace("b", "").startsWith("m")
                && !key.substring(1).replace("#", "").replace("b", "").startsWith("maj");
        String tonic = key.substring(0, 1).toUpperCase()
                + (key.length() > 1 && (key.charAt(1) == '#' || key.charAt(1) == 'b') ? key.substring(1, 2) : "");
        Integer count = (minor ? MINOR_KEYS : MAJOR_KEYS).get(tonic);
        if (count == null) {
            throw new IllegalArgumentException("tonalità non riconosciuta (campo K: " + field + ")");
        }
        int[] signature = new int[7];
        for (int i = 0; i < Math.abs(count); i++) {
            char letter = (count > 0 ? SHARP_ORDER : FLAT_ORDER).charAt(i);
            signature[letter - 'A'] = count > 0 ? 1 : -1;
        }
        return signature;
    }

    /** Scans the music lines character by character. */
    private static final class BodyReader {

        private final String text;
        private final int unit;
        private final int[] signature;
        private final List<List<Event>> bars = new ArrayList<>();
        private List<Event> bar = new ArrayList<>();
        private int[] barAccidentals = new int[128];
        private boolean[] barAltered = new boolean[128];
        private int pos;
        /** Numerator and denominator the next event is multiplied by, set by a broken rhythm mark. */
        private int pendingNum = 1;
        private int pendingDen = 1;
        /** Notes still to read in the current tuplet, and the ratio that shortens (or lengthens) them. */
        private int tupletLeft;
        private int tupletNum = 1;
        private int tupletDen = 1;

        BodyReader(String text, int unit, int[] signature) {
            this.text = text;
            this.unit = unit;
            this.signature = signature;
        }

        List<List<Event>> read() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c) || c == '-' || c == '\\' || c == ')') {
                    pos++;
                } else if (c == '|' || c == ':' || c == ']') {
                    endBar();
                    pos++;
                } else if (c == '[') {
                    if (pos + 1 < text.length() && text.charAt(pos + 1) == '|') {
                        pos++;
                    } else if (pos + 2 < text.length() && Character.isLetter(text.charAt(pos + 1))
                            && text.charAt(pos + 2) == ':') {
                        throw unsupported("cambi di tonalità o di metro in mezzo al brano");
                    } else {
                        chord();
                    }
                } else if (c == '"' || c == '!') {
                    int close = text.indexOf(c, pos + 1);
                    pos = close < 0 ? text.length() : close + 1;
                } else if (c == '(') {
                    pos++;
                    if (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                        tuplet(text.charAt(pos++) - '0');
                    }
                } else if (c == '{') {
                    // Grace notes ornament the next note and take no time of their own.
                    int close = text.indexOf('}', pos + 1);
                    pos = close < 0 ? text.length() : close + 1;
                } else if (c == '>' || c == '<') {
                    broken(c);
                } else if (c == 'z' || c == 'x' || c == '^' || c == '_' || c == '=' || isNoteLetter(c)) {
                    event();
                } else {
                    throw new IllegalArgumentException("carattere non riconosciuto '" + c + "'");
                }
            }
            endBar();
            return bars;
        }

        private static boolean isNoteLetter(char c) {
            return (c >= 'A' && c <= 'G') || (c >= 'a' && c <= 'g');
        }

        private IllegalArgumentException unsupported(String what) {
            return new IllegalArgumentException(what + " non sono supportati");
        }

        /** {@code (3} plays the next three notes in the time of two, and so on for the common groups. */
        private void tuplet(int notes) {
            if (pos < text.length() && text.charAt(pos) == ':') {
                throw unsupported("gruppi irregolari con rapporto esplicito");
            }
            int time = switch (notes) {
                case 2, 4 -> 3;
                case 3, 6 -> 2;
                default -> throw unsupported("gruppi irregolari di " + notes + " note");
            };
            tupletLeft = notes;
            tupletNum = time;
            tupletDen = notes;
        }

        private void endBar() {
            if (!bar.isEmpty()) {
                bars.add(bar);
                bar = new ArrayList<>();
            }
            barAccidentals = new int[128];
            barAltered = new boolean[128];
        }

        /** A broken rhythm mark lengthens one neighbour by half and shortens the other by half. */
        private void broken(char mark) {
            int count = 0;
            while (pos < text.length() && text.charAt(pos) == mark) {
                count++;
                pos++;
            }
            if (count != 1 || bar.isEmpty()) {
                throw unsupported("ritmi spezzati multipli");
            }
            Event previous = bar.remove(bar.size() - 1);
            boolean firstLong = mark == '>';
            bar.add(previous.withUnits(scale(previous.units(), firstLong ? 3 : 1, 2)));
            pendingNum = firstLong ? 1 : 3;
            pendingDen = 2;
        }

        private void event() {
            int midi = pitch();
            add(midi, midi < 0 ? List.of() : List.of(midi), duration());
        }

        /** Notes between square brackets sound together; the length is the one of the first note, or follows the bracket. */
        private void chord() {
            pos++; // [
            List<Integer> pitches = new ArrayList<>();
            int inner = -1;
            while (pos < text.length() && text.charAt(pos) != ']') {
                char c = text.charAt(pos);
                if (Character.isWhitespace(c) || c == '-' || c == '(' || c == ')') {
                    pos++;
                    continue;
                }
                if (c != '^' && c != '_' && c != '=' && !isNoteLetter(c)) {
                    throw new IllegalArgumentException("carattere non riconosciuto '" + c + "' in un accordo");
                }
                pitches.add(pitch());
                int length = duration();
                if (inner < 0) {
                    inner = length;
                }
            }
            if (pos >= text.length() || pitches.isEmpty()) {
                throw new IllegalArgumentException("accordo non chiuso o vuoto");
            }
            pos++; // ]
            int num = number(1);
            int den = 1;
            while (pos < text.length() && text.charAt(pos) == '/') {
                pos++;
                den *= number(2);
            }
            pitches.sort(null);
            add(pitches.get(pitches.size() - 1), List.copyOf(pitches), scale(inner, num, den));
        }

        private void add(int midi, List<Integer> pitches, int length) {
            int units = scale(length, pendingNum, pendingDen);
            if (tupletLeft > 0) {
                units = scale(units, tupletNum, tupletDen);
                tupletLeft--;
            }
            pendingNum = 1;
            pendingDen = 1;
            bar.add(new Event(midi, units, pitches));
        }

        /** Reads accidentals, letter and octave marks; returns -1 for a rest. */
        private int pitch() {
            int accidental = 0;
            boolean explicit = false;
            while (pos < text.length() && "^_=".indexOf(text.charAt(pos)) >= 0) {
                char a = text.charAt(pos++);
                accidental += a == '^' ? 1 : a == '_' ? -1 : 0;
                explicit = true;
            }
            if (pos >= text.length()) {
                throw new IllegalArgumentException("alterazione senza nota");
            }
            char letter = text.charAt(pos++);
            if (letter == 'z' || letter == 'x') {
                if (explicit) {
                    throw new IllegalArgumentException("alterazione su una pausa");
                }
                return -1;
            }
            if (!isNoteLetter(letter)) {
                throw new IllegalArgumentException("alterazione senza nota");
            }
            int octave = Character.isLowerCase(letter) ? 72 : 60;
            while (pos < text.length() && (text.charAt(pos) == ',' || text.charAt(pos) == '\'')) {
                octave += text.charAt(pos++) == ',' ? -12 : 12;
            }
            int index = Character.toUpperCase(letter) - 'A';
            int natural = octave + SEMITONES[index];
            if (explicit) {
                barAccidentals[natural] = accidental;
                barAltered[natural] = true;
            }
            return natural + (barAltered[natural] ? barAccidentals[natural] : signature[index]);
        }

        /** Reads the length written after a note, in units. */
        private int duration() {
            int num = number(1);
            int den = 1;
            while (pos < text.length() && text.charAt(pos) == '/') {
                pos++;
                den *= number(2);
            }
            return scale(unit, num, den);
        }

        private int number(int fallback) {
            int start = pos;
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            return start == pos ? fallback : Integer.parseInt(text.substring(start, pos));
        }

        private static int scale(int units, int num, int den) {
            if (units * num % den != 0) {
                throw new IllegalArgumentException("durata troppo breve per essere rappresentata");
            }
            return units * num / den;
        }
    }
}
