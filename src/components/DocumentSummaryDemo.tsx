import { CheckCircle2, Download, FileText, LoaderCircle, Trash2, Upload } from 'lucide-react'
import { type ChangeEvent, type DragEvent, useRef, useState } from 'react'
import { summarizePdf, type DocumentSummaryResponse } from '../api/agentClient'

const MAX_PDF_BYTES = 10 * 1024 * 1024

export function DocumentSummaryDemo() {
  const inputRef = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [summary, setSummary] = useState<DocumentSummaryResponse | null>(null)
  const [dragging, setDragging] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  function handleFileSelection(nextFile: File | null) {
    setError('')
    setSummary(null)

    if (!nextFile) {
      setFile(null)
      return
    }

    const isPdf = nextFile.type === 'application/pdf' || nextFile.name.toLowerCase().endsWith('.pdf')
    if (!isPdf) {
      setFile(null)
      setError('Solo se aceptan archivos PDF.')
      return
    }

    if (nextFile.size > MAX_PDF_BYTES) {
      setFile(null)
      setError('El PDF supera el limite de 10 MB.')
      return
    }

    setFile(nextFile)
  }

  function handleInputChange(event: ChangeEvent<HTMLInputElement>) {
    handleFileSelection(event.target.files?.[0] || null)
  }

  function handleDrop(event: DragEvent<HTMLElement>) {
    event.preventDefault()
    setDragging(false)
    handleFileSelection(event.dataTransfer.files?.[0] || null)
  }

  async function handleSummarize() {
    if (!file || loading) {
      return
    }

    setLoading(true)
    setError('')

    try {
      setSummary(await summarizePdf(file))
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'No se pudo resumir el PDF.')
    } finally {
      setLoading(false)
    }
  }

  function handleRemove() {
    setFile(null)
    setSummary(null)
    setError('')
    if (inputRef.current) {
      inputRef.current.value = ''
    }
  }

  function downloadSummary() {
    if (!summary) {
      return
    }

    const blob = new Blob([summary.summary], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = `${summary.fileName.replace(/\.pdf$/i, '')}-resumen.txt`
    anchor.click()
    URL.revokeObjectURL(url)
  }

  const summarySections = summary ? parseSummarySections(summary.summary) : []

  return (
    <div className="document-demo-grid">
      <article
        className={`document-dropzone document-uploader ${dragging ? 'document-dropzone-dragging' : ''}`}
        onDragEnter={(event) => {
          event.preventDefault()
          setDragging(true)
        }}
        onDragLeave={(event) => {
          event.preventDefault()
          setDragging(false)
        }}
        onDragOver={(event) => event.preventDefault()}
        onDrop={handleDrop}
      >
        <input
          ref={inputRef}
          accept=".pdf,application/pdf"
          onChange={handleInputChange}
          type="file"
        />
        <FileText size={34} />
        <h3>Subir PDF</h3>
        <p>Exclusivamente PDF, hasta 10 MB. El archivo se usa una vez y se descarta al terminar.</p>

        {file ? (
          <div className="document-file-pill" title={file.name}>
            <strong>{file.name}</strong>
            <span>{formatBytes(file.size)}</span>
          </div>
        ) : (
          <span className="document-empty-state">Arrastra un PDF o elegilo desde tu equipo.</span>
        )}

        <div className="document-actions">
          <button type="button" onClick={() => inputRef.current?.click()}>
            <Upload size={17} />
            Elegir PDF
          </button>
          {file && (
            <button className="document-secondary-action" type="button" onClick={handleRemove}>
              <Trash2 size={17} />
              Quitar
            </button>
          )}
        </div>
      </article>

      <article className="document-output document-summary-panel">
        <div className="document-output-header">
          <div>
            <FileText size={18} />
            <strong>Resumen</strong>
          </div>
          {summary && <span>{summary.model}</span>}
        </div>

        {error && <p className="document-error">{error}</p>}

        {!summary && !loading && (
          <div className="document-placeholder">
            <p>El resumen sale como texto plano, listo para descargar.</p>
            <ul>
              <li>Resumen ejecutivo</li>
              <li>Puntos clave</li>
              <li>Riesgos o dudas</li>
              <li>Proximos pasos</li>
            </ul>
          </div>
        )}

        {loading && (
          <div className="document-loading">
            <LoaderCircle className="spin" size={20} />
            Resumiendo PDF...
          </div>
        )}

        {summary && (
          <>
            <div className="document-summary-result">
              {summarySections.map((section, sectionIndex) => (
                <section className="document-summary-card" key={`${section.title}-${sectionIndex}`}>
                  <div className="document-summary-card-header">
                    <span>{String(sectionIndex + 1).padStart(2, '0')}</span>
                    <h4>{section.title}</h4>
                  </div>

                  {section.paragraphs.map((paragraph, paragraphIndex) => (
                    <p key={`${section.title}-paragraph-${paragraphIndex}`}>{paragraph}</p>
                  ))}

                  {section.items.length > 0 && (
                    <ul>
                      {section.items.map((item, itemIndex) => (
                        <li key={`${section.title}-item-${itemIndex}`}>
                          <CheckCircle2 size={15} />
                          <span>{item}</span>
                        </li>
                      ))}
                    </ul>
                  )}
                </section>
              ))}
            </div>
            <div className="document-summary-meta">
              <span>{formatBytes(summary.sizeBytes)}</span>
              <span>Caduca al terminar</span>
            </div>
          </>
        )}

        <div className="document-summary-actions">
          <button type="button" onClick={handleSummarize} disabled={!file || loading}>
            {loading ? <LoaderCircle className="spin" size={17} /> : <FileText size={17} />}
            Resumir PDF
          </button>
          <button type="button" onClick={downloadSummary} disabled={!summary}>
            <Download size={17} />
            Descargar TXT
          </button>
        </div>
      </article>
    </div>
  )
}

