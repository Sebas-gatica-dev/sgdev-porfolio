import { ArrowLeft, ExternalLink, Github, Image as ImageIcon, LoaderCircle } from 'lucide-react'
import { useEffect, useState } from 'react'
import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Route } from '../app/routing'
import { getProject, projectMediaUrl, type PortfolioProject } from '../api/projectClient'
import { PageLink } from '../components/PageLink'

export function ProjectDetailPage({
  slug,
  onNavigate,
}: {
  slug: string
  onNavigate: (path: Route) => void
}) {
  const [project, setProject] = useState<PortfolioProject | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    void getProject(slug)
      .then((item) => {
        if (active) setProject(item)
      })
      .catch((loadError) => {
        if (active) setError(loadError instanceof Error ? loadError.message : 'No se pudo cargar el proyecto.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [slug])

  if (loading) {
    return (
      <section className="section project-detail-page">
        <div className="project-state-panel">
          <LoaderCircle className="spin" size={24} />
          <strong>Cargando proyecto</strong>
        </div>
      </section>
    )
  }

  if (!project || error) {
    return (
      <section className="section project-detail-page">
        <PageLink className="project-back-link" href="/proyectos" onNavigate={onNavigate}>
          <ArrowLeft size={17} /> Volver a proyectos
        </PageLink>
        <div className="project-state-panel error">
          <ImageIcon size={26} />
          <div>
            <strong>Proyecto no disponible</strong>
            <span>{error || 'No encontramos el proyecto solicitado.'}</span>
          </div>
        </div>
      </section>
    )
  }

  const cover = project.images.find((image) => image.kind === 'cover') ?? project.images[0]
  const gallery = project.images.filter((image) => image.id !== cover?.id)

  return (
    <article className="section project-detail-page">
      <PageLink className="project-back-link" href="/proyectos" onNavigate={onNavigate}>
        <ArrowLeft size={17} /> Volver a proyectos
      </PageLink>

      <header className="project-detail-hero">
        <div className="project-detail-copy">
          <span className="project-card-eyebrow">Proyecto publicado</span>
          <h1>{project.title}</h1>
          <p>{project.summary}</p>
          <div className="project-tech-list">
            {project.techStack.map((technology) => (
              <span key={technology}>{technology}</span>
            ))}
          </div>
          <div className="project-detail-actions">
            {project.liveUrl && (
              <a className="button button-primary" href={project.liveUrl} target="_blank" rel="noreferrer">
                Ver en producción <ExternalLink size={18} />
              </a>
            )}
            {project.repositoryUrl && (
              <a className="button button-secondary" href={project.repositoryUrl} target="_blank" rel="noreferrer">
                Repositorio <Github size={18} />
              </a>
            )}
          </div>
        </div>
        <div className="project-detail-cover">
          {cover ? (
            <img src={projectMediaUrl(cover)} alt={cover.altText || `Vista principal de ${project.title}`} />
          ) : (
            <span><ImageIcon size={56} /></span>
          )}
        </div>
      </header>

      <section className="project-detail-content">
        <div className="project-description markdown-content">
          <ReactMarkdown remarkPlugins={[remarkGfm]}>{project.description || project.summary}</ReactMarkdown>
        </div>

        {gallery.length > 0 && (
          <div className="project-gallery">
            {gallery.map((image) => (
              <figure key={image.id}>
                <img src={projectMediaUrl(image)} alt={image.altText || `Captura de ${project.title}`} />
                {image.altText && <figcaption>{image.altText}</figcaption>}
              </figure>
            ))}
          </div>
        )}
      </section>
    </article>
  )
}
