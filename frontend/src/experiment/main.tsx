import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import '@fontsource/atkinson-hyperlegible/400.css'
import '@fontsource/atkinson-hyperlegible/700.css'
import '../index.css'
import './experiment.css'
import { ExperimentApp } from './ExperimentApp'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <ExperimentApp />
  </StrictMode>,
)
