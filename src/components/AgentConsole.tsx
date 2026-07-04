import { FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react'
import {
  AudioLines,
  Bot,
  CheckCircle2,
  Clipboard,
  Cpu,
  LoaderCircle,
  Mic,
  MicOff,
  MessageSquare,
  PhoneCall,
  Send,
  Sparkles,
  TerminalSquare,
  X,
} from 'lucide-react'
import {
  ChatRuntime,
  createVoiceConversationSession,
  createVoiceTranscriptionSession,
  FreeModelOffer,
  getPortfolioHealth,
  streamAgentResponse,
} from '../api/agentClient'

type Message = {
  id: string
  role: 'user' | 'assistant'
  content: string
}

type VoiceStatus = 'idle' | 'connecting' | 'listening' | 'error'

const VOICE_DEMO_LIMIT_MS = 60_000

const portfolioFollowUps = [
  {
    label: 'Habilidades',
    prompt: 'Contame cuales son las habilidades principales de Sebastian Gatica como desarrollador.',
  },
  {
    label: 'Experiencia laboral',
    prompt: 'Resumime la experiencia laboral de Sebastian Gatica y en que tipo de proyectos trabajo.',
  },
  {
    label: 'Aptitudes',
    prompt: 'Que aptitudes profesionales y blandas destacarías de Sebastian Gatica?',
  },
  {
    label: 'Stack tecnico',
    prompt: 'Cual es el stack tecnico de Sebastian Gatica y donde tiene mas fortaleza?',
  },
  {
    label: 'Demos',
    prompt: 'Que demos puedo probar en este portfolio y que demuestra cada una?',
  },
  {
    label: 'Contacto',
    prompt: 'Como puedo contactar a Sebastian Gatica o avanzar con una oportunidad laboral?',
  },
]

export function AgentConsole() {
  const [input, setInput] = useState('')
  const [messages, setMessages] = useState<Message[]>([
    {
      id: 'hello',
      role: 'assistant',
      content:
        'Soy un agente del portfolio de Sebastian Gatica. Mi objetivo principal es asesorar sobre sus capacidades como desarrollador, su experiencia profesional, su stack y sus demos; si queres hablar de otro tema, tambien puedo responder como asistente general.',
    },
  ])
  const [sessionId, setSessionId] = useState<string | null>(null)
  const [isStreaming, setIsStreaming] = useState(false)
  const [copiedMessageId, setCopiedMessageId] = useState<string | null>(null)
  const [voiceStatus, setVoiceStatus] = useState<VoiceStatus>('idle')
  const [voiceTranscript, setVoiceTranscript] = useState('')
  const [voiceModel, setVoiceModel] = useState<string | null>(null)
  const [voiceError, setVoiceError] = useState<string | null>(null)
  const [conversationStatus, setConversationStatus] = useState<VoiceStatus>('idle')
  const [conversationTranscript, setConversationTranscript] = useState('')
  const [conversationModel, setConversationModel] = useState<string | null>(null)
  const [conversationError, setConversationError] = useState<string | null>(null)
  const [voiceConfigured, setVoiceConfigured] = useState<boolean | null>(null)
  const [freeModelOffer, setFreeModelOffer] = useState<FreeModelOffer | null>(null)
  const [pendingFreePrompt, setPendingFreePrompt] = useState<string | null>(null)
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const dataChannelRef = useRef<RTCDataChannel | null>(null)
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null)
  const transcriptTurnsRef = useRef<Map<string, string>>(new Map())
  const voiceBaseTextRef = useRef('')
  const processedConversationItemsRef = useRef<Set<string>>(new Set())
  const assistantVoiceMessageIdRef = useRef<string | null>(null)
  const voiceLimitTimeoutRef = useRef<number | null>(null)

  const assistantMessage = useMemo(
    () => [...messages].reverse().find((message) => message.role === 'assistant'),
    [messages],
  )

  const voiceActive = voiceStatus === 'connecting' || voiceStatus === 'listening'
  const conversationActive =
    conversationStatus === 'connecting' || conversationStatus === 'listening'

  useEffect(() => {
    getPortfolioHealth()
      .then((health) => {
        setVoiceConfigured(health.voiceConfigured)
        if (!health.voiceConfigured) {
          setVoiceStatus('error')
          setConversationStatus('error')
          setVoiceError('Configura OPENAI_API_KEY en el backend para activar voz.')
          setConversationError('Configura OPENAI_API_KEY en el backend para conversar por voz.')
        }
      })
      .catch(() => {
        setVoiceConfigured(false)
        setVoiceStatus('error')
        setConversationStatus('error')
        setVoiceError('No pude leer el estado del backend de voz.')
        setConversationError('No pude leer el estado del backend de conversacion.')
      })

    return () => cleanupVoiceConnection()
  }, [])

  function dynamicContextPayload() {
    return [
      {
        type: 'time_now' as const,
        name: 'runtime-clock',
      },
    ]
  }

  async function handleSubmit(
    event?: FormEvent,
    overrideInput?: string,
    runtime: ChatRuntime = 'openai',
  ) {
    event?.preventDefault()
    const clean = (overrideInput ?? input).trim()
    if (!clean || isStreaming) {
      return
    }

    if (runtime === 'free') {
      setFreeModelOffer(null)
      setPendingFreePrompt(null)
    }

    cleanupVoiceConnection()
    setVoiceStatus('idle')
    setConversationStatus('idle')
    clearVoiceTranscript()
    clearConversationTranscript()

    const assistantId = crypto.randomUUID()
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', content: clean },
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setInput('')
    setIsStreaming(true)

    try {
      await streamAgentResponse(
        {
          message: clean,
          sessionId,
          agentId: 'coordinator',
          runtime,
          extensions: ['business-context'],
          dynamicContext: dynamicContextPayload(),
        },
        {
          onSession: setSessionId,
          onFreeModelOffer: (offer) => {
            setFreeModelOffer(offer)
            setPendingFreePrompt(clean)
          },
          onChunk: (text) => {
            setMessages((current) =>
              current.map((message) =>
                message.id === assistantId
                  ? { ...message, content: `${message.content}${text}` }
                  : message,
              ),
            )
          },
        },
      )
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Error desconocido'
      setMessages((current) =>
        current.map((item) =>
          item.id === assistantId
            ? { ...item, content: `No pude abrir el stream del backend: ${message}` }
            : item,
        ),
      )
    } finally {
      setIsStreaming(false)
    }
  }

  function handleFollowUp(prompt: string) {
    if (isStreaming || conversationActive) {
      return
    }

    void handleSubmit(undefined, prompt)
  }

  function handleUseFreeModel() {
    if (!pendingFreePrompt || isStreaming) {
      return
    }

    void handleSubmit(undefined, pendingFreePrompt, 'free')
  }

  function handleInputKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.nativeEvent.isComposing) {
      return
    }

    if (event.ctrlKey && event.shiftKey) {
      return
    }

    event.preventDefault()
    void handleSubmit()
  }

  async function toggleVoiceMode() {
    if (voiceActive) {
      cleanupVoiceConnection()
      setVoiceStatus('idle')
      return
    }
    if (conversationActive) {
      cleanupVoiceConnection()
      setConversationStatus('idle')
      clearConversationTranscript()
    }

    if (voiceConfigured === false) {
      setVoiceStatus('error')
      setVoiceError('Configura OPENAI_API_KEY en el backend para activar voz.')
      return
    }

    await startVoiceMode()
  }

  async function startVoiceMode() {
    if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === 'undefined') {
      setVoiceStatus('error')
      setVoiceError('Este navegador no permite audio WebRTC desde esta pagina.')
      return
    }

    setVoiceStatus('connecting')
    setConversationStatus('idle')
    setVoiceError(null)
    setConversationError(null)
    setVoiceTranscript('')
    transcriptTurnsRef.current = new Map()
    voiceBaseTextRef.current = input.trim()

    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      mediaStreamRef.current = mediaStream

      const session = await createVoiceTranscriptionSession()
      setVoiceModel(session.model)
      scheduleVoiceDemoLimit('dictation')

      const peerConnection = new RTCPeerConnection()
      peerConnectionRef.current = peerConnection
      mediaStream.getAudioTracks().forEach((track) => peerConnection.addTrack(track, mediaStream))

      const dataChannel = peerConnection.createDataChannel('oai-events')
      dataChannelRef.current = dataChannel
      dataChannel.addEventListener('open', () => setVoiceStatus('listening'))
      dataChannel.addEventListener('message', handleRealtimeEvent)

      peerConnection.addEventListener('connectionstatechange', () => {
        if (peerConnection.connectionState === 'connected') {
          setVoiceStatus('listening')
        }
        if (['failed', 'disconnected'].includes(peerConnection.connectionState)) {
          setVoiceStatus('error')
          setVoiceError('Se corto la conexion de voz. Podes activarla otra vez.')
          cleanupVoiceConnection()
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
        throw new Error(`OpenAI Realtime rechazo la conexion (${sdpResponse.status})`)
      }

      await peerConnection.setRemoteDescription({
        type: 'answer',
        sdp: await sdpResponse.text(),
      })
    } catch (error) {
      cleanupVoiceConnection()
      setVoiceStatus('error')
      setVoiceError(error instanceof Error ? error.message : 'No se pudo activar el modo voz.')
    }
  }

  async function toggleConversationMode() {
    if (conversationActive) {
      cleanupVoiceConnection()
      setConversationStatus('idle')
      clearConversationTranscript()
      return
    }

    if (voiceActive) {
      cleanupVoiceConnection()
      setVoiceStatus('idle')
      clearVoiceTranscript()
    }

    if (voiceConfigured === false) {
      setConversationStatus('error')
      setConversationError('Configura OPENAI_API_KEY en el backend para conversar por voz.')
      return
    }

    await startConversationMode()
  }

  async function startConversationMode() {
    if (!navigator.mediaDevices?.getUserMedia || typeof RTCPeerConnection === 'undefined') {
      setConversationStatus('error')
      setConversationError('Este navegador no permite conversacion WebRTC desde esta pagina.')
      return
    }

    setConversationStatus('connecting')
    setVoiceStatus('idle')
    setConversationError(null)
    setVoiceError(null)
    setConversationTranscript('')
    processedConversationItemsRef.current = new Set()
    assistantVoiceMessageIdRef.current = null

    try {
      const mediaStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true,
        },
      })
      mediaStreamRef.current = mediaStream

      const session = await createVoiceConversationSession()
      setConversationModel(session.model)
      scheduleVoiceDemoLimit('conversation')

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
      dataChannel.addEventListener('open', () => setConversationStatus('listening'))
      dataChannel.addEventListener('message', handleConversationRealtimeEvent)

      peerConnection.addEventListener('connectionstatechange', () => {
        if (peerConnection.connectionState === 'connected') {
          setConversationStatus('listening')
        }
        if (['failed', 'disconnected'].includes(peerConnection.connectionState)) {
          setConversationStatus('error')
          setConversationError('Se corto la conversacion. Podes activarla otra vez.')
          cleanupVoiceConnection()
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
        throw new Error(`OpenAI Realtime rechazo la conversacion (${sdpResponse.status})`)
      }

      await peerConnection.setRemoteDescription({
        type: 'answer',
        sdp: await sdpResponse.text(),
      })
    } catch (error) {
      cleanupVoiceConnection()
      setConversationStatus('error')
      setConversationError(
        error instanceof Error ? error.message : 'No se pudo activar el modo conversacion.',
      )
    }
  }

  function handleRealtimeEvent(event: MessageEvent<string>) {
    let payload: { type?: string; item_id?: string; delta?: string; transcript?: string; error?: { message?: string } }
    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }

    if (payload.type === 'conversation.item.input_audio_transcription.delta' && payload.delta) {
      const itemId = payload.item_id || 'live'
      const current = transcriptTurnsRef.current.get(itemId) || ''
      transcriptTurnsRef.current.set(itemId, `${current}${payload.delta}`)
      syncVoiceTranscript()
    }

    if (payload.type === 'conversation.item.input_audio_transcription.completed' && payload.transcript) {
      const itemId = payload.item_id || crypto.randomUUID()
      transcriptTurnsRef.current.set(itemId, payload.transcript)
      syncVoiceTranscript()
    }

    if (payload.type === 'error') {
      setVoiceStatus('error')
      setVoiceError(payload.error?.message || 'OpenAI devolvio un error de transcripcion.')
      cleanupVoiceConnection()
    }
  }

  function handleConversationRealtimeEvent(event: MessageEvent<string>) {
    let payload: {
      type?: string
      item_id?: string
      response_id?: string
      delta?: string
      transcript?: string
      error?: { message?: string }
    }
    try {
      payload = JSON.parse(event.data)
    } catch {
      return
    }

    if (payload.type === 'session.created') {
      setConversationStatus('listening')
      return
    }

    if (payload.type === 'conversation.item.input_audio_transcription.delta' && payload.delta) {
      setConversationTranscript((current) => `${current}${payload.delta}`.replace(/\s+/g, ' '))
      return
    }

    if (
      payload.type === 'conversation.item.input_audio_transcription.completed' &&
      payload.transcript?.trim()
    ) {
      appendConversationUserMessage(payload.item_id || crypto.randomUUID(), payload.transcript)
      setConversationTranscript('')
      return
    }

    if (
      ['response.output_audio_transcript.delta', 'response.output_text.delta'].includes(
        payload.type || '',
      ) &&
      payload.delta
    ) {
      appendAssistantVoiceDelta(payload.delta)
      return
    }

    if (
      ['response.output_audio_transcript.done', 'response.output_text.done', 'response.done'].includes(
        payload.type || '',
      )
    ) {
      assistantVoiceMessageIdRef.current = null
      return
    }

    if (payload.type === 'error') {
      setConversationStatus('error')
      setConversationError(payload.error?.message || 'OpenAI devolvio un error de conversacion.')
      cleanupVoiceConnection()
    }
  }

  function appendConversationUserMessage(itemId: string, transcript: string) {
    const clean = transcript.trim()
    if (!clean || processedConversationItemsRef.current.has(itemId)) {
      return
    }

    processedConversationItemsRef.current.add(itemId)
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', content: clean },
    ])
  }

  function appendAssistantVoiceDelta(delta: string) {
    let assistantId = assistantVoiceMessageIdRef.current

    if (!assistantId) {
      const newAssistantId = crypto.randomUUID()
      assistantVoiceMessageIdRef.current = newAssistantId
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

  function syncVoiceTranscript() {
    const transcript = Array.from(transcriptTurnsRef.current.values())
      .join(' ')
      .replace(/\s+/g, ' ')
      .trim()
    const combined = [voiceBaseTextRef.current, transcript].filter(Boolean).join(' ').trim()

    setVoiceTranscript(transcript)
    setInput(combined)
  }

  function clearVoiceTranscript() {
    setVoiceTranscript('')
    transcriptTurnsRef.current = new Map()
    voiceBaseTextRef.current = ''
  }

  function clearConversationTranscript() {
    setConversationTranscript('')
    processedConversationItemsRef.current = new Set()
    assistantVoiceMessageIdRef.current = null
  }

  function scheduleVoiceDemoLimit(mode: 'dictation' | 'conversation') {
    clearVoiceDemoLimit()
    voiceLimitTimeoutRef.current = window.setTimeout(() => {
      voiceLimitTimeoutRef.current = null
      cleanupVoiceConnection()

      if (mode === 'conversation') {
        setConversationStatus('idle')
        setConversationTranscript('')
        setConversationError('Se cumplio el minuto de demo de voz. Esta sesion consumio 5 creditos.')
        assistantVoiceMessageIdRef.current = null
        return
      }

      setVoiceStatus('idle')
      setVoiceTranscript('')
      setVoiceError('Se cumplio el minuto de demo de voz. Esta sesion consumio 5 creditos.')
    }, VOICE_DEMO_LIMIT_MS)
  }

  function clearVoiceDemoLimit() {
    if (voiceLimitTimeoutRef.current !== null) {
      window.clearTimeout(voiceLimitTimeoutRef.current)
      voiceLimitTimeoutRef.current = null
    }
  }

  function cleanupVoiceConnection() {
    clearVoiceDemoLimit()

    dataChannelRef.current?.removeEventListener('message', handleRealtimeEvent)
    dataChannelRef.current?.removeEventListener('message', handleConversationRealtimeEvent)
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

  async function copyMessage(message: Message) {
    if (!message.content.trim()) {
      return
    }

    await navigator.clipboard.writeText(message.content)
    setCopiedMessageId(message.id)
    window.setTimeout(() => setCopiedMessageId(null), 1600)
  }

  return (
    <section className="console-shell" id="demo">
      <div className="section-kicker">
        <TerminalSquare size={18} />
        Demo integrada
      </div>

      <div className="console-grid">
        <div className="console-main">
          <div className="console-header">
            <div>
              <span className="status-dot" />
              Portfolio Assistant
            </div>
            <span>{sessionId ? `session ${sessionId.slice(0, 8)}` : 'sin sesion'}</span>
          </div>

          <div className="message-list" aria-live="polite">
            {messages.map((message) => (
              <article className={`message message-${message.role}`} key={message.id}>
                <div className="message-icon">
                  {message.role === 'assistant' ? <Bot size={16} /> : <MessageSquare size={16} />}
                </div>
                <div className="message-bubble">
                  {message.content ? (
                    <FormattedMessage content={message.content} />
                  ) : (
                    <p>{message.id === assistantMessage?.id ? 'Pensando...' : ''}</p>
                  )}
                  {message.role === 'assistant' && message.content && (
                    <>
                      {shouldShowPortfolioFollowUps(message) && (
                        <div className="portfolio-followups" aria-label="Preguntas sugeridas">
                          <span>
                            <Sparkles size={14} />
                            Segui explorando
                          </span>
                          <div>
                            {portfolioFollowUps.map((followUp) => (
                              <button
                                key={followUp.label}
                                type="button"
                                onClick={() => handleFollowUp(followUp.prompt)}
                                disabled={isStreaming || conversationActive}
                              >
                                {followUp.label}
                              </button>
                            ))}
                          </div>
                        </div>
                      )}
                      <button
                        className="copy-response"
                        type="button"
                        onClick={() => copyMessage(message)}
                        aria-label="Copiar respuesta al portapapeles"
                      >
                        {copiedMessageId === message.id ? (
                          <>
                            <CheckCircle2 size={15} />
                            Copiado
                          </>
                        ) : (
                          <>
                            <Clipboard size={15} />
                            Copiar
                          </>
                        )}
                      </button>
                    </>
                  )}
                </div>
              </article>
            ))}
            {voiceActive && voiceTranscript && (
              <article className="message message-user message-live">
                <div className="message-icon">
                  <Mic size={16} />
                </div>
                <div className="message-bubble">
                  <FormattedMessage content={voiceTranscript} />
                  <span className="live-transcript-pill">
                    <AudioLines size={14} />
                    Transcribiendo
                  </span>
                </div>
              </article>
            )}
            {conversationActive && conversationTranscript && (
              <article className="message message-user message-live">
                <div className="message-icon">
                  <PhoneCall size={16} />
                </div>
                <div className="message-bubble">
                  <FormattedMessage content={conversationTranscript} />
                  <span className="live-transcript-pill">
                    <AudioLines size={14} />
                    Conversando
                  </span>
                </div>
              </article>
            )}
          </div>

          <div className={`voice-panel ${audioPanelClass(voiceStatus, conversationStatus)}`}>
            <div className="voice-actions">
              <button
                type="button"
                onClick={toggleVoiceMode}
                disabled={isStreaming || voiceConfigured === false || conversationStatus === 'connecting'}
                title={
                  voiceConfigured === false
                    ? 'Configura OPENAI_API_KEY en el backend para activar voz'
                    : undefined
                }
              >
                {voiceActive ? <MicOff size={18} /> : <Mic size={18} />}
                {voiceActive ? 'Detener dictado' : 'Dictar'}
              </button>
              <button
                type="button"
                onClick={toggleConversationMode}
                disabled={isStreaming || voiceConfigured === false || voiceStatus === 'connecting'}
                title={
                  voiceConfigured === false
                    ? 'Configura OPENAI_API_KEY en el backend para conversar'
                    : undefined
                }
              >
                {conversationActive ? <MicOff size={18} /> : <PhoneCall size={18} />}
                {conversationActive ? 'Detener conversacion' : 'Conversar'}
              </button>
            </div>
            <div>
              <strong>{audioLabel(voiceStatus, conversationStatus)}</strong>
              <span>
                {audioDetail({
                  voiceConfigured,
                  voiceModel,
                  voiceStatus,
                  conversationModel,
                  conversationStatus,
                })}
              </span>
            </div>
            {voiceError && <p>{voiceError}</p>}
            {conversationError && <p>{conversationError}</p>}
            <audio ref={remoteAudioRef} autoPlay playsInline className="conversation-audio" />
          </div>

          {freeModelOffer && (
            <div className="free-model-tooltip" role="status">
              <div>
                <Cpu size={18} />
                <span>
                  <strong>{freeModelOffer.title}</strong>
                  {freeModelOffer.enabled
                    ? freeModelOffer.message
                    : 'El modelo gratuito local todavia no esta activo en esta instancia.'}
                </span>
              </div>
              <div className="free-model-actions">
                <button
                  type="button"
                  onClick={handleUseFreeModel}
                  disabled={!freeModelOffer.enabled || !pendingFreePrompt || isStreaming}
                >
                  Usar modelo gratuito
                </button>
                <button
                  type="button"
                  onClick={() => setFreeModelOffer(null)}
                  aria-label="Cerrar aviso de modelo gratuito"
                >
                  <X size={16} />
                </button>
              </div>
            </div>
          )}

          <form className="console-form" onSubmit={handleSubmit}>
            <textarea
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleInputKeyDown}
              placeholder="Escribi tu mensaje..."
              disabled={conversationActive}
              rows={3}
            />
            <button type="submit" disabled={isStreaming || conversationActive} aria-label="Enviar mensaje">
              {isStreaming ? <LoaderCircle className="spin" size={18} /> : <Send size={18} />}
              Enviar
            </button>
          </form>
        </div>

      </div>
    </section>
  )
}

type FormattedBlock =
  | { type: 'heading'; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; items: string[] }
  | { type: 'numbered'; items: string[] }

function FormattedMessage({ content }: { content: string }) {
  const blocks = useMemo(() => buildBlocks(content), [content])

  return (
    <div className="assistant-content">
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          return <h4 key={`${block.type}-${index}`}>{block.content}</h4>
        }

        if (block.type === 'list') {
          return (
            <ul key={`${block.type}-${index}`}>
              {block.items.map((item, itemIndex) => (
                <li key={`${item}-${itemIndex}`}>{item}</li>
              ))}
            </ul>
          )
        }

        if (block.type === 'numbered') {
          return (
            <ol key={`${block.type}-${index}`}>
              {block.items.map((item, itemIndex) => (
                <li key={`${item}-${itemIndex}`}>{item}</li>
              ))}
            </ol>
          )
        }

        return <p key={`${block.type}-${index}`}>{block.content}</p>
      })}
    </div>
  )
}

