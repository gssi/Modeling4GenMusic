import { type ReactNode, useEffect, useRef } from 'react'

interface DialogProps {
  title: string
  onClose: () => void
  children: ReactNode
  /** Buttons, right aligned; the dialog closes on Escape or on the backdrop. */
  actions?: ReactNode
}

/** Modal dialog on the native element, which traps focus and restores it when closed. */
export function Dialog({ title, onClose, children, actions }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    const dialog = ref.current
    if (dialog && !dialog.open) dialog.showModal()
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
