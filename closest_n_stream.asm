; closest_n_stream.asm
; Option B: Streaming approach supporting up to N=20 without storing candidates.
; Input order:
;   N  TARGET  CAND1 CAND2 ... CANDN
; All values are multi-digit, signed (optional leading '-'), separated by any non-digit.
; We parse N, cap to 20, parse TARGET, then read N candidates one by one,
; compute absolute difference to TARGET and track the minimum; finally print
; the WINNER in full decimal (multi-digit) with sign.
;
; IMPORTANT: Addresses 000000..000005 are machine-reserved. We set LOC 6.
; Data segment is placed at LOC 239.. to stay within 8-bit address space.

LOC 6

; Ensure entry jumps to START instead of falling into READ_INT
JMA START

; === Unified multi-digit parser (R0=result, R1=signFlag, R2=seenDigit, R3=char/temp) ===
; MODE (stored in MODE_LOC) is not used here; we call parser directly as a subroutine-like block.
; On exit: R0 holds the parsed signed integer.

READ_INT:
    ; Preserve link register R3 (JSR stores return address in R3)
    STR 3,LINK_SAVE
    ; Clear state
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
RINT_LOOP:
    IN 3,0
    STR 3,READBUF
    ; If no digit seen, allow leading '-'
    JZ 2,RINT_CHECK_SIGN
RINT_DIGIT_CHECK:
    LDR 3,READBUF
    SMR 3,ASCII_0
    JGE 3,RINT_GE_ZERO
    ; Not a digit: skip if no digit yet, else done
    JZ 2,RINT_LOOP
    JMA RINT_DONE
RINT_CHECK_SIGN:
    LDR 3,READBUF
    SMR 3,ASCII_MINUS
    JZ 3,RINT_SET_NEG
    JMA RINT_DIGIT_CHECK
RINT_SET_NEG:
    LDR 1,ZERO
    AIR 1,1
    JMA RINT_LOOP
RINT_GE_ZERO:
    SIR 3,10
    JGE 3,RINT_NOT_DIGIT
    AIR 3,10
    ; seenDigit = 1
    LDR 2,ZERO
    AIR 2,1
    ; Accumulate: R0 = R0*10 + digit
    STR 3,PRINT_TMP
    STR 0,READBUF
    LDR 0,READBUF
    SRC 0,1,1,1
    STR 0,TEMP_DIFF
    LDR 0,READBUF
    SRC 0,3,1,1
    AMR 0,TEMP_DIFF
    AMR 0,PRINT_TMP
    JMA RINT_LOOP
RINT_NOT_DIGIT:
    JZ 2,RINT_LOOP
    ; else done
RINT_DONE:
    ; Apply sign if needed
    JZ 1,RINT_POS
    NOT 0
    AIR 0,1
RINT_POS:
    STR 0,READVAL
    ; Restore link register and return
    LDR 3,LINK_SAVE
    RFS 0 ; return to caller (R0 overwritten by RFS immediate)

; === Main ===
; Read N
START:
    JSR READ_INT
    LDR 0,READVAL
    STR 0,PRINT_TMP  ; store raw N
    ; Cap N to 20 if larger
    LDR 2,PRINT_TMP
    SIR 2,20
    JGE 2,N_CAP
    ; use N as-is
    LDR 0,PRINT_TMP
    STR 0,CNT
    STR 0,CNT_INIT
    JMA READ_TARGET
N_CAP:
    LDR 0,ZERO
    AIR 0,20
    STR 0,CNT
    STR 0,CNT_INIT

; Read TARGET
READ_TARGET:
    JSR READ_INT
    LDR 0,READVAL
    STR 0,TARGET

; If CNT_INIT == 0 then nothing to compare -> HLT
    LDR 2,CNT_INIT
    JZ 2,HALT_NOW

; Read first candidate to initialize MIN_DIFF and WINNER
INIT_FIRST:
    JSR READ_INT
    LDR 0,READVAL
    ; Initialize WINNER to first candidate immediately
    STR 0,WINNER
    STR 0,PRINT_TMP  ; cand in PRINT_TMP
    LDR 0,PRINT_TMP
    SMR 0,TARGET
    JGE 0,MINPOS0
    NOT 0
    AIR 0,1
MINPOS0:
    STR 0,MIN_DIFF
    ; CNT--
    LDR 2,CNT
    SIR 2,1
    STR 2,CNT
    JZ 2,COMP_DONE

