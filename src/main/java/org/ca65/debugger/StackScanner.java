package org.ca65.debugger;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * Heuristic caller detection for the synthetic 6502 call stack: scans the
 * hardware stack page for words that look like JSR return addresses. A
 * word W (pushed value = return address - 1) is accepted only when the
 * byte at W-2 is the JSR opcode AND W-2 maps to a known source line —
 * the combination makes false positives rare in practice. Hand-rolled
 * stack tricks (RTS dispatch tables, munged return addresses) will
 * confuse any scanner; plain jsr/rts code scans reliably.
 */
public final class StackScanner {
    private static final int OPCODE_JSR = 0x20;
    private static final int STACK_BASE = 0x0100;

    /** Byte-granular program memory access for opcode/operand validation. */
    public interface MemoryReader {
        int read(int address) throws IOException;
    }

    /**
     * One detected caller. {@code jsrAddress} is the calling instruction,
     * {@code targetAddress} its operand (the callee entry point),
     * {@code returnTo} where execution resumes after RTS. {@code ownedBytes}
     * are the stack bytes this frame's routine pushed after making the
     * call (PHA/PHP residue), starting at address {@code ownedFrom}.
     */
    public record CallerFrame(int jsrAddress, int targetAddress, int returnTo,
                              int ownedFrom, byte @NotNull [] ownedBytes) {
    }

    /**
     * Scan result: caller frames innermost-first, plus the stack bytes
     * owned by the currently executing (top) frame.
     */
    public record ScanResult(@NotNull List<CallerFrame> frames,
                             int topOwnedFrom, byte @NotNull [] topOwnedBytes) {
        public static final ScanResult EMPTY = new ScanResult(List.of(), 0, new byte[0]);
    }

    private StackScanner() {
    }

    /**
     * @param stackPage the 256 bytes at $0100–$01FF
     * @param sp        the S register (points below the top-of-stack entry)
     * @param isKnownCode whether an address maps to a source line in the debug info
     */
    @NotNull
    public static ScanResult scan(byte @NotNull [] stackPage, int sp, @NotNull MemoryReader memory,
                                  @NotNull IntPredicate isKnownCode, int maxFrames) throws IOException {
        List<CallerFrame> frames = new ArrayList<>();
        List<int[]> segments = new ArrayList<>();   // {startIndex, endIndexExclusive} per frame
        int index = (sp & 0xff) + 1;
        int segmentStart = index;
        while (index <= 0xfe && frames.size() < maxFrames) {
            int word = (stackPage[index] & 0xff) | ((stackPage[index + 1] & 0xff) << 8);
            int jsrAddress = word - 2;
            if (jsrAddress >= 0 && isKnownCode.test(jsrAddress) && memory.read(jsrAddress) == OPCODE_JSR) {
                int target = memory.read(jsrAddress + 1) | (memory.read(jsrAddress + 2) << 8);
                frames.add(new CallerFrame(jsrAddress, target, word + 1, 0, new byte[0]));
                segments.add(new int[]{segmentStart, index});
                index += 2;
                segmentStart = index;
            } else {
                index++;
            }
        }

        if (frames.isEmpty()) {
            return new ScanResult(List.of(), STACK_BASE + (sp & 0xff) + 1,
                    slice(stackPage, (sp & 0xff) + 1, 0x100));
        }

        // The segment before the first return address belongs to the top
        // frame; the segment after frame k's slot belongs to frame k.
        int[] first = segments.get(0);
        byte[] topOwned = slice(stackPage, first[0], first[1]);
        List<CallerFrame> withOwned = new ArrayList<>(frames.size());
        for (int k = 0; k < frames.size(); k++) {
            CallerFrame frame = frames.get(k);
            int from = k + 1 < segments.size() ? segments.get(k + 1)[0] : segmentStart;
            int to = k + 1 < segments.size() ? segments.get(k + 1)[1] : 0x100;
            withOwned.add(new CallerFrame(frame.jsrAddress(), frame.targetAddress(), frame.returnTo(),
                    STACK_BASE + from, slice(stackPage, from, to)));
        }
        return new ScanResult(withOwned, STACK_BASE + first[0], topOwned);
    }

    private static byte[] slice(byte[] page, int from, int toExclusive) {
        byte[] result = new byte[Math.max(0, toExclusive - from)];
        for (int i = 0; i < result.length; i++) {
            result[i] = page[from + i];
        }
        return result;
    }
}
