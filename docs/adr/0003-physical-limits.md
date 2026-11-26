# ADR 0003 — Limiti fisici nel profilo

Stato: accettato (novembre 2026)

## Contesto

Il profilo descriveva abilità (`Skill`), bisogni di resa (`LowVision`) e padronanza dei pattern.
Mancava un modo per dire ciò che l'allievo fisicamente non può fare, indipendentemente dallo
studio. Il primo caso è l'apertura massima della mano al pianoforte.

## Decisioni

**Un limite è un `Need`, non una `Skill`.** Una abilità è un obiettivo che si sposta: l'esercizio
punta appena oltre il livello attuale. Un limite è stabile e non va mai superato. `HandSpanLimit`
estende quindi `Need` e sta in `Learner.needs`, senza nuovi contenimenti.

**Limite funzionale, non diagnosi.** Il modello registra l'intervallo massimo coperto comodamente
(`maxStretchSemitones`); la misura in centimetri è facoltativa e solo informativa.

**Dal limite a un vincolo HARD.** La derivazione produce sempre uno `SpanConstraint` HARD con due
soglie uguali al limite: una per le note suonate insieme, una per i salti melodici senza spostare
la mano. Sono due attributi distinti perché hanno conseguenze diverse.

**Bicordo impossibile, salto impegnativo.** Un accordo più ampio dell'apertura non è suonabile: è
una violazione bloccante dell'esercizio. Un salto melodico più ampio si esegue spostando la mano:
nell'intento è un avviso senza correzione (decide la docente), nell'esercizio è un conteggio
informativo dei cambi di posizione.

**Semitoni nel modello, nomi nell'interfaccia.** Il modello salva semitoni; la scheda dell'allievo
fa scegliere l'apertura per nome dell'intervallo, dalla terza alla decima.

## Conseguenze

- L'esercizio è oggi una sola linea su un pentagramma: la mano del vincolo compare nei messaggi ma
  i controlli guardano tutte le note. Distinguere le due mani richiederà due voci nell'esercizio.
- Il modello ergonomico di diteggiatura di Parncutt e colleghi (riferimenti nella richiesta) resta
  una possibile metrica futura di difficoltà fisica.
