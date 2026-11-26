# 0005 — Assistente per scrivere le regole

## Contesto

Le regole di validazione (EVL) e di derivazione (ETL) si modificano dall'editor, ma chi insegna
non conosce questi linguaggi. Serve un modo di descrivere una regola a parole e ottenerla scritta.

## Decisioni

**Un modello linguistico locale.** La traduzione la fa un modello per il codice servito da Ollama
(`qwen2.5-coder:14b`), sullo stesso server della generazione musicale. Niente chiavi e nessun
dato che esce dalla macchina; il nome del modello è configurazione.

**Il contesto si ricava, non si scrive.** La richiesta al modello contiene le classi dei
metamodelli sotto i nomi che hanno nello script, le operazioni dichiarate, i metodi degli
strumenti e alcune regole dello script come esempio. Tutto viene dai metamodelli, dai descrittori
JSON e dagli script: cambiando un metamodello l'assistente si aggiorna da solo.

**La proposta passa dai controlli del salvataggio.** La regola è aggiunta in fondo allo script e
verificata con il parser e con la prova sui modelli. Gli errori tornano al modello per un nuovo
tentativo. Due controlli in più, che il motore non fa: nomi di regola già usati, e regole di
trasformazione che creerebbero elementi in un modello che lo script deve solo leggere.

**L'assistente non salva.** Restituisce una proposta; inserirla nel testo e salvarla sono due
gesti di chi usa l'editor.

**Si mostra cosa farebbe la regola.** I controlli dicono che la regola gira, non che significhi
ciò che è stato chiesto. Per le regole di validazione l'anteprima elenca cosa segnalerebbero sui
modelli del workspace: una regola che segnala tutto o niente si riconosce subito.

## Misure

Sulle fixture di Giulia, con il modello indicato: 8 richieste di validazione su 8 hanno superato
i controlli al primo tentativo (2–11 s); 4 di derivazione su 4, due delle quali al terzo
tentativo (fino a 30 s). A leggerle, 3 delle 12 non facevano ciò che era stato chiesto (un
controllo affidato allo strumento sbagliato, una condizione rovesciata, un confronto senza senso).

## Conseguenze

- Una proposta va sempre letta: l'interfaccia lo dice e non offre il salvataggio diretto.
- Per le regole di derivazione non c'è un'anteprima dell'effetto: si vede rifacendo la derivazione.
- Serve scaricare il modello (circa 9 GB). Senza, l'editor funziona come prima e il riquadro dice
  cosa manca.
