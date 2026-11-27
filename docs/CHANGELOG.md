# Changelog

Una nota per milestone. Le modifiche ai metamodelli vanno segnalate qui.

## Dimensioni di qualità definite nel modello di qualità

**Modifica ai metamodelli** (scelte in `docs/adr/0007-quality-dimensions-as-data.md`):

- `quality`: `QualityModel` contiene `QualityDimension` (nome, etichetta, descrizione, `metric`,
  `skillLimited` + `skillArea`, `implicit` + `defaultLevel`) con le sue `Band`, che hanno anche
  `hint`, la frase per il generatore. `MetricBinding` non esiste più. L'enum `Level` è ora qui.
- `exercise`: tolti gli enum `QualityAttribute` e `Level`. `QualityGoal` ha `dimension [1]` verso
  `quality.QualityDimension`; `ExerciseSpec` ha `qualityModel`. Intenti e modelli di qualità
  scritti con l'enum vanno riscritti.
- `profile`, `needs`, `weaving`: nessuna modifica.
- Diagrammi: tutti e cinque i metamodelli sono ora generati da `metamodels/render_diagrams.py`;
  nuovi `levels-needs` e `levels-quality` (da `render_levels.py`) con i livelli classe → tipo →
  istanza.

Il resto:

- Derivazione: il modello di qualità è quello indicato dall'intento (il campo `qualityModelId`
  della richiesta non serve più). `ApplyImplicitGoals` al posto di `FocusOtherDimensionsLow`;
  nessun nome di dimensione negli script.
- Validazione: `quality.evl` controlla nomi unici, una banda per livello, metrica conosciuta;
  `intent.evl` controlla che gli obiettivi usino dimensioni del modello di qualità dell'intento.
- Metriche calcolate sull'esercizio: `shortNoteShare`, `rhythmVariety`, `meanLeap`, `rangeUse`,
  `noteDensity`. `GET /api/generations/metrics` le elenca.
- Generazione: la frase del livello di ogni obiettivo entra nella richiesta; ogni obiettivo è
  misurato in un controllo `qualityGoal`, non bloccante. A parità di vincoli rispettati si tiene
  il tentativo più vicino agli obiettivi.
- Studio: scheda del modello di qualità per dimensioni; nell'intento si sceglie il modello di
  qualità e gli obiettivi tra le sue dimensioni.
- Esperimento: gli obiettivi di qualità sono tra i requisiti, come preferenze. I risultati della
  prima esecuzione sono stati tolti e i cinque casi rigenerati con il nuovo modello.

## Cartelle nel workspace

- I modelli si raccolgono in cartelle, anche annidate (`Classe 3/Giulia`). La cartella è un dato di
  `id.meta.json`, non una sottocartella sul disco: identificativi, riferimenti tra modelli e
  cronologia non cambiano quando un modello viene spostato.
- Pannello «Modelli»: cartelle che si aprono e chiudono (lo stato resta nel browser), con i modelli
  raggruppati per tipo dentro ciascuna; creazione e importazione direttamente in una cartella.
- Campo «Cartella» nella barra di ogni modello, con le cartelle in uso come suggerimenti. Spostare
  non crea una versione.
- I modelli prodotti da una derivazione vanno nella cartella dell'intento; rifacendo la derivazione
  restano dove sono stati messi.
- API: `PUT /api/models/{id}/folder`; `folder` in creazione e importazione.
- `experiment/run.mjs` mette ogni caso in `Esperimento/<ID>` e i modelli comuni in `Condivisi`.

## Esperimento con gli esperti

- `experiment/`: cinque casi (profilo, intento, descrizione in inglese) e lo script `run.mjs` che
  li deriva, chiede l'esercizio al modello musicale e scrive specifica, traccia, esercizio, esito
  dei controlli e requisiti con identificativi stabili (`C3-R2`). Dettagli in
  `experiment/README.md`.
- Sito statico in inglese (`frontend/experiment.html`, `npm run build:experiment`): per ogni caso
  descrizione, requisiti, partitura con ascolto e file da scaricare; con `?view=full` anche i
  controlli automatici, i tentativi e la richiesta al modello. Non serve il backend.
