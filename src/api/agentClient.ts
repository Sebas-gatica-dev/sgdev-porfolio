export type AgentRoute = {
  id: string
  name: string
  reason: string
}

export type AgentTrace = {
  label: string
  detail: string
  status: 'running' | 'connected' | 'fallback' | 'done'
}

export type ChatRuntime = 'openai' | 'free'

export type FreeModelOffer = {
  enabled: boolean
  runtime: ChatRuntime
  model: string
  title: string
  message: string
}

export type PromptLimitStatus = {
  enabled: boolean
  allowed: boolean
  used: number
  remaining: number
  maxPrompts: number
}

export type VoiceSession = {
  clientSecret: string
  expiresAt: number
  model: string
  realtimeUrl: string
}

export type AppointmentConsultationType = 'traumatology' | 'follow-up' | 'cardiology'

export type AppointmentDoctor = {
  id: string
  consultationType: AppointmentConsultationType
  name: string
  specialty: string
}

export type AppointmentEntry = {
  id: string
  doctorId: string
  consultationType: AppointmentConsultationType
  doctorName: string
  specialty: string
  patientName: string
  startAt: string
  endAt: string
  fromCurrentSession: boolean
}

export type AppointmentCalendarDay = {
  date: string
  workingDay: boolean
  appointments: AppointmentEntry[]
}

export type AppointmentSchedule = {
  doctors: AppointmentDoctor[]
  days: AppointmentCalendarDay[]
  workdayStart: string
  lunchStart: string
  lunchEnd: string
  workdayEnd: string
}

export type AppointmentActivity = {
  action: 'SELECT' | 'INSERT' | 'UPDATE' | string
  detail: string
  createdAt: string
}

export type AppointmentSlotSuggestion = {
  doctorId: string
  doctorName: string
  specialty: string
  startAt: string
  endAt: string
}

export type AppointmentAvailabilityResponse = {
  consultationType: AppointmentConsultationType
  requestedSlotStatus: string
  requestedSlotReason: string
  availableSlots: AppointmentSlotSuggestion[]
}

export type AppointmentMutationResponse = {
  status: 'BOOKED' | 'RESCHEDULED' | string
  appointment: AppointmentEntry
}

export type PortfolioHealth = {
  ok: boolean
  mode: string
  openaiConfigured: boolean
  freeModelConfigured: boolean
  freeModelName: string
  voiceConfigured: boolean
  promptLimitEnabled: boolean
  promptLimitUsed: number
  promptLimitRemaining: number
  promptLimitMaxPrompts: number
  openaiPromptAvailable: boolean
  openaiVoiceAvailable: boolean
  openaiVoiceCreditCost: number
}

export type DocumentSummaryResponse = {
  fileName: string
  sizeBytes: number
  maxSizeBytes: number
  model: string
  ephemeral: boolean
  summary: string
}

export type DynamicContextRequest = {
  type: 'http_get' | 'time_now' | 'params_echo'
  name?: string
  url?: string
  params?: Record<string, string>
  timeoutMs?: number
}

export type ContactMessagePayload = {
  name: string
  email: string
  company?: string
  message: string
}

export type ContactMessageResponse = {
  status: string
  message: string
  mailtrapReady: boolean
}

type StreamHandlers = {
  onSession?: (sessionId: string) => void
  onAgent?: (agent: AgentRoute) => void
  onTrace?: (trace: AgentTrace) => void
  onFreeModelOffer?: (offer: FreeModelOffer) => void
  onPromptLimit?: (status: PromptLimitStatus) => void
  onChunk?: (text: string) => void
  onDone?: (payload: { sessionId: string; live: boolean }) => void
}

const API_BASE_PATH = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`

function apiPath(path: string) {
  return `${API_BASE_PATH}${path.startsWith('/') ? path : `/${path}`}`
}

export async function streamAgentResponse(
  payload: {
    message: string
    sessionId?: string | null
    agentId?: string
    runtime?: ChatRuntime
    extensions?: string[]
    dynamicContext?: DynamicContextRequest[]
  },
  handlers: StreamHandlers,
) {
  const response = await fetch(apiPath('/agent/chat/stream'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok || !response.body) {
    throw new Error(await responseError(response, `No se pudo abrir el stream (${response.status})`))
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (value) {
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split(/\n\n/)
      buffer = events.pop() || ''

      for (const eventBlock of events) {
        dispatchSseEvent(eventBlock, handlers)
      }
    }

    if (done) {
      if (buffer.trim()) {
        dispatchSseEvent(buffer, handlers)
      }
      break
    }
  }
}

export async function createVoiceTranscriptionSession(): Promise<VoiceSession> {
  const response = await fetch(apiPath('/agent/voice/session'), {
    method: 'POST',
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo activar voz (${response.status})`))
  }

  return response.json()
}

