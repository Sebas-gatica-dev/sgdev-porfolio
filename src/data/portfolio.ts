import {
  Blocks,
  Bot,
  BrainCircuit,
  ClipboardCheck,
  Cloud,
  Code2,
  Database,
  Github,
  Mic2,
  ShieldCheck,
  Workflow,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export type ProductModule = {
  title: string
  subtitle: string
  summary: string
  icon: LucideIcon
  points: string[]
}

export type WorkStep = {
  title: string
  detail: string
}

export type RuntimeLayer = {
  title: string
  detail: string
  signal: string
  icon: LucideIcon
}

export type QualityGate = {
  title: string
  detail: string
  metric: string
  icon: LucideIcon
}

export type DemoPlaybook = {
  agent: string
  workflow: string
  tools: string
  state: string
  evaluation: string
}

export const stack = [
  'Java / Spring Boot',
  'Spring MVC / JPA',
  'WebFlux / Reactor / R2DBC',
  'React / Next.js',
  'TypeScript / Vite',
  'Google ADK',
  'Spring AI / RAG',
  'PostgreSQL / pgvector',
  'MySQL / MariaDB',
  'Docker / Nginx',
  'Linux VPS',
  'AWS / Google Cloud',
]

export const capabilityLayers: RuntimeLayer[] = [
  {
    title: 'Asistente IA',
    detail: 'Chat y voz para explicar perfil, experiencia, demos, stack y enfoque profesional desde datos del CV.',
    signal: 'Flujo Multiagente',
    icon: Bot,
  },
  {
    title: 'Demos enfocadas',
    detail: 'Turnos, documentos y portfolio separados para que cada caso se pueda probar sin ruido.',
    signal: 'Casos reales',
    icon: BrainCircuit,
  },
  {
    title: 'Integraciones',
    detail: 'APIs, agenda, PDF, voz realtime, datos persistidos y contexto dinámico conectados al backend.',
    signal: 'Tools reales',
    icon: Blocks,
  },
  {
    title: 'Estado vivo',
    detail: 'Sesion, agenda y actividad persistida para que la demo responda a lo que pasa.',
    signal: 'Datos visibles',
    icon: Database,
  },
  {
    title: 'Acciones seguras',
    detail: 'Confirmaciones y limites antes de ejecutar cambios con impacto real.',
    signal: 'Control humano',
    icon: ShieldCheck,
  },
  {
    title: 'Eval loop',
    detail: 'Cada demo muestra criterio de exito, fallback y observabilidad operacional.',
    signal: 'Quality loop',
    icon: ClipboardCheck,
  },
]

export const qualityGates: QualityGate[] = [
  {
    title: 'Requerimientos claros',
    detail: 'Detectar la necesidad, entender el proceso y usar software e IA solo donde aporta una mejora medible.',
    metric: 'Demo-first UX',
    icon: Workflow,
  },
  {
    title: 'Criterio de IA',
    detail: 'Aumento de productividad del desarrollo potenciado con IA, y criterio de calidad e iteracion escalable.',
    metric: 'Confiable',
    icon: Code2,
  },
  {
    title: 'Seguridad y control',
    detail: 'Buenas prácticas de control, confirmaciones humanas, trazabilidad y criterios básicos de seguridad para producción.',
    metric: 'Human-in-loop',
    icon: ShieldCheck,
  },
  {
    title: 'Deploy cloud',
    detail: 'Despliegue en VPS o servicio en la nube, integración continua y despliegue automatizado.',
    metric: 'Production-minded',
    icon: Cloud,
  },
]

export const demoPlaybooks: Record<string, DemoPlaybook> = {
  chat: {
    agent: 'Portfolio assistant + especialista seleccionable',
    workflow: 'Mensaje -> prompt CV-backed del portfolio -> streaming response -> trace event',
    tools: 'Dynamic context, extensions, Responses API, fallback local',
    state: 'Session id, créditos por IP, contexto del turno',
    evaluation: 'Respuesta accionable, experiencia laboral precisa, ruta correcta, trazas comprensibles',
  },
  appointments: {
    agent: 'Medical appointment workflow specialist',
    workflow: 'Consulta -> disponibilidad -> propuesta -> reserva/reprogramacion',
    tools: 'Agenda viva, disponibilidad, book, reschedule',
    state: 'Sesion de llamada, turno activo, actividad de base de datos',
    evaluation: 'No diagnostica, confirma acciones, calendario se actualiza en persistencia, en tiempo real',
  },
  documents: {
    agent: 'Document intelligence specialist',
    workflow: 'PDF analist -> resumen estructurado -> descarga el reporte',
    tools: 'Responses API con input_file, max 10 MB, store=false',
    state: 'Archivo solo en request, metadata de resumen',
    evaluation: 'Hechos del documento, dudas separadas, sin inventar contexto',
  },
}

export const productModules: ProductModule[] = [
  {
    title: 'Repo-aware agent chat',
    subtitle: 'Chat con contexto de repositorio',
    summary:
      'Un asistente que entiende un proyecto, explica arquitectura, detecta deuda técnica y propone cambios con criterio Java/Spring, React y DevOps.',
    icon: Github,
    points: ['Conectores GitHub/GitLab', 'RAG sobre código y docs', 'Streaming SSE', 'Sesión e historial'],
  },
  {
    title: 'Workflow automation copilot',
    subtitle: 'Procesos, datos y aprobaciones',
    summary:
      'Un flujo para clasificar solicitudes, consultar datos permitidos, proponer acciones y pedir aprobación humana antes de ejecutar integraciones o cambios persistentes.',
    icon: Workflow,
    points: ['Integraciones API', 'Reglas configurables', 'Historial auditable', 'Validación humana'],
  },
  {
    title: 'LLM utility layer',
    subtitle: 'Modulos reutilizables',
    summary:
      'Resumen, clasificación, extracción, reformulación y asistencia contextual como piezas reutilizables para procesos internos.',
    icon: Blocks,
    points: ['Prompts versionados', 'Tools por dominio', 'Guardrails', 'Métricas de uso'],
  },
]

export const architectureNodes = [
  { title: 'Portfolio UI', detail: 'React, consola agente, casos de uso y captura comercial.', icon: Code2 },
  { title: 'Agent API', detail: 'Backend propio que orquesta sesiones, SSE y fallback local.', icon: Workflow },
  { title: 'Coordinator', detail: 'Router de intenciones para perfil CV, repo, workflows, turnos y utilidades.', icon: Bot },
  { title: 'Tool layer', detail: 'APIs, agenda, base vectorial, CRM, documentos y servicios del cliente.', icon: Mic2 },
  { title: 'Human gate', detail: 'Feedback, revisiones, aprobación y transparencia antes de ejecutar.', icon: ShieldCheck },
  { title: 'Cloud deploy', detail: 'Docker primero; Cloud Run/GitHub Actions/Terraform como camino natural.', icon: Cloud },
]

export const workModel: WorkStep[] = [
  {
    title: '1. Discovery corto',
    detail: 'Entiendo el negocio, el repositorio, los datos disponibles y el riesgo real antes de prometer alcance.',
  },
  {
    title: '2. Prototipo navegable',
    detail: 'Primero muestro una experiencia que se puede usar, discutir y corregir. Nada de esperar semanas a ciegas.',
  },
  {
    title: '3. Integracion por hitos',
    detail: 'Contrato por objetivos: módulo, integración, demo, hardening y deploy. Las horas son referencia, no la unidad de valor.',
  },
  {
    title: '4. Feedback semanal',
    detail: 'Reuniones breves para validar direccion, ajustar prompts, revisar outputs y evitar construir algo que no encaja.',
  },
]

export const servicePacks = [
  {
    name: 'AI App Audit',
    time: '1 semana',
    value: 'Mapa técnico, riesgos, oportunidades LLM y backlog priorizado.',
  },
  {
    name: 'Agent MVP',
    time: '2 a 4 semanas',
    value: 'Chat o workflow multiagente funcional con API, memoria basica y demo deployable.',
  },
]
