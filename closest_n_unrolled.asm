; closest_n_unrolled.asm
; IMPORTANT: Reserved memory addresses 000000..000005 are used by the machine and must not
; be executed or written by user programs. We explicitly set the program origin to 000006
; to avoid these reserved locations. Do not define labels/data that bind to 0..5.
; Reads N (multi-digit, signed), then reads N candidates (multi-digit, signed), then reads 1 target (multi-digit, signed).
; Numbers are separated by any non-digit delimiter (e.g., space/newline/comma). Optional leading '-' is supported.
; Uses a compact parser that accumulates base-10 via shifts (x10 = x8 + x2). OUT still uses R3.
; Memory map (decimal):
; CAND0..CAND8 -> 129..137 (octal 0201..0207)
; TEMP_DIFF -> 140 (octal 0210)
; MIN_DIFF -> 141 (0211)
; WINNER   -> 142 (0212)
; CNT, CNT_INIT -> 143,144 etc

; Program origin set beyond reserved addresses (0..5)
LOC 6

; (data section placed later at LOC 129)

; === Unified multi-digit parser (R0=result, R1=signFlag, R2=seenDigit, R3=char/temp) ===
; MODE is kept in MIN_DIFF temporarily: 0=N, 1=CAND, 2=TARGET.
; To invoke: set MIN_DIFF to mode, zero R0..R2, then jump to RINT_LOOP.

; Invoke for N: MODE=0
    LDR 2,ZERO
    STR 2,MIN_DIFF       ; MODE=0 (N)
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
    LDR 3,ZERO
    JMA RINT_LOOP

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
    ; Dispatch by MODE (MIN_DIFF): 0->N_POST, 1->CAND_POST, 2->T_POST
    LDR 2,MIN_DIFF
    JZ 2,N_POST
    SIR 2,1
    JZ 2,CAND_POST
    ; else target
    JMA T_POST

N_POST:
    ; Cap N to 5 and store CNT/CNT_INIT, then go to candidate loop
    STR 0,PRINT_TMP       ; PRINT_TMP = N
    LDR 2,PRINT_TMP
    SIR 2,5                 ; R2 = N - 5
    JGE 2,N_CAP
    ; else store N as-is
    LDR 0,PRINT_TMP
    STR 0,CNT
    STR 0,CNT_INIT
    LDR 0,ZERO
    STR 0,IDX
    ; Next: read candidates
    ; Set MODE=1 (CAND)
    LDR 2,ZERO
    AIR 2,1
    STR 2,MIN_DIFF
    ; Reset parser state and loop
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
    JMA RINT_LOOP

N_CAP:
    LDR 0,ZERO
    AIR 0,5
    STR 0,CNT
    STR 0,CNT_INIT
    LDR 0,ZERO
    STR 0,IDX
    ; MODE=1 and restart parser for candidates
    LDR 2,ZERO
    AIR 2,1
    STR 2,MIN_DIFF
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
    JMA RINT_LOOP

CAND_POST:
    ; Move R0 parsed value into R3
    STR 0,PRINT_TMP
    LDR 3,PRINT_TMP
    ; Dispatch store based on IDX (0..4)
    LDR 1,IDX
    JZ 1,STORE_C0
    SIR 1,1
    JZ 1,STORE_C1
    SIR 1,1
    JZ 1,STORE_C2
    SIR 1,1
    JZ 1,STORE_C3
    SIR 1,1
    JZ 1,STORE_C4

STORE_C0:
    STR 3,CAND0
    JMA AFTER_STORE
STORE_C1:
    STR 3,CAND1
    JMA AFTER_STORE
STORE_C2:
    STR 3,CAND2
    JMA AFTER_STORE
STORE_C3:
    STR 3,CAND3
    JMA AFTER_STORE
STORE_C4:
    STR 3,CAND4
    JMA AFTER_STORE

AFTER_STORE:
    ; Increment IDX
    LDR 1,IDX
    AIR 1,1
    STR 1,IDX
    ; Decrement CNT and loop
    LDR 2,CNT
    SIR 2,1
    STR 2,CNT
    JNE 2,CAND_NEXT

READS_DONE:
; === Read Target using unified parser ===
    ; MODE=2 (TARGET)
    LDR 2,ZERO
    AIR 2,2
    STR 2,MIN_DIFF
    ; Reset parser state and jump to RINT_LOOP
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
    JMA RINT_LOOP

CAND_NEXT:
    ; Continue candidate loop using unified parser
    ; Reset parser state and set MODE=1
    LDR 2,ZERO
    AIR 2,1
    STR 2,MIN_DIFF
    LDR 0,ZERO
    LDR 1,ZERO
    LDR 2,ZERO
    JMA RINT_LOOP

T_POST:
    STR 0,TARGET

; If CNT_INIT == 0 then nothing to compare -> HLT
LDR 2,CNT_INIT
JZ 2,HALT_NOW

; Compare candidates in a compact loop (IDX 0..CNT_INIT-1)
    ; Initialize MIN_DIFF and WINNER with CAND0
    LDR 0,CAND0
    SMR 0,TARGET
    JGE 0,MINPOS0
    NOT 0
    AIR 0,1
