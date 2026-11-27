# ADR 0007 — Dimensioni di qualità definite nel modello di qualità

Stato: accettato (novembre 2026)

## Contesto

Gli obiettivi di qualità di un esercizio erano un enum di quattro valori nel metamodello `exercise`
(complessità ritmica, melodica, densità, lunghezza). Aggiungerne uno voleva dire cambiare il
metamodello; quali restassero bassi quando la docente non li cita, e quale abilità dell'allievo li
limitasse, era scritto negli script. E comunque non avevano effetto: nessuna metrica era
calcolata, il generatore non li riceveva e i controlli non li guardavano.

## Decisioni

**Le dimensioni sono dati, come i tipi di bisogno** (ADR 0006). Il modello di qualità è il loro
catalogo: una `QualityDimension` dichiara nome, etichetta, la metrica con cui si misura e, per ogni
livello, l'intervallo della metrica e la frase da dire al generatore. Nell'intento un `QualityGoal`
punta a una dimensione e ne fissa il livello; l'intento indica il suo modello di qualità.

**Ciò che era cablato passa nel modello.** `implicit` e `defaultLevel` dicono quali dimensioni
valgono anche quando la docente non le cita (prima: una lista in `derive.etl`); `skillLimited` e
`skillArea` dicono quale abilità fa scattare l'avviso su un obiettivo alto (prima: un'operazione
in `intent.evl`). Negli script non resta il nome di nessuna dimensione.

**Le metriche sono il vocabolario fisso.** Una metrica è una misura tra 0 e 1 calcolata
sull'esercizio (`Metrics`): quota di note brevi, varietà delle durate, ampiezza media dei salti,
estensione usata, note per pulsazione. Una dimensione ne nomina una; `quality.evl` rifiuta un nome
sconosciuto. Una dimensione nuova su una metrica esistente è una modifica a un modello; una
metrica nuova è codice.

**Gli obiettivi diventano efficaci, ma non bloccanti.** La frase del livello entra nella richiesta
al modello musicale e ogni obiettivo è misurato sull'esercizio, con il valore e l'intervallo
atteso. Un obiettivo mancato è un'osservazione, non rende l'esercizio non valido: già solo con i
vincoli circa un tentativo su cinque è valido (ADR 0002). Tra più tentativi, a parità di vincoli
rispettati si tiene quello più vicino agli obiettivi.

**Il modello di qualità è quello dell'intento.** La derivazione lo raggiunge dal riferimento
dell'intento, come raggiunge il catalogo dei bisogni dal profilo: non si sceglie più a parte.

## Conseguenze

- Aggiungere una dimensione non richiede né ricompilare né toccare gli script: la scheda
  dell'intento la propone, la derivazione la porta nella specifica e la risolve sulla sua banda,
  il generatore ne riceve la frase e il controllo la misura.
- **Le soglie non sono calibrate.** Metriche e intervalli sono una prima proposta, semplici da
  leggere su una partitura; quanto seguano la difficoltà percepita da chi insegna va verificato.
- La frase per il generatore è testo libero in inglese: il modello di qualità può chiedere al
  modello musicale ciò che la metrica non misura. Sta a chi lo scrive tenerle coerenti.
- Un'abilità collegata usa due attributi (`skillLimited`, `skillArea`) perché un enum non
  impostato non si distingue dal suo primo valore.
- I modelli scritti con l'enum (`QualityAttribute`, `MetricBinding`) non si caricano più.
  `Level` è passato da `exercise` a `quality`; `LENGTH` non è stata riproposta, perché duplicava
  il numero di battute.

## Alternative scartate

- **Aggiungere valori all'enum.** Ogni dimensione resterebbe una modifica al metamodello.
- **Metriche scritte come espressioni nel modello.** Più flessibili, ma un modello di qualità
  diventerebbe codice eseguito sul server, e le misure non sarebbero più verificabili una per una.
- **Obiettivi bloccanti.** Con l'affidabilità attuale del generatore quasi nessun esercizio
  sarebbe valido, e le soglie non sono ancora abbastanza fondate da giustificarlo.
