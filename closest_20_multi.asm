; closest_n_unrolled.asm
; Reads N (single digit ASCII), then reads up to 9 candidate chars (ASCII digits), then reads 1 target char.
; Uses fully unrolled read and compare logic to avoid index/register-format incompatibilities.
; Memory map (decimal):
; CAND0..CAND8 -> 129..137 (octal 0201..0207)
; TEMP_DIFF -> 140 (octal 0210)
; MIN_DIFF -> 141 (0211)
; WINNER   -> 142 (0212)
; CNT, CNT_INIT -> 143,144 etc

LOC 6

; (data section placed later at LOC 129)

; Read N (ASCII digit) into CNT
IN 0,0
SMR 0,0,ASCII_0        ; R0 = numeric N (mem-based ASCII '0')
STR 0,0,CNT     ; store CNT
STR 0,0,CNT_INIT

; --- Read N and candidates using a compact loop ---
    ; CNT/CNT_INIT already set earlier; initialize index (IDX) to zero
    LDR 0,0,ZERO
    STR 0,0,IDX

READ_LOOP:
    LDR 2,0,CNT
    JZ 2,0 READS_DONE
    ; Read a character (may be '-' then digit, or digit)
    IN 3,0
    STR 3,0,READBUF
    LDR 3,0,READBUF
    ; Check for '-'
    SMR 3,0,ASCII_MINUS
    JZ 3,0 READ_NEG
    ; Non-negative: convert ASCII digit to numeric
    LDR 3,0,READBUF
    SMR 3,0,ASCII_0
    JNE 3,0 DO_STORE
READ_NEG:
    ; Read next char for digit and convert to negative two's-complement
    IN 3,0
    STR 3,0,READBUF
    LDR 3,0,READBUF
    SMR 3,0,ASCII_0
    NOT 3
    AIR 3,1

DO_STORE:
    ; Dispatch store based on IDX (0..4)
    LDR 1,0,IDX
    JZ 1,0 STORE_C0
    SIR 1,1
    JZ 1,0 STORE_C1
    SIR 1,1
    JZ 1,0 STORE_C2
    SIR 1,1
    JZ 1,0 STORE_C3
    SIR 1,1
    JZ 1,0 STORE_C4

STORE_C0:
    STR 3,0,CAND0
    LDR 1,0,ZERO
    JZ 1,0 AFTER_STORE
STORE_C1:
    STR 3,0,CAND1
    LDR 1,0,ZERO
    JZ 1,0 AFTER_STORE
STORE_C2:
    STR 3,0,CAND2
    LDR 1,0,ZERO
    JZ 1,0 AFTER_STORE
STORE_C3:
    STR 3,0,CAND3
    LDR 1,0,ZERO
    JZ 1,0 AFTER_STORE
STORE_C4:
    STR 3,0,CAND4
    LDR 1,0,ZERO
    JZ 1,0 AFTER_STORE

AFTER_STORE:
    ; Increment IDX
    LDR 1,0,IDX
    AIR 1,1
    STR 1,0,IDX
    ; Decrement CNT and loop
    LDR 2,0,CNT
    SIR 2,1
    STR 2,0,CNT
    JNE 2,0 READ_LOOP

READS_DONE:

; Read Target
; Read Target (allow optional '-' for negative single-digit)
IN 3,0
STR 3,0,READBUF
LDR 2,0,READBUF
SMR 2,0,ASCII_MINUS
JZ 2,0 TARGET_NEG
LDR 2,0,READBUF
SMR 2,0,ASCII_0
STR 2,0,TARGET
JNE 2,0 TARGET_CONT
TARGET_NEG:
IN 3,0
STR 3,0,READBUF
LDR 2,0,READBUF
 SMR 2,0,ASCII_0
NOT 2
AIR 2,1
STR 2,0,TARGET
TARGET_CONT:

; If CNT_INIT == 0 then nothing to compare -> HLT
LDR 2,0,CNT_INIT
JZ 2,0 HALT_NOW

; Compare candidates in a compact loop (IDX 0..CNT_INIT-1)
    ; Initialize MIN_DIFF and WINNER with CAND0
    LDR 0,0,CAND0
    SMR 0,0,TARGET
    JGE 0,0 MINPOS0
    NOT 0
    AIR 0,1
MINPOS0:
    STR 0,0,MIN_DIFF
    LDR 3,0,CAND0
    STR 3,0,WINNER

    ; Set IDX = 1
    LDR 4,0,ZERO
    AIR 4,1
    STR 4,0,IDX

COMPARE_LOOP:
    ; If IDX == CNT_INIT -> we're done
    LDR 1,0,IDX
    SMR 1,0,CNT_INIT
    JZ 1,0 COMP_DONE

    ; Dispatch: load candidate (value) into R3 based on IDX (0..4),
    ; then copy to R0 via PRINT_TMP for diff computation.
    LDR 1,0,IDX
    JZ 1,0 LOAD_C0
    SIR 1,1
    JZ 1,0 LOAD_C1
    SIR 1,1
    JZ 1,0 LOAD_C2
    SIR 1,1
    JZ 1,0 LOAD_C3
    SIR 1,1
    JZ 1,0 LOAD_C4

