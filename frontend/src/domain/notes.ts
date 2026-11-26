const NAMES = ['Do', 'Do♯', 'Re', 'Re♯', 'Mi', 'Fa', 'Fa♯', 'Sol', 'Sol♯', 'La', 'La♯', 'Si']

/** Italian note name with the octave number of scientific pitch notation (MIDI 60 is Do4). */
export function midiName(midi: number): string {
  return `${NAMES[((midi % 12) + 12) % 12]}${Math.floor(midi / 12) - 1}`
}

/** Selectable range of the forms: C2 to C7. */
export const MIDI_CHOICES: number[] = Array.from({ length: 96 - 36 + 1 }, (_, i) => 36 + i)
