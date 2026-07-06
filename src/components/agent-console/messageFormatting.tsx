import { useMemo } from 'react'

type FormattedBlock =
  | { type: 'heading'; content: string }
  | { type: 'paragraph'; content: string }
  | { type: 'list'; items: string[] }
  | { type: 'numbered'; items: string[] }

export function FormattedMessage({ content }: { content: string }) {
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

export function shouldShowPortfolioFollowUps(message: { role: string; content: string }) {
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
