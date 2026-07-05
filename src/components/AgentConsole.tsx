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
const openAiLogoSrc = `${import.meta.env.BASE_URL}openai-logo.svg`
const qwenLogoSrc = `${import.meta.env.BASE_URL}qwen-logo.svg`

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
  const [openAiVoiceAvailable, setOpenAiVoiceAvailable] = useState<boolean | null>(null)
  const [openAiVoiceCreditCost, setOpenAiVoiceCreditCost] = useState(5)
  const [chatRuntime, setChatRuntime] = useState<ChatRuntime>('openai')
  const [openAiConfigured, setOpenAiConfigured] = useState<boolean | null>(null)
  const [qwenConfigured, setQwenConfigured] = useState<boolean | null>(null)
  const [qwenModel, setQwenModel] = useState('qwen3:0.6b')
  const [openAiCreditRemaining, setOpenAiCreditRemaining] = useState<number | null>(null)
  const [openAiCreditsExhausted, setOpenAiCreditsExhausted] = useState(false)
  const [freeModelOffer, setFreeModelOffer] = useState<FreeModelOffer | null>(null)
  const [pendingFreePrompt, setPendingFreePrompt] = useState<string | null>(null)
  const peerConnectionRef = useRef<RTCPeerConnection | null>(null)
  const mediaStreamRef = useRef<MediaStream | null>(null)
  const dataChannelRef = useRef<RTCDataChannel | null>(null)
  const remoteAudioRef = useRef<HTMLAudioElement | null>(null)
  const browserRecognitionRef = useRef<SpeechRecognition | null>(null)
  const browserVoiceModeRef = useRef<'dictation' | 'conversation' | null>(null)
  const freeConversationActiveRef = useRef(false)
  const freeConversationRestartTimeoutRef = useRef<number | null>(null)
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
  const openAiBlocked = openAiConfigured !== true || openAiCreditsExhausted
  const browserSpeechAvailable = browserSpeechSupported()
  const openAiVoiceReady = chatRuntime === 'openai' && openAiVoiceAvailable === true

  useEffect(() => {
    getPortfolioHealth()
      .then((health) => {
        setOpenAiConfigured(health.openaiConfigured)
        setVoiceConfigured(health.voiceConfigured)
        setOpenAiVoiceAvailable(health.openaiVoiceAvailable)
        setOpenAiVoiceCreditCost(health.openaiVoiceCreditCost || 5)
        setQwenConfigured(health.freeModelConfigured)
        setQwenModel(health.freeModelName || 'qwen3:0.6b')
        setOpenAiCreditRemaining(health.promptLimitEnabled ? health.promptLimitRemaining : null)
        const openAiPromptAvailable = health.openaiPromptAvailable !== false
        setOpenAiCreditsExhausted(health.openaiConfigured && !openAiPromptAvailable)
        const openAiAvailable = health.openaiConfigured && openAiPromptAvailable
        if (!openAiAvailable) {
          setChatRuntime('free')
        }
        if (!health.openaiVoiceAvailable && health.voiceConfigured && health.openaiConfigured) {
          setVoiceError(
            `OpenAI Realtime no tiene ${health.openaiVoiceCreditCost || 5} creditos disponibles para voz; el modo gratuito usa dictado del navegador.`,
          )
          setConversationError(
            `OpenAI Realtime no tiene ${health.openaiVoiceCreditCost || 5} creditos disponibles para conversar; el modo gratuito usa Qwen.`,
          )
        }
      })
      .catch(() => {
        setOpenAiConfigured(false)
        setVoiceConfigured(false)
        setOpenAiVoiceAvailable(false)
        setQwenConfigured(false)
        setOpenAiCreditRemaining(null)
        setOpenAiCreditsExhausted(false)
        setChatRuntime('free')
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
    runtime: ChatRuntime = chatRuntime,
  ) {
    event?.preventDefault()
    const clean = (overrideInput ?? input).trim()
    if (!clean || isStreaming) {
      return
    }

    const selectedRuntime: ChatRuntime = openAiBlocked && runtime === 'openai' ? 'free' : runtime

    if (selectedRuntime !== runtime) {
      setChatRuntime(selectedRuntime)
    }

    if (selectedRuntime === 'free') {
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
          runtime: selectedRuntime,
          extensions: ['business-context'],
          dynamicContext: dynamicContextPayload(),
        },
        {
          onSession: setSessionId,
          onFreeModelOffer: (offer) => {
            setOpenAiCreditsExhausted(true)
            setChatRuntime('free')
            setFreeModelOffer(offer)
            setPendingFreePrompt(clean)
          },
          onPromptLimit: (status) => {
            if (status.enabled) {
              setOpenAiCreditRemaining(status.remaining)
              setOpenAiVoiceAvailable(status.remaining >= openAiVoiceCreditCost)
            }
            if (status.enabled && status.remaining <= 0) {
              setOpenAiCreditsExhausted(true)
              setChatRuntime('free')
            }
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

    setChatRuntime('free')
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

    if (!openAiVoiceReady) {
      startFreeDictationMode()
      return
    }

    await startOpenAiVoiceMode()
  }

  async function startOpenAiVoiceMode() {
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
      consumeOpenAiVoiceCreditEstimate()
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
      setOpenAiVoiceAvailable(false)
      setVoiceStatus('error')
      setVoiceError(
        `${error instanceof Error ? error.message : 'No se pudo activar el modo voz.'} Podes usar dictado gratuito con el navegador.`,
      )
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

    if (!openAiVoiceReady) {
      startFreeConversationMode()
      return
    }

    await startOpenAiConversationMode()
  }

  async function startOpenAiConversationMode() {
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
      consumeOpenAiVoiceCreditEstimate()
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
      setOpenAiVoiceAvailable(false)
      setConversationStatus('error')
      setConversationError(
        `${error instanceof Error ? error.message : 'No se pudo activar el modo conversacion.'} Podes conversar gratis con Qwen en modo por turnos.`,
      )
    }
  }

  function startFreeDictationMode() {
    const SpeechRecognition = getSpeechRecognitionConstructor()
    if (!SpeechRecognition) {
      setVoiceStatus('error')
      setVoiceError(
        'Este navegador no ofrece dictado gratuito Web Speech. Podes escribir el mensaje o usar OpenAI Realtime si esta disponible.',
      )
      return
    }

    cleanupVoiceConnection()
    browserVoiceModeRef.current = 'dictation'
    transcriptTurnsRef.current = new Map()
    voiceBaseTextRef.current = input.trim()
    setVoiceTranscript('')
    setConversationTranscript('')

    const recognition = new SpeechRecognition()
    browserRecognitionRef.current = recognition
    recognition.lang = 'es-AR'
    recognition.continuous = true
    recognition.interimResults = true
    recognition.maxAlternatives = 1

    let finalTranscript = ''

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

      const liveTranscript = normalizeSpokenText(
        [finalTranscript, interimTranscript].filter(Boolean).join(' '),
      )
      const combined = [voiceBaseTextRef.current, liveTranscript].filter(Boolean).join(' ').trim()
      setVoiceTranscript(liveTranscript)
      setInput(combined)
    }

    recognition.onerror = (event) => {
      if (event.error === 'no-speech') {
        return
      }
      setVoiceStatus('error')
      setVoiceError(`El dictado gratuito del navegador se pauso (${event.error}).`)
      cleanupBrowserVoice()
    }

    recognition.onend = () => {
      if (browserVoiceModeRef.current !== 'dictation') {
        return
      }
      restartBrowserRecognition(recognition)
    }

    setVoiceModel('Web Speech API')
    setVoiceStatus('listening')
    setConversationStatus('idle')
    setVoiceError(null)
    setConversationError(null)

    try {
      recognition.start()
    } catch (error) {
      cleanupBrowserVoice()
      setVoiceStatus('error')
      setVoiceError(
        error instanceof Error ? error.message : 'No se pudo activar el dictado gratuito.',
      )
    }
  }

  function startFreeConversationMode() {
    const SpeechRecognition = getSpeechRecognitionConstructor()
    if (!SpeechRecognition) {
      setConversationStatus('error')
      setConversationError(
        'Este navegador no ofrece dictado gratuito Web Speech. Podes usar Qwen por texto o OpenAI Realtime si esta disponible.',
      )
      return
    }

    cleanupVoiceConnection()
    setChatRuntime('free')
    freeConversationActiveRef.current = true
    browserVoiceModeRef.current = 'conversation'
    processedConversationItemsRef.current = new Set()
    assistantVoiceMessageIdRef.current = null
    setConversationTranscript('')
    setConversationModel(`${qwenModel} + Web Speech API`)
    setConversationStatus('listening')
    setVoiceStatus('idle')
    setConversationError(null)
    setVoiceError(null)
    startFreeConversationListening()
  }

  function startFreeConversationListening() {
    if (!freeConversationActiveRef.current || browserVoiceModeRef.current !== 'conversation') {
      return
    }

    const SpeechRecognition = getSpeechRecognitionConstructor()
    if (!SpeechRecognition) {
      setConversationStatus('error')
      setConversationError('El dictado gratuito dejo de estar disponible en este navegador.')
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
      setConversationTranscript(latestTranscript)
    }

    recognition.onerror = (event) => {
      if (event.error === 'no-speech') {
        return
      }
      setConversationStatus('error')
      setConversationError(`La conversacion gratuita se pauso (${event.error}).`)
      cleanupBrowserVoice()
    }

    recognition.onend = () => {
      if (
        !freeConversationActiveRef.current ||
        browserVoiceModeRef.current !== 'conversation' ||
        browserRecognitionRef.current !== recognition
      ) {
        return
      }

      browserRecognitionRef.current = null
      const clean = normalizeSpokenText(finalTranscript || latestTranscript)
      if (clean) {
        setConversationTranscript('')
        void submitFreeConversationTurn(clean)
        return
      }

      restartFreeConversationListening(600)
    }

    try {
      recognition.start()
      setConversationStatus('listening')
    } catch (error) {
      setConversationStatus('error')
      setConversationError(
        error instanceof Error ? error.message : 'No se pudo escuchar con el navegador.',
      )
    }
  }

  async function submitFreeConversationTurn(prompt: string) {
    if (!freeConversationActiveRef.current || !prompt.trim()) {
      return
    }

    const assistantId = crypto.randomUUID()
    let assistantText = ''
    setMessages((current) => [
      ...current,
      { id: crypto.randomUUID(), role: 'user', content: prompt },
      { id: assistantId, role: 'assistant', content: '' },
    ])
    setIsStreaming(true)

    try {
      await streamAgentResponse(
        {
          message: prompt,
          sessionId,
          agentId: 'coordinator',
          runtime: 'free',
          extensions: ['business-context'],
          dynamicContext: dynamicContextPayload(),
        },
        {
          onSession: setSessionId,
          onChunk: (text) => {
            assistantText += text
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
      assistantText = error instanceof Error ? error.message : 'No se pudo responder con Qwen.'
      setMessages((current) =>
        current.map((message) =>
          message.id === assistantId
            ? { ...message, content: `No pude responder con Qwen: ${assistantText}` }
            : message,
        ),
      )
    } finally {
      setIsStreaming(false)
    }

    speakFreeConversationReply(assistantText)
  }

  function speakFreeConversationReply(text: string) {
    if (!freeConversationActiveRef.current) {
      return
    }

    if (!('speechSynthesis' in window) || typeof SpeechSynthesisUtterance === 'undefined') {
      setConversationError(
        'Tu navegador no tiene text-to-speech local; sigo respondiendo por texto.',
      )
      restartFreeConversationListening(600)
      return
    }

    const speechText = stripSpeechText(text)
    if (!speechText) {
      restartFreeConversationListening(350)
      return
    }

    const utterance = new SpeechSynthesisUtterance(speechText)
    utterance.lang = 'es-AR'
    utterance.rate = 1
    utterance.pitch = 1
    utterance.onend = () => restartFreeConversationListening(350)
    utterance.onerror = () => restartFreeConversationListening(350)

    window.speechSynthesis.cancel()
    window.speechSynthesis.speak(utterance)
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
        setConversationError(
          `Se cumplio el minuto de demo de voz. Esta sesion consumio ${openAiVoiceCreditCost} creditos.`,
        )
        assistantVoiceMessageIdRef.current = null
        return
      }

      setVoiceStatus('idle')
      setVoiceTranscript('')
      setVoiceError(
        `Se cumplio el minuto de demo de voz. Esta sesion consumio ${openAiVoiceCreditCost} creditos.`,
      )
    }, VOICE_DEMO_LIMIT_MS)
  }

  function consumeOpenAiVoiceCreditEstimate() {
    setOpenAiCreditRemaining((current) => {
      if (current === null) {
        return current
      }

      const next = Math.max(0, current - openAiVoiceCreditCost)
      setOpenAiVoiceAvailable(next >= openAiVoiceCreditCost)
      if (next <= 0) {
        setOpenAiCreditsExhausted(true)
        setChatRuntime('free')
      }
      return next
    })
  }

  function clearVoiceDemoLimit() {
    if (voiceLimitTimeoutRef.current !== null) {
      window.clearTimeout(voiceLimitTimeoutRef.current)
      voiceLimitTimeoutRef.current = null
    }
  }

  function cleanupVoiceConnection() {
    clearVoiceDemoLimit()
    cleanupBrowserVoice()

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

  function cleanupBrowserVoice() {
    if (freeConversationRestartTimeoutRef.current !== null) {
      window.clearTimeout(freeConversationRestartTimeoutRef.current)
      freeConversationRestartTimeoutRef.current = null
    }

    browserVoiceModeRef.current = null
    freeConversationActiveRef.current = false

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

  function restartBrowserRecognition(recognition: SpeechRecognition) {
    if (freeConversationRestartTimeoutRef.current !== null) {
      window.clearTimeout(freeConversationRestartTimeoutRef.current)
    }

    freeConversationRestartTimeoutRef.current = window.setTimeout(() => {
      freeConversationRestartTimeoutRef.current = null
      if (browserVoiceModeRef.current !== 'dictation' || browserRecognitionRef.current !== recognition) {
        return
      }

      try {
        recognition.start()
      } catch {
        setVoiceStatus('idle')
      }
    }, 350)
  }

  function restartFreeConversationListening(delayMs: number) {
    if (freeConversationRestartTimeoutRef.current !== null) {
      window.clearTimeout(freeConversationRestartTimeoutRef.current)
    }

    freeConversationRestartTimeoutRef.current = window.setTimeout(() => {
      freeConversationRestartTimeoutRef.current = null
      startFreeConversationListening()
    }, delayMs)
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
                disabled={(isStreaming && !voiceActive) || conversationStatus === 'connecting'}
                title={voiceButtonTitle(openAiVoiceReady, browserSpeechAvailable)}
              >
                {voiceActive ? <MicOff size={18} /> : <Mic size={18} />}
                {voiceActive ? 'Detener dictado' : openAiVoiceReady ? 'Dictar OpenAI' : 'Dictar gratis'}
              </button>
              <button
                type="button"
                onClick={toggleConversationMode}
                disabled={(isStreaming && !conversationActive) || voiceStatus === 'connecting'}
                title={conversationButtonTitle(openAiVoiceReady, browserSpeechAvailable)}
              >
                {conversationActive ? <MicOff size={18} /> : <PhoneCall size={18} />}
                {conversationActive
                  ? 'Detener conversacion'
                  : openAiVoiceReady
                    ? 'Conversar OpenAI'
                    : 'Conversar gratis'}
              </button>
            </div>
            <div className="voice-status-copy">
              <strong>{audioLabel(voiceStatus, conversationStatus)}</strong>
              <span>
                {audioDetail({
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
                })}
              </span>
              {voiceError && <p>{voiceError}</p>}
              {conversationError && <p>{conversationError}</p>}
            </div>
            <div
              className="runtime-provider-toggle"
              role="radiogroup"
              aria-label="Proveedor del chat"
            >
              <button
                type="button"
                role="radio"
                aria-checked={chatRuntime === 'openai'}
                className={chatRuntime === 'openai' ? 'runtime-provider-active' : undefined}
                onClick={() => setChatRuntime('openai')}
                disabled={isStreaming || conversationActive || openAiBlocked}
                title={openAiProviderTitle(openAiConfigured, openAiCreditsExhausted)}
              >
                <span className="runtime-provider-logo-frame">
                  <img src={openAiLogoSrc} alt="" aria-hidden="true" />
                </span>
                OpenAI
              </button>
              <button
                type="button"
                role="radio"
                aria-checked={chatRuntime === 'free'}
                className={chatRuntime === 'free' ? 'runtime-provider-active' : undefined}
                onClick={() => setChatRuntime('free')}
                disabled={isStreaming || conversationActive}
                title={
                  qwenConfigured === false
                    ? 'Qwen local no esta activo; usara fallback demo.'
                    : `Modelo ${qwenModel}`
                }
              >
                <span className="runtime-provider-logo-frame">
                  <img src={qwenLogoSrc} alt="" aria-hidden="true" />
                </span>
                Qwen
              </button>
            </div>
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
                  Usar Qwen
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

function openAiProviderTitle(openAiConfigured: boolean | null, openAiCreditsExhausted: boolean) {
  if (openAiConfigured === false) {
    return 'OPENAI_API_KEY no esta disponible; se usa Qwen.'
  }
  if (openAiConfigured === null) {
    return 'Chequeando disponibilidad de OpenAI.'
  }
  if (openAiCreditsExhausted) {
    return 'Demo OpenAI agotada para esta IP. Usa Qwen para seguir.'
  }
  return 'Usar OpenAI'
}

function voiceButtonTitle(openAiVoiceReady: boolean, browserSpeechAvailable: boolean) {
  if (openAiVoiceReady) {
    return 'Dictado OpenAI Realtime: consume creditos de voz.'
  }
  if (browserSpeechAvailable) {
    return 'Dictado gratuito del navegador: no consume OpenAI.'
  }
  return 'Este navegador no soporta dictado gratuito; proba Chrome, Edge o Safari.'
}

function conversationButtonTitle(openAiVoiceReady: boolean, browserSpeechAvailable: boolean) {
  if (openAiVoiceReady) {
    return 'Conversacion OpenAI Realtime: consume creditos de voz.'
  }
  if (browserSpeechAvailable) {
    return 'Conversacion gratuita por turnos: navegador + Qwen + voz local.'
  }
  return 'Este navegador no soporta dictado gratuito; Qwen sigue disponible por texto.'
}

function audioDetail({
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
      ? `OpenAI voz requiere ${openAiVoiceCreditCost} creditos; se usa modo gratuito del navegador.`
      : `OpenAI voz requiere ${openAiVoiceCreditCost} creditos y este navegador no ofrece dictado gratis.`
  }
  if (voiceConfigured === false) {
    return browserSpeechAvailable
      ? 'OpenAI Realtime no esta configurado; voz gratuita disponible desde el navegador.'
      : 'OpenAI Realtime no esta configurado y este navegador no ofrece dictado gratuito.'
  }
  return 'Dictado transcribe texto; Conversar responde con voz. Solo uno puede estar activo.'
}

function getSpeechRecognitionConstructor() {
  if (typeof window === 'undefined') {
    return undefined
  }
  return window.SpeechRecognition || window.webkitSpeechRecognition
}

function browserSpeechSupported() {
  return Boolean(getSpeechRecognitionConstructor())
}

function normalizeSpokenText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
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
