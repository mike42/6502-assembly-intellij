; Contrived constants for a fictional machine, used to exercise cross-file references.
.ifndef CONSTANTS_INC
CONSTANTS_INC = 1

OUTPUT_PORT = $40
DELAY_TICKS = $05

PRINT_CHAR := $9000
READ_CHAR  := $9003

; Defined here, referenced by nothing - should be flagged unused.
SPARE_VALUE := $1234

.endif
