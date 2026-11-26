# Changelog

Una nota per milestone. Le modifiche ai metamodelli vanno segnalate qui.

## Generazione con Ollama

- Nuovo generatore `ollama`: chiede l'esercizio in ABC a ChatMusician tramite un server Ollama
  locale. Il generatore di prova resta e rimane il predefinito.
- `POST /api/generations` accetta `generator`; la risposta porta anche l'elenco dei tentativi.
  Nuovo `GET /api/generations/generators` con la disponibilità di ciascun generatore.
- Ciclo genera, controlla, riprova, fino a tre tentativi; si restituisce il primo esercizio valido
  o il migliore tra quelli scartati.
- La risposta del modello viene riordinata (accordi, dinamiche, ritornelli, battuta in levare,
  numero di battute) senza modificare le note.
- Il lettore ABC legge terzine e gruppi comuni e ignora gli abbellimenti. L'unità di durata passa
  a 1/192 di semibreve.
- Vista «Esercizio»: scelta del generatore con stato di disponibilità, modello e tempi,
  tentativi scartati consultabili.
- Ascolto dell'esercizio: pulsanti «Ascolta», «Da capo» e scelta della velocità, con la nota che
  suona evidenziata sul pentagramma. Lo strumento è quello della specifica. I campioni audio si
  scaricano da `paulrosen.github.io` al primo ascolto, quindi serve la connessione a Internet.

Scostamenti da quanto concordato:

- Il repository GGUF previsto conteneva solo la quantizzazione Q2: si usa
  `mradermacher/ChatMusician-GGUF` Q4_K_M, che è la variante addestrata a seguire istruzioni.
- Il riscontro al modello sui controlli falliti è implementato ma disattivato: nelle misure non
  aumentava gli esercizi validi (16 su 30 con, 20 su 30 senza). Si riattiva con
  `studio.ollama.feedback=true`.

## Rifiniture dopo M6

- Riferimenti paralleli tra gli stessi due elementi disegnati su corsie separate, ognuno con la
  propria etichetta.
- Un collegamento trascinato si può rilasciare in qualunque punto dell'oggetto di destinazione.
- Dopo la chiusura di una finestra di dialogo il fuoco torna dov'era, così i comandi da tastiera
  del diagramma continuano a funzionare.
- Il diagramma adattato al riquadro non scende sotto una dimensione leggibile: se è più grande
  si scorre.
- Il tipo di un modello (intento o specifica derivata) si può cambiare dalla barra del modello,
  utile dopo un'importazione.
- `weaving.evl`: una traccia senza collegamenti riceve l'avviso `TraceWithoutLinks` al posto
  dell'errore di completezza, che resta per le tracce con almeno un link; nuovo avviso
  `DerivedSpecIsTheIntent`.
- Rapporto dell'esercizio: nuova osservazione non bloccante `barLength` sulle battute la cui
  durata non corrisponde al metro. Sull'ABC di esempio segnala le battute 2, 4, 6 e 7.
- nginx accetta intestazioni di richiesta fino a 64 KB, come il backend.
- Test end-to-end esteso a connessioni trascinate, copia e incolla, eliminazione con conferma,
  annulla e ripeti.

## M6 — Export e generazione di esempio

- Dal diagramma: export del modello in XMI e JSON e dell'immagine in PNG e SVG.
- Backend: interfacce `ExerciseGenerator`, `ExerciseValidator`, `MetricCalculator` e
  `POST /api/generations`. `MockExerciseGenerator` restituisce l'ABC di esempio;
  `BasicExerciseValidator` controlla estensione, occorrenze dei pattern richiesti ed esclusi e
  numero di battute. Le metriche `rhythmScore` e `melodicScore` non sono implementate.
- Vista «Esercizio» della specifica derivata: partitura con abcjs, ingrandita secondo il
  `RenderConstraint`, e rapporto dei controlli.

Note:

- L'ABC di esempio del brief ha quattro battute (2, 4, 6, 7) che durano 3/4 invece di 4/4. È
  rimasto identico; il validatore conta le battute e non ne controlla la durata.
- Ogni controllo del rapporto ha un campo `blocking`: i vincoli SOFT e i pattern non ritmici non
  rendono l'esercizio non valido.

## M5 — Viste low-code

- Selettore «Diagramma / Scheda» per modello; le due viste leggono e scrivono lo stesso modello.
- Scheda dell'allievo, costruttore di intento, tabella del modello di qualità con avviso su bande
  sovrapposte o che non coprono l'intervallo da 0 a 1.
- Editor di pattern ritmico con validazione mentre si scrive e anteprima in notazione. Il parser
  TypeScript (`frontend/src/domain/rhythm.ts`) accetta la stessa grammatica di quello Java.
- Test end-to-end Playwright: profilo dalla scheda, derivazione, colori di provenienza,
  confronto con le sorgenti, export, esercizio generato.

## M4 — Derivazione e provenienza

