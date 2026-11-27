# Expert evaluation of exercises for special needs

A small experiment: five combinations of learner profile and teaching intent, each with the
exercise a music language model generated for it. Experts are asked whether each exercise respects
the requirements that were modelled.

## What is here

```
cases/cases.json          the list of cases
cases/shared/             the catalog of need types and the quality model used by every case
cases/<id>/case.json      title and description of the case, in English (written by hand)
cases/<id>/<id>.profile.xmi, <id>.intent.xmi          the input models
cases/<id>/<id>.intent.derived.xmi, .weaving.xmi      the derived spec and its trace
cases/<id>/requirements.json   the requirements, read from the derived spec, with stable ids
cases/<id>/exercise.abc        the generated exercise
cases/<id>/result.json         automatic checks, every attempt, seed and request sent to the model
run.mjs                   the script that produced everything but the inputs
```

| Case | Needs in the profile | What it puts to the test |
|---|---|---|
| C1 | none (control) | range, tempo, requested rhythm |
| C2 | low vision, hand span limit | enlarged staff, leaps within a sixth, a rhythm being learned, a rhythm kept out |
| C3 | short attention span, reduced motor speed | 8 bars asked → 4, 100 bpm asked → 60 |
| C4 | visual crowding | enlarged staff, no pairs of sixteenth notes |
| C5 | five-finger position, narrow hand span | notes from C4 to G4 only, leaps within a fifth |

The names and thresholds of the need types are a modelling proposal: they describe functional
limits, not diagnoses.

## The site

`frontend/experiment.html` is a static page that shows the cases. It needs no backend.

```
cd frontend
npm run dev:experiment      # local preview
npm run build:experiment    # static site in frontend/dist-experiment/
```

- **Expert view** (default): description of the case, requirements with their ids, the score with
  playback, the files to download. Nothing is collected: answers go in a separate questionnaire
  and cite the ids (`C3-R2`).
- **Researcher view** (add `?view=full` to the address, before the `#`): the automatic checks, all
  the attempts, the request sent to the model, the quality goals and the rule behind each
  requirement. The expert view has no link to it, but the files it reads are public.

The workflow `.github/workflows/pages.yml` publishes the site on GitHub Pages at every push to
`main` that touches it. Playback downloads the instrument sounds from the Internet.

## How the exercises were obtained

One run per case, kept as it came out: no exercise was picked by hand or corrected. The generator
writes up to five exercises and keeps the first that passes the automatic checks, or the best one
when none does. In the published run C1, C2 and C4 pass every check; C3 and C5 have notes outside
the required range after five attempts.

To run it again (the results will differ: the seed is random):

```
ollama serve
ollama pull hf.co/mradermacher/ChatMusician-GGUF:Q4_K_M

cd backend && mvn -q package -DskipTests
SERVER_PORT=8091 STUDIO_WORKSPACE_DIR=../experiment/.workspace STUDIO_GENERATION_MAX_ATTEMPTS=5 \
  java -jar target/studio-backend-0.1.0-SNAPSHOT.jar

node experiment/run.mjs --force          # every case
node experiment/run.mjs --force c3       # one case
```

The backend runs on a workspace of its own, so the models of `workspace/` are not touched. Without
`--force` a case that already has a result is skipped.

## Limits to keep in mind when reading the answers

- The quality goals (rhythmic and melodic complexity) are in the derived spec but are neither told
  to the music model nor checked: they are left out of the expert view.
- The enlargement of the staff is applied by the page that shows the score, not by the generator.
- An exercise is a single melodic line: requirements about one hand are checked on all the notes.
- A requirement marked as a preference comes from a pattern the learner already masters.
- The title printed on the scores (“Esercizio”) is the one the generator writes.
