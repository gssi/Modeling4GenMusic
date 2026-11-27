# Music Learner Modeling Studio

Editor web di modellazione, grafico e low-code, per comporre il profilo di un allievo di musica,
l'intento didattico di un esercizio e il modello di qualità, e derivarne la specifica completa
dell'esercizio con la traccia di provenienza.

I metamodelli Ecore in `metamodels/` sono la fonte di verità: l'editor ne ricava palette, moduli
delle proprietà e regole di connessione, senza cablare classi del dominio.

## Stato

| Milestone | Contenuto | Stato |
|---|---|---|
| M0 | Struttura del progetto, build, container, CI | fatto |
| M1 | Registro dei metamodelli, JSON ↔ EMF, persistenza XMI, API | fatto |
| M2 | Editor grafico generico | fatto |
| M3 | Riferimenti tra modelli, validazione EMF ed EVL, correzioni | fatto |
| M4 | Derivazione ETL, weaving, vista di provenienza | fatto |
| M5 | Viste low-code ed editor di pattern ritmici | fatto |
| M6 | Export e stub di generazione | fatto |

Le note per milestone sono in [docs/CHANGELOG.md](docs/CHANGELOG.md), le scelte di architettura in
[docs/adr/](docs/adr/).

## Struttura

```
metamodels/   metamodelli .ecore e .emf, diagrammi in img/
backend/      Spring Boot: modelli EMF, persistenza, validazione (regole in src/main/resources/epsilon/)
frontend/     React + Vite: editor generico in src/editor/, parti di dominio in src/domain/
fixtures/     modelli XMI di esempio (Giulia)
workspace/    modelli creati a runtime, con la loro cronologia (fuori da git)
experiment/   casi e risultati della valutazione con gli esperti, pubblicati come sito statico
docs/         changelog e ADR
```

## Avvio con Docker

```
docker compose up --build
```

L'editor risponde su http://localhost:8081, le API su http://localhost:8080/api. I modelli vengono
salvati in `workspace/`.

## Sviluppo

Servono JDK 21, Maven e Node 24.

```
cd backend && mvn spring-boot:run      # API su :8080
cd frontend && npm install && npm run dev   # editor su :5173, con /api inoltrato al backend
```

Verifiche:

```
cd backend && mvn verify
cd frontend && npm run lint && npm test && npm run build
cd frontend && npx playwright install chromium && npm run e2e
```

Il test end-to-end avvia da solo backend (porta 8090) e frontend (porta 5174) su un workspace
temporaneo, quindi non tocca i modelli di `workspace/`.

## Generazione con Ollama

