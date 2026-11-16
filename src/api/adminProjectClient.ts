import type { ProjectForm } from '../features/admin/projectForm'
import type { PortfolioProject, PortfolioProjectImage } from './projectClient'

/** El backend usa el mismo record para crear y editar; `id` vacío significa alta. */
export type PortfolioProjectUpsertRequest = ProjectForm

export type ProjectImageKind = 'cover' | 'gallery'

export type ProjectImageUpload = {
  fileName: string
  contentType: string
  dataBase64: string
  kind: ProjectImageKind
  altText: string
  sortOrder: number
}

export type AdminProjectsResponse = {
  ok: boolean
  items: PortfolioProject[]
  maxImageBytes: number
}

export const DEFAULT_MAX_IMAGE_BYTES = 5 * 1024 * 1024

export class AdminApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AdminApiError'
    this.status = status
  }
}

const BASE_PATH = import.meta.env.BASE_URL.replace(/\/$/, '')
const API_BASE_PATH = `${BASE_PATH}/api`
const SESSION_EXPIRED_MESSAGE = 'Tu sesión venció. Volvé a ingresar.'

function apiPath(path: string) {
  return `${API_BASE_PATH}${path.startsWith('/') ? path : `/${path}`}`
}

async function readMessage(response: Response, fallback: string) {
  try {
    const payload = (await response.json()) as { message?: unknown }
    if (typeof payload?.message === 'string' && payload.message.trim()) {
      return payload.message
    }
  } catch {
    // Respuesta sin cuerpo JSON: se usa el mensaje por defecto.
  }
  return fallback
}

async function send(path: string, init: RequestInit, fallback: string): Promise<Response> {
  let response: Response
  try {
    response = await fetch(apiPath(path), {
      credentials: 'include',
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new AdminApiError('No se pudo conectar con el servidor.', 0)
  }

  if (!response.ok) {
    const message = await readMessage(
      response,
      response.status === 401 ? SESSION_EXPIRED_MESSAGE : fallback,
    )
    throw new AdminApiError(message, response.status)
  }
  return response
}

export async function listAdminProjects(): Promise<AdminProjectsResponse> {
  const response = await send('/admin/projects', { method: 'GET' }, 'No se pudieron cargar los proyectos.')
  let payload: { ok?: unknown; items?: unknown; maxImageBytes?: unknown }
  try {
    payload = (await response.json()) as { ok?: unknown; items?: unknown; maxImageBytes?: unknown }
  } catch {
    throw new AdminApiError('El servidor devolvió una respuesta inválida al listar proyectos.', response.status)
  }

  const items = Array.isArray(payload.items) ? (payload.items as PortfolioProject[]) : []
  const maxImageBytes =
    typeof payload.maxImageBytes === 'number' && payload.maxImageBytes > 0
      ? payload.maxImageBytes
      : DEFAULT_MAX_IMAGE_BYTES

  return { ok: true, items, maxImageBytes }
}

export async function saveAdminProject(request: PortfolioProjectUpsertRequest): Promise<PortfolioProject> {
  const response = await send(
    '/admin/projects',
    { method: 'POST', body: JSON.stringify(request) },
    'No se pudo guardar el proyecto.',
  )
  const payload = (await response.json()) as { item?: PortfolioProject }
  if (!payload?.item) {
    throw new AdminApiError('El servidor no devolvió el proyecto guardado.', response.status)
  }
  return payload.item
}

export async function deleteAdminProject(projectId: string): Promise<void> {
  await send(
    `/admin/projects/${encodeURIComponent(projectId)}`,
    { method: 'DELETE' },
    'No se pudo eliminar el proyecto.',
  )
}

export async function uploadAdminProjectImage(
  projectId: string,
  upload: ProjectImageUpload,
): Promise<PortfolioProjectImage> {
  const response = await send(
    `/admin/projects/${encodeURIComponent(projectId)}/images`,
    { method: 'POST', body: JSON.stringify(upload) },
    'No se pudo subir la imagen.',
  )
  const payload = (await response.json()) as { item?: PortfolioProjectImage }
  if (!payload?.item) {
    throw new AdminApiError('El servidor no devolvió la imagen guardada.', response.status)
  }
  return payload.item
}

export async function deleteAdminProjectImage(projectId: string, imageId: string): Promise<void> {
  await send(
    `/admin/projects/${encodeURIComponent(projectId)}/images/${encodeURIComponent(imageId)}`,
    { method: 'DELETE' },
    'No se pudo eliminar la imagen.',
  )
}
