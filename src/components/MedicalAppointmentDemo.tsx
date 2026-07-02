import {
  AudioLines,
  CalendarDays,
  CheckCircle2,
  Database,
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
  getAppointmentActivity,
  getAppointmentSchedule,
  getPortfolioHealth,
  rescheduleAppointment,
  type AppointmentActivity,
  type AppointmentConsultationType,
  type AppointmentSchedule,
} from '../api/agentClient'

type CallStatus = 'idle' | 'ringing' | 'connecting' | 'live' | 'error'

type ConsultationOption = {
  id: AppointmentConsultationType
  title: string
  label: string
  doctor: string
  icon: LucideIcon
}

type CallMessage = {
  id: string
  role: 'system' | 'assistant' | 'user'
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

export function MedicalAppointmentDemo() {
  const [selectedConsultationId, setSelectedConsultationId] =
    useState<AppointmentConsultationType>('traumatology')
  const [voiceConfigured, setVoiceConfigured] = useState<boolean | null>(null)
  const [status, setStatus] = useState<CallStatus>('idle')
  const [model, setModel] = useState<string | null>(null)
  const [liveTranscript, setLiveTranscript] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [schedule, setSchedule] = useState<AppointmentSchedule | null>(null)
  const [scheduleError, setScheduleError] = useState<string | null>(null)
  const [activity, setActivity] = useState<AppointmentActivity[]>([])
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

  useEffect(() => {
    getPortfolioHealth()
      .then((health) => {
        setVoiceConfigured(health.voiceConfigured)
        if (!health.voiceConfigured) {
          setStatus('error')
          setError('Configura OPENAI_API_KEY en el backend para activar esta demo de voz.')
        }
      })
      .catch(() => {
        setVoiceConfigured(false)
        setStatus('error')
        setError('No pude leer el estado del backend de voz.')
      })

    void refreshPanels()

    return () => {
      cleanupCall()
    }
  }, [])

  async function refreshPanels() {
    try {
      const [nextSchedule, nextActivity] = await Promise.all([
        getAppointmentSchedule(sessionIdRef.current),
        getAppointmentActivity(sessionIdRef.current),
      ])
      setSchedule(nextSchedule)
      setActivity(nextActivity)
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
    await startCall()
  }

  async function startCall() {
    if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === 'undefined') {
      setStatus('error')
      setError('Este navegador no permite audio WebRTC desde esta pagina.')
      return
    }

    if (voiceConfigured === false) {
      setStatus('error')
      setError('Configura OPENAI_API_KEY en el backend para activar esta demo de voz.')
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
      setError('Se cumplio el minuto de demo de voz. Esta sesion consumio 5 creditos.')
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
            <span className={`call-status call-status-${status}`}>{callStatusLabel(status)}</span>
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
            <button
              type="button"
              className="call-action"
              onClick={toggleCall}
              disabled={voiceConfigured === false || status === 'connecting'}
            >
              {status === 'connecting' || status === 'ringing' ? (
                <LoaderCircle className="spin" size={18} />
              ) : callActive ? (
                <MicOff size={18} />
              ) : (
                <PhoneCall size={18} />
              )}
              {callActive ? 'Cortar simulacion' : 'Iniciar llamada'}
            </button>
            <span className="call-model-pill">Modelo mini: {model || 'gpt-realtime-mini'}</span>
          </div>

          {error && <p className="call-error">{error}</p>}

          <div className="call-log" aria-live="polite">
            {messages.map((message) => (
              <div className={`call-message call-message-${message.role}`} key={message.id}>
                <strong>{callMessageLabel(message.role)}</strong>
                <span>{message.content}</span>
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

        <section className="appointment-steps-card">
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
        </section>

        <section className="appointment-activity-card">
          <div className="appointment-panel-heading">
            <Database size={18} />
            <strong>Actividad en base de datos</strong>
          </div>
          {activity.length === 0 ? (
            <p className="appointment-empty-copy">
              Todavia no hubo operaciones en esta llamada. Cuando el agente consulte o guarde,
              apareceran aca.
            </p>
          ) : (
            <div className="appointment-activity-list">
              {activity.map((item) => (
                <article key={`${item.createdAt}-${item.action}-${item.detail}`}>
                  <span>{item.action}</span>
                  <strong>{item.detail}</strong>
                  <time>{formatDateTime(item.createdAt)}</time>
                </article>
              ))}
            </div>
          )}
        </section>
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

function parseToolArguments(value?: string) {
  if (!value?.trim()) {
    return {} as Record<string, unknown>
  }
  try {
    return JSON.parse(value) as Record<string, unknown>
  } catch {
    return {} as Record<string, unknown>
  }
}

function stringArg(value: unknown) {
  return typeof value === 'string' ? value : ''
}

function optionalStringArg(value: unknown) {
  return typeof value === 'string' && value.trim() ? value : undefined
}

function delay(ms: number) {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}

function friendlyCallError(error: unknown) {
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

function callStatusLabel(status: CallStatus) {
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

function callMessageLabel(role: CallMessage['role']) {
  if (role === 'assistant') {
    return 'Agente'
  }
  if (role === 'user') {
    return 'Vos'
  }
  return 'Demo'
}

function formatWeekday(value: string) {
  return new Intl.DateTimeFormat('es-AR', { weekday: 'short' }).format(new Date(`${value}T12:00:00`))
}

function formatShortDate(value: string) {
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: 'short',
  }).format(new Date(`${value}T12:00:00`))
}

function formatTime(value: string) {
  return value.slice(11, 16)
}

function formatDateTime(value: string) {
  return `${formatShortDate(value.slice(0, 10))} · ${formatTime(value)}`
}
