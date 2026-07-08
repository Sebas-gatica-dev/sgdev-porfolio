import {
  browserSpeechSupported,
  getSpeechRecognitionConstructor,
  normalizeSpokenText,
  stripSpeechText,
} from '../shared/speech'

export type CallStatus = 'idle' | 'ringing' | 'connecting' | 'live' | 'error'
export type CallMessageRole = 'system' | 'assistant' | 'user'

export { browserSpeechSupported, getSpeechRecognitionConstructor, normalizeSpokenText }

export function parseToolArguments(value?: string) {
  if (!value?.trim()) {
    return {} as Record<string, unknown>
  }
  try {
    return JSON.parse(value) as Record<string, unknown>
  } catch {
    return {} as Record<string, unknown>
  }
}

export function stringArg(value: unknown) {
  return typeof value === 'string' ? value : ''
}

export function optionalStringArg(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : undefined
}

export function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

export function friendlyCallError(error: unknown) {
  if (error instanceof DOMException && ['NotAllowedError', 'PermissionDeniedError'].includes(error.name)) {
    return 'Chrome bloqueo el microfono. Permitilo desde el icono del candado/barra de direcciones y volve a intentar.'
  }

  if (error instanceof DOMException && error.name === 'NotFoundError') {
    return 'No encontre un microfono disponible para iniciar la simulacion.'
  }

  if (error instanceof Error && /permission denied|notallowed/i.test(error.message)) {
    return 'Chrome bloqueo el microfono. Permitilo desde el icono del candado/barra de direcciones y volve a intentar.'
  }

  return error instanceof Error ? error.message : 'No se pudo iniciar la simulacion.'
}

export function callStatusLabel(status: CallStatus) {
  if (status === 'ringing') {
    return 'Llamando'
  }
  if (status === 'connecting') {
    return 'Conectando'
  }
  if (status === 'live') {
    return 'En simulacion'
  }
  if (status === 'error') {
    return 'Error'
  }
  return 'Lista'
}

export function callMessageLabel(role: CallMessageRole) {
  if (role === 'assistant') {
    return 'Agente'
  }
  if (role === 'user') {
    return 'Vos'
  }
  return 'Demo'
}

export function formatWeekday(value: string) {
  return new Intl.DateTimeFormat('es-AR', { weekday: 'short' }).format(new Date(`${value}T12:00:00`))
}

export function formatShortDate(value: string) {
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
  }).format(new Date(`${value}T12:00:00`))
}

export function formatTime(value: string) {
  return value.slice(11, 16)
}

export function prepareSpeechText(value: string) {
  return normalizeSpokenText(
    stripSpeechText(value)
      .replace(
        /\b(\d{4})-(\d{2})-(\d{2})\s+(\d{1,2}):(\d{2})\b/g,
        (_, year: string, month: string, day: string, hour: string, minute: string) =>
          `${dateForSpeech(year, month, day)} a las ${timeForSpeech(hour, minute)}`,
      )
      .replace(
        /\b(\d{4})-(\d{2})-(\d{2})(\d{1,2}):(\d{2})\b/g,
        (_, year: string, month: string, day: string, hour: string, minute: string) =>
          `${dateForSpeech(year, month, day)} a las ${timeForSpeech(hour, minute)}`,
      )
      .replace(
        /\b(\d{4})-(\d{2})-(\d{2})\b/g,
        (_, year: string, month: string, day: string) => dateForSpeech(year, month, day),
      )
      .replace(
        /\b(\d{1,2}):(\d{2})\b/g,
        (_, hour: string, minute: string) => timeForSpeech(hour, minute),
      )
      .replace(/[\[\]{}()]/g, ' ')
      .replace(/[|*_#>]/g, ' ')
      .replace(/\s*[-\u2013\u2014]\s*/g, ' ')
      .replace(/(\p{L})(\d)/gu, '$1 $2')
      .replace(/(\d)(\p{L})/gu, '$1 $2'),
  )
}

function dateForSpeech(year: string, month: string, day: string) {
  const date = new Date(`${year}-${month}-${day}T12:00:00`)
  if (Number.isNaN(date.getTime())) {
    return `${day}/${month}/${year}`
  }
  return new Intl.DateTimeFormat('es-AR', {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  }).format(date)
}

function timeForSpeech(hourValue: string, minuteValue: string) {
  const hour = Number.parseInt(hourValue, 10)
  const minute = Number.parseInt(minuteValue, 10)
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return `${hourValue}:${minuteValue}`
  }
  const spokenHour = hour % 12 === 0 ? 12 : hour % 12
  const period = hour === 12 ? 'del mediodia' : hour < 12 ? 'de la mañana' : 'de la tarde'
  if (minute === 0) {
    return `${spokenHour} ${period}`
  }
  if (minute === 30) {
    return `${spokenHour} y media ${period}`
  }
  return `${spokenHour} y ${minute} ${period}`
}
