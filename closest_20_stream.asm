; closest_20_stream.asm
; Fixed-20 streaming approach with minimal memory use.
; Input order:
;   TARGET  C1 C2 ... C20
; All values are multi-digit, signed (optional leading '-'), separated by any non-digit.
; We read TARGET first, then process exactly 20 candidates streaming.
; Track absolute difference and winner; finally print the winner as a multi-digit decimal.
;
; IMPORTANT: Addresses 000000..000005 are machine-reserved. We set LOC 6.
; Data segment is placed at LOC 239.. to stay within 8-bit address space.

LOC 6

; Ensure entry jumps to START instead of falling into READ_INT
JMA START

; === Inline multi-digit parsers with fixed return labels ===
; We avoid subroutine call/return to keep R3 usage simple (R3 is used by IN/OUT and as link in JSR).

; Parser for TARGET: stores into TARGET then jumps to AFTER_TARGET
READ_TARGET:
    LDR 0,ZERO      ; result
    LDR 1,ZERO      ; signFlag (1 if '-')
    LDR 2,ZERO      ; seenDigit
RT_LOOP:
    IN 3,0
    STR 3,READBUF
    ; If no digit yet, allow leading '-'
    JZ 2,RT_CHECK_SIGN
RT_DIGIT_CHECK:
    LDR 3,READBUF
    SMR 3,ASCII_0
    JGE 3,RT_GE_ZERO
    ; Not a digit: if no digit yet, keep scanning; else finalize
    JZ 2,RT_LOOP
    JMA RT_DONE
RT_CHECK_SIGN:
    LDR 3,READBUF
    SMR 3,ASCII_MINUS
    JZ 3,RT_SET_NEG
    JMA RT_DIGIT_CHECK
RT_SET_NEG:
    LDR 1,ZERO
    AIR 1,1
    JMA RT_LOOP
RT_GE_ZERO:
    SIR 3,10
    JGE 3,RT_NOT_DIGIT
    AIR 3,10
    ; seenDigit = 1
    LDR 2,ZERO
    AIR 2,1
    ; Accumulate: R0 = R0*10 + digit (x10 via 8+2)
    STR 3,PRINT_TMP
    STR 0,READBUF
    LDR 0,READBUF
    SRC 0,1,1,1
    STR 0,TEMP_DIFF
    LDR 0,READBUF
    SRC 0,3,1,1
    AMR 0,TEMP_DIFF
    AMR 0,PRINT_TMP
    JMA RT_LOOP
RT_NOT_DIGIT:
    JZ 2,RT_LOOP
RT_DONE:
    ; Apply sign
    JZ 1,RT_POS
    NOT 0
    AIR 0,1
RT_POS:
    STR 0,TARGET
    JMA AFTER_TARGET

; Parser for a candidate: stores into READVAL then jumps to AFTER_CAND
READ_CAND:
    LDR 0,ZERO      ; result
    LDR 1,ZERO      ; signFlag
    LDR 2,ZERO      ; seenDigit
RC_LOOP:
    IN 3,0
    STR 3,READBUF
    JZ 2,RC_CHECK_SIGN
RC_DIGIT_CHECK:
    LDR 3,READBUF
    SMR 3,ASCII_0
    JGE 3,RC_GE_ZERO
    JZ 2,RC_LOOP
    JMA RC_DONE
RC_CHECK_SIGN:
    LDR 3,READBUF
    SMR 3,ASCII_MINUS
    JZ 3,RC_SET_NEG
    JMA RC_DIGIT_CHECK
RC_SET_NEG:
    LDR 1,ZERO
    AIR 1,1
    JMA RC_LOOP
RC_GE_ZERO:
    SIR 3,10
    JGE 3,RC_NOT_DIGIT
    AIR 3,10
    LDR 2,ZERO
    AIR 2,1
    STR 3,PRINT_TMP
    STR 0,READBUF
    LDR 0,READBUF
    SRC 0,1,1,1
    STR 0,TEMP_DIFF
    LDR 0,READBUF
    SRC 0,3,1,1
    AMR 0,TEMP_DIFF
    AMR 0,PRINT_TMP
    JMA RC_LOOP
RC_NOT_DIGIT:
    JZ 2,RC_LOOP
RC_DONE:
    JZ 1,RC_POS
    NOT 0
    AIR 0,1
RC_POS:
    STR 0,READVAL
    ; Dispatch return based on MODE: 1 -> first-candidate init, 0 -> loop
    LDR 2,MODE
    JZ 2,AFTER_CAND_LOOP
    ; MODE==1 -> clear MODE and go to first-candidate return
    LDR 2,ZERO
    STR 2,MODE
    JMA AFTER_CAND_FIRST

