import {
  ArrowRight,
  BrainCircuit,
  Braces,
  Check,
  Database,
  FileText,
  Github,
  Linkedin,
  Mail,
  MessageSquareText,
  Moon,
  Send,
  ServerCog,
  ShieldCheck,
  Sparkles,
  Stethoscope,
  Sun,
  Workflow,
} from 'lucide-react'
import Atropos from 'atropos/react'
import type { AnchorHTMLAttributes, FormEvent, ReactNode } from 'react'
import { useEffect, useMemo, useState } from 'react'
import { AgentConsole } from './components/AgentConsole'
import { DocumentSummaryDemo } from './components/DocumentSummaryDemo'
import { MedicalAppointmentDemo } from './components/MedicalAppointmentDemo'
import { sendContactMessage } from './api/agentClient'
import { capabilityLayers, demoPlaybooks, qualityGates, stack } from './data/portfolio'

type Route =
  | '/'
  | '/demos'
  | '/demos/chat'
  | '/demos/turnos'
  | '/demos/documentos'
  | '/demo'
  | '/contacto'

type Theme = 'light' | 'dark'

const THEME_STORAGE_KEY = 'sg-portfolio-theme'

const routes: Route[] = [
  '/',
  '/demos',
  '/demos/chat',
  '/demos/turnos',
  '/demos/documentos',
  '/demo',
  '/contacto',
]

const profileLinks = {
  linkedin: 'https://www.linkedin.com/',
  github: 'https://github.com/',
}

const demoCards = [
  {
    title: 'Portfolio assistant',
    eyebrow: 'Chat + voz + perfil',
    summary:
      'Asistente integrado al portfolio de Sebastian Gatica para consultar perfil, stack, demos y enfoque profesional.',
    route: '/demos/chat' as Route,
    icon: MessageSquareText,
    points: ['Chat streaming', 'Modo conversacion', 'Prompt editable', 'OpenAI API'],
    status: 'Demo principal',
  },
  {
    title: 'Medical appointment workflow',
    eyebrow: 'Tools + estado vivo',
    summary:
      'Agente de turnos que consulta disponibilidad, propone horarios, reserva y reprograma sobre datos persistidos.',
    route: '/demos/turnos' as Route,
    icon: Stethoscope,
    points: ['Function tools', 'Agenda viva', 'DB activity', 'Confirmable actions'],
    status: 'Workflow',
  },
  {
    title: 'Document intelligence workflow',
    eyebrow: 'PDF efimero',
    summary:
      'Carga un PDF, obtiene un resumen estructurado con IA y descarga el resultado sin conservar el archivo.',
    route: '/demos/documentos' as Route,
    icon: FileText,
    points: ['Input file', 'Store=false', 'Riesgos separados', 'TXT export'],
    status: 'Utilidad',
  },
]

