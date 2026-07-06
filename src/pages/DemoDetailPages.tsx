import { MessageSquareText } from 'lucide-react'
import type { Route } from '../app/routing'
import { AgentConsole } from '../components/AgentConsole'
import { DemoBackLink } from '../components/demo/DemoBackLink'
import { DocumentSummaryDemo } from '../components/DocumentSummaryDemo'
import { MedicalAppointmentDemo } from '../components/MedicalAppointmentDemo'
import { SectionHeading } from '../components/SectionHeading'

export function AgentChatDemoPage({
  title,
  onNavigate,
}: {
  title: string
  onNavigate: (path: Route) => void
}) {
  return (
    <>
      <section className="page-intro">
        <DemoBackLink onNavigate={onNavigate} />
        <div className="section-kicker">
          <MessageSquareText size={18} />
          {title}
        </div>
        <h1>Portfolio assistant</h1>
        <p>
          Chat por streaming y modo conversacion por voz para consultar el perfil profesional de
          Sebastian Gatica, sus demos y su enfoque tecnico.
        </p>
      </section>
      <AgentConsole />
    </>
  )
}

export function DocumentDemoPage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <section className="section demo-detail-page">
      <DemoBackLink onNavigate={onNavigate} />
      <SectionHeading
        kicker="Demo documentos"
        title="Document intelligence con tratamiento efimero."
        text="Subi un PDF, obtene un resumen con input_file y store=false, separando hechos, dudas y proximos pasos sin conservar el archivo."
      />

      <DocumentSummaryDemo />
    </section>
  )
}

export function MedicalAppointmentDemoPage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <section className="section demo-detail-page">
      <DemoBackLink onNavigate={onNavigate} />
      <SectionHeading
        kicker="Demo turnos"
        title="Workflow de turnos medicos con tools reales."
        text="La persona llama, pide un turno, el agente consulta disponibilidad, propone alternativas y deja la reserva visible en el calendario en tiempo real."
      />

      <MedicalAppointmentDemo />
    </section>
  )
}
