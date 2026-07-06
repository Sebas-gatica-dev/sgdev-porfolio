export function getSpeechRecognitionConstructor() {
  if (typeof window === 'undefined') {
    return undefined
  }
  return window.SpeechRecognition || window.webkitSpeechRecognition
}

export function browserSpeechSupported() {
  return Boolean(getSpeechRecognitionConstructor())
}

export function normalizeSpokenText(value: string) {
  return value.replace(/\s+/g, ' ').trim()
}

export function stripSpeechText(value: string) {
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