function shouldShowPortfolioFollowUps(message: Message) {
  if (message.role !== 'assistant' || !message.content.trim()) {
    return false
  }

  const normalized = message.content
    .toLowerCase()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')

  return (
    normalized.includes('portfolio') &&
    (normalized.includes('sebastian gatica') ||
      normalized.includes('habilidades') ||
      normalized.includes('capacidades') ||
      normalized.includes('experiencia') ||
      normalized.includes('stack') ||
      normalized.includes('demos'))
  )
}

function buildBlocks(content: string): FormattedBlock[] {
  const normalized = normalizeAssistantText(content)
  const lines = normalized
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  const blocks: FormattedBlock[] = []
  let listItems: string[] = []
  let numberedItems: string[] = []

  const flushLists = () => {
    if (listItems.length) {
      blocks.push({ type: 'list', items: listItems })
      listItems = []
    }

    if (numberedItems.length) {
      blocks.push({ type: 'numbered', items: numberedItems })
      numberedItems = []
    }
  }

  for (const line of lines) {
    if (/^#{1,3}\s+/.test(line)) {
      flushLists()
      blocks.push({ type: 'heading', content: line.replace(/^#{1,3}\s+/, '') })
      continue
    }

    if (/^[-*]\s+/.test(line)) {
      if (numberedItems.length) {
        blocks.push({ type: 'numbered', items: numberedItems })
        numberedItems = []
      }
      listItems.push(line.replace(/^[-*]\s+/, ''))
      continue
    }

    if (/^\d+\.\s+/.test(line)) {
      if (listItems.length) {
        blocks.push({ type: 'list', items: listItems })
        listItems = []
      }
      numberedItems.push(line.replace(/^\d+\.\s+/, ''))
      continue
    }

    flushLists()

    if (isCompactHeading(line)) {
      blocks.push({ type: 'heading', content: line })
    } else {
      blocks.push({ type: 'paragraph', content: line })
    }
  }

  flushLists()
  return blocks
}

function normalizeAssistantText(content: string) {
  return content
    .replace(/\r\n/g, '\n')
    .replace(/([^\n])(\d+\.\s+)/g, '$1\n$2')
    .replace(/([^\n])\s+-\s+/g, '$1\n- ')
    .replace(/(^|\n)([A-Z][A-Za-z0-9 /&-]{3,70})-\s+/g, '$1### $2\n- ')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function isCompactHeading(line: string) {
  const first = line.charAt(0)

  return (
    line.length <= 72 &&
    !line.endsWith('.') &&
    !line.includes(':') &&
    first === first.toUpperCase() &&
    first !== first.toLowerCase()
  )
}

function voiceLabel(status: VoiceStatus) {
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

function conversationLabel(status: VoiceStatus) {
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

function audioLabel(voiceStatus: VoiceStatus, conversationStatus: VoiceStatus) {
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

function audioPanelClass(voiceStatus: VoiceStatus, conversationStatus: VoiceStatus) {
  if (conversationStatus === 'listening') {
    return 'voice-panel-conversation'
  }
  if (conversationStatus === 'connecting') {
    return 'voice-panel-connecting'
  }
  return `voice-panel-${voiceStatus}`
}

function audioDetail({
  voiceConfigured,
  voiceModel,
  voiceStatus,
  conversationModel,
  conversationStatus,
}: {
  voiceConfigured: boolean | null
  voiceModel: string | null
  voiceStatus: VoiceStatus
  conversationModel: string | null
  conversationStatus: VoiceStatus
}) {
  if (voiceConfigured === false) {
    return 'Falta OPENAI_API_KEY en la instancia backend actual.'
  }
  if (conversationStatus === 'connecting' || conversationStatus === 'listening') {
    return conversationModel
      ? `Modelo conversacion: ${conversationModel}`
      : 'Modelo conversacion: gpt-realtime-mini'
  }
  if (voiceStatus === 'connecting' || voiceStatus === 'listening') {
    return voiceModel ? `Modelo dictado: ${voiceModel}` : 'Modelo dictado: gpt-4o-mini-transcribe'
  }
  return 'Dictado transcribe texto; Conversar responde con voz. Solo uno puede estar activo.'
}
