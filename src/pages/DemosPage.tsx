import type { Route } from '../app/routing'
import { DemoCardGrid } from '../components/demo/DemoCardGrid'
import { SectionHeading } from '../components/SectionHeading'
import { demoCards } from '../data/siteContent'

export function DemosPage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <section className="section demos-page">
      <SectionHeading
        kicker="Demos"
        title="Tres experiencias para probar IA aplicada."
        text="La demo principal presenta el asistente del portfolio; las otras dos son casos aparte para turnos y documentos."
      />

      <DemoCardGrid demos={demoCards} onNavigate={onNavigate} />
    </section>
  )
}
