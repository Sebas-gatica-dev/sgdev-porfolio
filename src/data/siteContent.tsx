import {
  BrainCircuit,
  Braces,
  Briefcase,
  CalendarDays,
  Code2,
  Coffee,
  FileText,
  Mail,
  MessageSquareText,
  ServerCog,
  Sparkles,
  Stethoscope,
  Target,
  Workflow,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { Route } from '../app/routing'
import { OpenClawIcon } from '../components/OpenClawIcon'

type IconComponent = LucideIcon | typeof OpenClawIcon

export const primaryNavItems: Array<{ href: Route; label: string; icon: IconComponent }> = [
  { href: '/', label: 'Inicio', icon: Sparkles },
  { href: '/demos', label: 'Demos', icon: MessageSquareText },
  { href: '/contacto', label: 'Contacto', icon: Mail },
]

export const profileLinks = {
  linkedin: 'https://ar.linkedin.com/in/sebastian-gatica-dev',
  github: 'https://github.com/Sebas-gatica-dev',
}

export const professionalProfile = [
  'Cuento con 3 años de experiencia construyendo aplicaciones robustas y escalables en entornos empresariales y proyectos freelance.',
  'Me especializo en el desarrollo backend con Java y Spring Boot, y en la creación de interfaces modernas y performantes con ReactJS y Next.js.',
  'Además, diseño e implemento flujos multiagente con Google ADK para automatizar procesos y potenciar soluciones inteligentes sobre infraestructura de microservicios.',
]

export const professionalCapabilities = [
  {
    title: 'Backend y frontend',
    text: 'APIs, servicios Spring Boot, interfaces React y experiencias web orientadas a producto.',
    icon: Braces,
  },
  {
    title: 'Agentes de IA',
    text: 'Creación de agentes personalizados en OpenClaw, orquestación multi agente y funcionalidades a medida.',
    icon: BrainCircuit,
  },
  {
    title: 'Infraestructura',
    text: 'Administración de VPS, Nginx, Docker, bases de datos y despliegue de modelos LLM open source.',
    icon: ServerCog,
  },
  {
    title: 'Integración continua',
    text: 'Flujos CI/CD con Terraform, GitHub Actions y múltiples gestores de versión.',
    icon: Workflow,
  },
]

export const professionalStats = [
  { value: '3+ años', label: 'Experiencia', icon: CalendarDays },
  { value: 'Proyectos', label: 'Empresariales y freelance', icon: Briefcase },
  { value: 'Enfoque', label: 'Calidad, escalabilidad e IA', icon: Target },
]

export const professionalStackRows = [
  {
    title: 'Java + Spring Boot',
    text: 'APIs RESTful, arquitectura por capas y microservicios listos para producción.',
    icon: Coffee,
    tags: ['Java', 'Spring Boot'],
  },
  {
    title: 'ReactJS + Next.js',
    text: 'Interfaces modernas, componentes reutilizables y experiencias rápidas y optimizadas.',
    icon: Code2,
    tags: ['React', 'Next.js'],
  },
  {
    title: 'Multiagentes con Google ADK',
    text: 'Diseño de flujos multiagente para automatizar tareas y resolver problemas complejos.',
    icon: BrainCircuit,
    tags: ['ADK'],
  },
  {
    title: 'OpenClaw',
    text: 'Creación y personalización de agentes con herramientas, memoria y orquestación a medida.',
    icon: OpenClawIcon,
    tags: ['OpenClaw'],
  },
]

export const demoCards = [
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