- `derive.etl` con le dieci regole del brief; ogni elemento o attributo della specifica derivata
  ha un `Link` nel modello di weaving con regola e motivazione.
- `POST /api/derivations` produce `{intento}.derived` e `{intento}.weaving`; una nuova derivazione
  li sovrascrive. Se profilo o intento hanno errori risponde 422.
- `weaving.evl` con `DerivedElementTraced`.
- Vista di derivazione con scelta dei tre modelli, controlli sull'intento e correzioni da applicare
  o lasciare; specifica derivata colorata per provenienza (blu docente, verde profilo o regole);
  pannello che spiega l'elemento selezionato e apre le sorgenti accanto; log di derivazione;
  schema del processo.

Scostamenti dal brief:

- `TempoLimit` limita sempre il tempo a quello comodo e lo registra come `ConflictFix`; senza
  tempo richiesto usa il tempo comodo.
- `CopyIntent` produce un `Refinement` per attributo, e copia anche `requestedBpm`.
- Regole in più, con nome `CopyTeacherConstraints`: copiano i vincoli di esclusione, estensione e
  resa scritti dalla docente, che altrimenti andrebbero persi.
- `ExcludeNotIntroduced` non esclude un pattern che la docente richiede esplicitamente: resta
  l'avviso `PatternNotIntroduced` sull'intento.
- `DerivedElementTraced` è un solo vincolo sulla radice del weaving, che elenca gli elementi non
  tracciati.
- Nello schema del processo le fasi 2–5 sono segnaposto senza nome.

## M3 — Riferimenti tra modelli, validazione, correzioni

- Validazione strutturale con il `Diagnostician` di EMF (molteplicità, valori, riferimenti non
  risolti) e validazione EVL, restituite nello stesso formato di diagnostica.
- Regole in `backend/src/main/resources/epsilon/`: `profile.evl`, `intent.evl`, `quality.evl`.
  Quale script vale per quale modello, e quali altri modelli gli servono, è scritto in
  `validations.json`: il codice Java non conosce il dominio.
- Correzioni EVL senza stato sul server: l'id della diagnostica è deterministico
  (`modello#vincolo@elemento`), la correzione riesegue la validazione, si applica e salva.
- Parser dei pattern ritmici in Java (`rhythm/`), usato da `PatternSyntax`. Token ammessi: `1`,
  `2`, `4`, `8`, `16`, con punto finale opzionale, separati da spazi.
- Nell'editor: pannello Problemi con selezione dell'elemento e pulsanti di correzione, contatori
  sui nodi, scelta dei riferimenti verso altri modelli, nodo segnaposto per l'elemento esterno.

Scostamenti dal brief:

- `TempoAboveComfort` è violato quando `requestedBpm` supera il tempo comodo **e** `maxBpm` non è
  già impostato a un valore entro il tempo comodo. Con la sola condizione del brief l'avviso
  resterebbe anche dopo la correzione, che imposta `maxBpm` e non tocca `requestedBpm`.
- Le regole dell'intento che dipendono dal learner non scattano se il riferimento manca o non si
  risolve: in quel caso resta l'errore strutturale.
- `ContainsExcludesConflict` e `PatternNotIntroduced` confrontano le espressioni a meno degli spazi.

## M2 — Editor grafico generico

- Diagramma a oggetti in notazione UML per qualunque metamodello registrato: nodi `id : Classe`
  con gli attributi, contenimenti con rombo pieno, riferimenti con freccia aperta, riferimenti
  verso altri modelli tratteggiati.
- Palette delle classi concrete; il nuovo oggetto va alla radice, nell'unico contenitore possibile,
  oppure si sceglie dove.
- Pannello delle proprietà generato dal metamodello, con rinomina dell'identificativo.
- Disposizione automatica (ELK) alla prima apertura, poi posizioni manuali salvate a parte.
- Annulla e ripeti, copia e incolla, eliminazione con conferma se l'elemento ha contenuto,
  salvataggio con controllo di versione.
- Zoom solo dai pulsanti; il diagramma resta adattato al riquadro finché non lo si sposta a mano.

## M1 — Metamodelli, modelli, persistenza

- Registro che carica i `.ecore` di `metamodels/` e li descrive in JSON al frontend.
- Conversione generica JSON ↔ EMF dinamico ↔ XMI, con riferimenti tra modelli.
- Persistenza su file: `.xmi`, `.meta.json`, `.layout.json` per modello.
- API REST del §5 per metamodelli e modelli, con import ed export XMI.
- Fixture dell'esempio Giulia in `fixtures/giulia/`.

Scostamento dal brief: nel JSON dei modelli `contents` e `references` associano sempre il nome
della feature a una lista, anche quando il limite superiore è 1.

## M0 — Struttura del progetto

- Backend Spring Boot (Maven, Java 21) e frontend React + TypeScript (Vite).
- `docker-compose.yml` con backend e frontend dietro nginx; `workspace/` montato come volume.
- CI su GitHub Actions: build e test di backend e frontend.
