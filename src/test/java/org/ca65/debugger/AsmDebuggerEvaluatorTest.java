package org.ca65.debugger;

import com.intellij.openapi.util.TextRange;
import org.ca65.dbginfo.DbgInfo;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Pure expression-resolution logic of the debugger evaluator, against the
 * real hello.dbg from the test resources.
 */
public class AsmDebuggerEvaluatorTest {
    private static final Set<String> REGISTERS = Set.of("A", "X", "Y", "SP", "PC", "FL");
    private static DbgInfo ourDbgInfo;

    @BeforeClass
    public static void parseDbgFile() throws Exception {
        try (Reader reader = new InputStreamReader(
                AsmDebuggerEvaluatorTest.class.getResourceAsStream("/dbginfo/hello.dbg"),
                StandardCharsets.UTF_8)) {
            ourDbgInfo = DbgInfo.parse(reader);
        }
    }

    private static AsmDebuggerEvaluator.Target resolve(String expression) {
        return AsmDebuggerEvaluator.resolve(expression, REGISTERS, ourDbgInfo);
    }

    @Test
    public void registerNamesResolveCaseInsensitively() {
        assertEquals(new AsmDebuggerEvaluator.Target.Register("A"), resolve("a"));
        assertEquals(new AsmDebuggerEvaluator.Target.Register("PC"), resolve("PC"));
    }

    @Test
    public void hexAddressResolvesToMemory() {
        assertEquals(new AsmDebuggerEvaluator.Target.Memory(0x0400), resolve("$0400"));
        assertEquals(new AsmDebuggerEvaluator.Target.Memory(0x02), resolve(" $02 "));
    }

    @Test
    public void zeroPageEquateResolvesToItsAddress() {
        // COUNTER = $02 in hello.s
        assertEquals(new AsmDebuggerEvaluator.Target.Memory(0x02), resolve("COUNTER"));
    }

    @Test
    public void kernalEquateResolvesToItsAddress() {
        assertEquals(new AsmDebuggerEvaluator.Target.Memory(0xffd2), resolve("CHROUT"));
    }

    @Test
    public void codeLabelResolvesToItsAddress() {
        assertEquals(new AsmDebuggerEvaluator.Target.Memory(0x081f), resolve("loop"));
    }

    @Test
    public void unknownNamesAndMalformedInputResolveToNull() {
        assertNull(resolve("bogus_name"));
        assertNull(resolve("$xyz"));
        assertNull(resolve("$10000"));
        assertNull(resolve(""));
    }

    @Test
    public void numberParsingSupportsAssemblerRadixes() {
        assertEquals(Integer.valueOf(0x1f), AsmDebuggerEvaluator.parseNumber("$1f"));
        assertEquals(Integer.valueOf(31), AsmDebuggerEvaluator.parseNumber("31"));
        assertEquals(Integer.valueOf(31), AsmDebuggerEvaluator.parseNumber("%00011111"));
        assertNull(AsmDebuggerEvaluator.parseNumber("banana"));
    }

    @Test
    public void hoverRangeCoversIdentifier() {
        String text = "        lda message,x";
        int offset = text.indexOf("essage");
        assertEquals(new TextRange(12, 19), AsmDebuggerEvaluator.expressionRangeAt(text, offset));
        assertEquals("message", text.substring(12, 19));
    }

    @Test
    public void hoverRangeIncludesHexSigil() {
        String text = "        sta $d020";
        int offset = text.indexOf("d020");
        TextRange range = AsmDebuggerEvaluator.expressionRangeAt(text, offset);
        assertEquals("$d020", text.subSequence(range.getStartOffset(), range.getEndOffset()).toString());
    }

    @Test
    public void hoverRangeOutsideIdentifiersIsNull() {
        String text = "lda message,x";
        assertNull(AsmDebuggerEvaluator.expressionRangeAt(text, text.indexOf(',')));
        assertNull(AsmDebuggerEvaluator.expressionRangeAt(text, text.length()));
    }
}
