package org.ca65.helpers;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Parsed representation of a ca65 numeric literal.
 *
 * Supports all four source forms:
 *   DECIMAL    — plain digits, e.g. 42
 *   HEX        — $-prefixed,   e.g. $ff
 *   BINARY     — %-prefixed,   e.g. %10101010
 *   ZILOG_HEX  — h-suffixed,   e.g. 0ffh  (non-idiomatic; must start with 0-9)
 *
 * Underscores are accepted anywhere in the digit string (ca65 feature flag) and
 * are stripped before parsing. All values are validated to the 24-bit (0–$FFFFFF) range.
 *
 * Use {@link #parse} to obtain an instance; it returns null for anything that is not
 * a well-formed, in-range literal.
 */
public final class NumericLiteralValue {

    public enum Representation { DECIMAL, HEX, BINARY, ZILOG_HEX }

    private static final int MAX_VALUE = 0xFFFFFF;

    private final int value;
    private final Representation representation;

    private NumericLiteralValue(int value, Representation representation) {
        this.value = value;
        this.representation = representation;
    }

    /** Returns null if {@code text} is not a valid, in-range numeric literal. */
    public static @Nullable NumericLiteralValue parse(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        String digits = text.replace("_", "");
        try {
            int value;
            Representation rep;
            if (digits.startsWith("$")) {
                if (digits.length() < 2) return null;
                value = Integer.parseInt(digits.substring(1), 16);
                rep = Representation.HEX;
            } else if (digits.startsWith("%")) {
                if (digits.length() < 2) return null;
                value = Integer.parseInt(digits.substring(1), 2);
                rep = Representation.BINARY;
            } else if (digits.endsWith("h") || digits.endsWith("H")) {
                if (digits.length() < 2) return null;
                value = Integer.parseInt(digits.substring(0, digits.length() - 1), 16);
                rep = Representation.ZILOG_HEX;
            } else {
                value = Integer.parseInt(digits, 10);
                rep = Representation.DECIMAL;
            }
            if (value < 0 || value > MAX_VALUE) return null;
            return new NumericLiteralValue(value, rep);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public int getValue() { return value; }

    public @NotNull Representation getRepresentation() { return representation; }

    /** Renders as ca65 hex: {@code $xx}, always an even number of digits, lowercase. */
    public @NotNull String toHex() {
        return formatHex(value);
    }

    /** Renders as plain decimal. */
    public @NotNull String toDecimal() {
        return formatDecimal(value);
    }

    /** Renders as ca65 binary: {@code %xxxxxxxx}, always a multiple of 8 digits. */
    public @NotNull String toBinary() {
        return formatBinary(value);
    }

    /** Renders {@code value} as ca65 hex: {@code $xx}, always an even number of digits, lowercase. */
    public static @NotNull String formatHex(int value) {
        String raw = Integer.toHexString(value);
        if (raw.length() % 2 == 1) raw = "0" + raw;
        return "$" + raw;
    }

    /** Renders {@code value} as plain decimal. */
    public static @NotNull String formatDecimal(int value) {
        return Integer.toString(value);
    }

    /** Renders {@code value} as ca65 binary: {@code %xxxxxxxx}, always a multiple of 8 digits. */
    public static @NotNull String formatBinary(int value) {
        String raw = Integer.toBinaryString(value);
        int remainder = raw.length() % 8;
        if (remainder != 0) raw = "0".repeat(8 - remainder) + raw;
        return "%" + raw;
    }

    /**
     * Renders {@code value} using the source form {@code rep}. Hex is always idiomatic lowercase
     * {@code $xx} (the non-idiomatic {@code ZILOG_HEX} form is normalised to it).
     */
    public static @NotNull String format(int value, @NotNull Representation rep) {
        return switch (rep) {
            case DECIMAL -> formatDecimal(value);
            case BINARY -> formatBinary(value);
            case HEX, ZILOG_HEX -> formatHex(value);
        };
    }
}
