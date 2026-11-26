# 0004 — Versioni dei modelli e catalogo delle regole

## Contesto

I modelli sono file nel workspace e ogni salvataggio sostituiva il precedente: il numero di
versione serviva solo al lock ottimistico. Le regole di validazione erano visibili solo nei file
EVL del backend.

## Decisioni

**Versioni come copie sul disco.** Ogni scrittura di un modello lascia `N.xmi` e `N.json` in
`.history/<id>/` accanto ai modelli. È la stessa persistenza a file del resto, senza un database
né git nel workspace, e una versione resta un XMI leggibile con qualunque strumento EMF.

**Il ripristino aggiunge.** Riportare una versione ne crea una nuova con quel contenuto. Il numero
di versione continua a crescere, quindi il lock ottimistico non cambia e nessuna versione viene
persa per errore.

**Una versione si legge all'indirizzo del modello.** Il contenuto storico viene caricato come se
fosse il file corrente, così gli href relativi verso gli altri modelli si risolvono. I riferimenti
puntano quindi ai modelli di oggi: la cronologia è per modello, non una fotografia del workspace.

**Il confronto è per identificativo.** Il frontend confronta due contenuti per `xmi:id`: elementi
in più, in meno, attributi e riferimenti diversi. Un elemento rinominato risulta tolto e aggiunto.

**Le regole si descrivono con il parser che le esegue.** `RuleCatalog` analizza ogni script con
`EvlModule` e ricava il testo delle parti dalle regioni dell'albero sintattico. Non c'è una
seconda grammatica da tenere allineata, e gli errori di sintassi sono quelli del motore.

## Conseguenze

- La cronologia occupa spazio: è limitata alle ultime 50 versioni per modello.
- La disposizione del diagramma non è versionata: dopo un ripristino gli elementi tornati
  vengono disposti di nuovo.

## Modifica delle regole

**Gli originali non si toccano.** Uno script modificato è un file in `workspace/.rules/` che
prende il posto di quello nel jar; rimuoverlo riporta all'originale. Il workspace è già il posto
dei dati che cambiano a runtime, ed è il volume montato in Docker.

**Due controlli prima di salvare.** Il parser EVL trova gli errori di sintassi. Non trova un nome
di proprietà o di operazione sbagliato, perché EOL risolve i nomi solo in esecuzione: per questo
lo script viene anche eseguito sui modelli del workspace a cui si applica, e un errore di
esecuzione blocca il salvataggio. La prova copre solo ciò che i modelli presenti fanno scattare.

**Si modifica il testo intero dello script.** Regole e operazioni di supporto stanno nello stesso
file e si riferiscono tra loro: modificare una regola per volta avrebbe richiesto di ricomporre
il file e avrebbe nascosto le operazioni.

Conseguenze:

- Le regole modificate non hanno cronologia: c'è solo il ritorno all'originale.
- Chi modifica uno script può far eseguire codice al server (EOL chiama classi Java). Senza
  autenticazione l'editor va usato in locale o su una rete fidata.
- I descrittori JSON (`validations.json`, `derivation.json`) restano risorse del backend.

## Trasformazione

`derive.etl` segue le stesse scelte: descritto con il parser ETL, modificato come testo intero,
salvato in `workspace/.rules/`. La prova è una derivazione su ogni modello che può fare da
ingresso principale e non è a sua volta un prodotto della derivazione, con il modello che cita
(o il primo disponibile) per ciascun altro ingresso; ciò che produce non viene salvato. Un `throw`
dello script è un rifiuto voluto dell'ingresso e non conta come errore.
