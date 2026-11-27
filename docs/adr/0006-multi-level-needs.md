# ADR 0006 — Bisogni tipizzati da un catalogo

Stato: accettato (novembre 2026)

## Contesto

Ogni bisogno dell'allievo era una sottoclasse di `Need` nel metamodello `profile` (`LowVision`,
`HandSpanLimit`) e aveva, per nome di classe, una regola in `derive.etl`, un controllo in
`profile.evl` e una sezione nella scheda dell'allievo. Un bisogno nuovo richiedeva di toccare il
metamodello, due script e il frontend; una sottoclasse aggiunta senza il resto veniva ignorata.
Chi conosce la didattica non poteva descrivere un bisogno senza chi scrive il codice.

## Decisioni

**I tipi di bisogno sono dati.** Un nuovo metamodello, `needs`, descrive un catalogo: ogni
`NeedType` dichiara i suoi parametri (`ParameterDef`: tipo, minimo, massimo, valore predefinito) e
i suoi effetti sulla specifica dell'esercizio. Nel profilo `Need` è una sola classe: punta al suo
tipo e ne valorizza i parametri con degli `Slot`. È il pattern type-object: EMF resta a due
livelli, il terzo livello (tipo di bisogno → bisogno di un allievo) sta nei modelli.

**Un effetto nomina ciò che tocca.** Un `ConstraintEffect` dice quale classe di vincolo di
`exercise` creare e, con i `Binding`, quali attributi valorizzare (anche di un elemento contenuto:
`pattern.expression`), da un parametro o da un letterale. Uno `SpecEffect` limita un attributo
della specifica (`bars`, `maxBpm`) come tetto, soglia minima o valore fisso.

**Un interprete solo.** `derive.etl` non nomina più alcun bisogno: `ApplyNeeds` e
`ApplyNeedLimits` seguono bisogno → tipo → effetto e scrivono la specifica per riflessione, con
l'aiuto di `NeedTool`. Un vincolo che viene da un bisogno è sempre HARD (ADR 0003: un limite è
stabile). Ogni elemento prodotto è tracciato a due sorgenti: lo slot del profilo e l'effetto del
catalogo (nuova origine `NEED_CATALOG`).

**Il catalogo si controlla prima di derivare.** `needs.evl` rifiuta un effetto che nomina una
classe o un attributo che `exercise` non ha, o un parametro di tipo incompatibile; `profile.evl`
controlla ogni valore contro il tipo e i limiti dichiarati. La derivazione non parte con un
catalogo o un profilo in errore.

**La docente è avvisata quando un bisogno la scavalca.** Se l'intento chiede più di quanto un
bisogno consente, l'avviso `NeedLimitExceeded` lo dice prima di derivare; la derivazione applica
comunque il limite e lo registra come correzione di quell'avviso.

**La scheda dell'allievo legge il catalogo.** I tipi che si possono aggiungere, i loro nomi e un
campo per parametro vengono dal catalogo scelto nel profilo. Il catalogo si modifica nel diagramma
generico.

## Conseguenze

- Un tipo di bisogno che si esprime con i vincoli esistenti si aggiunge modificando un modello:
  nessuna ricompilazione, e prompt e controlli dell'esercizio lo rispettano perché leggono la
  specifica derivata, il cui vocabolario non cambia.
- **Limite:** un bisogno che richiede un tipo di vincolo nuovo richiede ancora di estendere
  `exercise`, il prompt del generatore e i controlli dell'esercizio. Il catalogo sposta nei dati la
  scelta di *quali* vincoli applicare, non il significato dei vincoli.
- I valori degli slot sono testo: il tipo si controlla con le regole, non con il metamodello.
- Nomi di classi e attributi negli effetti sono stringhe: un cambio di nome in `exercise` non
  viene propagato al catalogo, ma `needs.evl` lo segnala.
- I profili scritti con `LowVision` e `HandSpanLimit` non si caricano più e vanno riscritti (la
  fixture di Giulia lo è, con gli stessi valori e la stessa specifica derivata).

## Alternative scartate

- **Una sottoclasse per bisogno, con un registro di regole.** Resta codice per ogni bisogno.
- **Metamodellazione multi-livello vera** (potenza, clabject): gli strumenti usati, EMF ed Epsilon,
  sono a due livelli; avrebbe richiesto un altro stack per un vantaggio che qui non serve.
- **Effetti scritti come frammenti di script nel catalogo.** Più espressivi, ma un catalogo
  diventerebbe codice eseguito sul server e non sarebbe più controllabile contro il metamodello.
