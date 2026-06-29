.include "constants.inc"

start:
   lda #FILL_BYTE
   sta OUTPUT_PORT
   jsr PRINT_CHAR
   lda #DELAY_TICKS
   jsr PRINT_CHAR
   jmp start

FILL_BYTE = $20
