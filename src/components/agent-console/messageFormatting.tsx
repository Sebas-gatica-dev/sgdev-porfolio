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
  return content
    .replace(/\r\n/g, '\n')
    .replace(/([^\n])(```[A-Za-z0-9_-]*)/g, '$1\n\n$2')
    .replace(/```([A-Za-z0-9_-]+)[ \t]+([^\n])/g, '```$1\n$2')
    .replace(
      /([^\n])\s+(Navegador|Node\.js|Browser|Cliente|Servidor|Backend|Frontend|Express|React|Python|Docker|Deploy|Resumen|Importante|Nota|Notas|Ejemplo|Ejemplos|Uso|Prueba)(\s*\([^)\n]+\))?:/gi,
      '$1\n\n$2$3:',
    )
    .replace(/([^\n])(\d+\.\s+)/g, '$1\n\n$2')
    .replace(/([^\n])\s+([-*]\s+)/g, '$1\n$2')
    .replace(
      /(^|\n)([A-Z\u00c1\u00c9\u00cd\u00d3\u00da\u00d1][A-Za-z\u00c1\u00c9\u00cd\u00d3\u00da\u00dc\u00d1\u00e1\u00e9\u00ed\u00f3\u00fa\u00fc\u00f10-9 /&-]{3,70})-\s+/g,
      '$1### $2\n- ',
    )
    .replace(/\n{3,}/g, '\n\n')
    .trim()
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
