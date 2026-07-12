; hello.s - C64 "hello world" test program for debugger testing.
;
; Prints a message via the KERNAL, then spins forever incrementing a
; counter at COUNTER_ADDR so there is something to set breakpoints and
; watchpoints on.
;
; Build:
;   ca65 -g --cpu 6502 -o hello.o hello.s
;   ld65 -C c64-asm.cfg -o hello.prg --dbgfile hello.dbg -Ln hello.lbl -m hello.map hello.o

CHROUT = $ffd2                  ; KERNAL: output character in A
COUNTER = $02                   ; zero page scratch, safe once BASIC is not used

.export __LOADADDR__: absolute = 1

.segment "LOADADDR"
        .addr *+2               ; PRG load address ($0801)

.segment "EXEHDR"
        ; BASIC stub: 10 SYS 2062
        .word @nextline
        .word 10                ; line number
        .byte $9e, " 2062", $00 ; SYS token + address of `start`
@nextline:
        .word 0                 ; end of BASIC program

.segment "CODE"
start:
        ldx #0
print:
        lda message,x
        beq printed
        jsr CHROUT
        inx
        bne print
printed:
        lda #0
        sta COUNTER
loop:
        inc COUNTER             ; watchpoint target
        jmp loop                ; breakpoint target

.segment "RODATA"
message:
        .byte "HELLO, WORLD!", $0d, $00
