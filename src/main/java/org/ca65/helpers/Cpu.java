package org.ca65.helpers;

public enum Cpu {
    CPU_6502("6502"),
    CPU_65C02("65C02"),
    CPU_65C816("65C816");

    Cpu(String name) {
        this.name = name;
    }

    public final String name;
}
