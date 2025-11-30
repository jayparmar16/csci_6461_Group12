; Program 2: Paragraph search using TRAP services
; - Reads a paragraph (from source.txt) into memory
; - Prints the paragraph
; - Prompts the user for a word (single token)
; - Searches the paragraph; if found, prints: "<word> <sentence#> <word#>"
;   else prints: "NOTFOUND"
;
                LOC 6
START:
                LDA 0,64             ; R0 = &PAR_BUF (decimal 64 == octal 0100)
                LDR 1,241            ; R1 = PAR_CAP (1024)
                TRAP 4               ; service 4: read file -> memory[R0..], returns R1=length
                STR 1,242            ; save paragraph length at PAR_LEN_SAVED (000362 oct)

                ; Print the paragraph we just loaded
                ; (R0 still = &PAR, R1 = paragraph length)
                TRAP 1               ; service 1: print memory[R0..R0+R1)

                ; Print prompt "\n?"
                LDA 0,245            ; R0 = &PROMPT (000365 oct)
                LDR 1,247            ; R1 = PROMPT_LEN (000367 oct) = 2
                TRAP 1

                ; Read a single word into WORD_BUF (delimited by newline). R1 gets word length
                LDA 0,50            ; R0 = &WORD_BUF (moved to safe low address)
                TRAP 2               ; service 2: read word -> WORD_BUF, R1 = word length
                STR 1,49            ; save word length (moved to safe low address)

                ; Prepare search arguments and run search
                LDA 0,64             ; R0 = &PAR_BUF (paragraph start; decimal 64 == octal 0100)
                LDR 1,242            ; R1 = paragraph length (saved earlier)
                LDA 2,50            ; R2 = &WORD_BUF (word start)
                LDR 3,49            ; R3 = word length (from 49)
                TRAP 3               ; service 3: search and print result (also R0=sent# R1=word# on success)

                HLT                  ; done

; Data region: keep within 8-bit addressable window
; Note: Assembler interprets numbers as decimal; place data at decimal 240
                LOC 240
PAR:            DATA 0               ; paragraph buffer start (contents filled by TRAP 4)
PAR_CAP:        DATA 1024            ; capacity: up to 1024 chars
PAR_LEN_SAVED:  DATA 0               ; slot to save paragraph length
WORD_BUF:       DATA 0               ; word buffer start (unused placeholder)
WORD_LEN:       DATA 0               ; word length slot (unused placeholder)
PROMPT:         DATA 10              ; '\n'
                DATA 63              ; '?'
PROMPT_LEN:     DATA 2               ; length of PROMPT
