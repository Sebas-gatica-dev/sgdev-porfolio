import {
  AudioLines,
  CalendarDays,
  CheckCircle2,
  CircleHelp,
  HeartPulse,
  LoaderCircle,
  MicOff,
  PhoneCall,
  RefreshCcw,
  Stethoscope,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'
import {
  bookAppointment,
  createAppointmentVoiceSession,
  findAvailableAppointments,
  getAppointmentSchedule,
  getPortfolioHealth,
  rescheduleAppointment,
  streamAppointmentFreeResponse,
  type AppointmentToolEvent,
  type AppointmentConsultationType,
  type AppointmentSchedule,
  type ChatRuntime,
} from '../api/agentClient'
import {
  browserSpeechSupported,
  callMessageLabel,
  callStatusLabel,
  type CallMessageRole,
  type CallStatus,
  delay,
  formatShortDate,
  formatTime,
  formatWeekday,
  friendlyCallError,
  getSpeechRecognitionConstructor,
  normalizeSpokenText,
  optionalStringArg,
  parseToolArguments,
  stringArg,
} from './appointments/medicalAppointmentUtils'

type ConsultationOption = {
  id: AppointmentConsultationType
  title: string
  label: string
  doctor: string
  icon: LucideIcon
}

type CallMessage = {
  id: string
  role: CallMessageRole
  content: string
}

type FunctionCallItem = {
  type?: string
  name?: string
  call_id?: string
  arguments?: string
}

const consultationOptions: ConsultationOption[] = [
  {
    id: 'traumatology',
    title: 'Consulta con traumatologo',
    label: 'Traumatologo',
    doctor: 'Dr. Hernan Varela',
    icon: Stethoscope,
  },
  {
    id: 'follow-up',
    title: 'Consulta de control',
    label: 'Control',
    doctor: 'Dra. Paula Mendez',
    icon: CalendarDays,
  },
  {
    id: 'cardiology',
    title: 'Consulta con cardiologo',
    label: 'Cardiologo',
    doctor: 'Dr. Tomas Ibarra',
    icon: HeartPulse,
  },
]

const VOICE_DEMO_LIMIT_MS = 60_000
const openAiLogoSrc = `${import.meta.env.BASE_URL}openai-logo.svg`
const qwenLogoSrc = `${import.meta.env.BASE_URL}qwen-logo.svg`

export function MedicalAppointmentDemo() {
  const [selectedConsultationId, setSelectedConsultationId] =
    useState<AppointmentConsultationType>('traumatology')
  const [voiceConfigured, setVoiceConfigured] = useState<boolean | null>(null)
  const [openAiVoiceAvailable, setOpenAiVoiceAvailable] = useState<boolean | null>(null)
  const [openAiVoiceTokenCost, setOpenAiVoiceTokenCost] = useState(10)
  const [qwenConfigured, setQwenConfigured] = useState<boolean | null>(null)
  const [qwenModel, setQwenModel] = useState('qwen3:0.6b')
  const [callRuntime, setCallRuntime] = useState<ChatRuntime>('openai')
  const [status, setStatus] = useState<CallStatus>('idle')
  const [model, setModel] = useState<string | null>(null)
  const [liveTranscript, setLiveTranscript] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [schedule, setSchedule] = useState<AppointmentSchedule | null>(null)
  const [scheduleError, setScheduleError] = useState<string | null>(null)
  const [availabilityChecked, setAvailabilityChecked] = useState(false)
  const [appointmentBooked, setAppointmentBooked] = useState(false)
  const [appointmentRescheduled, setAppointmentRescheduled] = useState(false)
  const [messages, setMessages] = useState<CallMessage[]>([
    {
      id: 'intro',
      role: 'system',
      content:
        'Elegí una consulta y pedile un turno al agente. Probá incluso con un horario ocupado para ver cómo negocia alternativas.',
    },
  ])

  const sessionIdRef = useRef<string>(crypto.randomUUID())
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const dataChannelRef = useRef<RTCDataChannel | null>(null)
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null)
  const browserRecognitionRef = useRef<SpeechRecognition | null>(null)
  const callLogRef = useRef<HTMLDivElement | null>(null)
  const browserCallActiveRef = useRef(false)
  const browserRestartTimeoutRef = useRef<number | null>(null)
  const freeTurnPendingRef = useRef(false)
  const assistantMessageIdRef = useRef<string | null>(null)
  const processedUserItemsRef = useRef<Set<string>>(new Set())
  const processedToolCallsRef = useRef<Set<string>>(new Set())
  const demoLimitTimeoutRef = useRef<number | null>(null)

  const selectedConsultation = useMemo(
    () =>
      consultationOptions.find((consultation) => consultation.id === selectedConsultationId) ||
      consultationOptions[0],
    [selectedConsultationId],
  )
  const callActive = ['ringing', 'connecting', 'live'].includes(status)
  const browserSpeechAvailable = browserSpeechSupported()

  useEffect(() => {
    getPortfolioHealth()
      .then((health) => {
        setVoiceConfigured(health.voiceConfigured)
        setOpenAiVoiceAvailable(health.openaiVoiceAvailable)
        setOpenAiVoiceTokenCost(health.openaiVoiceTokenCost || health.openaiVoiceCreditCost || 10)
        setQwenConfigured(health.freeModelConfigured)
        setQwenModel(health.freeModelName || 'qwen3:0.6b')
        if (!health.openaiVoiceAvailable || !health.voiceConfigured) {
          setCallRuntime('free')
        }
      })
      .catch(() => {
        setVoiceConfigured(false)
        setOpenAiVoiceAvailable(false)
        setQwenConfigured(false)
        setCallRuntime('free')
        setError('No pude leer el estado del backend; Qwen usara fallback local si esta disponible.')
      })

    void refreshPanels()

    return () => {
      cleanupCall()
    }
  }, [])

  useEffect(() => {
    if (!callLogRef.current) {
      return
    }
    callLogRef.current.scrollTop = callLogRef.current.scrollHeight
  }, [messages, liveTranscript])

  async function refreshPanels() {
    try {
      const nextSchedule = await getAppointmentSchedule(sessionIdRef.current)
      setSchedule(nextSchedule)
      setScheduleError(null)
    } catch (panelError) {
      setScheduleError(
        panelError instanceof Error
          ? panelError.message
          : 'No pude cargar la agenda de la demo.',
      )
    }
  }

  async function toggleCall() {
    if (callActive) {
      stopCall()
      return
    }
    if (callRuntime === 'free') {
      startFreeCall()
      return
    }
    await startOpenAiCall()
  }

  async function startOpenAiCall() {
    if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === 'undefined') {
      setStatus('error')
      setError('Este navegador no permite audio WebRTC desde esta pagina.')
      return
    }

    if (voiceConfigured === false || openAiVoiceAvailable === false) {
      setCallRuntime('free')
      startFreeCall()
      return
    }

    cleanupCall()
    setError(null)
    setLiveTranscript('')
    setStatus('ringing')
    setAvailabilityChecked(false)
    setAppointmentBooked(false)
    setAppointmentRescheduled(false)
    assistantMessageIdRef.current = null
    processedUserItemsRef.current = new Set()
    processedToolCallsRef.current = new Set()
    setMessages([
      {
        id: crypto.randomUUID(),
        role: 'system',
        content: `Llamada visual simulada: ${selectedConsultation.title}. La agenda visible se actualiza desde base de datos.`,
      },
    ])

    await delay(700)
    setStatus('connecting')

    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      mediaStreamRef.current = mediaStream

      const session = await createAppointmentVoiceSession(selectedConsultation.id)
      setModel(session.model)
      scheduleDemoLimit()

      const peerConnection = new RTCPeerConnection()
      peerConnectionRef.current = peerConnection
      mediaStream.getAudioTracks().forEach((track) => peerConnection.addTrack(track, mediaStream))

      peerConnection.addEventListener('track', (event) => {
        const [remoteStream] = event.streams
        if (!remoteStream || !remoteAudioRef.current) {
          return
        }
        remoteAudioRef.current.srcObject = remoteStream
        remoteAudioRef.current.play().catch(() => undefined)
      })

      const dataChannel = peerConnection.createDataChannel('oai-events')
      dataChannelRef.current = dataChannel
      dataChannel.addEventListener('open', () => {
        setStatus('live')
        dataChannel.send(
          JSON.stringify({
            type: 'response.create',
            response: {
              instructions: `El usuario inicio una llamada para "${selectedConsultation.title}". Saluda, confirma el tipo de consulta y preguntale que disponibilidad horaria tiene.`,
            },
          }),
        )
      })
      dataChannel.addEventListener('message', handleRealtimeEvent)

      peerConnection.addEventListener('connectionstatechange', () => {
        if (peerConnection.connectionState === 'connected') {
          setStatus('live')
        }
        if (['failed', 'disconnected'].includes(peerConnection.connectionState)) {
          setStatus('error')
          setError('Se corto la simulacion. Podes iniciarla otra vez.')
          cleanupCall()
        }
      })

      const offer = await peerConnection.createOffer()
      await peerConnection.setLocalDescription(offer)

      const sdpResponse = await fetch(session.realtimeUrl, {
        method: 'POST',
        body: offer.sdp || '',
        headers: {
          Authorization: `Bearer ${session.clientSecret}`,
          'Content-Type': 'application/sdp',
        },
      })

      if (!sdpResponse.ok) {
        throw new Error(`OpenAI Realtime rechazo la simulacion (${sdpResponse.status})`)
      }

      await peerConnection.setRemoteDescription({
        type: 'answer',
        sdp: await sdpResponse.text(),
      })
    } catch (callError) {
      cleanupCall()
      setStatus('error')
      setError(friendlyCallError(callError))
    }
  }

  function startFreeCall() {
    const SpeechRecognition = getSpeechRecognitionConstructor()
    if (!SpeechRecognition) {
      setStatus('error')
      setError(
        'Este navegador no ofrece Web Speech para la llamada gratuita. Proba Chrome, Edge o Safari.',
      )
      return
    }

    cleanupCall()
    browserCallActiveRef.current = true
    freeTurnPendingRef.current = false
    setCallRuntime('free')
    setError(null)
    setLiveTranscript('')
    setStatus('ringing')
    setModel(`${qwenModel} + Web Speech`)
    setAvailabilityChecked(false)
    setAppointmentBooked(false)
    setAppointmentRescheduled(false)
    assistantMessageIdRef.current = null
    processedUserItemsRef.current = new Set()
    processedToolCallsRef.current = new Set()
    setMessages([
      {
        id: crypto.randomUUID(),
        role: 'system',
        content: `Llamada gratuita: ${selectedConsultation.title}. Web Speech escucha, Qwen responde y las tools actualizan la agenda real.`,
      },
    ])

    window.setTimeout(() => {
      if (!browserCallActiveRef.current) {
        return
      }
      setStatus('live')
      startFreeListening()
    }, 450)
  }

  function startFreeListening() {
    if (!browserCallActiveRef.current || freeTurnPendingRef.current) {
      return
    }

    const SpeechRecognition = getSpeechRecognitionConstructor()
    if (!SpeechRecognition) {
      setStatus('error')
      setError('Web Speech dejo de estar disponible en este navegador.')
      return
    }

    const recognition = new SpeechRecognition()
    browserRecognitionRef.current = recognition
    recognition.lang = 'es-AR'
    recognition.continuous = false
    recognition.interimResults = true
    recognition.maxAlternatives = 1

    let finalTranscript = ''
    let latestTranscript = ''

    recognition.onresult = (event) => {
      let interimTranscript = ''
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index]
        const transcript = result[0]?.transcript || ''
        if (result.isFinal) {
          finalTranscript = [finalTranscript, transcript].filter(Boolean).join(' ')
        } else {
          interimTranscript = [interimTranscript, transcript].filter(Boolean).join(' ')
        }
      }

      latestTranscript = normalizeSpokenText(
        [finalTranscript, interimTranscript].filter(Boolean).join(' '),
      )
      setLiveTranscript(latestTranscript)
    }

    recognition.onerror = (event) => {
      if (event.error === 'no-speech') {
        return
      }
      setStatus('error')
      setError(`La llamada gratuita se pauso (${event.error}).`)
      cleanupBrowserCall()
    }

    recognition.onend = () => {
      if (!browserCallActiveRef.current || browserRecognitionRef.current !== recognition) {
        return
      }

      browserRecognitionRef.current = null
      const clean = normalizeSpokenText(finalTranscript || latestTranscript)
      if (clean) {
        setLiveTranscript('')
        appendUserMessage(`free-${crypto.randomUUID()}`, clean)
        void submitFreeAppointmentTurn(clean)
        return
      }

      restartFreeListening(600)
    }

    try {
      recognition.start()
      setStatus('live')
    } catch (speechError) {
      setStatus('error')
      setError(
        speechError instanceof Error
          ? speechError.message
          : 'No se pudo escuchar con Web Speech.',
      )
    }
  }

  async function submitFreeAppointmentTurn(prompt: string) {
    if (!browserCallActiveRef.current || !prompt.trim()) {
      return
    }

    freeTurnPendingRef.current = true
    const assistantId = crypto.randomUUID()
    let assistantText = ''
    setMessages((current) => [
      ...current,
      { id: assistantId, role: 'assistant', content: '' },
    ])

    try {
      await streamAppointmentFreeResponse(
        {
          message: prompt,
          sessionId: sessionIdRef.current,
          consultationType: selectedConsultation.id,
        },
        {
          onTool: handleFreeAppointmentTool,
          onChunk: (text) => {
            assistantText += text
            appendAssistantChunk(assistantId, text)
          },
          onDone: () => {
            void refreshPanels()
          },
        },
      )
    } catch (freeError) {
      assistantText = freeError instanceof Error ? freeError.message : 'No se pudo responder con Qwen.'
      appendAssistantChunk(assistantId, `No pude responder con Qwen: ${assistantText}`)
    } finally {
      freeTurnPendingRef.current = false
      void refreshPanels()
    }

    speakFreeAppointmentReply(assistantText)
  }

  function handleFreeAppointmentTool(tool: AppointmentToolEvent) {
    if (tool.action === 'availability') {
      setAvailabilityChecked(true)
    }
    if (tool.action === 'book') {
      setAvailabilityChecked(true)
      setAppointmentBooked(true)
    }
    if (tool.action === 'reschedule') {
      setAvailabilityChecked(true)
      setAppointmentBooked(true)
      setAppointmentRescheduled(true)
    }
  }

  function appendAssistantChunk(assistantId: string, text: string) {
    setMessages((current) =>
      current.map((message) =>
        message.id === assistantId
          ? { ...message, content: `${message.content}${text}` }
          : message,
      ),
    )
  }

  function speakFreeAppointmentReply(text: string) {
    if (!browserCallActiveRef.current) {
      return
    }

    if (!('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
      setError('Tu navegador no tiene text-to-speech local; sigo respondiendo por texto.')
      restartFreeListening(600)
      return
    }

    const speechText = prepareSpeechText(text)
    if (!speechText) {
      restartFreeListening(350)
      return
    }

    const utterance = new SpeechSynthesisUtterance(speechText)
    utterance.lang = 'es-AR'
    utterance.rate = 1
    utterance.pitch = 1
    utterance.onend = () => restartFreeListening(350)
    utterance.onerror = () => restartFreeListening(350)

    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
  }

  function stopCall() {
    cleanupCall()
    setStatus('idle')
    setLiveTranscript('')
    assistantMessageIdRef.current = null
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'system', content: 'Simulacion finalizada.' },
    ])
  }

  function scheduleDemoLimit() {
    clearDemoLimit()
    demoLimitTimeoutRef.current = window.setTimeout(() => {
      demoLimitTimeoutRef.current = null
      cleanupCall()
      setStatus('idle')
      setLiveTranscript('')
      assistantMessageIdRef.current = null
      setError(`Se cumplio el minuto de demo de voz. Esta sesion consumio ${openAiVoiceTokenCost} tokens.`)
      setMessages((current) => [
        ...current,
        {
          id: crypto.randomUUID(),
          role: 'system',
          content: 'Se cumplio el minuto de demo de voz. La llamada se cerro automaticamente.',
        },
      ])
    }, VOICE_DEMO_LIMIT_MS)
  }

  function clearDemoLimit() {
    if (demoLimitTimeoutRef.current !== null) {
      window.clearTimeout(demoLimitTimeoutRef.current)
      demoLimitTimeoutRef.current = null
    }
  }

  function handleRealtimeEvent(event: MessageEvent<string>) {
    let payload: {
      type?: string
      item_id?: string
      delta?: string
      transcript?: string
      error?: { message?: string }
      response?: { output?: FunctionCallItem[] }
    }

    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }

    if (payload.type === 'session.created') {
      setStatus('live')
      return
    }

    if (payload.type === 'conversation.item.input_audio_transcription.delta' && payload.delta) {
      setLiveTranscript((current) => `${current}${payload.delta}`.replace(/\s+/g, ' '))
      return
    }

    if (
      payload.type === 'conversation.item.input_audio_transcription.completed' &&
      payload.transcript?.trim()
    ) {
      appendUserMessage(payload.item_id || crypto.randomUUID(), payload.transcript)
      setLiveTranscript('')
      return
    }

    if (
      ['response.output_audio_transcript.delta', 'response.output_text.delta'].includes(
        payload.type || '',
      ) &&
      payload.delta
    ) {
      appendAssistantDelta(payload.delta)
      return
    }

    if (payload.type === 'response.done') {
      assistantMessageIdRef.current = null
      const functionCalls = payload.response?.output?.filter(
        (item) => item.type === 'function_call' && item.call_id && item.name,
      )
      functionCalls?.forEach((item) => {
        void handleFunctionCall(item)
      })
      return
    }

    if (
      ['response.output_audio_transcript.done', 'response.output_text.done'].includes(
        payload.type || '',
      )
    ) {
      assistantMessageIdRef.current = null
      return
    }

    if (payload.type === 'error') {
      setStatus('error')
      setError(payload.error?.message || 'OpenAI devolvio un error durante la simulacion.')
      cleanupCall()
    }
  }

  async function handleFunctionCall(item: FunctionCallItem) {
    if (!item.call_id || !item.name || processedToolCallsRef.current.has(item.call_id)) {
      return
    }
    processedToolCallsRef.current.add(item.call_id)

    const args = parseToolArguments(item.arguments)
    let output: unknown

    try {
      if (item.name === 'find_available_appointments') {
        output = await findAvailableAppointments({
          sessionId: sessionIdRef.current,
          consultationType: selectedConsultation.id,
          dateFrom: stringArg(args.date_from),
          dateTo: stringArg(args.date_to),
          preferredTimeFrom: optionalStringArg(args.preferred_time_from),
          preferredTimeTo: optionalStringArg(args.preferred_time_to),
        })
        setAvailabilityChecked(true)
      } else if (item.name === 'book_appointment') {
        output = await bookAppointment({
          sessionId: sessionIdRef.current,
          consultationType: selectedConsultation.id,
          patientName: stringArg(args.patient_name),
          startAt: stringArg(args.start_at),
        })
        setAppointmentBooked(true)
      } else if (item.name === 'reschedule_current_appointment') {
        output = await rescheduleAppointment({
          sessionId: sessionIdRef.current,
          startAt: stringArg(args.start_at),
        })
        setAppointmentRescheduled(true)
      } else {
        output = { error: `Herramienta no soportada: ${item.name}` }
      }
    } catch (toolError) {
      output = {
        error: toolError instanceof Error ? toolError.message : 'No se pudo ejecutar la herramienta.',
      }
    }

    await refreshPanels()
    dataChannelRef.current?.send(
      JSON.stringify({
        type: 'conversation.item.create',
        item: {
          type: 'function_call_output',
          call_id: item.call_id,
          output: JSON.stringify(output),
        },
      }),
    )
    dataChannelRef.current?.send(JSON.stringify({ type: 'response.create' }))
  }

  function appendUserMessage(itemId: string, transcript: string) {
    const clean = transcript.trim()
    if (!clean || processedUserItemsRef.current.has(itemId)) {
      return
    }

    processedUserItemsRef.current.add(itemId)
    setMessages((current) => [...current, { id: crypto.randomUUID(), role: 'user', content: clean }])
  }

  function appendAssistantDelta(delta: string) {
    const assistantId = assistantMessageIdRef.current
    if (!assistantId) {
      const newAssistantId = crypto.randomUUID()
      assistantMessageIdRef.current = newAssistantId
      setMessages((current) => [
        ...current,
        { id: newAssistantId, role: 'assistant', content: delta },
      ])
      return
    }

    setMessages((current) =>
      current.map((message) =>
        message.id === assistantId
          ? { ...message, content: `${message.content}${delta}` }
          : message,
      ),
    )
  }

  function cleanupCall() {
    clearDemoLimit()
    cleanupBrowserCall()

    dataChannelRef.current?.removeEventListener('message', handleRealtimeEvent)
    dataChannelRef.current?.close()
    dataChannelRef.current = null

    peerConnectionRef.current?.getSenders().forEach((sender) => sender.track?.stop())
    peerConnectionRef.current?.close()
    peerConnectionRef.current = null

    mediaStreamRef.current?.getTracks().forEach((track) => track.stop())
    mediaStreamRef.current = null

    if (remoteAudioRef.current) {
      remoteAudioRef.current.pause()
      remoteAudioRef.current.srcObject = null
    }
  }

  function cleanupBrowserCall() {
    if (browserRestartTimeoutRef.current !== null) {
      window.clearTimeout(browserRestartTimeoutRef.current)
      browserRestartTimeoutRef.current = null
    }

    browserCallActiveRef.current = false
    freeTurnPendingRef.current = false

    const recognition = browserRecognitionRef.current
    browserRecognitionRef.current = null
    if (recognition) {
      recognition.onend = null
      recognition.onerror = null
      recognition.onresult = null
      try {
        recognition.abort()
      } catch {
        // Some browsers throw if recognition was already stopped.
      }
    }

    if ('speechSynthesis' in window) {
      window.speechSynthesis.cancel()
    }
  }

  function restartFreeListening(delayMs: number) {
    if (browserRestartTimeoutRef.current !== null) {
      window.clearTimeout(browserRestartTimeoutRef.current)
    }

    browserRestartTimeoutRef.current = window.setTimeout(() => {
      browserRestartTimeoutRef.current = null
      startFreeListening()
    }, delayMs)
  }

  return (
    <div className="appointment-demo-shell">
      <div className="appointment-demo-grid">
        <article className={`demo-preview-panel appointment-call-panel appointment-call-${status}`}>
          <div className="voice-call-top">
            <div className="voice-orb">
              {status === 'connecting' || status === 'ringing' ? (
                <LoaderCircle className="spin" size={34} />
              ) : (
                <PhoneCall size={34} />
              )}
            </div>
            <div className="appointment-call-tools">
              <span className={`call-status call-status-${status}`}>{callStatusLabel(status)}</span>
              <div className="appointment-flow-help">
                <button
                  type="button"
                  aria-label="Ver flujo guiado"
                  aria-describedby="appointment-flow-tooltip"
                >
                  <CircleHelp size={18} />
                </button>
                <div
                  className="appointment-flow-tooltip appointment-steps-card"
                  id="appointment-flow-tooltip"
                  role="tooltip"
                >
                  <div className="appointment-panel-heading">
                    <CheckCircle2 size={18} />
                    <strong>Flujo guiado</strong>
                  </div>
                  <ol>
                    <li data-done="true">
                      <span>1</span>
                      <div>
                        <strong>Especialidad elegida</strong>
                        <p>{selectedConsultation.title}</p>
                      </div>
                    </li>
                    <li data-done={callActive || appointmentBooked}>
                      <span>2</span>
                      <div>
                        <strong>Llamada iniciada</strong>
                        <p>El agente toma la disponibilidad del paciente.</p>
                      </div>
                    </li>
                    <li data-done={availabilityChecked || appointmentBooked}>
                      <span>3</span>
                      <div>
                        <strong>Agenda consultada</strong>
                        <p>Busca turnos reales antes de responder.</p>
                      </div>
                    </li>
                    <li data-done={appointmentBooked}>
                      <span>4</span>
                      <div>
                        <strong>Turno persistido</strong>
                        <p>La reserva aparece en el calendario en tiempo real.</p>
                      </div>
                    </li>
                    <li data-done={appointmentRescheduled}>
                      <span>5</span>
                      <div>
                        <strong>Reprogramacion</strong>
                        <p>Si cambia de opinion, actualiza el mismo turno.</p>
                      </div>
                    </li>
                  </ol>
                </div>
              </div>
            </div>
          </div>

          <h3>Agente de reserva de turnos</h3>
          <p>
            El agente conversa, consulta la agenda real de demo, propone alternativas y actualiza la
            reserva dentro de la misma llamada.
          </p>

          <div className="appointment-consultations" role="group" aria-label="Tipos de consulta">
            {consultationOptions.map((consultation) => {
              const Icon = consultation.icon
              return (
                <button
                  type="button"
                  key={consultation.id}
                  onClick={() => setSelectedConsultationId(consultation.id)}
                  aria-pressed={consultation.id === selectedConsultationId}
                  disabled={callActive}
                  className="appointment-consultation-button"
                >
                  <Icon size={17} />
                  <strong>{consultation.label}</strong>
                  <span>{consultation.doctor}</span>
                </button>
              )
            })}
          </div>

          <div className="call-controls">
            <div
              className="appointment-runtime-toggle runtime-provider-toggle"
              role="radiogroup"
              aria-label="Proveedor de la llamada"
            >
              <button
                type="button"
                role="radio"
                aria-checked={callRuntime === 'openai'}
                className={callRuntime === 'openai' ? 'runtime-provider-active' : undefined}
                onClick={() => setCallRuntime('openai')}
                disabled={callActive || voiceConfigured === false || openAiVoiceAvailable === false}
                title={
                  openAiVoiceAvailable === false || voiceConfigured === false
                    ? 'OpenAI Realtime no esta disponible; usa Qwen.'
                    : 'Usar OpenAI Realtime'
                }
              >
                <span className="runtime-provider-logo-frame">
                  <img src={openAiLogoSrc} alt="" aria-hidden="true" />
                </span>
                OpenAI
              </button>
              <button
                type="button"
                role="radio"
                aria-checked={callRuntime === 'free'}
                className={callRuntime === 'free' ? 'runtime-provider-active' : undefined}
                onClick={() => setCallRuntime('free')}
                disabled={callActive}
                title={
                  browserSpeechAvailable
                    ? qwenConfigured === false
                      ? 'Qwen local no esta activo; usara fallback demo con Web Speech.'
                      : `Usar ${qwenModel} con Web Speech`
                    : 'Este navegador no soporta Web Speech.'
                }
              >
                <span className="runtime-provider-logo-frame">
                  <img src={qwenLogoSrc} alt="" aria-hidden="true" />
                </span>
                Qwen
              </button>
            </div>
            <button
              type="button"
              className="call-action"
              onClick={toggleCall}
              disabled={
                status === 'connecting' ||
                (callRuntime === 'openai' &&
                  (voiceConfigured === false || openAiVoiceAvailable === false))
              }
            >
              {status === 'connecting' || status === 'ringing' ? (
                <LoaderCircle className="spin" size={18} />
              ) : callActive ? (
                <MicOff size={18} />
              ) : (
                <PhoneCall size={18} />
              )}
              {callActive
                ? 'Cortar simulacion'
                : callRuntime === 'free'
                  ? 'Iniciar llamada gratis'
                  : 'Iniciar llamada'}
            </button>
            <span className="call-model-pill">
              Modelo: {callRuntime === 'free' ? `${qwenModel} + Web Speech` : model || 'gpt-realtime-mini'}
            </span>
          </div>

          {error && <p className="call-error">{error}</p>}

          <div className="call-log" ref={callLogRef} aria-live="polite">
            {messages.map((message) => (
              <div className={`call-message call-message-${message.role}`} key={message.id}>
                <strong>{callMessageLabel(message.role)}</strong>
                <span>
                  {message.role === 'assistant' && !message.content.trim() ? (
                    <span className="call-agent-thinking" role="status">
                      <LoaderCircle className="spin" size={16} />
                      Procesando respuesta...
                    </span>
                  ) : (
                    message.content
                  )}
                </span>
              </div>
            ))}
            {liveTranscript && (
              <div className="call-message call-message-user call-message-live">
                <strong>
                  <AudioLines size={14} />
                  Vos
                </strong>
                <span>{liveTranscript}</span>
              </div>
            )}
          </div>
        </article>
      </div>

      <section className="appointment-calendar-card">
        <div className="appointment-calendar-header">
          <div>
            <CalendarDays size={18} />
            <strong>Agenda de los proximos 15 dias</strong>
          </div>
          <span>
            Lun-vie · 08:00-13:00 · 14:00-18:00
            <RefreshCcw size={14} />
          </span>
        </div>

        {scheduleError ? (
          <div className="appointment-error-copy">
            <p>{scheduleError}</p>
            <button type="button" onClick={() => void refreshPanels()}>
              Reintentar
            </button>
          </div>
        ) : !schedule ? (
          <p className="appointment-empty-copy">Cargando agenda...</p>
        ) : (
          <div className="appointment-calendar-strip">
            {schedule.days.map((day) => (
              <article
                className={`appointment-day-card${day.workingDay ? '' : ' appointment-day-closed'}`}
                key={day.date}
              >
                <header>
                  <strong>{formatWeekday(day.date)}</strong>
                  <span>{formatShortDate(day.date)}</span>
                </header>
                {!day.workingDay ? (
                  <p>Sin atencion</p>
                ) : day.appointments.length === 0 ? (
                  <p>Sin turnos ocupados</p>
                ) : (
                  <div className="appointment-slot-list">
                    {day.appointments.map((appointment) => (
                      <div
                        className={`appointment-slot appointment-slot-${appointment.doctorId}${
                          appointment.fromCurrentSession ? ' appointment-slot-current' : ''
                        }`}
                        key={appointment.id}
                      >
                        <strong>{formatTime(appointment.startAt)}</strong>
                        <span>{appointment.specialty}</span>
                      </div>
                    ))}
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>

      <audio ref={remoteAudioRef} autoPlay playsInline className="conversation-audio" />
    </div>
  )
}

function prepareSpeechText(value: string) {
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
      .replace(/\s*[-–—]\s*/g, ' ')
      .replace(/(\p{L})(\d)/gu, '$1 $2')
      .replace(/(\d)(\p{L})/gu, '$1 $2'),
  )
}

function stripSpeechText(value: string) {
  return normalizeSpokenText(
    value
      .replace(/```[\s\S]*?```/g, ' ')
      .replace(/`([^`]+)`/g, '$1')
      .replace(/^#{1,6}\s*/gm, '')
      .replace(/^\s*[-*]\s+/gm, '')
      .replace(/\*\*|__/g, '')
      .replace(/\[(.*?)\]\(.*?\)/g, '$1'),
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