L'esercizio può essere scritto da [ChatMusician](https://huggingface.co/m-a-p/ChatMusician), un
modello addestrato sulla notazione ABC, eseguito in locale da [Ollama](https://ollama.com).

```
ollama serve
ollama pull hf.co/mradermacher/ChatMusician-GGUF:Q4_K_M    # 4,1 GB
```

Nella vista «Esercizio» scegli «Ollama» e premi «Genera esercizio»; con «Ascolta» lo senti
suonare (i campioni dello strumento si scaricano da Internet al primo ascolto). Ogni esercizio viene
controllato rispetto alla specifica; se non la rispetta se ne chiede un altro, fino a tre. Il
modello segue i vincoli in modo debole: aspettati che una parte delle generazioni non sia valida.
Dettagli e misure in [docs/adr/0002-generation-with-ollama.md](docs/adr/0002-generation-with-ollama.md).

Le impostazioni sono in `backend/src/main/resources/application.properties` (`studio.ollama.*`,
`studio.generation.*`) e si possono sovrascrivere con variabili d'ambiente, per esempio
`STUDIO_GENERATION_MAX_ATTEMPTS=5`. Con Docker il backend raggiunge l'Ollama della macchina
all'indirizzo `host.docker.internal:11434`.

## Modelli sul disco

Ogni modello `id` di un workspace è fatto di tre file in `workspace/<workspace>/`:

- `id.xmi`: il modello, con `xmi:id` stabili; i riferimenti ad altri modelli sono href relativi;
- `id.meta.json`: nome, metamodello, versione (lock ottimistico), ruolo e cartella;
- `id.layout.json`: posizioni dei nodi nel diagramma.

### Cartelle

I modelli si possono raccogliere in cartelle, anche una dentro l'altra (`Classe 3/Giulia`). Il
pannello «Modelli» le mostra come sezioni che si aprono e chiudono; dentro ciascuna i modelli
restano raggruppati per tipo. Un modello si sposta dal campo «Cartella» della sua barra, senza
salvarlo; «+» e «↥» dentro una cartella creano o importano lì. Ciò che si deriva da un intento
finisce nella cartella dell'intento.

Una cartella è un'etichetta del modello, scritta in `id.meta.json`: sul disco i file restano tutti
uno accanto all'altro e gli identificativi non cambiano, quindi spostare un modello non tocca i
riferimenti di chi lo cita, né la sua cronologia. Una cartella esiste finché contiene un modello.

API: `PUT /api/models/{id}/folder` con `{"folder": "Classe 3/Giulia"}` (vuoto per toglierlo da ogni
cartella); `folder` è accettato anche da `POST /api/models` e `POST /api/models/import`.

### Versioni

Ogni salvataggio (dall'editor, da una correzione rapida, da una derivazione, da un'importazione)
lascia una copia in `workspace/<workspace>/.history/<id>/`: `N.xmi` con il contenuto della versione
`N` e `N.json` con data e origine. Il pulsante «Versioni» della barra del modello le elenca,
confronta quella scelta con il modello aperto e la ripristina. Il ripristino aggiunge una nuova
versione con il vecchio contenuto: la cronologia non viene mai riscritta.

- Si conservano le ultime 50 versioni di ogni modello (`studio.history-size`).
- La cronologia è per singolo modello: ripristinare un profilo non riporta indietro gli intenti
  che lo citano. Nome, tipo e disposizione del diagramma restano quelli attuali.
- Eliminare un modello elimina anche la sua cronologia.
- API: `GET /api/models/{id}/versions`, `GET /api/models/{id}/versions/{n}`,
  `POST /api/models/{id}/versions/{n}/restore`.

## Regole di validazione

Le regole stanno in `backend/src/main/resources/epsilon/*.evl`; `validations.json` dice a quali
modelli si applica ogni script. «Regole», in alto, le mostra: per
ogni script le regole raggruppate per classe, con gravità, condizione (`guard`), controllo
(`check`), messaggio e correzioni rapide, più le operazioni di supporto e il sorgente completo.
Da un problema, «Vedi la regola» porta alla regola che lo ha segnalato.

La struttura mostrata è quella che restituisce il parser EVL di Epsilon, lo stesso che esegue gli
script: uno script con errori di sintassi compare con riga, colonna e motivo di ogni errore al
posto delle regole.

«Modifica» apre il testo di uno script. Mentre scrivi il server lo controlla in due passi, e si
salva solo se passano entrambi:

1. la sintassi, con il parser EVL: ogni errore ha riga, colonna e motivo;
2. una prova: le regole vengono eseguite sui modelli del workspace a cui si applicano. È il solo
   modo di scoprire una proprietà o un'operazione inesistente, che per il parser è sintassi
   corretta; un errore in una regola che nessun modello presente fa scattare può sfuggire.

Lo script modificato è salvato in `workspace/.rules/<script>` e sostituisce quello fornito con
l'applicazione, che non viene toccato; «Ripristina le originali» lo rimuove. Dopo un salvataggio
i modelli aperti vengono validati di nuovo.

Uno script EVL può eseguire codice sul server (EOL sa chiamare classi Java): chi può modificare le
regole può farlo. L'editor non ha autenticazione, quindi va usato in locale o su una rete fidata.

API: `GET /api/rules`, `POST /api/rules/{script}/check`, `PUT /api/rules/{script}`,
`DELETE /api/rules/{script}`.

### Assistente per scrivere le regole

Nell'editor di ogni script, «Descrivi la regola» traduce una descrizione in italiano in una regola
EVL (o ETL per `derive.etl`). La scrive un modello linguistico locale servito da Ollama, lo stesso
server della generazione musicale; nulla esce dalla macchina.

```sh
ollama serve                    # se non è già attivo
ollama pull qwen2.5-coder:14b   # circa 9 GB, una volta sola
```

Come lavora:

1. Al modello vanno le classi dei metamodelli che lo script vede, le operazioni e gli strumenti
   che può chiamare e alcune regole dello script come esempio; tutto è letto dai metamodelli e
   dagli script, non scritto a mano.
2. La regola proposta viene aggiunta in fondo allo script e passata agli stessi due controlli del
   salvataggio (sintassi, prova sui modelli). Se non li supera, il modello viene interrogato di
   nuovo con gli errori, fino a tre volte.
3. L'anteprima mostra la regola, l'esito dei controlli e, per le regole di validazione, cosa
   segnalerebbe sui modelli del workspace. «Inserisci nel testo» la mette nell'editor; il
   salvataggio resta quello manuale. L'assistente non salva mai da solo.

I controlli garantiscono che la regola giri, non che faccia ciò che è stato chiesto: va letta. Per
questo l'anteprima dice cosa segnalerebbe, ed è il modo più rapido di accorgersi di una regola
che segnala tutto o niente. Nelle prove sulle fixture di Giulia, 8 richieste di validazione su 8
e 4 di derivazione su 4 hanno dato una regola che supera i controlli (da 2 a 30 secondi); a
leggerle, circa una su quattro non faceva esattamente ciò che era stato chiesto.

Impostazioni: `studio.assistant.model`, `.timeout`, `.temperature`, `.max-attempts`.
API: `GET /api/assistant`, `POST /api/assistant/rules`.

### Regole di derivazione

La stessa scheda mostra `derive.etl`, la trasformazione che costruisce la specifica: le regole che
creano elementi (da quale classe, quali classi creano, quando si applicano), i blocchi `pre` e
`post`, le operazioni e il sorgente. Si modifica come gli script di validazione, con gli stessi
due controlli; qui la prova è una derivazione eseguita, senza salvare nulla, su ogni intento del
workspace con il profilo che cita e un modello di qualità. Se la trasformazione rifiuta un
ingresso con un suo `throw` (per esempio un profilo senza allievo) non è un errore dello script.

Le derivazioni già fatte non cambiano: per applicare le nuove regole va rifatto «Deriva».

API: `GET /api/derivations/script`, `POST /api/derivations/script/check`,
`PUT /api/derivations/script`, `DELETE /api/derivations/script`.

## Dal profilo all'esercizio

1. Crea o importa un catalogo dei bisogni, un profilo, un intento didattico e un modello di
   qualità. Ogni modello si modifica come diagramma oppure, con il selettore «Scheda», come modulo.
2. «Deriva un esercizio» fa scegliere i tre modelli, mostra i controlli sull'intento con le
   correzioni proposte e produce la specifica dell'esercizio e la traccia di derivazione.
3. Nella specifica derivata il blu indica ciò che ha chiesto la docente, il verde ciò che viene
   dal profilo, dai bisogni o dalle regole; selezionando un elemento si legge il perché e si
   aprono le sorgenti.
4. La vista «Esercizio» della specifica chiede un esercizio e lo controlla. Può scriverlo un
   brano di prova fisso oppure un modello musicale locale servito da Ollama (vedi sotto).

Per caricare l'esempio, importa dall'editor i file di `fixtures/giulia/` con «Importa XMI»,
partendo da `default.needs.xmi`: il profilo cita il catalogo dei bisogni.

## Bisogni dell'allievo

I bisogni non sono classi del metamodello: sono tipi descritti in un modello, il catalogo
(`fixtures/giulia/default.needs.xmi`, metamodello `needs`). Ogni tipo dichiara i suoi parametri e
i suoi effetti sulla specifica: quale vincolo creare e con quali valori, oppure quale attributo
limitare (battute, tempo). Nel profilo un bisogno indica il suo tipo e ne valorizza i parametri.

Per aggiungere un tipo di bisogno apri il catalogo nel diagramma, crea un `NeedType` con i suoi
`ParameterDef` e almeno un effetto, e salva: la scheda dell'allievo lo propone subito e la
derivazione lo applica, senza modifiche al codice. Il catalogo è controllato al salvataggio: un
effetto che nomina una classe o un attributo che il metamodello `exercise` non ha è un errore.

Il limite è il vocabolario dei vincoli: un bisogno che non si esprime con quelli esistenti
(pattern richiesto o escluso, estensione, apertura della mano, resa grafica, battute, tempo)
richiede di estendere `exercise`, il generatore e i controlli. Le scelte sono in
[docs/adr/0006-multi-level-needs.md](docs/adr/0006-multi-level-needs.md).
