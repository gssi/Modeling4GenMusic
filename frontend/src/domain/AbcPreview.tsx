import abcjs from 'abcjs'
import { useEffect, useRef } from 'react'

interface AbcPreviewProps {
  abc: string
  /** Size multiplier of the staff, for example the one asked by a low-vision need. */
  scale?: number
  label: string
}

/** Music notation rendered from ABC. */
export function AbcPreview({ abc, scale = 1, label }: AbcPreviewProps) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    if (!ref.current) return
    abcjs.renderAbc(ref.current, abc, {
      scale,
      paddingtop: 0,
      paddingbottom: 0,
      paddingleft: 0,
      paddingright: 0,
      add_classes: true,
    })
  }, [abc, scale])
  return <div ref={ref} className="abc-preview" role="img" aria-label={label} />
}
