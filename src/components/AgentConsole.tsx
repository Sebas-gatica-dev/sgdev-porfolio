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
  getUsageStatus,
  requestMoreTokens,
  streamAgentResponse,
} from '../api/agentClient'
import { FormattedMessage, shouldShowPortfolioFollowUps } from './agent-console/messageFormatting'
import {
  audioDetail,
  audioLabel,
  audioPanelClass,
  conversationButtonTitle,
  formatVoiceAllowance,
  openAiProviderTitle,
  type VoiceStatus,
  voiceButtonTitle,
} from './agent-console/voiceUi'
import {
  browserSpeechSupported,
  getSpeechRecognitionConstructor,
  normalizeSpokenText,
  stripSpeechText,
} from './shared/speech'

type Message = {
  id: string
  role: 'user' | 'assistant'
  content: string
}

const VOICE_DEMO_LIMIT_MS = 120_000
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
  const [openAiVoiceCreditCost, setOpenAiVoiceCreditCost] = useState(10)
  const [chatRuntime, setChatRuntime] = useState<ChatRuntime>('openai')
  const [openAiConfigured, setOpenAiConfigured] = useState<boolean | null>(null)
  const [qwenConfigured, setQwenConfigured] = useState<boolean | null>(null)
  const [qwenModel, setQwenModel] = useState('qwen3:0.6b')
  const [openAiCreditRemaining, setOpenAiCreditRemaining] = useState<number | null>(null)
  const [openAiTokenLimit, setOpenAiTokenLimit] = useState<number | null>(null)
  const [openAiChatTokenCost, setOpenAiChatTokenCost] = useState(10)
  const [openAiVoiceSecondsRemaining, setOpenAiVoiceSecondsRemaining] = useState<number | null>(null)
  const [openAiVoiceMaxSeconds, setOpenAiVoiceMaxSeconds] = useState<number | null>(null)
  const [openAiCreditsExhausted, setOpenAiCreditsExhausted] = useState(false)
  const [quotaModalOpen, setQuotaModalOpen] = useState(false)
  const [tokenRequestPending, setTokenRequestPending] = useState(false)
  const [tokenRequestLoading, setTokenRequestLoading] = useState(false)
  const [tokenRequestMessage, setTokenRequestMessage] = useState<string | null>(null)
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
        setOpenAiVoiceCreditCost(health.openaiVoiceTokenCost || health.openaiVoiceCreditCost || 10)
        setOpenAiChatTokenCost(health.promptLimitChatTokenCost || 10)
        setQwenConfigured(health.freeModelConfigured)
        setQwenModel(health.freeModelName || 'qwen3:0.6b')
        setOpenAiCreditRemaining(health.promptLimitEnabled ? health.promptLimitRemaining : null)
        setOpenAiTokenLimit(
          health.promptLimitEnabled
            ? health.promptLimitMaxTokens || health.promptLimitMaxPrompts || null
            : null,
        )
        setOpenAiVoiceSecondsRemaining(
          health.promptLimitEnabled ? health.openaiVoiceSecondsRemaining : null,
        )
        setOpenAiVoiceMaxSeconds(health.promptLimitEnabled ? health.openaiVoiceMaxSeconds : null)
        setTokenRequestPending(Boolean(health.promptLimitTokenRequestPending))
        if (health.promptLimitEnabled && health.promptLimitNewVisitor) {
          setQuotaModalOpen(true)
        }
        const openAiPromptAvailable = health.openaiPromptAvailable !== false
        setOpenAiCreditsExhausted(health.openaiConfigured && !openAiPromptAvailable)
        const openAiAvailable = health.openaiConfigured && openAiPromptAvailable
        if (!openAiAvailable) {
          setChatRuntime('free')
        }
        if (!health.openaiVoiceAvailable && health.voiceConfigured && health.openaiConfigured) {
          setVoiceError(
            `OpenAI Realtime no tiene ${health.openaiVoiceTokenCost || health.openaiVoiceCreditCost || 10} tokens disponibles para voz; el modo gratuito usa dictado del navegador.`,
          )
          setConversationError(
            `OpenAI Realtime no tiene ${health.openaiVoiceTokenCost || health.openaiVoiceCreditCost || 10} tokens disponibles para conversar; el modo gratuito usa Qwen.`,
          )
        }
      })
      .catch(() => {
        setOpenAiConfigured(false)
        setVoiceConfigured(false)
        setOpenAiVoiceAvailable(false)
        setQwenConfigured(false)
        setOpenAiCreditRemaining(null)
        setOpenAiTokenLimit(null)
        setOpenAiVoiceSecondsRemaining(null)
        setOpenAiVoiceMaxSeconds(null)
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

  function applyPromptLimitStatus(status: import('../api/agentClient').PromptLimitStatus) {
    if (!status.enabled) {
      setOpenAiCreditRemaining(null)
      setOpenAiTokenLimit(null)
      setOpenAiVoiceSecondsRemaining(null)
      setOpenAiVoiceMaxSeconds(null)
      setTokenRequestPending(false)
      return
    }

    setOpenAiCreditRemaining(status.remaining)
    setOpenAiTokenLimit(status.maxTokens)
    setOpenAiChatTokenCost(status.chatTokenCost || 10)
    setOpenAiVoiceCreditCost(status.voiceTokenCost || 10)
    setOpenAiVoiceSecondsRemaining(status.voiceSecondsRemaining)
    setOpenAiVoiceMaxSeconds(status.maxVoiceSeconds)
    setTokenRequestPending(status.tokenRequestPending)
    setOpenAiVoiceAvailable(
      status.remaining >= (status.voiceTokenCost || 10) &&
        status.voiceSecondsRemaining >= (status.voiceSessionSeconds || 60),
    )
    if (status.remaining <= 0) {
      setOpenAiCreditsExhausted(true)
      setChatRuntime('free')
    } else {
      setOpenAiCreditsExhausted(false)
    }
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
            applyPromptLimitStatus(status)
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

  async function handleRequestMoreTokens() {
    if (tokenRequestLoading || tokenRequestPending) {
      return
    }

    setTokenRequestLoading(true)
    setTokenRequestMessage(null)
    try {
      const response = await requestMoreTokens()
      setTokenRequestPending(true)
      setTokenRequestMessage(response.message)
      const status = await getUsageStatus().catch(() => null)
      if (status) {
        applyPromptLimitStatus(status)
      }
    } catch (error) {
      setTokenRequestMessage(
        error instanceof Error ? error.message : 'No pude solicitar mas tokens.',
      )
    } finally {
      setTokenRequestLoading(false)
    }
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
          `Se cumplieron los 2 minutos de demo de voz. Esta sesion consumio ${openAiVoiceCreditCost} tokens.`,
        )
        assistantVoiceMessageIdRef.current = null
        return
      }

      setVoiceStatus('idle')
      setVoiceTranscript('')
      setVoiceError(
        `Se cumplieron los 2 minutos de demo de voz. Esta sesion consumio ${openAiVoiceCreditCost} tokens.`,
      )
    }, VOICE_DEMO_LIMIT_MS)
  }

  function consumeOpenAiVoiceCreditEstimate() {
    setOpenAiVoiceSecondsRemaining((current) => {
      if (current === null) {
        return current
      }
      return Math.max(0, current - VOICE_DEMO_LIMIT_MS / 1000)
    })
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

      {quotaModalOpen && openAiCreditRemaining !== null && (
        <div className="usage-modal-backdrop" role="presentation">
          <div
            className="usage-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="usageModalTitle"
          >
            <header className="usage-modal-header">
              <div>
                <span>Demo OpenAI</span>
                <h3 id="usageModalTitle">Tu cuota inicial esta activa</h3>
              </div>
              <button
                type="button"
                onClick={() => setQuotaModalOpen(false)}
                aria-label="Cerrar aviso de cuota"
              >
                <X size={18} />
              </button>
            </header>
            <div className="usage-modal-grid">
              <div>
                <span>Tokens</span>
                <strong>{openAiCreditRemaining}/{openAiTokenLimit || 200}</strong>
              </div>
              <div>
                <span>Voz</span>
                <strong>{formatVoiceAllowance(openAiVoiceMaxSeconds || 300)}</strong>
              </div>
              <div>
                <span>Interaccion</span>
                <strong>{openAiChatTokenCost} tokens</strong>
              </div>
              <div>
                <span>Llamada</span>
                <strong>max. 1 min</strong>
              </div>
            </div>
            <p>
              Cada mensaje por chat o dictado consume {openAiChatTokenCost} tokens. Cada sesion de
              voz usa hasta un minuto y se corta automaticamente.
            </p>
            <footer className="usage-modal-actions">
              <button type="button" onClick={() => setQuotaModalOpen(false)}>
                Entendido
              </button>
            </footer>
          </div>
        </div>
      )}

      <div className="console-grid">
        <div className="console-main">
          <div className="console-header">
            <div>
              <span className="status-dot" />
              Portfolio Assistant
            </div>
            <span>{sessionId ? `session ${sessionId.slice(0, 8)}` : 'sin sesion'}</span>
          </div>

          {openAiCreditRemaining !== null && (
            <div className="usage-quota-strip">
              <div>
                <span>Tokens OpenAI</span>
                <strong>{openAiCreditRemaining}/{openAiTokenLimit || 200}</strong>
              </div>
              <div>
                <span>Voz restante</span>
                <strong>
                  {formatVoiceAllowance(openAiVoiceSecondsRemaining ?? openAiVoiceMaxSeconds ?? 300)}
                </strong>
              </div>
              <div>
                <span>Costo</span>
                <strong>{openAiChatTokenCost} tokens</strong>
              </div>
              {openAiCreditRemaining <= 0 && (
                <button
                  type="button"
                  onClick={handleRequestMoreTokens}
                  disabled={tokenRequestLoading || tokenRequestPending}
                >
                  {tokenRequestPending
                    ? 'Solicitud enviada'
                    : tokenRequestLoading
                      ? 'Enviando...'
                      : 'Solicitar mas tokens'}
                </button>
              )}
              {tokenRequestMessage && <p>{tokenRequestMessage}</p>}
            </div>
          )}

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
