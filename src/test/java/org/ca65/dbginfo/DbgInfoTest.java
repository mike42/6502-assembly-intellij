package org.ca65.dbginfo;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Parses the real .dbg produced by `ca65 -g` + `ld65 --dbgfile` for the
 * hello-world program (src/test/resources/dbginfo/hello.dbg). Expected
 * addresses cross-checked against the label file and the VICE probes:
 * start=$080E, loop=$081F (line 40, inc), jmp loop=$0821 (line 41).
 */
public class DbgInfoTest {
    private DbgInfo myDbgInfo;

    @Before
    public void setUp() throws IOException {
        try (var stream = DbgInfoTest.class.getResourceAsStream("/dbginfo/hello.dbg")) {
            assertNotNull(stream);
            myDbgInfo = DbgInfo.parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    @Test
    public void lineToAddress() {
        assertEquals(Long.valueOf(0x081F), myDbgInfo.getAddressForLine("hello.s", 40));
        assertEquals(Long.valueOf(0x0821), myDbgInfo.getAddressForLine("hello.s", 41));
        assertEquals(Long.valueOf(0x080E), myDbgInfo.getAddressForLine("hello.s", 29));
    }

    @Test
    public void lineWithoutCodeHasNoAddress() {
        assertNull(myDbgInfo.getAddressForLine("hello.s", 28));   // "start:" label only
        assertNull(myDbgInfo.getAddressForLine("hello.s", 27));   // .segment directive
    }

    @Test
    public void unknownFileHasNoAddress() {
        assertNull(myDbgInfo.getAddressForLine("other.s", 40));
    }

    @Test
    public void pathIsMatchedBySuffix() {
        assertEquals(Long.valueOf(0x081F),
                myDbgInfo.getAddressForLine("/home/user/project/hello.s", 40));
    }

    @Test
    public void addressToPosition() {
        DbgInfo.SourcePosition position = myDbgInfo.getPositionForAddress(0x081F);
        assertNotNull(position);
        assertEquals("hello.s", position.fileName());
        assertEquals(40, position.line());
    }

    @Test
    public void addressInsideInstructionMapsToItsLine() {
        DbgInfo.SourcePosition position = myDbgInfo.getPositionForAddress(0x0822);
        assertNotNull(position);
        assertEquals(41, position.line());
    }

    @Test
    public void romAddressHasNoPosition() {
        assertNull(myDbgInfo.getPositionForAddress(0xFFD2));
    }

    @Test
    public void symbols() {
        DbgInfo.Symbol loop = myDbgInfo.getSymbol("loop");
        assertNotNull(loop);
        assertEquals(0x081F, loop.value());
        assertEquals("lab", loop.type());
        assertEquals("CODE", loop.segmentName());

        DbgInfo.Symbol chrout = myDbgInfo.getSymbol("CHROUT");
        assertNotNull(chrout);
        assertEquals(0xFFD2, chrout.value());
        assertEquals("equ", chrout.type());

        assertNull(myDbgInfo.getSymbol("nonexistent"));
    }
}