type SummarySection = {
  title: string
  paragraphs: string[]
  items: string[]
}

function parseSummarySections(value: string): SummarySection[] {
  const lines = value
    .replace(/\r\n/g, '\n')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  const sections: SummarySection[] = []

  function ensureSection() {
    if (sections.length === 0) {
      sections.push({ title: 'Resultado', paragraphs: [], items: [] })
    }
    return sections[sections.length - 1]
  }

  for (const line of lines) {
    const markdownHeading = line.match(/^#{1,4}\s+(.+)$/)
    if (markdownHeading) {
      sections.push({ title: cleanHeading(markdownHeading[1]), paragraphs: [], items: [] })
      continue
    }

    const numberedHeading = line.match(/^\d+[.)]\s+(.+)$/)
    if (numberedHeading) {
      sections.push({ title: cleanHeading(numberedHeading[1]), paragraphs: [], items: [] })
      continue
    }

    const noteMatch = line.match(/^nota:\s*(.*)$/i)
    if (noteMatch) {
      sections.push({
        title: 'Nota',
        paragraphs: noteMatch[1] ? [noteMatch[1]] : [],
        items: [],
      })
      continue
    }

    const colonHeading = line.match(/^([^:]{3,80}):$/)
    if (colonHeading) {
      sections.push({ title: cleanHeading(colonHeading[1]), paragraphs: [], items: [] })
      continue
    }

    const bullet = line.match(/^[-*\u2022]\s+(.+)$/)
    if (bullet) {
      ensureSection().items.push(bullet[1])
      continue
    }

    ensureSection().paragraphs.push(line)
  }

  return sections.length > 0 ? sections : [{ title: 'Resultado', paragraphs: [value], items: [] }]
}

function cleanHeading(value: string) {
  return value.replace(/[:.]\s*$/, '').trim() || 'Resultado'
}

function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`
  }

  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`
  }

  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}