function App() {
  const [route, setRoute] = useState<Route>(() => resolveRoute(window.location.pathname))
  const [theme, setTheme] = useState<Theme>(getInitialTheme)
  const pageTitle = useMemo(() => getPageTitle(route), [route])

  useEffect(() => {
    document.documentElement.dataset.theme = theme
    document.documentElement.style.colorScheme = theme
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  }, [theme])

  useEffect(() => {
    if (!routes.includes(window.location.pathname as Route)) {
      window.history.replaceState({}, '', '/')
    }

    const handlePopState = () => setRoute(resolveRoute(window.location.pathname))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  function navigate(path: Route) {
    window.history.pushState({}, '', path)
    setRoute(path)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function toggleTheme() {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
  }

  return (
    <div className="app-shell">
      <header className="nav">
        <PageLink className="brand" href="/" onNavigate={navigate} aria-label="Ir a inicio">
          <span>SG</span>
          <strong>Sebastian Gatica</strong>
        </PageLink>
        <div className="nav-actions">
          <nav aria-label="Navegacion principal">
            <PageLink href="/" onNavigate={navigate} active={route === '/'}>
              Inicio
            </PageLink>
            <PageLink
              href="/demos"
              onNavigate={navigate}
              active={route === '/demos' || route.startsWith('/demos') || route === '/demo'}
            >
              Demos
            </PageLink>
            <PageLink href="/contacto" onNavigate={navigate} active={route === '/contacto'}>
              Contacto
            </PageLink>
          </nav>
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
            title={theme === 'dark' ? 'Tema claro' : 'Tema oscuro'}
          >
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
      </header>

      <main className="page-shell" id="top">
        {route === '/' && <HomePage onNavigate={navigate} />}
        {route === '/demos' && <DemosPage onNavigate={navigate} />}
        {(route === '/demos/chat' || route === '/demo') && <AgentChatDemoPage title={pageTitle} />}
        {route === '/demos/turnos' && <MedicalAppointmentDemoPage />}
        {route === '/demos/documentos' && <DocumentDemoPage />}
        {route === '/contacto' && <ContactPage />}
      </main>

      <footer className="footer">
        <div>
          <strong>Sebastian Gatica</strong>
          <span>Portfolio interactivo de demos con IA aplicada.</span>
        </div>
        <div className="footer-links">
          <PageLink href="/" onNavigate={navigate}>
            <Sparkles size={18} />
            Inicio
          </PageLink>
          <PageLink href="/demos" onNavigate={navigate}>
            <Sparkles size={18} />
            Demos
          </PageLink>
          <PageLink href="/contacto" onNavigate={navigate}>
            <Mail size={18} />
            Contacto
          </PageLink>
          <a href={profileLinks.linkedin} target="_blank" rel="noreferrer">
            <Linkedin size={18} />
            LinkedIn
          </a>
          <a href={profileLinks.github} target="_blank" rel="noreferrer">
            <Github size={18} />
            GitHub
          </a>
        </div>
      </footer>
    </div>
  )
}

function HomePage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <>
      <section className="hero-band">
        <div className="hero-copy">
          <div className="section-kicker">
            <Sparkles size={18} />
            Sebastian Gatica
          </div>
          <h1>Java Full Stack + IA aplicada.</h1>
          <p className="hero-lead">
            Soy Sebastian Gatica. Desarrollo soluciones a medida con Spring Boot, React,
            OpenAI, voz realtime, APIs y flujos de negocio usables.
          </p>

          <div className="hero-actions">
            <PageLink className="button button-primary" href="/demos" onNavigate={onNavigate}>
              Ver demos
              <ArrowRight size={18} />
            </PageLink>
            <PageLink className="button button-secondary" href="/contacto" onNavigate={onNavigate}>
              Contacto
              <Mail size={18} />
            </PageLink>
          </div>

          <div className="stack-strip" aria-label="Stack principal">
            {stack.slice(0, 8).map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </div>

        <Atropos
          className="hero-tilt tilt-wrap"
          activeOffset={18}
          rotateXMax={4}
          rotateYMax={6}
          shadow={false}
          highlight={false}
          aria-label="Resumen del portfolio"
        >
          <div className="hero-visual">
            <div className="visual-header" data-atropos-offset="-1">
              <img src="/favicon.svg" alt="" />
              <div>
                <strong>SG AI portfolio</strong>
                <span>React, WebFlux, OpenAI y workflows auditables.</span>
              </div>
            </div>

            <div className="signal-grid" aria-label="Indicadores">
              <div className="metric" data-atropos-offset="2">
                <strong>3</strong>
                <span>Demos navegables</span>
              </div>
              <div className="metric" data-atropos-offset="3">
                <strong>SSE</strong>
                <span>Streaming real</span>
              </div>
              <div className="metric" data-atropos-offset="3">
                <strong>RT</strong>
                <span>Voz realtime</span>
              </div>
              <div className="metric" data-atropos-offset="2">
                <strong>PDF</strong>
                <span>Documentos efimeros</span>
              </div>
            </div>

            <div className="flow-lines" aria-label="Flujo tecnico" data-atropos-offset="4">
              <span>React</span>
              <ArrowRight size={16} />
              <span>WebFlux</span>
              <ArrowRight size={16} />
              <span>OpenAI</span>
            </div>

            <div className="runtime-mini-map" data-atropos-offset="3">
              {capabilityLayers.slice(0, 5).map((layer) => (
                <article key={layer.title}>
                  <layer.icon size={18} />
                  <strong>{layer.title}</strong>
                  <span>{layer.signal}</span>
                </article>
              ))}
            </div>

            <div className="hero-demo-list" data-atropos-offset="2">
              {demoCards.slice(0, 3).map((demo) => (
                <PageLink
                  className="hero-demo-link"
                  href={demo.route}
                  key={demo.title}
                  onNavigate={onNavigate}
                >
                  <demo.icon size={18} />
                  <span>{demo.title}</span>
                  <ArrowRight size={16} />
                </PageLink>
              ))}
            </div>
          </div>
        </Atropos>
      </section>

      <section className="home-demos-band">
        <SectionHeading
          kicker="Demos"
          title="Experiencias listas para probar, no solo capturas."
          text="Cada entrada muestra una capacidad concreta: chat integrado, toma de turnos con tools o analisis de documentos."
        />
        <DemoCardGrid demos={demoCards} onNavigate={onNavigate} />
      </section>

      <section className="skill-snapshot-band">
        <div className="skill-snapshot-copy">
          <div className="section-kicker">
            <ShieldCheck size={18} />
            Criterio tecnico
          </div>
          <h2>La calidad tambien se ve en pantalla.</h2>
          <p>
            El portfolio prioriza interaccion real, datos vivos, fallback y acciones seguras
            para que cada demo muestre criterio de producto, no solo una pantalla linda.
          </p>
        </div>

        <div className="skill-pill-grid">
          {qualityGates.map((gate) => (
            <article className="skill-pill" key={gate.title}>
              <gate.icon size={20} />
              <div>
                <strong>{gate.title}</strong>
                <span>{gate.detail}</span>
              </div>
            </article>
          ))}
        </div>
      </section>
    </>
  )
}

function DemosPage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <section className="section demos-page">
      <SectionHeading
        kicker="Demos"
        title="Tres experiencias para probar IA aplicada."
        text="La demo principal presenta el asistente del portfolio; las otras dos son casos aparte para turnos y documentos."
      />

      <DemoCardGrid demos={demoCards} onNavigate={onNavigate} />
      <QualityGateGrid />
    </section>
  )
}