- `.github/workflows/pages.yml` pubblica il sito su GitHub Pages.
- `ScorePlayer` accetta i testi dei comandi, per una pagina in un'altra lingua.

## Bisogni tipizzati da un catalogo

**Modifica ai metamodelli** (scelte in `docs/adr/0006-multi-level-needs.md`):

- Nuovo metamodello `needs` (`http://music/needs`): `NeedCatalog`, `NeedType`, `ParameterDef`,
  `Effect` con `ConstraintEffect` e `SpecEffect`, `Value` con `Binding`; enum `ParameterKind` e
  `Combine`.
- `profile`: tolte `LowVision` e `HandSpanLimit`. `Need` è concreta, con `type [1]` verso
  `needs.NeedType` e gli `Slot` (`parameter [1]`, `value`); `Learner` ha `catalog` verso
  `needs.NeedCatalog`. I profili scritti con le vecchie classi vanno riscritti.
- `weaving`: `DerivationWeaving.needCatalog` e nuova origine `NEED_CATALOG`.
- `exercise`: nessuna modifica.
- Diagrammi: `needs` nuovo e `profile` rigenerato da `metamodels/render_diagrams.py`. Il diagramma
  `weaving` è ancora quello originale e non mostra le due aggiunte.

Il resto:

- Derivazione: `ApplyNeeds` e `ApplyNeedLimits` al posto di `RenderForLowVision` e
  `SpanFromHandLimit`. Seguono bisogno → tipo → effetto senza nominare alcun bisogno; ogni link ha
  per sorgenti lo slot del profilo e l'effetto del catalogo.
- Validazione: nuovo `needs.evl` (il catalogo deve poter essere applicato a `exercise`);
  `profile.evl` controlla catalogo, tipo, parametri obbligatori e valori al posto di
  `HandSpanBounds`; `intent.evl` ha il nuovo avviso `NeedLimitExceeded`.
- Fixture: `default.needs.xmi` con sei tipi (ipovisione, apertura della mano, attenzione breve,
  velocità motoria ridotta, affollamento visivo, posizione fissa della mano); `giulia.profile.xmi`
  riscritto con gli stessi valori. Il catalogo va importato prima del profilo.
- Scheda dell'allievo: una sola sezione «Bisogni», costruita dal catalogo scelto.
- Generazione: più vincoli di estensione sono detti al modello come la loro intersezione; il testo
  della richiesta è nei metadati del risultato (`prompt`).

## Assistente per scrivere le regole

- Nell'editor di ogni script, «Descrivi la regola»: una descrizione in italiano diventa una regola
  EVL o ETL, scritta da un modello linguistico locale su Ollama (`qwen2.5-coder:14b`).
- La proposta passa gli stessi controlli del salvataggio; se fallisce, il modello è interrogato di
  nuovo con gli errori (fino a tre volte). In più: nomi già usati e regole di trasformazione che
  creerebbero elementi in un modello di sola lettura vengono rifiutati.
- L'anteprima mostra cosa segnalerebbe una regola di validazione sui modelli del workspace.
- L'assistente non salva: la regola si inserisce nel testo e si salva a mano.
- API: `GET /api/assistant`, `POST /api/assistant/rules`. Impostazioni `studio.assistant.*`.

## Regole di derivazione consultabili e modificabili

- La scheda «Regole» mostra anche `derive.etl`, descritto con il parser ETL: regole di
  trasformazione (sorgente, elementi creati, `guard`), blocchi `pre` e `post`, operazioni.
- Si modifica come gli script di validazione: sintassi, poi una derivazione di prova sugli intenti
  del workspace, senza salvare ciò che produce. Lo script modificato sta in `workspace/.rules/`.
- API: `GET`, `PUT`, `DELETE /api/derivations/script` e `POST /api/derivations/script/check`.
- Il pulsante in alto ora si chiama «Regole».

## Regole di validazione modificabili

- «Modifica» su ogni script nella scheda delle regole: il testo è controllato mentre si scrive
  (sintassi con il parser EVL, poi esecuzione di prova sui modelli del workspace) e si salva solo
  se passa entrambi i controlli.
