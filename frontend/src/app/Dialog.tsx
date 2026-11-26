import { type ReactNode, useEffect, useRef } from 'react'

interface DialogProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Buttons, right aligned; the dialog closes on Escape or on the backdrop. */
  actions?: ReactNode
}

/** Modal dialog on the native element, which traps focus while it is open. */
export function Dialog({ title, onClose, children, actions }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (!dialog) return
    // Remember what had the focus, to give it back when the dialog goes away:
    // keyboard commands keep working where the person left off.
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null
    if (!dialog.open) dialog.showModal()
    return () => {
      if (dialog.open) dialog.close()
      if (previous?.isConnected) previous.focus()
    }
  }, [])

  return (
    <dialog
      ref={ref}
      className="dialog"
      aria-label={title}
      onCancel={(e) => {
        e.preventDefault()
        onClose()
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose()
      }}
    >
      <div className="dialog-body">
        <h2>{title}</h2>
        {children}
        {actions && <div className="dialog-actions">{actions}</div>}
      </div>
    </dialog>
  )
}
