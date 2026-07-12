package org.ca65.debugger;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The synthetic-call-stack heuristic against hand-crafted stacks. A frame
 * is only accepted when the candidate word points one past a JSR opcode at
 * an address the debug info knows about.
 */
public class StackScannerTest {
    /** Program memory: sparse map, everything else reads 0. */
    private final Map<Integer, Integer> myMemory = new HashMap<>();
    private final byte[] myStackPage = new byte[256];

    private void jsrAt(int address, int target) {
        myMemory.put(address, 0x20);
        myMemory.put(address + 1, target & 0xff);
        myMemory.put(address + 2, (target >> 8) & 0xff);
    }

    /** Pushes the return-address word for a JSR at the given address. */
    private int pushReturnAddress(int sp, int jsrAddress) {
        int pushed = jsrAddress + 2;                  // 6502 pushes PC+2
        myStackPage[sp--] = (byte) ((pushed >> 8) & 0xff);
        myStackPage[sp--] = (byte) (pushed & 0xff);
        return sp;
    }

    private StackScanner.ScanResult scan(int sp, IntPredicate isKnownCode) throws Exception {
        return StackScanner.scan(myStackPage, sp,
                address -> myMemory.getOrDefault(address, 0), isKnownCode, 16);
    }

    @Test
    public void singleFrameWithPushedByte() throws Exception {
        jsrAt(0x0810, 0x0830);
        int sp = 0xff;
        sp = pushReturnAddress(sp, 0x0810);
        myStackPage[sp--] = 0x42;                     // callee did PHA
        StackScanner.ScanResult result = scan(sp, address -> address == 0x0810);

        assertEquals(1, result.frames().size());
        StackScanner.CallerFrame frame = result.frames().get(0);
        assertEquals(0x0810, frame.jsrAddress());
        assertEquals(0x0830, frame.targetAddress());
        assertEquals(0x0813, frame.returnTo());
        // the PHA'd byte belongs to the top (currently executing) frame
        assertEquals(0x0100 + sp + 1, result.topOwnedFrom());
        assertArrayEquals(new byte[]{0x42}, result.topOwnedBytes());
    }

    @Test
    public void nestedFramesAssignOwnedBytesToTheRightFrame() throws Exception {
        jsrAt(0x0810, 0x0830);                        // outer call
        jsrAt(0x0836, 0x0850);                        // inner call, made from 0x0830's routine
        int sp = 0xff;
        sp = pushReturnAddress(sp, 0x0810);
        myStackPage[sp--] = 0x11;                     // pushed by outer callee before inner call
        sp = pushReturnAddress(sp, 0x0836);
        myStackPage[sp--] = 0x22;                     // pushed by inner callee
        StackScanner.ScanResult result =
                scan(sp, address -> address == 0x0810 || address == 0x0836);

        assertEquals(2, result.frames().size());
        assertArrayEquals(new byte[]{0x22}, result.topOwnedBytes());
        assertEquals(0x0836, result.frames().get(0).jsrAddress());
        assertArrayEquals(new byte[]{0x11}, result.frames().get(0).ownedBytes());
        assertEquals(0x0810, result.frames().get(1).jsrAddress());
    }

    @Test
    public void wordsWithoutJsrOpcodeAreRejected() throws Exception {
        myMemory.put(0x0810, 0xa9);                   // lda, not jsr
        int sp = 0xff;
        sp = pushReturnAddress(sp, 0x0810);
        StackScanner.ScanResult result = scan(sp, address -> true);
        assertTrue(result.frames().isEmpty());
    }

    @Test
    public void wordsOutsideKnownCodeAreRejected() throws Exception {
        jsrAt(0xe1d4, 0x1234);                        // real JSR, but in ROM (not in .dbg)
        int sp = 0xff;
        sp = pushReturnAddress(sp, 0xe1d4);
        StackScanner.ScanResult result = scan(sp, address -> false);
        assertTrue(result.frames().isEmpty());
    }

    @Test
    public void emptyStackYieldsNoFramesAndNoBytes() throws Exception {
        StackScanner.ScanResult result = scan(0xff, address -> true);
        assertTrue(result.frames().isEmpty());
        assertEquals(0, result.topOwnedBytes().length);
    }
}
