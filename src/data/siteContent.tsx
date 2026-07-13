import {
  BrainCircuit,
  Braces,
  Briefcase,
  CalendarDays,
  Code2,
  Coffee,
  Database,
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

type IconComponent = LucideIcon

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
  'Cuento con más de 2 años de experiencia profesional construyendo aplicaciones empresariales, CRMs a medida, integraciones y despliegues sobre VPS Linux.',
  'Mi base técnica combina Java, Spring Boot, Spring MVC, Spring Data JPA, WebFlux, Reactor, R2DBC, APIs REST, PostgreSQL/pgvector, MySQL y MariaDB.',
  'También desarrollo interfaces con React, Next.js, TypeScript, Vite y TailwindCSS, y diseño flujos de IA aplicada con Google ADK, multiagentes, prompts, skills y RAG.',
]

export const professionalCapabilities = [
  {
    title: 'Backend y frontend',
    text: 'APIs REST, microservicios Spring Boot, migraciones legacy y frontends React/Next.js orientados a producto.',
    icon: Braces,
  },
  {
    title: 'Agentes de IA',
    text: 'Google ADK, Spring AI, prompts, skills, RAG y flujos multiagente para automatizar procesos reales.',
    icon: BrainCircuit,
  },
  {
    title: 'Infraestructura',
    text: 'VPS Linux, Nginx, Docker, bases de datos, AWS S3/EC2, Google Cloud y despliegues productivos.',
    icon: ServerCog,
  },
  {
    title: 'Integración continua',
    text: 'GitHub/GitLab CI/CD, Terraform, control de versiones y entregas por hitos con feedback técnico.',
    icon: Workflow,
  },
]

export const professionalStats = [
  { value: '+2 años', label: 'Experiencia profesional', icon: CalendarDays },
  { value: '3 etapas', label: 'Bank, Emplag y CFOTECH', icon: Briefcase },
  { value: 'Enfoque', label: 'Calidad, escalabilidad e IA', icon: Target },
]

export const professionalStackRows = [
  {
    title: 'Java + Spring Boot',
    text: 'Spring MVC, Data JPA, WebFlux, Reactor, R2DBC, APIs REST y microservicios listos para producción.',
    icon: Coffee,
    tags: ['Java', 'Spring Boot', 'WebFlux'],
  },
  {
    title: 'ReactJS + Next.js',
    text: 'Interfaces modernas con React, Next.js, TypeScript, Vite, TailwindCSS y componentes reutilizables.',
    icon: Code2,
    tags: ['React', 'Next.js', 'TypeScript'],
  },
  {
    title: 'Multiagentes con Google ADK',
    text: 'Diseño de flujos multiagente, prompts, skills y RAG para automatizar procesos de negocio.',
    icon: BrainCircuit,
    tags: ['Google ADK', 'RAG', 'Spring AI'],
  },
  {
    title: 'Infra y datos',
    text: 'PostgreSQL/pgvector, MySQL, MariaDB, Docker, Nginx, VPS Linux, AWS S3/EC2 y Google Cloud.',
    icon: Database,
    tags: ['Docker', 'Nginx', 'AWS'],
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
