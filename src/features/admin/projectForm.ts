// Contrato del formulario; coincide con PortfolioProjectUpsertRequest del backend.
import type { PortfolioProject } from '../../api/projectClient'

export type ProjectForm = {
  id: string
  slug: string
  title: string
  summary: string
  description: string
  liveUrl: string
  repositoryUrl: string
  infraAppSlug: string
  techStack: string[]
  status: 'draft' | 'published' | 'archived'
  featured: boolean
  sortOrder: number
}

export const PROJECT_STATUS_OPTIONS: Array<{ value: ProjectForm['status']; label: string }> = [
  { value: 'draft', label: 'Borrador' },
  { value: 'published', label: 'Publicado' },
  { value: 'archived', label: 'Archivado' },
]

// Mismo patrón que PortfolioProjectService.SLUG_PATTERN.
export const SLUG_PATTERN = /^[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$/

export function createEmptyProject(): ProjectForm {
  return {
    id: '',
    slug: '',
    title: '',
    summary: '',
    description: '',
    liveUrl: '',
    repositoryUrl: '',
    infraAppSlug: '',
    techStack: [],
    status: 'draft',
    featured: false,
    sortOrder: 0,
  }
}

export function isValidSlug(slug: string) {
  return SLUG_PATTERN.test(slug.trim())
}

export function slugifyTitle(title: string) {
  return title
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 120)
    .replace(/-+$/g, '')
}

export function parseTechStack(value: string) {
  const seen = new Set<string>()
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item) => {
      if (!item) {
        return false
      }
      const key = item.toLowerCase()
      if (seen.has(key)) {
        return false
      }
      seen.add(key)
      return true
    })
    .slice(0, 20)
}

export function projectFormFromProject(project: PortfolioProject): ProjectForm {
  const status = PROJECT_STATUS_OPTIONS.some((option) => option.value === project.status)
    ? (project.status as ProjectForm['status'])
    : 'draft'

  return {
    id: project.id,
    slug: project.slug,
    title: project.title,
    summary: project.summary,
    description: project.description,
    liveUrl: project.liveUrl,
    repositoryUrl: project.repositoryUrl,
    infraAppSlug: project.infraAppSlug,
    techStack: project.techStack,
    status,
    featured: project.featured,
    sortOrder: project.sortOrder,
  }
}

export function validateProjectForm(form: ProjectForm) {
  if (!form.title.trim()) {
    return 'El título es obligatorio.'
  }
  if (!isValidSlug(form.slug)) {
    return 'Slug inválido. Usá entre 3 y 120 caracteres: minúsculas, números y guiones.'
  }
  if (!form.summary.trim()) {
    return 'El resumen es obligatorio.'
  }
  return ''
}
