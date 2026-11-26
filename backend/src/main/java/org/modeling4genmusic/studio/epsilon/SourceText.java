package org.modeling4genmusic.studio.epsilon;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.epsilon.common.module.ModuleElement;
import org.eclipse.epsilon.common.parse.Region;

/** Cuts out of a script the text the parser attributes to an element. */
public final class SourceText {

    private final String[] lines;

    public SourceText(String source) {
        this.lines = source.split("\\R", -1);
    }

    public int line(ModuleElement element) {
        return element.getRegion().getStart().getLine();
    }

    /** The text of an element, with the indentation of its first line removed from every line. */
    public String of(ModuleElement element) {
        if (element == null || element.getRegion() == null) {
            return null;
        }
        Region region = element.getRegion();
        int first = region.getStart().getLine();
        int last = Math.min(region.getEnd().getLine(), lines.length);
        if (first < 1 || first > last) {
            return null;
        }
        int indent = Math.min(region.getStart().getColumn(), lines[first - 1].length());
        StringBuilder text = new StringBuilder();
        for (int i = first; i <= last; i++) {
            String line = lines[i - 1];
            if (i == last) {
                line = line.substring(0, Math.min(region.getEnd().getColumn(), line.length()));
            }
            int cut = 0;
            while (cut < indent && cut < line.length() && Character.isWhitespace(line.charAt(cut))) {
                cut++;
            }
            text.append(i == first ? line.substring(indent) : line.substring(cut));
            if (i < last) {
                text.append('\n');
            }
        }
        return text.toString().stripTrailing();
    }

    /** One part of a rule, without the keyword that introduces it and its braces, if the region has them. */
    public String part(ModuleElement block, String keyword) {
        String text = of(block);
        if (text == null) {
            return null;
        }
        text = text.strip();
        if (text.startsWith(keyword)) {
            text = text.substring(keyword.length()).strip();
        }
        if (text.startsWith(":")) {
            text = text.substring(1).strip();
        }
        if (text.startsWith("{") && text.endsWith("}")) {
            text = text.substring(1, text.length() - 1).strip();
        }
        return text.isEmpty() ? null : text.lines().map(String::strip).reduce((a, b) -> a + "\n" + b).orElse(text);
    }

    /** The {@code //} comment lines right above an element. */
    public String commentAbove(ModuleElement element) {
        List<String> comment = new ArrayList<>();
        for (int i = line(element) - 2; i >= 0 && lines[i].strip().startsWith("//"); i--) {
            comment.add(0, lines[i].strip().substring(2).strip());
        }
        return comment.isEmpty() ? null : String.join(" ", comment);
    }
}
