# ADR 0002 — Generazione dell'esercizio con un modello musicale locale

Stato: accettato (novembre 2026)

## Contesto

Fino a M6 l'esercizio era un brano di prova fisso. Serve un generatore vero che scriva l'esercizio
a partire dalla specifica derivata, restando dietro l'interfaccia `ExerciseGenerator` e lasciando a
`ExerciseValidator` il giudizio sul risultato.

## Decisioni

**Ollama come servizio di inferenza.** Il backend parla con un server Ollama via HTTP
(`/api/generate`, `/api/tags`). Il modello gira sulla macchina di chi usa lo studio; nome del
modello, indirizzo, tempo massimo e temperatura sono configurazione (`studio.ollama.*`).

**ChatMusician come modello.** È un LLaMA2 adattato alla notazione ABC. Non è nella libreria di
Ollama: si usa la conversione GGUF `mradermacher/ChatMusician-GGUF`, quantizzazione Q4_K_M (4,1 GB),
della variante addestrata a seguire istruzioni.

**Richiesta nella forma di addestramento.** La richiesta usa il compito «testo → partitura» del
modello: metro, tonalità, lunghezza di base, tempo e una descrizione che riporta estensione,
pattern richiesti ed esclusi. L'intestazione ABC è scritta dal backend a partire dalla specifica e
il modello continua con le sole note: tonalità, metro e tempo sono quindi sempre quelli richiesti.

**Riordino della risposta, senza toccare le note.** `AbcExtractor` toglie sigle di accordi,
dinamiche, abbellimenti e segni di ritornello, scarta un'eventuale battuta in levare e ferma il
brano al numero di battute richiesto. Le note che restano non vengono mai modificate.

**Genera, controlla, riprova.** Ogni esercizio è controllato da `BasicExerciseValidator`; se non è
valido se ne chiede un altro, fino a `studio.generation.max-attempts` (3). Si restituisce il primo
valido, altrimenti quello con meno controlli obbligatori falliti, insieme a tutti i tentativi.

**Nessun ripiego silenzioso.** Se Ollama non risponde o il modello manca, la richiesta fallisce con
503 e un messaggio che dice cosa fare. Il generatore di prova resta disponibile e predefinito, così
test e CI non richiedono il modello.

## Misure (specifica derivata di Giulia, Apple M4 Max)

- Un tentativo richiede 2–4 secondi.
- Un singolo tentativo supera tutti i controlli obbligatori in circa un caso su cinque.
- Con 5 tentativi: 20 generazioni valide su 30 senza riscontro, 16 su 30 rimandando al modello
  l'elenco dei controlli falliti. Il riscontro non aiuta, quindi è disattivato
  (`studio.ollama.feedback=false`); ogni nuovo tentativo cambia solo il seme.
- I controlli falliti più spesso: pattern richiesto usato troppo poco, note fuori estensione,
  pattern escluso presente.
- Cambiare la formulazione della descrizione non ha spostato il risultato in modo apprezzabile.

## Conseguenze e limiti

- Il modello segue la specifica in modo debole: tonalità, metro e battute sono garantiti dal
  backend, il resto dipende dai tentativi. L'interfaccia mostra sempre il rapporto dei controlli e
  i tentativi scartati.
- Gli obiettivi di qualità entrano nella richiesta con la frase del loro livello e sono misurati
  sull'esercizio, senza renderlo non valido: vedi l'[ADR 0007](0007-quality-dimensions-as-data.md).
- Il lettore ABC legge terzine e gruppi comuni e ignora gli abbellimenti; accordi e cambi di
  tonalità o metro in mezzo al brano fanno scartare il tentativo.
- Strade per migliorare, non intraprese: più tentativi, un modello generico più ubbidiente,
  un risolutore di vincoli per lo scheletro ritmico con il modello a completare le altezze.
