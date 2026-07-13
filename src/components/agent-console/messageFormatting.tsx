import { Children, isValidElement, useMemo, type ReactNode } from 'react'
import ReactMarkdown, { type Components } from 'react-markdown'
import remarkGfm from 'remark-gfm'

const markdownComponents: Components = {
  h1: ({ children }) => <h4>{children}</h4>,
  h2: ({ children }) => <h4>{children}</h4>,
  h3: ({ children }) => <h4>{children}</h4>,
  h4: ({ children }) => <h4>{children}</h4>,
  h5: ({ children }) => <h4>{children}</h4>,
  h6: ({ children }) => <h4>{children}</h4>,
  a: ({ children, href }) => {
    const isExternal = Boolean(href && !href.startsWith('#'))

    return (
      <a
        href={href}
        rel={isExternal ? 'noreferrer' : undefined}
        target={isExternal ? '_blank' : undefined}
      >
        {children}
      </a>
    )
  },
  pre: ({ children }) => (
    <pre className="assistant-code-block" tabIndex={0}>
      {children}
    </pre>
  ),
  p: ({ children }) => (
    <p className={isMarkerLine(children) ? 'assistant-marker-line' : undefined}>
      {children}
    </p>
  ),
  code: ({ children, className }) => <code className={className}>{children}</code>,
}

export function FormattedMessage({ content }: { content: string }) {
  const normalizedContent = useMemo(() => normalizeAssistantMarkdown(content), [content])

  return (
    <div className="assistant-content">
      <ReactMarkdown
        components={markdownComponents}
        remarkPlugins={[remarkGfm]}
        skipHtml
      >
        {normalizedContent}
      </ReactMarkdown>
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

function normalizeAssistantMarkdown(content: string) {
  const normalized = content
    .replace(/\r\n/g, '\n')
    .replace(/\bde(\d+)/gi, 'de $1')
    .replace(
      /\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec|Ene|Abr|Ago|Dic)(\d{4})\b/gi,
      '$1 $2',
    )
    .replace(
      /\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec|Ene|Abr|Ago|Dic)\s+(\d{4})\s*[–-]\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Sept|Oct|Nov|Dec|Ene|Abr|Ago|Dic)\s+(\d{4})\b/gi,
      '$1 $2 - $3 $4',
    )
    .replace(
      /(^|\n)\s*\*\*(Resumen general|Historial y proyectos(?: \(resumido\))?|Experiencia destacada|Tecnologías y enfoques recurrentes|Tecnologias y enfoques recurrentes|Stack técnico|Stack tecnico)\*\*\s*:?\s*/gi,
      '$1### $2\n',
    )
    .replace(
      /(^|\n)(Resumen general|Historial y proyectos(?: \(resumido\))?|Experiencia destacada|Tecnologías y enfoques recurrentes|Tecnologias y enfoques recurrentes|Stack técnico|Stack tecnico)\s*:?(\n|$)/gi,
      '$1### $2$3',
    )
    .replace(/([^\n])(```[A-Za-z0-9_-]*)/g, '$1\n\n$2')
    .replace(/```([A-Za-z0-9_-]+)[ \t]+([^\n])/g, '```$1\n$2')
    .replace(
      /([^\n])\s+(Navegador|Node\.js|Browser|Cliente|Servidor|Backend|Frontend|Express|React|Python|Docker|Deploy|Resumen|Importante|Nota|Notas|Ejemplo|Ejemplos|Uso|Prueba)(\s*\([^)\n]+\))?:/gi,
      '$1\n\n$2$3:',
    )
    .replace(/([:;.!?])\s+(\d+\.\s+)/g, '$1\n\n$2')
    .replace(/([:;.!?])\s+([-*]\s+)/g, '$1\n$2')

  return normalizeOrderedListRuns(normalized)
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function normalizeOrderedListRuns(content: string) {
  const lines = content.split('\n')
  const normalizedLines: string[] = []
  let inOrderedRun = false
  let nextListNumber = 1

  for (const line of lines) {
    const orderedMatch = line.match(/^(\s{0,3})(\d+)[.)]\s+/)

    if (orderedMatch) {
      if (inOrderedRun) {
        while (normalizedLines[normalizedLines.length - 1]?.trim() === '') {
          normalizedLines.pop()
        }
      } else {
        nextListNumber = Number(orderedMatch[2]) || 1
      }

      const listNumber = nextListNumber
      normalizedLines.push(
        line.replace(/^(\s{0,3})\d+[.)]\s+/, (_match, indent: string) => {
          return `${indent}${listNumber}. `
        }),
      )
      nextListNumber += 1
      inOrderedRun = true
      continue
    }

    const unorderedMatch = line.match(/^(\s{0,3})[-*+]\s+/)
    if (inOrderedRun && unorderedMatch) {
      const missingIndent = Math.max(0, 3 - unorderedMatch[1].length)
      normalizedLines.push(`${' '.repeat(missingIndent)}${line}`)
      continue
    }

    normalizedLines.push(line)

    if (line.trim() !== '' && !/^\s{2,}\S/.test(line)) {
      inOrderedRun = false
    }
  }

  return normalizedLines.join('\n')
}

function isMarkerLine(children: ReactNode) {
  const text = plainTextFromChildren(children).trim()

  return text.length > 0 && text.length <= 90 && /[:：]$/.test(text)
}

function plainTextFromChildren(children: ReactNode): string {
  return Children.toArray(children)
    .map((child) => {
      if (typeof child === 'string' || typeof child === 'number') {
        return String(child)
      }

      if (isValidElement<{ children?: ReactNode }>(child)) {
        return plainTextFromChildren(child.props.children)
      }

      return ''
    })
    .join('')
}
