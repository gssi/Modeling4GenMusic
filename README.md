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
| M4 | Derivazione ETL, weaving, vista di provenienza | da fare |
| M5 | Viste low-code ed editor di pattern ritmici | da fare |
| M6 | Export e stub di generazione | da fare |

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
```

## Modelli sul disco

Ogni modello `id` di un workspace è fatto di tre file in `workspace/<workspace>/`:

- `id.xmi`: il modello, con `xmi:id` stabili; i riferimenti ad altri modelli sono href relativi;
- `id.meta.json`: nome, metamodello, versione (lock ottimistico) e ruolo;
- `id.layout.json`: posizioni dei nodi nel diagramma.

Per caricare l'esempio, importa dall'editor i tre file di `fixtures/giulia/` con «Importa XMI».
