package org.ca65.helpers;

import java.util.Set;

public class MnemonicHelper {
    public static MnemonicInfo[] allMnemonics = new MnemonicInfo[] {
            new MnemonicInfo("adc", "Add Memory to Accumulator with Carry"),
            new MnemonicInfo("and", "\"AND\" Memory with Accumulator"),
            new MnemonicInfo("asl", "Shift One Bit Left, Memory or Accumulator"),
            new MnemonicInfo("bbr0", "Branch on bit 0 reset"),
            new MnemonicInfo("bbr1", "Branch on bit 1 reset"),
            new MnemonicInfo("bbr2", "Branch on bit 2 reset"),
            new MnemonicInfo("bbr3", "Branch on bit 3 reset"),
            new MnemonicInfo("bbr4", "Branch on bit 4 reset"),
            new MnemonicInfo("bbr5", "Branch on bit 5 reset"),
            new MnemonicInfo("bbr6", "Branch on bit 6 reset"),
            new MnemonicInfo("bbr7", "Branch on bit 7 reset"),
            new MnemonicInfo("bbs0", "Branch on bit 0 set"),
            new MnemonicInfo("bbs1", "Branch on bit 1 set"),
            new MnemonicInfo("bbs2", "Branch on bit 2 set"),
            new MnemonicInfo("bbs3", "Branch on bit 3 set"),
            new MnemonicInfo("bbs4", "Branch on bit 4 set"),
            new MnemonicInfo("bbs5", "Branch on bit 5 set"),
            new MnemonicInfo("bbs6", "Branch on bit 6 set"),
            new MnemonicInfo("bbs7", "Branch on bit 7 set"),
            new MnemonicInfo("bcc", "Branch on Carry Clear (C=0)"),
            new MnemonicInfo("bcs", "Branch on Carry Set (C=1)"),
            new MnemonicInfo("beq", "Branch if Equal (Z=1)"),
            new MnemonicInfo("bit", "Bit Test"),
            new MnemonicInfo("bmi", "Branch if Result Minus (N=1)"),
            new MnemonicInfo("bne", "Branch if Not Equal (Z=0)"),
            new MnemonicInfo("bpl", "Branch if Result Plus (N=0)"),
            new MnemonicInfo("bra", "Branch Always"),
            new MnemonicInfo("brk", "Force Break"),
            new MnemonicInfo("brl", "Branch Always Long"),
            new MnemonicInfo("bvc", "Branch on Overflow Clear (V=0)"),
            new MnemonicInfo("bvs", "Branch on Overflow Set (V=1)"),
            new MnemonicInfo("clc", "Clear Carry Flag"),
            new MnemonicInfo("cld", "Clear Decimal Mode"),
            new MnemonicInfo("cli", "Clear Interrupt Disable Bit"),
            new MnemonicInfo("clv", "Clear Overflow Flag"),
            new MnemonicInfo("cmp", "Compare Memory and Accumulator"),
            new MnemonicInfo("cop", "Coprocessor"),
            new MnemonicInfo("cpx", "Compare Memory and Index X"),
            new MnemonicInfo("cpy", "Compare Memory and Index Y"),
            new MnemonicInfo("dec", "Decrement Memory or Accumulator by One"),
            new MnemonicInfo("dex", "Decrement Index X by One"),
            new MnemonicInfo("dey", "Decrement Index Y by One"),
            new MnemonicInfo("eor", "\"Exclusive OR\" Memory with Accumulator"),
            new MnemonicInfo("inc", "Increment Memory or Accumulator by One"),
            new MnemonicInfo("inx", "Increment Index X by One"),
            new MnemonicInfo("iny", "Increment Index Y by One"),
            new MnemonicInfo("jml", "Jump Long"),
            new MnemonicInfo("jmp", "Jump to New Location"),
            new MnemonicInfo("jsl", "Jump Subroutine Long"),
            new MnemonicInfo("jsr", "Jump to News Location Saving Return"),
            new MnemonicInfo("lda", "Load Accumulator with Memory"),
            new MnemonicInfo("ldx", "Load Index X with Memory"),
            new MnemonicInfo("ldy", "Load Index Y with Memory"),
            new MnemonicInfo("lsr", "Shift One Bit Right (Memory or Accumulator)"),
            new MnemonicInfo("mvn", "Block Move Negative"),
            new MnemonicInfo("mvp", "Block Move Positive"),
            new MnemonicInfo("nop", "No Operation"),
            new MnemonicInfo("ora", "\"OR\" Memory with Accumulator"),
            new MnemonicInfo("pea", "Push Absolute Address"),
            new MnemonicInfo("pei", "Push Indirect Address"),
            new MnemonicInfo("per", "Push Program Counter Relative Address"),
            new MnemonicInfo("pha", "Push Accumulator on Stack"),
            new MnemonicInfo("phb", "Push Data Bank Register on Stack"),
            new MnemonicInfo("phd", "Push Direct Register on Stack"),
            new MnemonicInfo("phk", "Push Program Bank Register on Stack"),
            new MnemonicInfo("php", "Push Processor Status on Stack"),
            new MnemonicInfo("phx", "Push Index X on Stack"),
            new MnemonicInfo("phy", "Push Index Y on Stack"),
            new MnemonicInfo("pla", "Pull Accumulator from Stack"),
            new MnemonicInfo("plb", "Pull Data Bank Register from Stack"),
            new MnemonicInfo("pld", "Pull Direct Register from Stack"),
            new MnemonicInfo("plp", "Pull Processor Status from Stack"),
            new MnemonicInfo("plx", "Pull Index X from Stack"),
            new MnemonicInfo("ply", "Pull Index Y from Stack"),
            new MnemonicInfo("rep", "Reset Status Bits"),
            new MnemonicInfo("rmb0", "Reset Memory Bit 0"),
            new MnemonicInfo("rmb1", "Reset Memory Bit 1"),
            new MnemonicInfo("rmb2", "Reset Memory Bit 2"),
            new MnemonicInfo("rmb3", "Reset Memory Bit 3"),
            new MnemonicInfo("rmb4", "Reset Memory Bit 4"),
            new MnemonicInfo("rmb5", "Reset Memory Bit 5"),
            new MnemonicInfo("rmb6", "Reset Memory Bit 6"),
            new MnemonicInfo("rmb7", "Reset Memory Bit 7"),
            new MnemonicInfo("rol", "Rotate One Bit Left (Memory or Accumulator)"),
            new MnemonicInfo("ror", "Rotate One Bit Right"),
            new MnemonicInfo("rti", "Return from Interrupt"),
            new MnemonicInfo("rtl", "Return from Subroutine Long"),
            new MnemonicInfo("rts", "Return from Subroutine"),
            new MnemonicInfo("sbc", "Subtract Memory from Accumulator"),
            new MnemonicInfo("sep", "Set Processor Status Bit"),
            new MnemonicInfo("sec", "Set Carry Flag"),
            new MnemonicInfo("sed", "Set Decimal Mode"),
            new MnemonicInfo("sei", "Set Interrupt Disable Status"),
            new MnemonicInfo("smb0", "Set Memory Bit 0"),
            new MnemonicInfo("smb1", "Set Memory Bit 1"),
            new MnemonicInfo("smb2", "Set Memory Bit 2"),
            new MnemonicInfo("smb3", "Set Memory Bit 3"),
            new MnemonicInfo("smb4", "Set Memory Bit 4"),
            new MnemonicInfo("smb5", "Set Memory Bit 5"),
            new MnemonicInfo("smb6", "Set Memory Bit 6"),
            new MnemonicInfo("smb7", "Set Memory Bit 7"),
            new MnemonicInfo("sta", "Store Accumulator in Memory"),
            new MnemonicInfo("stp", "Stop the Clock"),
            new MnemonicInfo("stx", "Store Index X in Memory"),
            new MnemonicInfo("sty", "Store Index Y in Memory"),
            new MnemonicInfo("stz", "Store Zero in Memory"),
            new MnemonicInfo("tax", "Transfer Accumulator in Index X"),
            new MnemonicInfo("tay", "Transfer Accumulator to Index Y"),
            new MnemonicInfo("tcd", "Transfer C Accumulator to Direct Register"),
            new MnemonicInfo("tcs", "Transfer C Accumulator to Stack Pointer"),
            new MnemonicInfo("tdc", "Transfer Direct Register to C Accumulator"),
            new MnemonicInfo("trb", "Test and Reset Bit"),
            new MnemonicInfo("tsb", "Test and Set Bit"),
            new MnemonicInfo("tsc", "Transfer Stack Pointer to C Accumulator"),
            new MnemonicInfo("tsx", "Transfer Stack Pointer Register to Index X"),
            new MnemonicInfo("txa", "Transfer Index X to Accumulator"),
            new MnemonicInfo("txs", "Transfer Index X to Stack Pointer Register"),
            new MnemonicInfo("txy", "Transfer Index X to Index Y"),
            new MnemonicInfo("tya", "Transfer Index Y to Accumulator"),
            new MnemonicInfo("tyx", "Transfer Index Y to Index X"),
            new MnemonicInfo("wai", "Wait for Interrupt"),
            new MnemonicInfo("wdm", "Reserved for future use"),
            new MnemonicInfo("xba", "Exchange B and A Accumulator"),
            new MnemonicInfo("xce", "Exchange Carry and Emulation Bits"),
    };