function ArchitecturePage() {
  return (
    <section className="section architecture-page">
      <SectionHeading
        kicker="Arquitectura de demo"
        title="Capacidades simples, herramientas claras y experiencia primero."
        text="La investigacion queda convertida en una arquitectura de portfolio: un asistente que enruta a capacidades concretas y deja cada flujo listo para probar."
      />

      <RuntimeLayerGrid />

      <div className="architecture-contract-grid">
        <article>
          <ServerCog size={22} />
          <h3>Backend runner</h3>
          <p>Spring WebFlux abre streams, compone prompts, aplica limites y expone eventos SSE.</p>
        </article>
        <article>
          <Braces size={22} />
          <h3>Prompt contracts</h3>
          <p>Core, agentes y extensiones viven en archivos separados para evolucionar sin romper todo.</p>
        </article>
        <article>
          <Database size={22} />
          <h3>State seam</h3>
          <p>Sesion, contexto dinamico y actividad de base quedan listos para memory o vector store.</p>
        </article>
        <article>
          <Workflow size={22} />
          <h3>Workflow gates</h3>
          <p>Reservas, reprogramaciones y acciones sensibles tienen frontera de tool y confirmacion.</p>
        </article>
      </div>

      <section className="architecture-quality-section">
        <SectionHeading
          kicker="Calidad"
          title="Lo profesional no esta escondido en el pitch."
          text="Las demos muestran fallback, modo local, creditos, store=false y criterios de exito. Eso hace que el portfolio sea una muestra de ingenieria y no solo una pagina linda."
        />
        <QualityGateGrid />
      </section>
    </section>
  )
}

