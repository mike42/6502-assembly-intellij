package org.ca65.debugger.decode;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Mos6502DecoderTest {
    private final Mos6502Decoder myNmos = new Mos6502Decoder(true);
    private final Mos6502Decoder myCmos = new Mos6502Decoder(false);
    private final Map<Integer, Integer> myMemory = new HashMap<>();
    private final Map<String, Integer> myRegisters = new HashMap<>();

    private int read(int address) {
        return myMemory.getOrDefault(address, 0);
    }

    private Integer effective(InstructionDecoder.DecodedInstruction instruction, int pc) throws Exception {
        return myNmos.effectiveAddress(instruction, pc, myRegisters, this::read);
    }

    @Test
    public void decodesCommonInstructions() {
        InstructionDecoder.DecodedInstruction lda = myNmos.decode(0xa9, 0x05, 0x00);
        assertNotNull(lda);
        assertEquals("lda", lda.mnemonic());
        assertEquals(AddressingMode.IMMEDIATE, lda.mode());
        assertEquals(2, lda.length());

        InstructionDecoder.DecodedInstruction sta = myNmos.decode(0x9d, 0x00, 0x04);
        assertNotNull(sta);
        assertEquals(AddressingMode.ABSOLUTE_X, sta.mode());
        assertEquals(0x0400, sta.operand());
        assertEquals(3, sta.length());

        assertEquals(AddressingMode.IMPLIED, myNmos.decode(0x60, 0, 0).mode());   // rts
    }

    @Test
    public void undocumentedOpcodesDecodeToNull() {
        assertNull(myNmos.decode(0x02, 0, 0));    // JAM
        assertNull(myNmos.decode(0x80, 0, 0));    // undocumented nop
        // CMOS-only opcode (stz zp = $64) has no entry in the NMOS-common table
        assertNull(myCmos.decode(0x64, 0x10, 0));
    }

    @Test
    public void indexedModesAddTheRegister() throws Exception {
        myRegisters.put("X", 0x05);
        myRegisters.put("Y", 0x10);
        assertEquals(Integer.valueOf(0x0405),
                effective(myNmos.decode(0xbd, 0x00, 0x04), 0x1000));              // lda $0400,x
        assertEquals(Integer.valueOf(0x0410),
                effective(myNmos.decode(0xb9, 0x00, 0x04), 0x1000));              // lda $0400,y
    }

    @Test
    public void zeroPageIndexingWrapsInZeroPage() throws Exception {
        myRegisters.put("X", 0x10);
        assertEquals(Integer.valueOf(0x08),
                effective(myNmos.decode(0xb5, 0xf8, 0), 0x1000));                 // lda $f8,x
    }

    @Test
    public void indirectIndexedReadsZeroPagePointer() throws Exception {
        myRegisters.put("Y", 0x03);
        myMemory.put(0x02, 0x00);
        myMemory.put(0x03, 0xc0);
        assertEquals(Integer.valueOf(0xc003),
                effective(myNmos.decode(0xb1, 0x02, 0), 0x1000));                 // lda ($02),y
    }

    @Test
    public void indexedIndirectWrapsPointerInZeroPage() throws Exception {
        myRegisters.put("X", 0x02);
        myMemory.put(0xff, 0x34);
        myMemory.put(0x00, 0x12);
        assertEquals(Integer.valueOf(0x1234),
                effective(myNmos.decode(0xa1, 0xfd, 0), 0x1000));                 // lda ($fd,x)
    }

    @Test
    public void jmpIndirectPageBugIsNmosOnly() throws Exception {
        myMemory.put(0x02ff, 0x00);
        myMemory.put(0x0200, 0x40);   // NMOS wraps to here for the high byte
        myMemory.put(0x0300, 0x50);   // CMOS correctly reads across the page
        InstructionDecoder.DecodedInstruction jmp = myNmos.decode(0x6c, 0xff, 0x02);
        assertEquals(Integer.valueOf(0x4000),
                myNmos.effectiveAddress(jmp, 0x1000, myRegisters, this::read));
        assertEquals(Integer.valueOf(0x5000),
                myCmos.effectiveAddress(jmp, 0x1000, myRegisters, this::read));
    }

    @Test
    public void relativeBranchesShowSignedOffsetAndResolvedTarget() throws Exception {
        // bne with a negative offset: $0821: d0 fc  -> $081f
        InstructionDecoder.DecodedInstruction bne = myNmos.decode(0xd0, 0xfc, 0);
        assertEquals(AddressingMode.RELATIVE, bne.mode());
        assertEquals(Integer.valueOf(0x081f), effective(bne, 0x0821));
        assertEquals("bne -4", myNmos.format(bne, 0x0821));
        assertEquals("[→ $081f]", myNmos.describeOperand(bne, 0x0821, myRegisters, this::read));

        InstructionDecoder.DecodedInstruction forward = myNmos.decode(0x10, 0x06, 0);   // bpl +6
        assertEquals("bpl +6", myNmos.format(forward, 0x0810));
        assertEquals("[→ $0818]", myNmos.describeOperand(forward, 0x0810, myRegisters, this::read));
    }

    @Test
    public void indexedModesDescribeRegisterAndComputedAddress() throws Exception {
        myRegisters.put("X", 0x09);
        myRegisters.put("Y", 0x03);
        assertEquals("[X=$09 → $082d]",
                myNmos.describeOperand(myNmos.decode(0xbd, 0x24, 0x08), 0, myRegisters, this::read));
        myMemory.put(0x02, 0x00);
        myMemory.put(0x03, 0xc0);
        assertEquals("[Y=$03 → $c003]",
                myNmos.describeOperand(myNmos.decode(0x91, 0x02, 0), 0, myRegisters, this::read));
    }

    @Test
    public void selfContainedModesHaveNoOperandDescription() throws Exception {
        assertNull(myNmos.describeOperand(myNmos.decode(0xa9, 0x05, 0), 0, myRegisters, this::read));
        assertNull(myNmos.describeOperand(myNmos.decode(0xe6, 0x02, 0), 0, myRegisters, this::read));
        // absolute jmp/jsr: the 16-bit operand in the disassembly says it all
        assertNull(myNmos.describeOperand(myNmos.decode(0x20, 0x17, 0x08), 0, myRegisters, this::read));
    }

    @Test
    public void controlFlowInstructionsAreRecognized() {
        assertTrue(myNmos.isControlFlow(myNmos.decode(0x20, 0x17, 0x08)));   // jsr
        assertTrue(myNmos.isControlFlow(myNmos.decode(0x4c, 0x10, 0x08)));   // jmp
        assertTrue(myNmos.isControlFlow(myNmos.decode(0xd0, 0xfc, 0)));      // bne
        assertTrue(myNmos.isControlFlow(myNmos.decode(0x60, 0, 0)));         // rts
        assertFalse(myNmos.isControlFlow(myNmos.decode(0xbd, 0x24, 0x08)));  // lda abs,x
        assertFalse(myNmos.isControlFlow(myNmos.decode(0x91, 0x02, 0)));     // sta (zp),y
    }

    @Test
    public void impliedAndImmediateHaveNoEffectiveAddress() throws Exception {
        assertNull(effective(myNmos.decode(0xe8, 0, 0), 0x1000));                 // inx
        assertNull(effective(myNmos.decode(0xa9, 0x05, 0), 0x1000));              // lda #$05
    }

    @Test
    public void formatsCa65Style() {
        assertEquals("lda #$05", myNmos.format(myNmos.decode(0xa9, 0x05, 0), 0));
        assertEquals("inc $02", myNmos.format(myNmos.decode(0xe6, 0x02, 0), 0));
        assertEquals("lda $0824,x", myNmos.format(myNmos.decode(0xbd, 0x24, 0x08), 0));
        assertEquals("sta ($02),y", myNmos.format(myNmos.decode(0x91, 0x02, 0), 0));
        assertEquals("jmp ($02ff)", myNmos.format(myNmos.decode(0x6c, 0xff, 0x02), 0));
        assertEquals("rts", myNmos.format(myNmos.decode(0x60, 0, 0), 0));
    }

    @Test
    public void decoderSelectionIsCpuSpecific() {
        assertNotNull(InstructionDecoders.forCpuName("6502"));
        assertNotNull(InstructionDecoders.forCpuName("65C02"));
        assertNotNull(InstructionDecoders.forCpuName("45GS02"));
        // the 65816 has different encodings and must not reuse this table
        assertNull(InstructionDecoders.forCpuName("65816"));
    }
}