    public static Set<String> validMnemonics65C816 = Set.of("adc", "and", "asl", "bcc", "bcs", "beq", "bit", "bmi", "bne", "bpl", "bra", "brk", "brl", "bvc", "bvs", "clc", "cld", "cli", "clv", "cmp", "cop", "cpx", "cpy", "dec", "dex", "dey", "eor", "inc", "inx", "iny", "jml", "jmp", "jsl", "jsr", "lda", "ldx", "ldy", "lsr", "mvn", "mvp", "nop", "ora", "pea", "pei", "per", "pha", "phb", "phd", "phk", "php", "phx", "phy", "pla", "plb", "pld", "plp", "plx", "ply", "rep", "rol", "ror", "rti", "rtl", "rts", "sbc", "sep", "sec", "sed", "sei", "sta", "stp", "stx", "sty", "stz", "tax", "tay", "tcd", "tcs", "tdc", "trb", "tsb", "tsc", "tsx", "txa", "txs", "txy", "tya", "tyx", "wai", "wdm", "xba", "xce");
    public static Set<String> validMnemonics65C02 = Set.of("adc", "and", "asl", "bbr0", "bbr1", "bbr2", "bbr3", "bbr4", "bbr5", "bbr6", "bbr7", "bbs0", "bbs1", "bbs2", "bbs3", "bbs4", "bbs5", "bbs6", "bbs7", "bcc", "bcs", "beq", "bit", "bmi", "bne", "bpl", "bra", "brk", "bvc", "bvs", "clc", "cld", "cli", "clv", "cmp", "cpx", "cpy", "dec", "dex", "dey", "eor", "inc", "inx", "iny", "jmp", "jsr", "lda", "ldx", "ldy", "lsr", "nop", "ora", "pha", "php", "phx", "phy", "pla", "plp", "plx", "ply", "rmb0", "rmb1", "rmb2", "rmb3", "rmb4", "rmb5", "rmb6", "rmb7", "rol", "ror", "rti", "rts", "sbc", "sec", "sed", "sei", "smb0", "smb1", "smb2", "smb3", "smb4", "smb5", "smb6", "smb7", "sta", "stp", "stx", "sty", "stz", "tax", "tay", "trb", "tsb", "tsx", "txa", "txs", "tya", "wai");
    public static Set<String> validMnemonics6502 = Set.of("adc", "and", "asl", "bcc", "bcs", "beq", "bit", "bmi", "bne", "bpl", "brk", "bvc", "bvs", "clc", "cld", "cli", "clv", "cmp", "cpx", "cpy", "dec", "dex", "dey", "eor", "inc", "inx", "iny", "jmp", "jsr", "lda", "ldx", "ldy", "lsr", "nop", "ora", "pha", "php", "pla", "plp", "ror", "rti", "rts", "sbc", "sec", "sed", "sei", "sta", "stx", "sty", "tax", "tay", "tsx", "txa", "txs", "tya");

    public static Set<String> getMnemonicsForCpu(Cpu cpu) {
        switch (cpu) {
            case CPU_6502:
                return validMnemonics6502;
            case CPU_65C02:
                return validMnemonics65C02;
            case CPU_65C816:
                return validMnemonics65C816;
        }
        throw new UnsupportedOperationException("Unknown CPU " + cpu);
    }
}