function DemoCardGrid({
  demos,
  onNavigate,
}: {
  demos: typeof demoCards
  onNavigate: (path: Route) => void
}) {
  return (
    <div className="demo-card-grid">
      {demos.map((demo) => (
        <PageLink
          className="demo-card demo-card-link"
          href={demo.route}
          onNavigate={onNavigate}
          aria-label={`Abrir ${demo.title}`}
          key={demo.title}
        >
          <div className="demo-card-top">
            <demo.icon size={24} />
            <span>{demo.status}</span>
          </div>
          <span className="demo-eyebrow">{demo.eyebrow}</span>
          <h3>{demo.title}</h3>
          <p>{demo.summary}</p>
          <ul>
            {demo.points.map((point) => (
              <li key={point}>
                <Check size={15} />
                {point}
              </li>
            ))}
          </ul>
          <span className="demo-card-action">
            Abrir demo
            <ArrowRight size={17} />
          </span>
        </PageLink>
      ))}
    </div>
  )
}

function RuntimeLayerGrid() {
  return (
    <div className="runtime-layer-grid">
      {capabilityLayers.map((layer) => (
        <article className="runtime-layer-card" key={layer.title}>
          <div>
            <layer.icon size={20} />
            <span>{layer.signal}</span>
          </div>
          <h3>{layer.title}</h3>
          <p>{layer.detail}</p>
        </article>
      ))}
    </div>
  )
}

function QualityGateGrid() {
  return (
    <div className="quality-gate-grid">
      {qualityGates.map((gate) => (
        <article className="quality-gate-card" key={gate.title}>
          <gate.icon size={20} />
          <span>{gate.metric}</span>
          <h3>{gate.title}</h3>
          <p>{gate.detail}</p>
        </article>
      ))}
    </div>
  )
}

function DemoPlaybookPanel({ playbookId }: { playbookId: keyof typeof demoPlaybooks }) {
  const playbook = demoPlaybooks[playbookId]

  return (
    <aside className="demo-playbook-card">
      <div className="demo-playbook-header">
        <BrainCircuit size={20} />
        <div>
          <span>Como se prueba</span>
          <strong>{playbook.agent}</strong>
        </div>
      </div>
      <dl>
        <div>
          <dt>Workflow</dt>
          <dd>{playbook.workflow}</dd>
        </div>
        <div>
          <dt>Tools</dt>
          <dd>{playbook.tools}</dd>
        </div>
        <div>
          <dt>State</dt>
          <dd>{playbook.state}</dd>
        </div>
        <div>
          <dt>Evaluacion</dt>
          <dd>{playbook.evaluation}</dd>
        </div>
      </dl>
    </aside>
  )
}

