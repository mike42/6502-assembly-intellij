package org.ca65.buildcfg;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses ca65/ld65 diagnostics so they can be surfaced as clickable
 * messages in the Build tool window.
 *
 * <p>ca65 (and ld65 for lines it can attribute) reports
 * {@code file(line): Error: message}; ld65 also emits file-less
 * {@code ld65: Error: message} lines.
 */
public final class Ca65OutputParser {
    public enum Kind {ERROR, WARNING, NOTE}

    /** A diagnostic; {@code file} and {@code line} are null for file-less messages. */
    public record Diagnostic(@NotNull Kind kind, @Nullable String file, @Nullable Integer line,
                             @NotNull String message) {
    }

    private static final Pattern FILE_LINE =
            Pattern.compile("^(.+?)\\((\\d+)\\): (Error|Warning|Note): (.*)$");
    private static final Pattern TOOL_ONLY =
            Pattern.compile("^(?:ca65|ld65|cl65): (Error|Warning|Note): (.*)$");

    private Ca65OutputParser() {
    }

    /** Returns the parsed diagnostic, or null if the line is not one. */
    @Nullable
    public static Diagnostic parseLine(@NotNull String line) {
        Matcher matcher = FILE_LINE.matcher(line);
        if (matcher.matches()) {
            return new Diagnostic(kindOf(matcher.group(3)), matcher.group(1),
                    Integer.parseInt(matcher.group(2)), matcher.group(4));
        }
        matcher = TOOL_ONLY.matcher(line);
        if (matcher.matches()) {
            return new Diagnostic(kindOf(matcher.group(1)), null, null, matcher.group(2));
        }
        return null;
    }

    private static Kind kindOf(String word) {
        return switch (word) {
            case "Error" -> Kind.ERROR;
            case "Warning" -> Kind.WARNING;
            default -> Kind.NOTE;
        };
    }
}