export async function createVoiceConversationSession(): Promise<VoiceSession> {
  const response = await fetch(apiPath('/agent/conversation/session'), {
    method: 'POST',
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo activar conversacion (${response.status})`))
  }

  return response.json()
}

export async function createAppointmentVoiceSession(
  consultationType: AppointmentConsultationType,
): Promise<VoiceSession> {
  const response = await fetch(apiPath('/agent/appointment/session'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ consultationType }),
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo iniciar la demo de turnos (${response.status})`))
  }

  return response.json()
}

export async function summarizePdf(file: File): Promise<DocumentSummaryResponse> {
  const response = await fetch(apiPath('/agent/document/summary'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/pdf',
      'X-File-Name': encodeURIComponent(file.name),
    },
    body: file,
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo resumir el PDF (${response.status})`))
  }

  return response.json()
}

export async function getAppointmentSchedule(
  sessionId: string,
  days = 15,
): Promise<AppointmentSchedule> {
  const params = new URLSearchParams({
    sessionId,
    days: String(days),
  })
  const response = await fetch(apiPath(`/appointments/demo/schedule?${params}`))

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo leer la agenda (${response.status})`))
  }

  return response.json()
}

export async function getAppointmentActivity(
  sessionId: string,
  limit = 12,
): Promise<AppointmentActivity[]> {
  const params = new URLSearchParams({
    sessionId,
    limit: String(limit),
  })
  const response = await fetch(apiPath(`/appointments/demo/activity?${params}`))

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo leer la actividad (${response.status})`))
  }

  return response.json()
}

export async function findAvailableAppointments(payload: {
  sessionId: string
  consultationType: AppointmentConsultationType
  dateFrom: string
  dateTo: string
  preferredTimeFrom?: string
  preferredTimeTo?: string
}): Promise<AppointmentAvailabilityResponse> {
  const response = await fetch(apiPath('/appointments/demo/tools/availability'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo consultar disponibilidad (${response.status})`))
  }

  return response.json()
}

export async function bookAppointment(payload: {
  sessionId: string
  consultationType: AppointmentConsultationType
  patientName: string
  startAt: string
}): Promise<AppointmentMutationResponse> {
  const response = await fetch(apiPath('/appointments/demo/tools/book'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo guardar el turno (${response.status})`))
  }

  return response.json()
}

export async function rescheduleAppointment(payload: {
  sessionId: string
  startAt: string
}): Promise<AppointmentMutationResponse> {
  const response = await fetch(apiPath('/appointments/demo/tools/reschedule'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo reprogramar el turno (${response.status})`))
  }

  return response.json()
}

export async function getPortfolioHealth(): Promise<PortfolioHealth> {
  const response = await fetch(apiPath('/portfolio/health'))

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo leer estado (${response.status})`))
  }

  return response.json()
}

export async function sendContactMessage(
  payload: ContactMessagePayload,
): Promise<ContactMessageResponse> {
  const response = await fetch(apiPath('/contact/message'), {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok) {
    throw new Error(await responseError(response, `No se pudo enviar el mensaje (${response.status})`))
  }

  return response.json()
}

async function responseError(response: Response, fallback: string) {
  const body = await response.text().catch(() => '')
  if (!body.trim()) {
    return fallback
  }

  try {
    const payload = JSON.parse(body)
    return payload.message || payload.error || payload.detail || fallback
  } catch {
    return body.slice(0, 240)
  }
}

function dispatchSseEvent(block: string, handlers: StreamHandlers) {
  const eventLine = block.split(/\n/).find((line) => line.startsWith('event:'))
  const dataLine = block.split(/\n/).find((line) => line.startsWith('data:'))
  const event = eventLine?.replace('event:', '').trim()
  const data = dataLine?.replace('data:', '').trim()

  if (!event || !data) {
    return
  }

  const payload = JSON.parse(data)

  if (event === 'session') {
    handlers.onSession?.(payload.sessionId)
  }

  if (event === 'agent') {
    handlers.onAgent?.(payload)
  }

  if (event === 'trace') {
    handlers.onTrace?.(payload)
  }

  if (event === 'free_model_offer') {
    handlers.onFreeModelOffer?.(payload)
  }

  if (event === 'prompt_limit') {
    handlers.onPromptLimit?.(payload)
  }

  if (event === 'chunk') {
    handlers.onChunk?.(payload.text)
  }

  if (event === 'done') {
    handlers.onDone?.(payload)
  }
}