; Loop remaining candidates
CAND_LOOP:
    ; If no more candidates, finish
    LDR 2,CNT
    JZ 2,COMP_DONE
    ; Pre-decrement CNT
    SIR 2,1
    STR 2,CNT
    ; Read next candidate
    JSR READ_INT
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
    ; if diff < MIN_DIFF then update
    LDR 1,MIN_DIFF
    LDR 2,TEMP_DIFF
    SMR 2,MIN_DIFF
    JGE 2,NO_UPD
    ; update
    LDR 2,TEMP_DIFF
    STR 2,MIN_DIFF
    LDR 3,PRINT_TMP
    STR 3,WINNER
NO_UPD:
    ; Continue loop
    JMA CAND_LOOP

COMP_DONE:
    ; Print WINNER as multi-digit decimal
    ; R1 holds |WINNER|, R2=pow, R0 temp
    ; Handle sign
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
    ; If R1 == 0 -> print '0'
    SMR 1,ZERO
    JNE 1,PW_FINDPOW
    LDR 3,ASCII_0
    OUT 3,1
    HLT

PW_FINDPOW:
    ; Compute highest decimal power (R2) such that R2 <= |WINNER| without overflow.
    ; Approach: tmp = R1; pow = 1; while (tmp >= 10) { tmp/=10; pow*=10 }
    ; Initialize pow = 1
    LDR 2,ZERO
    AIR 2,1
    ; tmp = R1
    STR 1,READBUF
PW_POW_LOOP:
    ; Check if tmp >= 10
    LDR 0,READBUF         ; R0 = tmp
    SIR 0,10              ; R0 = tmp - 10
    JGE 0,PW_POW_STEP     ; if tmp >= 10, step
    ; else done, proceed to print with current pow
    JMA PW_PRINT
PW_POW_STEP:
    ; tmp = tmp / 10
    LDR 0,READBUF         ; R0 = tmp
    LDR 3,ZERO
    AIR 3,10              ; R3 = 10
    DVD 0,3               ; R0 = tmp/10, R3 = tmp%10
    STR 0,READBUF         ; save new tmp
    ; pow = pow * 10 (via 8+2)
    STR 2,PRINT_TMP       ; save pow
    LDR 0,PRINT_TMP       ; R0 = pow
    SRC 0,1,1,1           ; 2*pow
    STR 0,TEMP_DIFF
    LDR 0,PRINT_TMP       ; R0 = pow
    SRC 0,3,1,1           ; 8*pow
    AMR 0,TEMP_DIFF       ; 10*pow
    STR 0,PRINT_TMP       ; store new pow
    LDR 2,PRINT_TMP       ; R2 = new pow
    JMA PW_POW_LOOP

PW_PRINT:
PW_PRINT_LOOP:
    STR 2,PRINT_TMP       ; save pow
    STR 1,READBUF         ; save R1
    LDR 0,READBUF         ; R0 = R1
    LDR 2,PRINT_TMP       ; R2 = pow
    DVD 0,2               ; R0=digit, R2=remainder
    STR 0,TEMP_DIFF       ; save digit
    STR 2,READBUF         ; save remainder
    LDR 1,READBUF         ; R1 = remainder
    ; pow = pow / 10
    LDR 2,PRINT_TMP       ; reload pow
    LDR 0,ZERO
    AIR 0,10
    DVD 2,0               ; R2 = pow / 10
    ; print the saved digit
    LDR 0,TEMP_DIFF
    STR 0,PRINT_TMP
    LDR 3,ASCII_0
    AMR 3,PRINT_TMP
    OUT 3,1
    ; Continue if pow != 0
    SMR 2,ZERO
    JNE 2,PW_PRINT_LOOP
    HLT

HALT_NOW:
    HLT

; --- Data ---
LOC 239
ASCII_0:   DATA 48
ASCII_MINUS: DATA 45
TEMP_DIFF:  DATA 0
MIN_DIFF:   DATA 0
WINNER:     DATA 0
CNT:        DATA 0
CNT_INIT:   DATA 0
TARGET:     DATA 0
READBUF:    DATA 0
PRINT_TMP:  DATA 0
ZERO:       DATA 0
READVAL:    DATA 0
LINK_SAVE:  DATA 0

; End