- Gli script modificati stanno in `workspace/.rules/` e prendono il posto degli originali, che
  restano nel backend; «Ripristina le originali» torna a quelli.
- API: `POST /api/rules/{script}/check`, `PUT /api/rules/{script}`, `DELETE /api/rules/{script}`.
- I file `validations.json` e `derivation.json` non si modificano dall'editor.

## Regole di validazione consultabili

- `GET /api/rules`: gli script EVL di `validations.json` descritti con il parser di Epsilon
  (contesti, regole con gravità, `guard`, `check`, messaggio, correzioni, operazioni, sorgente).
  Uno script che non si analizza viene restituito con i suoi errori di sintassi.
- Scheda «Regole di validazione», in sola lettura, con ricerca; da ogni problema «Vedi la regola».

## Versioni dei modelli

- Ogni salvataggio lascia una versione in `workspace/<workspace>/.history/<id>/`, con data e
  origine (creazione, importazione, salvataggio, correzione rapida, derivazione, ripristino).
- API: elenco, lettura e ripristino di una versione; il ripristino aggiunge una versione.
- Pulsante «Versioni» in ogni modello: elenco, confronto con il modello aperto (elementi in più,
  in meno, valori diversi) e ripristino.
- I modelli già presenti, o modificati sul disco, entrano nella cronologia alla prima lettura.
- Si conservano le ultime 50 versioni per modello (`studio.history-size`).

## Limite fisico nel profilo: apertura della mano

**Modifica ai metamodelli** (richiesta in `docs/hand-span-limit.md`):

- `profile`: nuovo enum `Hand { LEFT, RIGHT }` e nuova classe `HandSpanLimit extends Need` con
  `hand [1]`, `handSpanCm` (facoltativo, informativo) e `maxStretchSemitones [1]`.
- `exercise`: nuova classe `SpanConstraint extends Constraint` con `hand [1]` (riusa
  `profile.Hand`), `maxSimultaneousSemitones` e `maxStretchInPosition`.
- `weaving`: nessuna modifica.
- I file `.ecore` sono stati aggiornati a mano in parallelo agli `.emf`, perché in questo ambiente
  non c'è Emfatic; i riferimenti tra file restano relativi (`profile.ecore#//Hand`). I diagrammi
  `profile` ed `exercise` in `metamodels/img/` sono rigenerati con le nuove classi da
  `metamodels/render_diagrams.py`, che legge gli `.ecore`; le copie PNG si ottengono con
  `metamodels/svg_to_png.mjs`. I diagrammi `quality` e `weaving` sono quelli originali.

Il resto:

- Fixture: `giulia.profile.xmi` ha un `HandSpanLimit` (mano destra, 16 cm, sesta maggiore);
  nuovo `giulia-leap.intent.xmi` con un salto d'ottava.
- Parser dei pattern intervallari (`+3 -2 +12`) in Java (`interval/`) e TypeScript
  (`frontend/src/domain/interval.ts`), con gli stessi casi di test.
- `profile.evl`: `HandSpanBounds` (errore). `intent.evl`: `LeapBeyondReach` (avviso, senza
  correzione), sul limite della mano destra.
- `derive.etl`: regola `SpanFromHandLimit`, con un link `Derivation` di origine PROFILE.
- Controlli dell'esercizio: `handSpan` (bloccante, sull'accordo più ampio) e `positionChanges`
  (informativo, conta i salti melodici oltre l'apertura). Il lettore ABC ora legge gli accordi.
- Scheda dell'allievo: sezione «Limiti fisici» con mano, apertura scelta per nome dell'intervallo e
  misura in centimetri. Costruttore di intento: vincolo «Apertura della mano» e validazione dei
  pattern intervallari.

Scelte dove la richiesta lasciava margine:

- Ogni passo di un pattern intervallare porta il segno (`+3`, `-2`); `0` ripete la nota; al
  massimo 24 semitoni per passo.
- L'esercizio è su un solo pentagramma: i controlli `handSpan` e `positionChanges` guardano tutte
  le note, qualunque sia la mano del vincolo.
- `LeapBeyondReach` considera solo il limite della mano destra; senza quel limite non scatta.

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
