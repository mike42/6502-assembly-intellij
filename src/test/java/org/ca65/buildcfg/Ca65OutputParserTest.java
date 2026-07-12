package org.ca65.buildcfg;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class Ca65OutputParserTest {

    @Test
    public void parsesCa65Error() {
        Ca65OutputParser.Diagnostic d = Ca65OutputParser.parseLine("hello.s(2): Error: ':' expected");
        assertEquals(Ca65OutputParser.Kind.ERROR, d.kind());
        assertEquals("hello.s", d.file());
        assertEquals(Integer.valueOf(2), d.line());
        assertEquals("':' expected", d.message());
    }

    @Test
    public void parsesWarningWithPathAndParens() {
        Ca65OutputParser.Diagnostic d = Ca65OutputParser.parseLine(
                "src/irq (copy).s(123): Warning: Range error (2457 not in [0..255])");
        assertEquals(Ca65OutputParser.Kind.WARNING, d.kind());
        assertEquals("src/irq (copy).s", d.file());
        assertEquals(Integer.valueOf(123), d.line());
        assertEquals("Range error (2457 not in [0..255])", d.message());
    }

    @Test
    public void parsesFilelessLinkerError() {
        Ca65OutputParser.Diagnostic d = Ca65OutputParser.parseLine(
                "ld65: Error: Cannot open 'missing.o': No such file or directory");
        assertEquals(Ca65OutputParser.Kind.ERROR, d.kind());
        assertNull(d.file());
        assertNull(d.line());
    }

    @Test
    public void ignoresOrdinaryOutput() {
        assertNull(Ca65OutputParser.parseLine("ca65 V2.18 - Debian 2.19-2"));
        assertNull(Ca65OutputParser.parseLine(""));
        assertNull(Ca65OutputParser.parseLine("some random text"));
    }
}
