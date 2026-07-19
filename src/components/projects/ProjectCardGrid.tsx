import { ArrowRight, ExternalLink, FolderKanban, Star } from 'lucide-react'
import type { Route } from '../../app/routing'
import { PageLink } from '../PageLink'
import { projectMediaUrl, type PortfolioProject } from '../../api/projectClient'

export function ProjectCardGrid({
  projects,
  onNavigate,
}: {
  projects: PortfolioProject[]
  onNavigate: (path: Route) => void
}) {
  return (
    <div className="project-card-grid">
      {projects.map((project) => {
        const cover = project.images.find((image) => image.kind === 'cover') ?? project.images[0]
        const detailRoute = `/proyectos/${project.slug}` as Route
        return (
          <article className="portfolio-project-card" key={project.id}>
            <PageLink className="project-card-cover" href={detailRoute} onNavigate={onNavigate}>
              {cover ? (
                <img src={projectMediaUrl(cover)} alt={cover.altText || `Vista de ${project.title}`} />
              ) : (
                <span className="project-card-placeholder" aria-hidden="true">
                  <FolderKanban size={42} />
                </span>
              )}
              {project.featured && (
                <span className="project-featured-pill">
                  <Star size={14} /> Destacado
                </span>
              )}
            </PageLink>

            <div className="project-card-body">
              <div>
                <span className="project-card-eyebrow">Proyecto en producción</span>
                <h3>{project.title}</h3>
                <p>{project.summary}</p>
              </div>

              <div className="project-tech-list" aria-label={`Tecnologías de ${project.title}`}>
                {project.techStack.slice(0, 6).map((technology) => (
                  <span key={technology}>{technology}</span>
                ))}
              </div>

              <div className="project-card-actions">
                <PageLink href={detailRoute} onNavigate={onNavigate}>
                  Ver proyecto <ArrowRight size={16} />
                </PageLink>
                {project.liveUrl && (
                  <a href={project.liveUrl} target="_blank" rel="noreferrer">
                    Abrir <ExternalLink size={16} />
                  </a>
                )}
              </div>
            </div>
          </article>
        )
      })}
    </div>
  )
}
