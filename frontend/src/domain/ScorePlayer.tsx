import abcjs from 'abcjs'
import { useEffect, useRef, useState } from 'react'

interface ScorePlayerProps {
  abc: string
  /** Size multiplier of the staff. */
  scale?: number
  /** General MIDI program of the instrument that plays the tune. */
  program?: number
  label: string
  /** Wording of the controls, for a page in another language; the studio uses the defaults. */
  text?: Partial<PlayerText>
}

export interface PlayerText {
  group: string
  play: string
  pause: string
  resume: string
  loading: string
  restart: string
  speed: string
  speeds: [string, string, string]
  error: string
  unsupported: string
}

const TEXT: PlayerText = {
  group: 'Ascolto dell’esercizio',
  play: 'Ascolta',
  pause: 'Pausa',
  resume: 'Riprendi',
  loading: 'Preparo l’audio…',
  restart: 'Da capo',
  speed: 'Velocità',
  speeds: ['Metà velocità', 'Tre quarti', 'Velocità scritta'],
  error: 'Non riesco a preparare l’audio: per scaricare i suoni dello strumento serve la connessione a Internet.',
  unsupported: 'Questo browser non può riprodurre l’audio dell’esercizio.',
}

type Status = 'idle' | 'loading' | 'playing' | 'paused' | 'error'

const SPEEDS = [50, 75, 100]

/**
 * A score with playback: the notes sound while they are highlighted on the staff.
 * The instrument samples are downloaded the first time the tune is played.
 */
export function ScorePlayer({ abc, scale = 1, program = 0, label, text }: ScorePlayerProps) {
  const t = { ...TEXT, ...text }
  const paper = useRef<HTMLDivElement>(null)
  const hidden = useRef<HTMLDivElement>(null)
  const controller = useRef<abcjs.SynthObjectController | null>(null)
  const tune = useRef<abcjs.TuneObject | null>(null)
  const ready = useRef(false)
  const [status, setStatus] = useState<Status>('idle')
  const [speed, setSpeed] = useState(100)
  const supported = abcjs.synth.supportsAudio()

  useEffect(() => {
    if (!paper.current) return
    const clearHighlight = () => paper.current?.querySelectorAll('.is-sounding').forEach((el) => el.classList.remove('is-sounding'))
    tune.current = abcjs.renderAbc(paper.current, abc, {
      scale,
      paddingtop: 0,
      paddingbottom: 0,
      paddingleft: 0,
      paddingright: 0,
      add_classes: true,
    })[0]
    ready.current = false
    setStatus('idle')
    setSpeed(100)
    if (!supported || !hidden.current) return
    const synth = new abcjs.synth.SynthController()
    // The controller needs a place for its own buttons; ours replace them, so that place stays hidden.
    synth.load(
      hidden.current,
      {
        onStart: () => setStatus('playing'),
        onFinished: () => {
          clearHighlight()
          setStatus('idle')
        },
        onEvent: (event) => {
          clearHighlight()
          for (const group of event.elements ?? []) for (const el of group) el.classList.add('is-sounding')
        },
      },
      { displayPlay: true, displayProgress: true, displayRestart: true },
    )
    controller.current = synth
    return () => {
      synth.pause()
      clearHighlight()
      controller.current = null
    }
  }, [abc, scale, supported])

  const prepare = async () => {
    if (ready.current || !controller.current || !tune.current) return
    setStatus('loading')
    await controller.current.setTune(tune.current, true, { program })
    ready.current = true
  }

  const toggle = async () => {
    try {
      if (status === 'playing') {
        controller.current?.pause()
        setStatus('paused')
        return
      }
      await prepare()
      controller.current?.play()
      setStatus('playing')
    } catch {
      setStatus('error')
    }
  }

  const restart = async () => {
    try {
      await prepare()
      controller.current?.restart()
      if (status !== 'playing') {
        controller.current?.play()
        setStatus('playing')
      }
    } catch {
      setStatus('error')
    }
  }

  const changeSpeed = async (value: number) => {
    setSpeed(value)
    try {
      await prepare()
      await controller.current?.setWarp(value)
      setStatus((current) => (current === 'loading' ? 'idle' : current))
    } catch {
      setStatus('error')
    }
  }

  return (
    <div className="score-player">
      {supported ? (
        <div className="player-bar" role="group" aria-label={t.group}>
          <button type="button" className="primary" onClick={() => void toggle()} disabled={status === 'loading'}>
            {status === 'playing' ? t.pause : status === 'loading' ? t.loading : status === 'paused' ? t.resume : t.play}
          </button>
          <button type="button" onClick={() => void restart()} disabled={status === 'loading' || status === 'idle'}>
            {t.restart}
          </button>
          <label className="player-speed">
            {t.speed}
            <select value={speed} onChange={(e) => void changeSpeed(Number(e.target.value))} disabled={status === 'loading'}>
              {SPEEDS.map((value, index) => (
                <option key={value} value={value}>
                  {t.speeds[index]}
                </option>
              ))}
            </select>
          </label>
          {status === 'error' && (
            <span className="field-error" role="alert">
              {t.error}
            </span>
          )}
        </div>
      ) : (
        <p className="hint">{t.unsupported}</p>
      )}
      <div className="score">
        <div ref={paper} className="abc-preview" role="img" aria-label={label} />
      </div>
      <div ref={hidden} hidden />
    </div>
  )
}
