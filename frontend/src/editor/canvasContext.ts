import { createContext } from 'react'

/** What nodes need from the canvas that hosts them. */
export interface CanvasActions {
  canConnect: (sourceId: string, targetId: string) => boolean
  openExternal: (model: string, id: string) => void
}

export const CanvasContext = createContext<CanvasActions>({
  canConnect: () => false,
  openExternal: () => undefined,
})
