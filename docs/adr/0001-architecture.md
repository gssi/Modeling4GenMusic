# ADR 0001 — Architettura

Stato: accettato (novembre 2026)

## Contesto

Serve un editor web con cui un docente di musica compone tre modelli (profilo dell'allievo, intento
didattico, modello di qualità) e ne deriva la specifica di un esercizio con la traccia di
provenienza. I quattro metamodelli Ecore esistono già e sono la fonte di verità.

## Decisioni

**Backend: Java 21, Spring Boot 3.5, EMF, Epsilon.** EMF carica i metamodelli e gestisce i modelli
in modo dinamico, senza codice generato: aggiungere o cambiare una classe del dominio non richiede
di toccare il Java. Epsilon (EVL, più avanti ETL) è usato come libreria. Spring Boot resta sulla
linea 3.x; il passaggio alla 4.x è rimandato a quando servirà.

**Frontend: React, TypeScript, Vite, React Flow, Zustand, ELK.** L'editor generico sta in
`src/editor/` e ricava tutto dalla descrizione JSON dei metamodelli; le parti specifiche del
dominio musicale stanno in `src/domain/`.

**Persistenza su file.** Un modello è un file XMI; nome, versione, ruolo e posizioni del diagramma
stanno in file affiancati, così il modello semantico non contiene notazione. Il ruolo distingue
modelli dello stesso metamodello (intento e specifica derivata sono entrambi `ExerciseSpec`).

**Contratto JSON generico.** Il frontend non conosce l'XMI. Ogni oggetto ha `id`, `eClass`,
`attributes`, `contents` e `references`; le ultime due associano sempre una lista al nome della
feature. Un riferimento verso un altro modello porta l'id del modello.

**Regole fuori dal codice.** Vincoli e correzioni sono script EVL; l'associazione tra metamodello,
script e modelli collegati è in un descrittore (`epsilon/validations.json`). Gli script chiamano
codice Java solo attraverso oggetti di supporto dichiarati nello stesso descrittore.

**Correzioni senza stato.** L'id di una diagnostica è deterministico, quindi il server non conserva
i risultati di una validazione: per applicare una correzione la riesegue.

**Deploy.** `docker-compose` con due container; nginx serve il frontend e inoltra `/api`.

**Nessuna autenticazione nell'MVP**, ma ogni API porta un `workspaceId` (oggi `default`).

## Decisioni sulle regole di derivazione (per M4 e M5)

- La derivazione limita sempre il tempo a quello comodo dell'allievo e lo registra come
  `ConflictFix`; la correzione EVL serve a renderlo esplicito al docente prima di derivare.
- `CopyIntent` produce un `Refinement` per attributo, per colorare la provenienza attributo per
  attributo.
- La grammatica ritmica ammette le durate `1`, `2`, `4`, `8`, `16` con punto opzionale.

## Alternativa scartata

Eclipse Sirius Web darebbe un editor da Ecore con meno codice, ma le viste low-code previste
(editor di pattern ritmici con anteprima in notazione, vista di provenienza) sono molto specifiche.
L'editor generico di M2 è risultato fattibile con React Flow, quindi la scelta è confermata.

## Conseguenze

- Il backend non contiene logica del dominio musicale, salvo gli script Epsilon, il parser ritmico
  e, più avanti, i moduli di generazione.
- Modificare un metamodello vuol dire aggiornare il `.emf`, rigenerare il `.ecore` e annotarlo nel
  changelog; l'editor si adegua da solo, gli script EVL vanno rivisti.
- La persistenza su file non regge l'uso concorrente di più persone sullo stesso modello oltre al
  controllo di versione al salvataggio.
