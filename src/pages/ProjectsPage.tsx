import { FolderKanban, LoaderCircle, RefreshCw } from 'lucide-react'
import { useCallback, useEffect, useState } from 'react'
import type { Route } from '../app/routing'
import { getProjects, type PortfolioProject } from '../api/projectClient'
import { ProjectCardGrid } from '../components/projects/ProjectCardGrid'
import { SectionHeading } from '../components/SectionHeading'

export function ProjectsPage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  const [projects, setProjects] = useState<PortfolioProject[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setProjects(await getProjects())
    } catch (loadError) {
      setError(loadError instanceof Error ? loadError.message : 'No se pudieron cargar los proyectos.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void load()
  }, [load])

  return (
    <section className="section projects-page">
      <SectionHeading
        kicker="Proyectos"
        title="Productos y sistemas desplegados."
        text="Aplicaciones completas construidas, integradas y operadas en producción. Las demos muestran capacidades puntuales; acá podés recorrer productos reales."
      />

      {loading && (
        <div className="project-state-panel">
          <LoaderCircle className="spin" size={24} />
          <div>
            <strong>Cargando proyectos</strong>
            <span>Consultando el catálogo publicado.</span>
          </div>
        </div>
      )}

      {!loading && error && (
        <div className="project-state-panel error">
          <RefreshCw size={24} />
          <div>
            <strong>No pude cargar el catálogo</strong>
            <span>{error}</span>
          </div>
          <button className="button button-secondary" type="button" onClick={() => void load()}>
            Reintentar
          </button>
        </div>
      )}

      {!loading && !error && projects.length === 0 && (
        <div className="project-state-panel">
          <FolderKanban size={28} />
          <div>
            <strong>Próximamente</strong>
            <span>Los primeros proyectos todavía están en preparación editorial.</span>
          </div>
        </div>
      )}

      {!loading && !error && projects.length > 0 && (
        <ProjectCardGrid projects={projects} onNavigate={onNavigate} />
      )}
    </section>
  )
}
