# Changelog

Una nota per milestone. Le modifiche ai metamodelli vanno segnalate qui.

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
- `weaving.evl` arriva con M4.

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
