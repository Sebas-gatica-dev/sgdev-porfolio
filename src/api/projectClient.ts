export type PortfolioProjectImage = {
  id: string
  projectId: string
  kind: 'cover' | 'gallery' | string
  storageKey: string
  url: string
  altText: string
  sortOrder: number
  mimeType: string
  sizeBytes: number
  width: number | null
  height: number | null
  createdAt: string
}

export type PortfolioProject = {
  id: string
  slug: string
  title: string
  summary: string
  description: string
  liveUrl: string
  repositoryUrl: string
  infraAppSlug: string
  techStack: string[]
  status: 'draft' | 'published' | 'archived' | string
  featured: boolean
  sortOrder: number
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  images: PortfolioProjectImage[]
}

const BASE_PATH = import.meta.env.BASE_URL.replace(/\/$/, '')
const API_BASE_PATH = `${BASE_PATH}/api`

function apiPath(path: string) {
  return `${API_BASE_PATH}${path.startsWith('/') ? path : `/${path}`}`
}

export function projectMediaUrl(image: PortfolioProjectImage) {
  if (/^https?:\/\//.test(image.url)) {
    return image.url
  }
  if (image.url.startsWith('/api/')) {
    return `${BASE_PATH}${image.url}`
  }
  return image.url
}

export async function getProjects(): Promise<PortfolioProject[]> {
  const response = await fetch(apiPath('/projects'))
  if (!response.ok) {
    throw new Error(`No se pudieron cargar los proyectos (${response.status})`)
  }
  const payload = (await response.json()) as { items?: PortfolioProject[] }
  return Array.isArray(payload.items) ? payload.items : []
}

export async function getProject(slug: string): Promise<PortfolioProject> {
  const response = await fetch(apiPath(`/projects/${encodeURIComponent(slug)}`))
  if (!response.ok) {
    throw new Error(response.status === 404 ? 'Proyecto no encontrado.' : `No se pudo cargar el proyecto (${response.status})`)
  }
  const payload = (await response.json()) as { item: PortfolioProject }
  return payload.item
}