MINPOS0:
    STR 0,MIN_DIFF
    LDR 3,CAND0
    STR 3,WINNER

    ; Set IDX = 1
    LDR 1,ZERO
    AIR 1,1
    STR 1,IDX

COMPARE_LOOP:
    ; If IDX == CNT_INIT -> we're done
    LDR 1,IDX
    SMR 1,CNT_INIT
    JZ 1,COMP_DONE

    ; Dispatch: load candidate (value) into R3 based on IDX (0..4),
    ; then copy to R0 via PRINT_TMP for diff computation.
    LDR 1,IDX
    JZ 1,LOAD_C0
    SIR 1,1
    JZ 1,LOAD_C1
    SIR 1,1
    JZ 1,LOAD_C2
    SIR 1,1
    JZ 1,LOAD_C3
    SIR 1,1
    JZ 1,LOAD_C4

LOAD_C0:
    LDR 3,CAND0
    STR 3,PRINT_TMP
    LDR 0,PRINT_TMP
    JMA DO_COMPARE
LOAD_C1:
    LDR 3,CAND1
    STR 3,PRINT_TMP
    LDR 0,PRINT_TMP
    JMA DO_COMPARE
LOAD_C2:
    LDR 3,CAND2
    STR 3,PRINT_TMP
    LDR 0,PRINT_TMP
    JMA DO_COMPARE
LOAD_C3:
    LDR 3,CAND3
    STR 3,PRINT_TMP
    LDR 0,PRINT_TMP
    JMA DO_COMPARE
LOAD_C4:
    LDR 3,CAND4
    STR 3,PRINT_TMP
    LDR 0,PRINT_TMP
    JMA DO_COMPARE

DO_COMPARE:
    ; R0 contains candidate value; compute absolute difference with TARGET
    SMR 0,TARGET
    JGE 0,DIFFPOS
    NOT 0
    AIR 0,1
DIFFPOS:
    STR 0,TEMP_DIFF
    ; If TEMP_DIFF < MIN_DIFF then update
    LDR 1,MIN_DIFF
    LDR 2,TEMP_DIFF
    SMR 2,MIN_DIFF
    ; (SMR 2,0,MIN_DIFF used as: R2 = R2 - MIN_DIFF)
    JGE 2,SKIP_UPDATE
    ; Update MIN_DIFF and WINNER (use PRINT_TMP which holds this candidate)
    LDR 2,TEMP_DIFF
    STR 2,MIN_DIFF
    LDR 3,PRINT_TMP
    STR 3,WINNER
    JMA CONT_COMPARE
SKIP_UPDATE:
    ; nothing to do
CONT_COMPARE:
    ; Increment IDX and loop
    LDR 1,IDX
    AIR 1,1
    STR 1,IDX
    JMA COMPARE_LOOP

COMP_DONE:
    ; Full multi-digit print in decimal
    ; R1 holds |WINNER|, R2=pow, R0 temp; uses READBUF/TEMP_DIFF/PRINT_TMP.
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
    ; R2 = 1
    LDR 2,ZERO
    AIR 2,1
PW_POW_LOOP:
    ; R0 = 10 * R2 (via 8+2)
    STR 2,READBUF
    LDR 0,READBUF
    SRC 0,1,1,1
    STR 0,TEMP_DIFF       ; TEMP_DIFF = 2*R2
    LDR 0,READBUF
    SRC 0,3,1,1             ; R0 = 8*R2
    AMR 0,TEMP_DIFF         ; R0 = 10*R2
    STR 0,PRINT_TMP
    ; if (10*R2) <= R1 then R2 = 10*R2 and repeat
    STR 1,READBUF         ; save R1
    LDR 0,READBUF         ; R0 = R1
    SMR 0,PRINT_TMP       ; R0 = R1 - 10*R2
    JGE 0,PW_GROW
    ; else proceed with current R2
    JMA PW_PRINT
PW_GROW:
    LDR 2,PRINT_TMP       ; R2 = 10*R2
    JMA PW_POW_LOOP

PW_PRINT:
    ; loop: quotient = R1 / R2; remainder = R1 % R2
PW_PRINT_LOOP:
    STR 2,PRINT_TMP       ; save pow
    STR 1,READBUF         ; save R1
    LDR 0,READBUF         ; R0 = R1
    LDR 2,PRINT_TMP       ; R2 = pow
    DVD 0,2               ; R0=quotient digit, R2=remainder
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
; End
; Place data in low memory region reserved for program data
; Move data above the code region to avoid overlap (code uses up to ~167 dec)
; Keep within 8-bit address space (<=255). 17 data words => start at 239 so last is 255.
LOC 239
; Candidate storage (initially zero)
CAND0: DATA 0
CAND1: DATA 0
CAND2: DATA 0
CAND3: DATA 0
CAND4: DATA 0

; ASCII constants
ASCII_0: DATA 48
ASCII_MINUS: DATA 45

; Working data (initialized to zero)
TEMP_DIFF: DATA 0
MIN_DIFF: DATA 0
WINNER: DATA 0
CNT: DATA 0
CNT_INIT: DATA 0
TARGET: DATA 0
IDX: DATA 0

READBUF: DATA 0
PRINT_TMP: DATA 0
ZERO: DATA 0

; End
