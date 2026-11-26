# Limite fisico nel profilo: apertura della mano

> Change request per metamodelli, modelli di esempio, regole Epsilon, validatore e UI.

## Contesto

Il profilo del learner deve poter esprimere **limiti funzionali** (non diagnosi) che vincolano la generazione dell'esercizio. Il primo caso è l'**apertura massima della mano al pianoforte**.

Una `Skill` è un obiettivo che si sposta: il generatore punta appena oltre il livello attuale. Un limite invece è stabile e diventa sempre un vincolo **HARD** sulla spec.

Prima di modificare, leggere lo stato attuale di `metamodels/`, `backend/src/main/resources/epsilon/`, `fixtures/giulia/` e dei test, e adattare i path se diversi.

## 1. Metamodelli

### profile.emf

Aggiungere l'enum `Hand` e la classe `HandSpanLimit`:

```
enum Hand { LEFT = 0; RIGHT = 1; }

class HandSpanLimit extends Need {
  attr Hand[1] hand;
  attr double handSpanCm;              // misura pollice–mignolo, opzionale e solo informativa
  attr int[1] maxStretchSemitones;     // intervallo massimo coperto comodamente (es. 9 = sesta maggiore)
}
```

### exercise.emf

Aggiungere `SpanConstraint`, riusando `profile.Hand` senza duplicare l'enum:

```
class SpanConstraint extends Constraint {
  attr profile.Hand[1] hand;
  attr int maxSimultaneousSemitones;   // bicordi/accordi nella stessa mano
  attr int maxStretchInPosition;       // salto melodico massimo senza cambio di posizione
}
```

### weaving

Nessuna modifica: i link sono generici.

### Note

- Rigenerare i `.ecore` dagli `.emf` e verificare che i riferimenti cross-file restino relativi.
- Se nel repo esiste uno script che genera i class diagram in `metamodels/img/`, rigenerarli.
- Aggiornare ADR e CHANGELOG con la modifica dei metamodelli.

## 2. Modelli di esempio (fixtures)

- **`fixtures/giulia/giulia.profile.xmi`:** aggiungere a Giulia un `HandSpanLimit` con hand = RIGHT, handSpanCm = 16.0, maxStretchSemitones = 9.
- **`fixtures/giulia/giulia-leap.intent.xmi` (nuovo):** stesso contenuto di `giulia.intent.xmi`, più un `ContainsPattern` con Pattern kind = INTERVAL, expression = `"+12"` (salto d'ottava). Serve a mostrare il warning EVL del §3.
- Aggiornare i test di round-trip perché coprano anche le nuove classi.

> I valori di Giulia (16 cm, massimo una sesta maggiore) sono plausibili ma di esempio. In un caso reale vanno misurati o stabiliti dalla docente.

## 3. Validazione (EVL)

| File | Vincolo | Gravità | Condizione |
|---|---|---|---|
| `profile.evl` | `HandSpanBounds` | error | `2 ≤ maxStretchSemitones ≤ 24`; `handSpanCm`, se presente, > 0 |
| `intent.evl` | `LeapBeyondReach` | warning | un `ContainsPattern` con pattern INTERVAL contiene un intervallo assoluto > `maxStretchSemitones` dell'`HandSpanLimit` della mano corrispondente (default RIGHT per esercizi di sola mano destra) |

Il messaggio di `LeapBeyondReach` è in italiano e spiega che il salto richiede un cambio di posizione della mano e potrebbe essere troppo impegnativo per il learner. Non ha fix: decide la docente. È un warning e non un errore perché un salto melodico ampio si può eseguire spostando la mano. Un bicordo oltre l'apertura, invece, non è suonabile (§6).

Il parser delle espressioni INTERVAL (`+3 -2 +12`) va condiviso tra Java e TypeScript, come il parser ritmico.

## 4. Derivazione (ETL)

Nuova regola **`SpanFromHandLimit`**: per ogni `HandSpanLimit` del profilo crea uno `SpanConstraint` HARD con:

- `hand` uguale a quella del limite;
- `maxSimultaneousSemitones = maxStretchSemitones`;
- `maxStretchInPosition = maxStretchSemitones`.

Nel weaving aggiunge un link `Derivation` con:

| Campo | Valore |
|---|---|
| origin | PROFILE |
| feature | `maxStretchSemitones` |
| rule | `SpanFromHandLimit` |
| rationale | generata dai dati, es. "Limite di apertura della mano destra: massimo 9 semitoni (sesta maggiore)" |

## 5. Risultato atteso (test di integrazione)

**Con `giulia.intent.xmi`** la spec derivata contiene in più:

- `SpanConstraint` HARD, hand RIGHT, maxSimultaneousSemitones 9, maxStretchInPosition 9;
- nel weaving, un `Derivation` aggiuntivo che lo traccia;
- `weaving.evl` senza errori.

**Con `giulia-leap.intent.xmi`:** warning `LeapBeyondReach` sull'intento, poi derivazione completata con lo stesso `SpanConstraint`.

## 6. Validatore dell'esercizio (stub)

In `BasicExerciseValidator`:

- calcolare per ogni mano l'intervallo massimo fra note simultanee (accordi ABC `[..]`): se supera `maxSimultaneousSemitones` è una violazione **HARD**;
- contare i salti melodici > `maxStretchInPosition` come "cambi di posizione" e riportarli come **INFO**.

Test:

- l'ABC mock esistente risulta valido;
- un ABC con il bicordo `[Gg]` (ottava) viola lo `SpanConstraint`.

## 7. UI

- **Editor generico:** `HandSpanLimit` e `SpanConstraint` devono comparire in palette e pannello proprietà senza codice ad hoc. Va verificato.
- **Scheda learner:** nuova sezione "Limiti fisici" con mano, apertura in cm (opzionale) e apertura massima scelta tramite il nome dell'intervallo (terza … decima). In UI si mostra il nome, nel modello si salvano i semitoni.
- **Vista provenienza:** `SpanConstraint` va colorato come derivato dal profilo.

## 8. Regole di lavoro

- Niente commit, push o modifiche alla config git. A fine lavoro proporre i commit (file + messaggio in inglese, nessun trailer).
- Nessun riferimento all'agente o ad AI nel codice o nei documenti.
- Non rompere fixture e test esistenti: build e test devono essere verdi.
- Fermarsi e chiedere prima di cambiare qualsiasi altra parte dei metamodelli.

## Riferimenti

- R. Parncutt, J. A. Sloboda, E. F. Clarke, M. Raekallio, P. Desain, "An ergonomic model of keyboard fingering for melodic fragments", *Music Perception* 14(4), 1997, pp. 341–382. Il modello di difficoltà di diteggiatura è utilizzabile in futuro come metrica di difficoltà fisica calibrata sul learner.
- J. P. Jacobs, "Refinements to the ergonomic model for keyboard fingering of Parncutt, Sloboda, Clarke, Raekallio, and Desain", *Music Perception* 18(4), 2001, pp. 505–511.
- B. G. Wristen, M. C. Jung, A. K. G. Wismer, M. S. Hallbeck, "Assessment of muscle activity and joint angles in small-handed pianists: a pilot study on the 7/8-sized keyboard versus the full-sized keyboard", *Medical Problems of Performing Artists* 21(1), 2006, pp. 3–9.
- R. Boyle, R. Boyle, "Hand size and the piano keyboard", APPCA, 2009: calibrazione tra apertura della mano e massimo intervallo suonabile.
