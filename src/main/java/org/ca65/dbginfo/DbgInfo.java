package org.ca65.dbginfo;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Debug information from an ld65 {@code --dbgfile} (version 2): the mapping
 * between source positions and memory addresses, plus symbol values.
 *
 * <p>The format is line oriented: {@code keyword\tattr=value,attr=value,…}
 * where values are decimal, hex ({@code 0x…}), quoted strings, or
 * {@code +}-joined lists. See cc65 doc/debugging and ld65 --dbgfile.
 *
 * <p>Pure Java, no IDE dependencies: unit-testable and reusable from the
 * protocol layer.
 */
public final class DbgInfo {
    /** A resolved source position: file name as recorded by ca65, 1-based line. */
    public record SourcePosition(@NotNull String fileName, int line) {
    }

    /** A symbol from the {@code sym} table. */
    public record Symbol(@NotNull String name, long value, @Nullable String segmentName,
                         @NotNull String type) {
    }

    private record CodeRange(long start, long size, int fileId, int line) {
        boolean contains(long address) {
            return address >= start && address < start + size;
        }
    }

    private final Map<Integer, String> myFileNames;
    /** fileId → (source line → lowest mapped address). */
    private final Map<Integer, Map<Integer, Long>> myLineToAddress;
    /** All code ranges, sorted by start address. */
    private final List<CodeRange> myRanges;
    private final Map<String, Symbol> mySymbols;
    /** Symbols of type "lab", sorted by address, for reverse lookup. */
    private final List<Symbol> myLabelsByAddress;

    private DbgInfo(Map<Integer, String> fileNames, Map<Integer, Map<Integer, Long>> lineToAddress,
                    List<CodeRange> ranges, Map<String, Symbol> symbols) {
        myFileNames = fileNames;
        myLineToAddress = lineToAddress;
        myRanges = ranges;
        mySymbols = symbols;
        myLabelsByAddress = new ArrayList<>();
        for (Symbol symbol : symbols.values()) {
            if ("lab".equals(symbol.type())) {
                myLabelsByAddress.add(symbol);
            }
        }
        myLabelsByAddress.sort((a, b) -> Long.compare(a.value(), b.value()));
    }

    @NotNull
    public static DbgInfo parse(@NotNull Reader reader) throws IOException {
        Map<Integer, Map<String, String>> files = new HashMap<>();
        List<Map<String, String>> lines = new ArrayList<>();
        Map<Integer, Map<String, String>> spans = new HashMap<>();
        Map<Integer, Map<String, String>> segments = new HashMap<>();
        List<Map<String, String>> symbols = new ArrayList<>();

        BufferedReader buffered = new BufferedReader(reader);
        String rawLine;
        while ((rawLine = buffered.readLine()) != null) {
            int tab = rawLine.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String keyword = rawLine.substring(0, tab);
            Map<String, String> attrs = parseAttributes(rawLine.substring(tab + 1));
            switch (keyword) {
                case "file" -> files.put(intAttr(attrs, "id"), attrs);
                case "line" -> lines.add(attrs);
                case "span" -> spans.put(intAttr(attrs, "id"), attrs);
                case "seg" -> segments.put(intAttr(attrs, "id"), attrs);
                case "sym" -> symbols.add(attrs);
                default -> {
                    // version/info/mod/scope/type/csym: not needed yet
                }
            }
        }

        Map<Integer, String> fileNames = new HashMap<>();
        for (Map.Entry<Integer, Map<String, String>> entry : files.entrySet()) {
            fileNames.put(entry.getKey(), entry.getValue().getOrDefault("name", ""));
        }

        Map<Integer, Map<Integer, Long>> lineToAddress = new HashMap<>();
        List<CodeRange> ranges = new ArrayList<>();
        for (Map<String, String> lineAttrs : lines) {
            String spanList = lineAttrs.get("span");
            if (spanList == null) {
                continue;
            }
            int fileId = intAttr(lineAttrs, "file");
            int sourceLine = intAttr(lineAttrs, "line");
            for (String spanId : spanList.split("\\+")) {
                Map<String, String> span = spans.get(Integer.parseInt(spanId));
                if (span == null) {
                    continue;
                }
                Map<String, String> segment = segments.get(intAttr(span, "seg"));
                if (segment == null) {
                    continue;
                }
                long address = longAttr(segment, "start") + longAttr(span, "start");
                long size = longAttr(span, "size");
                ranges.add(new CodeRange(address, size, fileId, sourceLine));
                lineToAddress.computeIfAbsent(fileId, k -> new HashMap<>())
                        .merge(sourceLine, address, Math::min);
            }
        }
        ranges.sort((a, b) -> Long.compare(a.start, b.start));

        Map<String, Symbol> symbolMap = new HashMap<>();
        for (Map<String, String> symAttrs : symbols) {
            String name = symAttrs.get("name");
            String value = symAttrs.get("val");
            if (name == null || value == null) {
                continue;
            }
            String segmentName = null;
            String segId = symAttrs.get("seg");
            if (segId != null) {
                Map<String, String> segment = segments.get(Integer.parseInt(segId));
                segmentName = segment == null ? null : segment.get("name");
            }
            symbolMap.putIfAbsent(name, new Symbol(name, parseNumber(value), segmentName,
                    symAttrs.getOrDefault("type", "")));
        }

        return new DbgInfo(fileNames, lineToAddress, ranges, symbolMap);
    }

