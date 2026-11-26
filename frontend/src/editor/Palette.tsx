import type { MetamodelDescriptor } from '../api/types'
import { concreteClasses } from './metamodel'

export const PALETTE_MIME = 'application/x-studio-class'

interface PaletteProps {
  metamodel: MetamodelDescriptor
  color: string
  onAdd: (className: string) => void
}

/** Concrete classes of the metamodel. Drag one onto the diagram, or press it to add it. */
export function Palette({ metamodel, color, onAdd }: PaletteProps) {
  return (
    <section className="palette" aria-label="Elementi che puoi aggiungere">
      <h2>Elementi</h2>
      <p className="hint">Trascina sul diagramma, oppure premi per aggiungere.</p>
      <ul>
        {concreteClasses(metamodel).map((c) => (
          <li key={c.name}>
            <button
              type="button"
              className="palette-item"
              draggable
              onDragStart={(e) => {
                e.dataTransfer.setData(PALETTE_MIME, c.name)
                e.dataTransfer.effectAllowed = 'copy'
              }}
              onClick={() => onAdd(c.name)}
            >
              <span className="palette-swatch" style={{ background: color }} aria-hidden="true" />
              {c.name}
            </button>
          </li>
        ))}
      </ul>
    </section>
  )
}