function AgentChatDemoPage({ title }: { title: string }) {
  return (
    <>
      <section className="page-intro">
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

function DocumentDemoPage() {
  return (
    <section className="section demo-detail-page">
      <SectionHeading
        kicker="Demo documentos"
        title="Document intelligence con tratamiento efimero."
        text="Subi un PDF, obtene un resumen con input_file y store=false, separando hechos, dudas y proximos pasos sin conservar el archivo."
      />

      <DemoPlaybookPanel playbookId="documents" />
      <DocumentSummaryDemo />
    </section>
  )
}

function MedicalAppointmentDemoPage() {
  return (
    <section className="section demo-detail-page">
      <SectionHeading
        kicker="Demo turnos"
        title="Workflow de turnos medicos con tools reales."
        text="La persona llama, pide un turno, el agente consulta disponibilidad, propone alternativas y deja la reserva visible en el calendario en tiempo real."
      />

      <DemoPlaybookPanel playbookId="appointments" />
      <MedicalAppointmentDemo />
    </section>
  )
}

function ContactPage() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    company: '',
    message: '',
  })
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [statusMessage, setStatusMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatus('submitting')
    setStatusMessage('')

    try {
      const response = await sendContactMessage(form)
      setStatus('success')
      setStatusMessage(response.message)
      setForm({
        name: '',
        email: '',
        company: '',
        message: '',
      })
    } catch (error) {
      setStatus('error')
      setStatusMessage(error instanceof Error ? error.message : 'No pude enviar el mensaje.')
    }
  }

  return (
    <section className="contact-page">
      <div className="contact-copy">
        <div className="section-kicker">
          <Mail size={18} />
          Contacto
        </div>
        <h1>Conversemos con Sebastian sobre una demo, integracion o MVP con IA.</h1>
        <p>
          El canal de email queda preparado desde el backend para contactar a Sebastian Gatica.
          La proxima iteracion conecta Mailtrap para envio real, testing de templates y trazabilidad.
        </p>

        <div className="contact-actions" aria-label="Canales de contacto">
          <a href={profileLinks.linkedin} target="_blank" rel="noreferrer">
            <Linkedin size={19} />
            LinkedIn
          </a>
          <a href={profileLinks.github} target="_blank" rel="noreferrer">
            <Github size={19} />
            GitHub
          </a>
        </div>
      </div>

      <form className="contact-form-panel" onSubmit={handleSubmit}>
        <div className="contact-form-header">
          <Send size={20} />
          <div>
            <span>Email integration</span>
            <strong>Mailtrap-ready</strong>
          </div>
        </div>

        <label>
          Nombre
          <input
            name="name"
            autoComplete="name"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            required
          />
        </label>

        <label>
          Email
          <input
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            required
          />
        </label>

        <label>
          Empresa
          <input
            name="company"
            autoComplete="organization"
            value={form.company}
            onChange={(event) => setForm({ ...form, company: event.target.value })}
          />
        </label>

        <label>
          Mensaje
          <textarea
            name="message"
            rows={5}
            value={form.message}
            onChange={(event) => setForm({ ...form, message: event.target.value })}
            required
          />
        </label>

        <button type="submit" disabled={status === 'submitting'}>
          <Send size={18} />
          {status === 'submitting' ? 'Enviando...' : 'Enviar mensaje'}
        </button>

        {statusMessage && (
          <p className={`contact-form-status contact-form-status-${status}`}>{statusMessage}</p>
        )}
      </form>
    </section>
  )
}

function PageLink({
  href,
  onNavigate,
  active,
  children,
  className,
  ...props
}: {
  href: Route
  onNavigate: (path: Route) => void
  active?: boolean
  children: ReactNode
  className?: string
} & Omit<AnchorHTMLAttributes<HTMLAnchorElement>, 'href' | 'onClick'>) {
  return (
    <a
      {...props}
      aria-current={active ? 'page' : undefined}
      className={className}
      href={href}
      onClick={(event) => {
        event.preventDefault()
        onNavigate(href)
      }}
    >
      {children}
    </a>
  )
}

function resolveRoute(pathname: string): Route {
  return routes.includes(pathname as Route) ? (pathname as Route) : '/'
}

function getInitialTheme(): Theme {
  const savedTheme = window.localStorage.getItem(THEME_STORAGE_KEY)
  if (savedTheme === 'light' || savedTheme === 'dark') {
    return savedTheme
  }

  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

function getPageTitle(route: Route) {
  if (route === '/') {
    return 'Inicio'
  }
  if (route === '/demos' || route === '/demo') {
    return 'Demos'
  }
  if (route === '/demos/chat') {
    return 'Demo principal'
  }
  if (route === '/demos/turnos') {
    return 'Demo turnos'
  }
  if (route === '/demos/documentos') {
    return 'Demo documentos'
  }
  if (route === '/contacto') {
    return 'Contacto'
  }
  return 'Inicio'
}

function SectionHeading({ kicker, title, text }: { kicker: string; title: string; text: string }) {
  return (
    <div className="section-heading">
      <span>{kicker}</span>
      <h2>{title}</h2>
      <p>{text}</p>
    </div>
  )
}

export default App