    /** File names exactly as recorded by ca65 (usually relative to the build's working directory). */
    @NotNull
    public List<String> getFileNames() {
        return new ArrayList<>(myFileNames.values());
    }

    /**
     * The address for a breakpoint at the given source line, or null when
     * the line generated no code. {@code fileName} must match a recorded
     * file name (see {@link #findFileIdByPath}).
     */
    @Nullable
    public Long getAddressForLine(@NotNull String fileName, int line) {
        Integer fileId = findFileIdByPath(fileName);
        if (fileId == null) {
            return null;
        }
        Map<Integer, Long> byLine = myLineToAddress.get(fileId);
        return byLine == null ? null : byLine.get(line);
    }

    /**
     * The source position owning the given address, or null when the
     * address is outside the program (e.g. ROM). When ranges nest (macro
     * expansions), the smallest containing range wins.
     */
    @Nullable
    public SourcePosition getPositionForAddress(long address) {
        CodeRange best = null;
        for (CodeRange range : myRanges) {
            if (range.start > address) {
                break;
            }
            if (range.contains(address) && (best == null || range.size < best.size)) {
                best = range;
            }
        }
        if (best == null) {
            return null;
        }
        String fileName = myFileNames.get(best.fileId);
        return fileName == null ? null : new SourcePosition(fileName, best.line);
    }

    @Nullable
    public Symbol getSymbol(@NotNull String name) {
        return mySymbols.get(name);
    }

    @NotNull
    public Map<String, Symbol> getSymbols() {
        return mySymbols;
    }

    /**
     * Name of the nearest label at or below the address — {@code "name"} or
     * {@code "name+offset"} — or null when no label is within 4 KiB (e.g.
     * ROM addresses). Used to title synthetic stack frames.
     */
    @Nullable
    public String getLabelForAddress(long address) {
        Symbol best = null;
        for (Symbol label : myLabelsByAddress) {
            if (label.value() > address) {
                break;
            }
            best = label;
        }
        if (best == null || address - best.value() > 0x1000) {
            return null;
        }
        long offset = address - best.value();
        return offset == 0 ? best.name() : best.name() + "+" + offset;
    }

    /**
     * Matches a path against the recorded file names: exact match first,
     * then by trailing path components (dbg files record paths as given to
     * ca65, typically project-relative with '/' separators).
     */
    @Nullable
    private Integer findFileIdByPath(@NotNull String path) {
        String normalized = path.replace('\\', '/');
        for (Map.Entry<Integer, String> entry : myFileNames.entrySet()) {
            if (entry.getValue().replace('\\', '/').equals(normalized)) {
                return entry.getKey();
            }
        }
        for (Map.Entry<Integer, String> entry : myFileNames.entrySet()) {
            String recorded = entry.getValue().replace('\\', '/');
            if (normalized.endsWith("/" + recorded) || recorded.endsWith("/" + normalized)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private static int intAttr(Map<String, String> attrs, String name) {
        return Integer.parseInt(attrs.getOrDefault(name, "0"));
    }

    private static long longAttr(Map<String, String> attrs, String name) {
        return parseNumber(attrs.getOrDefault(name, "0"));
    }

    private static long parseNumber(String value) {
        if (value.startsWith("0x") || value.startsWith("0X")) {
            return Long.parseLong(value.substring(2), 16);
        }
        return Long.parseLong(value);
    }

    /** Splits {@code a=1,b="x,y",c=2+3} respecting quoted strings. */
    private static Map<String, String> parseAttributes(String text) {
        Map<String, String> result = new HashMap<>();
        int i = 0;
        while (i < text.length()) {
            int eq = text.indexOf('=', i);
            if (eq < 0) {
                break;
            }
            String key = text.substring(i, eq).trim();
            int j = eq + 1;
            String value;
            if (j < text.length() && text.charAt(j) == '"') {
                int close = text.indexOf('"', j + 1);
                if (close < 0) {
                    break;
                }
                value = text.substring(j + 1, close);
                i = close + 1;
            } else {
                int comma = text.indexOf(',', j);
                if (comma < 0) {
                    comma = text.length();
                }
                value = text.substring(j, comma).trim();
                i = comma;
            }
            result.put(key, value);
            if (i < text.length() && text.charAt(i) == ',') {
                i++;
            }
        }
        return result;
    }
}