; === Main ===
START:
    ; Read TARGET first
    JMA READ_TARGET
AFTER_TARGET:

    ; Read first candidate -> initialize WINNER/MIN_DIFF
    ; Indicate first-candidate return path via MODE=1
    LDR 2,ZERO
    AIR 2,1
    STR 2,MODE
    JMA READ_CAND
AFTER_CAND_FIRST:
    LDR 0,READVAL
    STR 0,WINNER
    LDR 0,WINNER
    SMR 0,TARGET
    JGE 0,MINPOS0
    NOT 0
    AIR 0,1
MINPOS0:
    STR 0,MIN_DIFF

    ; Set CNT = 19 remaining (since we already processed 1st)
    LDR 2,ZERO
    AIR 2,19
    STR 2,CNT

; Loop remaining 19 candidates
CAND_LOOP:
    LDR 2,CNT
    JZ 2,COMP_DONE
    SIR 2,1
    STR 2,CNT

    ; Ensure MODE=0 for loop returns
    LDR 2,ZERO
    STR 2,MODE
    JMA READ_CAND
AFTER_CAND_LOOP:
    LDR 0,READVAL
    STR 0,PRINT_TMP

    ; diff = |cand - TARGET|
    LDR 0,PRINT_TMP
    SMR 0,TARGET
    JGE 0,DIFFPOS
    NOT 0
    AIR 0,1
DIFFPOS:
    STR 0,TEMP_DIFF

    ; if diff < MIN_DIFF -> update MIN_DIFF and WINNER
    LDR 1,MIN_DIFF
    LDR 2,TEMP_DIFF
    SMR 2,MIN_DIFF
    JGE 2,NO_UPD
    LDR 2,TEMP_DIFF
    STR 2,MIN_DIFF
    LDR 3,PRINT_TMP
    STR 3,WINNER
NO_UPD:
    JMA CAND_LOOP

; Print WINNER as multi-digit decimal with sign
COMP_DONE:
    ; R1 holds |WINNER|, R2=pow, R0 temp
    LDR 0,WINNER
    JGE 0,PW_ABS
    LDR 3,ASCII_MINUS
    OUT 3,1
    LDR 1,WINNER
    NOT 1
    AIR 1,1
    JMA PW_ZCHK
PW_ABS:
    LDR 1,WINNER
PW_ZCHK:
    SMR 1,ZERO
    JNE 1,PW_FINDPOW
    LDR 3,ASCII_0
    OUT 3,1
    HLT

; Find highest power of 10 without overflow: tmp=R1; pow=1; while (tmp>=10) {tmp/=10; pow*=10}
PW_FINDPOW:
    LDR 2,ZERO
    AIR 2,1
    STR 1,READBUF
PW_POW_LOOP:
    LDR 0,READBUF
    SIR 0,10
    JGE 0,PW_POW_STEP
    JMA PW_PRINT
PW_POW_STEP:
    LDR 0,READBUF
    LDR 3,ZERO
    AIR 3,10
    DVD 0,3
    STR 0,READBUF
    STR 2,PRINT_TMP
    LDR 0,PRINT_TMP
    SRC 0,1,1,1
    STR 0,TEMP_DIFF
    LDR 0,PRINT_TMP
    SRC 0,3,1,1
    AMR 0,TEMP_DIFF
    STR 0,PRINT_TMP
    LDR 2,PRINT_TMP
    JMA PW_POW_LOOP

PW_PRINT:
PW_PRINT_LOOP:
    STR 2,PRINT_TMP
    STR 1,READBUF
    LDR 0,READBUF
    LDR 2,PRINT_TMP
    DVD 0,2
    STR 0,TEMP_DIFF
    STR 2,READBUF
    LDR 1,READBUF
    LDR 2,PRINT_TMP
    LDR 0,ZERO
    AIR 0,10
    DVD 2,0
    LDR 0,TEMP_DIFF
    STR 0,PRINT_TMP
    LDR 3,ASCII_0
    AMR 3,PRINT_TMP
    OUT 3,1
    SMR 2,ZERO
    JNE 2,PW_PRINT_LOOP
    HLT

; --- Data ---
LOC 239
ASCII_0:     DATA 48
ASCII_MINUS: DATA 45
TEMP_DIFF:    DATA 0
MIN_DIFF:     DATA 0
WINNER:       DATA 0
CNT:          DATA 0
TARGET:       DATA 0
READBUF:      DATA 0
PRINT_TMP:    DATA 0
ZERO:         DATA 0
READVAL:      DATA 0
LINK_SAVE:    DATA 0
MODE:         DATA 0

; End