LOAD_C0:
    LDR 3,0,CAND0
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 DO_COMPARE
LOAD_C1:
    LDR 3,0,CAND1
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 DO_COMPARE
LOAD_C2:
    LDR 3,0,CAND2
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 DO_COMPARE
LOAD_C3:
    LDR 3,0,CAND3
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 DO_COMPARE
LOAD_C4:
    LDR 3,0,CAND4
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 DO_COMPARE

DO_COMPARE:
    ; R0 contains candidate value; compute absolute difference with TARGET
    SMR 0,0,TARGET
    JGE 0,0 DIFFPOS
    NOT 0
    AIR 0,1
DIFFPOS:
    STR 0,0,TEMP_DIFF
    ; If TEMP_DIFF < MIN_DIFF then update
    LDR 1,0,MIN_DIFF
    LDR 2,0,TEMP_DIFF
    SMR 2,0,MIN_DIFF
    ; (SMR 2,0,MIN_DIFF used as: R2 = R2 - MIN_DIFF)
    JGE 2,0 SKIP_UPDATE
    ; Update MIN_DIFF and WINNER (use PRINT_TMP which holds this candidate)
    LDR 2,0,TEMP_DIFF
    STR 2,0,MIN_DIFF
    LDR 3,0,PRINT_TMP
    STR 3,0,WINNER
    LDR 1,0,ZERO
    JZ 1,0 CONT_COMPARE
SKIP_UPDATE:
    ; nothing to do
CONT_COMPARE:
    ; Increment IDX and loop
    LDR 1,0,IDX
    AIR 1,1
    STR 1,0,IDX
    LDR 1,0,ZERO
    JZ 1,0 COMPARE_LOOP

COMP_DONE:
    ; As a safety net, recompute MIN_DIFF and WINNER from candidates
    ; using a compact loop that carries candidate via PRINT_TMP.
RECOMP_INIT:
    LDR 0,0,CAND0
    SMR 0,0,TARGET
    JGE 0,0 RECOMP_POS0
    NOT 0
    AIR 0,1
RECOMP_POS0:
    STR 0,0,MIN_DIFF
    LDR 3,0,CAND0
    STR 3,0,WINNER
    ; IDX = 1
    LDR 1,0,ZERO
    AIR 1,1
    STR 1,0,IDX

RECOMP_LOOP:
    ; if IDX == CNT_INIT -> done
    LDR 1,0,IDX
    SMR 1,0,CNT_INIT
    JZ 1,0 RECOMP_DONE
    ; dispatch load into R3 and copy to R0 via PRINT_TMP
    LDR 1,0,IDX
    JZ 1,0 RLC0
    SIR 1,1
    JZ 1,0 RLC1
    SIR 1,1
    JZ 1,0 RLC2
    SIR 1,1
    JZ 1,0 RLC3
    SIR 1,1
    JZ 1,0 RLC4
RLC0:
    LDR 3,0,CAND0
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 RDO_CMP
RLC1:
    LDR 3,0,CAND1
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 RDO_CMP
RLC2:
    LDR 3,0,CAND2
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 RDO_CMP
RLC3:
    LDR 3,0,CAND3
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 RDO_CMP
RLC4:
    LDR 3,0,CAND4
    STR 3,0,PRINT_TMP
    LDR 0,0,PRINT_TMP
    LDR 1,0,ZERO
    JZ 1,0 RDO_CMP

RDO_CMP:
    SMR 0,0,TARGET
    JGE 0,0 RDIFF_POS
    NOT 0
    AIR 0,1
RDIFF_POS:
    STR 0,0,TEMP_DIFF
    LDR 2,0,MIN_DIFF
    LDR 0,0,TEMP_DIFF
    SMR 0,0,MIN_DIFF
    JGE 0,0 RNEXT
    ; update
    LDR 2,0,TEMP_DIFF
    STR 2,0,MIN_DIFF
    LDR 3,0,PRINT_TMP
    STR 3,0,WINNER
RNEXT:
    LDR 1,0,IDX
    AIR 1,1
    STR 1,0,IDX
    LDR 1,0,ZERO
    JZ 1,0 RECOMP_LOOP

RECOMP_DONE:
    ; Print WINNER as a human-readable signed single-digit using R3 for OUT
    ; R1 will hold the numeric value; PRINT_TMP is temporary memory to help AMR
    LDR 1,0,WINNER      ; R1 = winner (signed 16-bit)
    LDR 0,0,WINNER
    SMR 0,0,ZERO        ; set flags for sign check
    JGE 0,0 PRINT_POS
    ; Negative: print '-' then digit
    LDR 3,0,ASCII_MINUS
    OUT 3,1
    ; Convert two's-complement negative to positive digit: NOT + 1
    LDR 1,0,WINNER
    NOT 1
    AIR 1,1
    STR 1,0,PRINT_TMP
    LDR 3,0,ASCII_0     ; ASCII '0'
    AMR 3,PRINT_TMP     ; add digit value to ASCII '0'
    OUT 3,1
    HLT

PRINT_POS:
    ; Positive (or zero): print digit
    LDR 1,0,WINNER
    STR 1,0,PRINT_TMP
    LDR 3,0,ASCII_0     ; ASCII '0'
    AMR 3,PRINT_TMP
    OUT 3,1
    HLT

HALT_NOW:
    HLT

; --- Data ---
; End
; Place data in low memory region reserved for program data
LOC 129
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
