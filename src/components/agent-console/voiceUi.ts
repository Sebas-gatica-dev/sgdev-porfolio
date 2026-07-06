import type { ChatRuntime } from '../../api/agentClient'

export type VoiceStatus = 'idle' | 'connecting' | 'listening' | 'error'

export function voiceLabel(status: VoiceStatus) {
  if (status === 'connecting') {
    return 'Conectando microfono'
  }
  if (status === 'listening') {
    return 'Escuchando'
  }
  if (status === 'error') {
    return 'Voz pausada'
  }
  return 'Modo voz listo'
}

export function conversationLabel(status: VoiceStatus) {
  if (status === 'connecting') {
    return 'Conectando conversacion'
  }
  if (status === 'listening') {
    return 'Conversacion activa'
  }
  if (status === 'error') {
    return 'Conversacion pausada'
  }
  return 'Modo conversacion listo'
}

export function audioLabel(voiceStatus: VoiceStatus, conversationStatus: VoiceStatus) {
  if (conversationStatus === 'connecting' || conversationStatus === 'listening') {
    return conversationLabel(conversationStatus)
  }
  if (voiceStatus === 'connecting' || voiceStatus === 'listening') {
    return voiceLabel(voiceStatus)
  }
  if (conversationStatus === 'error') {
    return conversationLabel(conversationStatus)
  }
  return voiceLabel(voiceStatus)
}

export function audioPanelClass(voiceStatus: VoiceStatus, conversationStatus: VoiceStatus) {
  if (conversationStatus === 'listening') {
    return 'voice-panel-conversation'
  }
  if (conversationStatus === 'connecting') {
    return 'voice-panel-connecting'
  }
  return `voice-panel-${voiceStatus}`
}

export function formatVoiceAllowance(seconds: number) {
  const safeSeconds = Math.max(0, seconds || 0)
  const minutes = Math.floor(safeSeconds / 60)
  const remainingSeconds = safeSeconds % 60
  if (remainingSeconds === 0) {
    return `${minutes} min`
  }
  return `${minutes}:${String(remainingSeconds).padStart(2, '0')} min`
}

export function openAiProviderTitle(openAiConfigured: boolean | null, openAiCreditsExhausted: boolean) {
  if (openAiConfigured === false) {
    return 'OPENAI_API_KEY no esta disponible; se usa Qwen.'
  }
  if (openAiConfigured === null) {
    return 'Chequeando disponibilidad de OpenAI.'
  }
  if (openAiCreditsExhausted) {
    return 'Tokens OpenAI agotados para esta IP. Usa Qwen o solicita mas tokens.'
  }
  return 'Usar OpenAI'
}

export function voiceButtonTitle(openAiVoiceReady: boolean, browserSpeechAvailable: boolean) {
  if (openAiVoiceReady) {
    return 'Dictado OpenAI Realtime: consume tokens y tiempo de voz.'
  }
  if (browserSpeechAvailable) {
    return 'Dictado gratuito del navegador: no consume OpenAI.'
  }
  return 'Este navegador no soporta dictado gratuito; proba Chrome, Edge o Safari.'
}

export function conversationButtonTitle(openAiVoiceReady: boolean, browserSpeechAvailable: boolean) {
  if (openAiVoiceReady) {
    return 'Conversacion OpenAI Realtime: consume tokens y tiempo de voz.'
  }
  if (browserSpeechAvailable) {
    return 'Conversacion gratuita por turnos: navegador + Qwen + voz local.'
  }
  return 'Este navegador no soporta dictado gratuito; Qwen sigue disponible por texto.'
}

export function audioDetail({
  voiceConfigured,
  voiceModel,
  voiceStatus,
  conversationModel,
  conversationStatus,
  chatRuntime,
  qwenModel,
  openAiVoiceAvailable,
  openAiVoiceCreditCost,
  browserSpeechAvailable,
}: {
  voiceConfigured: boolean | null
  voiceModel: string | null
  voiceStatus: VoiceStatus
  conversationModel: string | null
  conversationStatus: VoiceStatus
  chatRuntime: ChatRuntime
  qwenModel: string
  openAiVoiceAvailable: boolean | null
  openAiVoiceCreditCost: number
  browserSpeechAvailable: boolean
}) {
  if (conversationStatus === 'connecting' || conversationStatus === 'listening') {
    return conversationModel
      ? `Modelo conversacion: ${conversationModel}`
      : 'Modelo conversacion: gpt-realtime-mini'
  }
  if (voiceStatus === 'connecting' || voiceStatus === 'listening') {
    return voiceModel ? `Modelo dictado: ${voiceModel}` : 'Modelo dictado: gpt-4o-mini-transcribe'
  }
  if (chatRuntime === 'free') {
    return browserSpeechAvailable
      ? `Modo gratuito: dictado del navegador, respuestas ${qwenModel} y voz local.`
      : `Qwen (${qwenModel}) esta disponible por texto; este navegador no ofrece dictado gratis.`
  }
  if (openAiVoiceAvailable === false) {
    return browserSpeechAvailable
      ? `OpenAI voz requiere ${openAiVoiceCreditCost} tokens y tiempo disponible; se usa modo gratuito del navegador.`
      : `OpenAI voz requiere ${openAiVoiceCreditCost} tokens y este navegador no ofrece dictado gratis.`
  }
  if (voiceConfigured === false) {
    return browserSpeechAvailable
      ? 'OpenAI Realtime no esta configurado; voz gratuita disponible desde el navegador.'
      : 'OpenAI Realtime no esta configurado y este navegador no ofrece dictado gratuito.'
  }
  return 'Dictado transcribe texto; Conversar responde con voz. Solo uno puede estar activo.'
}
