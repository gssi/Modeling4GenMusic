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
workspace/    modelli creati a runtime (non versionati)
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

Nella vista «Esercizio» scegli «Ollama» e premi «Genera esercizio». Ogni esercizio viene
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
- `id.meta.json`: nome, metamodello, versione (lock ottimistico) e ruolo;
- `id.layout.json`: posizioni dei nodi nel diagramma.

## Dal profilo all'esercizio

1. Crea o importa un profilo, un intento didattico e un modello di qualità. Ogni modello si
   modifica come diagramma oppure, con il selettore «Scheda», come modulo.
2. «Deriva un esercizio» fa scegliere i tre modelli, mostra i controlli sull'intento con le
   correzioni proposte e produce la specifica dell'esercizio e la traccia di derivazione.
3. Nella specifica derivata il blu indica ciò che ha chiesto la docente, il verde ciò che viene
   dal profilo o dalle regole; selezionando un elemento si legge il perché e si aprono le sorgenti.
4. La vista «Esercizio» della specifica chiede un esercizio e lo controlla. Può scriverlo un
   brano di prova fisso oppure un modello musicale locale servito da Ollama (vedi sotto).

Per caricare l'esempio, importa dall'editor i tre file di `fixtures/giulia/` con «Importa XMI».
