import {
  Image as ImageIcon,
  LoaderCircle,
  LogOut,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from 'lucide-react'
import { useCallback, useEffect, useState, type ChangeEvent, type FormEvent } from 'react'
import {
  AdminApiError,
  DEFAULT_MAX_IMAGE_BYTES,
  deleteAdminProject,
  deleteAdminProjectImage,
  listAdminProjects,
  saveAdminProject,
  uploadAdminProjectImage,
  type ProjectImageKind,
} from '../api/adminProjectClient'
import { AuthApiError, getSession, logout } from '../api/authClient'
import { projectMediaUrl, type PortfolioProject, type PortfolioProjectImage } from '../api/projectClient'
import type { Route } from '../app/routing'
import { PageLink } from '../components/PageLink'
import {
  PROJECT_STATUS_OPTIONS,
  createEmptyProject,
  isValidSlug,
  parseTechStack,
  projectFormFromProject,
  slugifyTitle,
  validateProjectForm,
  type ProjectForm,
} from '../features/admin/projectForm'

type AdminPageProps = {
  onNavigate: (path: Route) => void
}

const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const IMAGE_ACCEPT = ALLOWED_IMAGE_TYPES.join(',')

function formatDate(value: string) {
  if (!value) {
    return '—'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '—'
  }
  return date.toLocaleString('es-AR', { dateStyle: 'medium', timeStyle: 'short' })
}

function formatBytes(bytes: number) {
  const megabytes = bytes / (1024 * 1024)
  if (megabytes >= 1) {
    return `${Number.isInteger(megabytes) ? megabytes : megabytes.toFixed(1)} MB`
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`
}

function readFileAsDataUrl(file: File) {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(typeof reader.result === 'string' ? reader.result : '')
    reader.onerror = () => reject(new Error('No se pudo leer el archivo seleccionado.'))
    reader.readAsDataURL(file)
  })
}

function statusLabel(status: string) {
  return PROJECT_STATUS_OPTIONS.find((option) => option.value === status)?.label ?? status
}

export function AdminPage({ onNavigate }: AdminPageProps) {
  const [username, setUsername] = useState('')
  const [checkingSession, setCheckingSession] = useState(true)

  const [projects, setProjects] = useState<PortfolioProject[]>([])
  const [maxImageBytes, setMaxImageBytes] = useState(DEFAULT_MAX_IMAGE_BYTES)
  const [listLoading, setListLoading] = useState(false)
  const [listError, setListError] = useState('')
  const [actionError, setActionError] = useState('')
  const [busyProjectId, setBusyProjectId] = useState('')

  const [form, setForm] = useState<ProjectForm | null>(null)
  const [techStackText, setTechStackText] = useState('')
  const [slugTouched, setSlugTouched] = useState(false)
  const [formError, setFormError] = useState('')
  const [saving, setSaving] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)

  const [imageAlt, setImageAlt] = useState('')
  const [imageError, setImageError] = useState('')
  const [imageNotice, setImageNotice] = useState('')
  const [uploadingKind, setUploadingKind] = useState<ProjectImageKind | ''>('')
  const [deletingImageId, setDeletingImageId] = useState('')
  const [editorNonce, setEditorNonce] = useState(0)

    const loadProjects = useCallback(async () => {
    setListLoading(true)
    setListError('')
    try {
      const data = await listAdminProjects()
      setProjects(data.items)
      setMaxImageBytes(data.maxImageBytes)
    } catch (error) {
      if (error instanceof AdminApiError && error.status === 401) {
        onNavigate('/admin/login')
        return
      }
      setListError(error instanceof Error ? error.message : 'No se pudieron cargar los proyectos.')
    } finally {
      setListLoading(false)
    }
  }, [onNavigate])

  useEffect(() => {
    let active = true

    void (async () => {
      const session = await getSession()
      if (!active) {
        return
      }
      if (!session.authenticated) {
        onNavigate('/admin/login')
        return
      }
      setUsername(session.username ?? '')
      setCheckingSession(false)
      await loadProjects()
    })()

    return () => {
      active = false
    }
    // La verificación de sesión corre una sola vez al montar el panel.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    if (!form) {
      return
    }
    document.getElementById('admin-project-form')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    // Solo se reposiciona al abrir el editor.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editorNonce])

  const handleUnauthorized = useCallback(
    (error: unknown) => {
      if (error instanceof AdminApiError && error.status === 401) {
        onNavigate('/admin/login')
        return true
      }
      return false
    },
    [onNavigate],
  )

  function updateForm(patch: Partial<ProjectForm>) {
    setForm((current) => (current ? { ...current, ...patch } : current))
  }

  function clearImageMessages() {
    setImageError('')
    setImageNotice('')
  }

  function startCreate() {
    setEditorNonce((current) => current + 1)
    setForm(createEmptyProject())
    setTechStackText('')
    setSlugTouched(false)
    setFormError('')
    clearImageMessages()
  }

  function startEdit(project: PortfolioProject) {
    setEditorNonce((current) => current + 1)
    setForm(projectFormFromProject(project))
    setTechStackText(project.techStack.join(', '))
    setSlugTouched(true)
    setFormError('')
    clearImageMessages()
  }

  function cancelForm() {
    setForm(null)
    setTechStackText('')
    setFormError('')
    clearImageMessages()
  }

  async function handleLogout() {
    setLoggingOut(true)
    setActionError('')
    try {
      await logout()
      onNavigate('/admin/login')
    } catch (error) {
      setActionError(
        error instanceof AuthApiError || error instanceof Error
          ? error.message
          : 'No se pudo cerrar la sesión.',
      )
    } finally {
      setLoggingOut(false)
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!form) {
      return
    }

    const candidate: ProjectForm = {
      ...form,
      slug: form.slug.trim().toLowerCase(),
      title: form.title.trim(),
      summary: form.summary.trim(),
      techStack: parseTechStack(techStackText),
    }
    const validationError = validateProjectForm(candidate)
    if (validationError) {
      setFormError(validationError)
      return
    }

    setSaving(true)
    setFormError('')
    clearImageMessages()

    try {
      const saved = await saveAdminProject(candidate)
      setForm(projectFormFromProject(saved))
      setTechStackText(saved.techStack.join(', '))
      setSlugTouched(true)
      setImageNotice(candidate.id ? 'Cambios guardados.' : 'Proyecto creado. Ya podés subir imágenes.')
      await loadProjects()
    } catch (error) {
      if (handleUnauthorized(error)) {
        return
      }
      setFormError(error instanceof Error ? error.message : 'No se pudo guardar el proyecto.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDelete(project: PortfolioProject) {
    const confirmed = window.confirm(
      `¿Eliminar "${project.title}"? Se eliminan también sus imágenes y no se puede deshacer.`,
    )
    if (!confirmed) {
      return
    }

    setBusyProjectId(project.id)
    setActionError('')
    try {
      await deleteAdminProject(project.id)
      if (form?.id === project.id) {
        cancelForm()
      }
      await loadProjects()
    } catch (error) {
      if (handleUnauthorized(error)) {
        return
      }
      setActionError(error instanceof Error ? error.message : 'No se pudo eliminar el proyecto.')
    } finally {
      setBusyProjectId('')
    }
  }

  async function handleImageSelected(kind: ProjectImageKind, event: ChangeEvent<HTMLInputElement>) {
    const input = event.currentTarget
    const file = input.files?.[0]
    input.value = ''

    if (!file || !form?.id) {
      return
    }

    clearImageMessages()

    if (!ALLOWED_IMAGE_TYPES.includes(file.type)) {
      setImageError('Formato no permitido. Usá JPEG, PNG o WebP.')
      return
    }
    if (file.size === 0) {
      setImageError('El archivo está vacío.')
      return
    }
    if (file.size > maxImageBytes) {
      setImageError(`La imagen supera el máximo de ${formatBytes(maxImageBytes)}.`)
      return
    }

    setUploadingKind(kind)
    try {
      const dataUrl = await readFileAsDataUrl(file)
      const commaIndex = dataUrl.indexOf(',')
      const dataBase64 = commaIndex >= 0 ? dataUrl.slice(commaIndex + 1) : ''
      if (!dataBase64) {
        throw new Error('No se pudo leer el archivo seleccionado.')
      }

      const galleryCount =
        projects.find((project) => project.id === form.id)?.images.filter((image) => image.kind !== 'cover')
          .length ?? 0

      await uploadAdminProjectImage(form.id, {
        fileName: file.name,
        contentType: file.type,
        dataBase64,
        kind,
        altText: imageAlt.trim() || form.title.trim(),
        sortOrder: kind === 'cover' ? 0 : galleryCount + 1,
      })

      setImageAlt('')
      setImageNotice(kind === 'cover' ? 'Portada actualizada.' : 'Imagen agregada a la galería.')
      await loadProjects()
    } catch (error) {
      if (handleUnauthorized(error)) {
        return
      }
      setImageError(error instanceof Error ? error.message : 'No se pudo subir la imagen.')
    } finally {
      setUploadingKind('')
    }
  }

  async function handleDeleteImage(image: PortfolioProjectImage) {
    if (!form?.id) {
      return
    }
    const confirmed = window.confirm('¿Eliminar esta imagen? Se borra el archivo del servidor.')
    if (!confirmed) {
      return
    }

    setDeletingImageId(image.id)
    clearImageMessages()
    try {
      await deleteAdminProjectImage(form.id, image.id)
      setImageNotice('Imagen eliminada.')
      await loadProjects()
    } catch (error) {
      if (handleUnauthorized(error)) {
        return
      }
      setImageError(error instanceof Error ? error.message : 'No se pudo eliminar la imagen.')
    } finally {
      setDeletingImageId('')
    }
  }

  if (checkingSession) {
    return (
      <section className="section admin-page">
        <div className="project-state-panel">
          <LoaderCircle className="spin" size={24} />
          <div>
            <strong>Verificando sesión</strong>
            <span>Un momento…</span>
          </div>
        </div>
      </section>
    )
  }

  const selectedProject =
    form && form.id ? projects.find((project) => project.id === form.id) ?? null : null
  const cover = selectedProject?.images.find((image) => image.kind === 'cover') ?? null
  const gallery = selectedProject?.images.filter((image) => image.kind !== 'cover') ?? []

  return (
    <section className="section admin-page">
      <header className="admin-header">
        <div>
          <span className="section-kicker">Administración</span>
          <h1>Proyectos del portafolio</h1>
          <p className="admin-session-note">
            Sesión activa{username ? <>: <strong>{username}</strong></> : null}
          </p>
        </div>
        <div className="admin-header-actions">
          <button className="button button-primary" type="button" onClick={startCreate}>
            <Plus size={18} />
            Nuevo proyecto
          </button>
          <button
            className="button button-secondary"
            type="button"
            onClick={() => void handleLogout()}
            disabled={loggingOut}
          >
            <LogOut size={18} />
            {loggingOut ? 'Cerrando…' : 'Cerrar sesión'}
          </button>
        </div>
      </header>

      {actionError && (
        <p className="admin-alert" role="alert">
          {actionError}
        </p>
      )}

      <div className="admin-layout">
        <section className="admin-panel admin-list-panel" aria-labelledby="admin-projects-heading">
          <div className="admin-panel-head">
            <h2 id="admin-projects-heading">Proyectos ({projects.length})</h2>
            <button
              className="button button-secondary"
              type="button"
              onClick={() => void loadProjects()}
              disabled={listLoading}
            >
              <RefreshCw size={16} className={listLoading ? 'spin' : undefined} />
              Actualizar
            </button>
          </div>

          {listLoading && <p className="admin-hint">Cargando proyectos…</p>}
          {listError && (
            <p className="admin-alert" role="alert">
              {listError}
            </p>
          )}
          {!listLoading && !listError && projects.length === 0 && (
            <p className="admin-hint">Todavía no hay proyectos. Creá el primero con “Nuevo proyecto”.</p>
          )}

          {projects.length > 0 && (
            <ul className="admin-project-list">
              {projects.map((project) => (
                <li className="admin-project-item" key={project.id}>
                  <div className="admin-project-info">
                    <div className="admin-project-title-row">
                      <strong>{project.title || '(sin título)'}</strong>
                      <span className={`admin-status admin-status-${project.status}`}>
                        {statusLabel(project.status)}
                      </span>
                      {project.featured && <span className="admin-featured">Destacado</span>}
                    </div>
                    <span className="admin-project-slug">/{project.slug}</span>
                    <span className="admin-project-meta">
                      Actualizado: {formatDate(project.updatedAt)} · Orden {project.sortOrder}
                    </span>
                  </div>
                  <div className="admin-project-actions">
                    <button
                      className="button button-secondary"
                      type="button"
                      onClick={() => startEdit(project)}
                    >
                      <Pencil size={16} />
                      Editar
                    </button>
                    <button
                      className="button button-secondary admin-danger"
                      type="button"
                      onClick={() => void handleDelete(project)}
                      disabled={busyProjectId === project.id}
                    >
                      {busyProjectId === project.id ? (
                        <LoaderCircle className="spin" size={16} />
                      ) : (
                        <Trash2 size={16} />
                      )}
                      Eliminar
                    </button>
                    {project.status === 'published' && (
                      <PageLink
                        className="button button-secondary"
                        href={`/proyectos/${project.slug}` as Route}
                        onNavigate={onNavigate}
                      >
                        Ver público
                      </PageLink>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        {form ? (
          <form
            className="admin-panel admin-form"
            id="admin-project-form"
            onSubmit={handleSubmit}
            aria-labelledby="admin-form-heading"
            noValidate
          >
            <div className="admin-panel-head">
              <h2 id="admin-form-heading">{form.id ? 'Editar proyecto' : 'Nuevo proyecto'}</h2>
            </div>

            {formError && (
              <p className="admin-alert" role="alert">
                {formError}
              </p>
            )}
            {imageNotice && (
              <p className="admin-notice" role="status">
                {imageNotice}
              </p>
            )}

            <div className="admin-field-grid">
              <div className="form-group">
                <label htmlFor="project-title">Título</label>
                <input
                  id="project-title"
                  type="text"
                  value={form.title}
                  maxLength={160}
                  onChange={(event) => {
                    const value = event.target.value
                    setForm((current) =>
                      current
                        ? { ...current, title: value, slug: slugTouched ? current.slug : slugifyTitle(value) }
                        : current,
                    )
                  }}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="project-slug">Slug</label>
                <div className="admin-slug-row">
                  <input
                    id="project-slug"
                    type="text"
                    value={form.slug}
                    maxLength={120}
                    aria-describedby="project-slug-hint"
                    aria-invalid={form.slug !== '' && !isValidSlug(form.slug)}
                    onChange={(event) => {
                      setSlugTouched(true)
                      updateForm({ slug: event.target.value.toLowerCase() })
                    }}
                    required
                  />
                  <button
                    className="button button-secondary"
                    type="button"
                    onClick={() => {
                      setSlugTouched(true)
                      updateForm({ slug: slugifyTitle(form.title) })
                    }}
                  >
                    Sugerir
                  </button>
                </div>
                <span id="project-slug-hint" className="admin-hint">
                  Minúsculas, números y guiones. Se usa en la URL pública del proyecto.
                </span>
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="project-summary">Resumen</label>
              <textarea
                id="project-summary"
                rows={3}
                maxLength={600}
                value={form.summary}
                onChange={(event) => updateForm({ summary: event.target.value })}
                required
              />
            </div>

            <div className="form-group">
              <label htmlFor="project-description">Descripción (Markdown)</label>
              <textarea
                id="project-description"
                rows={8}
                maxLength={20000}
                value={form.description}
                onChange={(event) => updateForm({ description: event.target.value })}
              />
            </div>

            <div className="admin-field-grid">
              <div className="form-group">
                <label htmlFor="project-live-url">URL de producción (opcional)</label>
                <input
                  id="project-live-url"
                  type="text"
                  inputMode="url"
                  value={form.liveUrl}
                  onChange={(event) => updateForm({ liveUrl: event.target.value })}
                  placeholder="https://… o /argenticommerce/"
                />
              </div>
              <div className="form-group">
                <label htmlFor="project-repository-url">URL de repositorio (opcional)</label>
                <input
                  id="project-repository-url"
                  type="text"
                  inputMode="url"
                  value={form.repositoryUrl}
                  onChange={(event) => updateForm({ repositoryUrl: event.target.value })}
                  placeholder="https://github.com/…"
                />
              </div>
            </div>

            <div className="form-group">
              <label htmlFor="project-tech-stack">Stack (separado por comas)</label>
              <input
                id="project-tech-stack"
                type="text"
                value={techStackText}
                onChange={(event) => setTechStackText(event.target.value)}
                placeholder="Next.js, TypeScript, PostgreSQL"
              />
            </div>

            <div className="admin-field-grid">
              <div className="form-group">
                <label htmlFor="project-status">Estado</label>
                <select
                  id="project-status"
                  value={form.status}
                  onChange={(event) => updateForm({ status: event.target.value as ProjectForm['status'] })}
                >
                  {PROJECT_STATUS_OPTIONS.map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label htmlFor="project-sort-order">Orden</label>
                <input
                  id="project-sort-order"
                  type="number"
                  step={1}
                  min={-10000}
                  max={10000}
                  value={form.sortOrder}
                  onChange={(event) => {
                    const value = Number.parseInt(event.target.value, 10)
                    updateForm({ sortOrder: Number.isNaN(value) ? 0 : value })
                  }}
                />
              </div>
            </div>

            <label className="admin-checkbox" htmlFor="project-featured">
              <input
                id="project-featured"
                type="checkbox"
                checked={form.featured}
                onChange={(event) => updateForm({ featured: event.target.checked })}
              />
              Destacado en el portafolio
            </label>

            <div className="admin-form-actions">
              <button className="button button-primary" type="submit" disabled={saving}>
                {saving ? <LoaderCircle className="spin" size={16} /> : <Save size={16} />}
                {saving ? 'Guardando…' : 'Guardar'}
              </button>
              <button className="button button-secondary" type="button" onClick={cancelForm} disabled={saving}>
                Cancelar
              </button>
            </div>

            <section className="admin-images" aria-labelledby="admin-images-heading">
              <h3 id="admin-images-heading">Imágenes</h3>

              {imageError && (
                <p className="admin-alert" role="alert">
                  {imageError}
                </p>
              )}

              {!form.id && (
                <p className="admin-hint">Guardá el proyecto para habilitar la portada y la galería.</p>
              )}

              {form.id && !selectedProject && (
                <p className="admin-hint">El proyecto no está en el listado; guardá o actualizá para ver sus imágenes.</p>
              )}

              {form.id && selectedProject && (
                <>
                  <div className="form-group">
                    <label htmlFor="image-alt">Texto alternativo para la próxima imagen</label>
                    <input
                      id="image-alt"
                      type="text"
                      value={imageAlt}
                      maxLength={240}
                      onChange={(event) => setImageAlt(event.target.value)}
                      placeholder="Se usa el título del proyecto si queda vacío"
                    />
                  </div>

                  <div className="admin-image-group">
                    <h4>Portada</h4>
                    {cover ? (
                      <figure className="admin-image-card">
                        <img
                          src={projectMediaUrl(cover)}
                          alt={cover.altText || `Portada de ${selectedProject.title}`}
                        />
                        <figcaption>
                          <span>{cover.altText || 'Sin texto alternativo'}</span>
                          <button
                            className="button button-secondary admin-danger"
                            type="button"
                            onClick={() => void handleDeleteImage(cover)}
                            disabled={deletingImageId === cover.id}
                          >
                            {deletingImageId === cover.id ? (
                              <LoaderCircle className="spin" size={16} />
                            ) : (
                              <Trash2 size={16} />
                            )}
                            Eliminar
                          </button>
                        </figcaption>
                      </figure>
                    ) : (
                      <p className="admin-hint admin-empty-media">
                        <ImageIcon size={20} /> Sin portada cargada.
                      </p>
                    )}
                    <label className="admin-file-field" htmlFor="project-cover-input">
                      <span>{cover ? 'Reemplazar portada' : 'Subir portada'}</span>
                      <input
                        id="project-cover-input"
                        type="file"
                        accept={IMAGE_ACCEPT}
                        onChange={(event) => void handleImageSelected('cover', event)}
                        disabled={uploadingKind !== ''}
                      />
                    </label>
                  </div>

                  <div className="admin-image-group">
                    <h4>Galería</h4>
                    {gallery.length === 0 ? (
                      <p className="admin-hint">Sin imágenes de galería.</p>
                    ) : (
                      <div className="admin-image-grid">
                        {gallery.map((image) => (
                          <figure className="admin-image-card" key={image.id}>
                            <img
                              src={projectMediaUrl(image)}
                              alt={image.altText || `Imagen de ${selectedProject.title}`}
                            />
                            <figcaption>
                              <span>{image.altText || 'Sin texto alternativo'}</span>
                              <button
                                className="button button-secondary admin-danger"
                                type="button"
                                onClick={() => void handleDeleteImage(image)}
                                disabled={deletingImageId === image.id}
                              >
                                {deletingImageId === image.id ? (
                                  <LoaderCircle className="spin" size={16} />
                                ) : (
                                  <Trash2 size={16} />
                                )}
                                Eliminar
                              </button>
                            </figcaption>
                          </figure>
                        ))}
                      </div>
                    )}
                    <label className="admin-file-field" htmlFor="project-gallery-input">
                      <span>Agregar imagen a la galería</span>
                      <input
                        id="project-gallery-input"
                        type="file"
                        accept={IMAGE_ACCEPT}
                        onChange={(event) => void handleImageSelected('gallery', event)}
                        disabled={uploadingKind !== ''}
                      />
                    </label>
                  </div>

                  <p className="admin-hint">
                    Formatos permitidos: JPEG, PNG o WebP hasta {formatBytes(maxImageBytes)}.
                    {uploadingKind ? ' Subiendo imagen…' : ''}
                  </p>
                </>
              )}
            </section>
          </form>
        ) : (
          <section className="admin-panel admin-empty">
            <h2>Editor</h2>
            <p className="admin-hint">
              Elegí “Editar” en un proyecto existente o creá uno nuevo para completar sus datos e imágenes.
            </p>
          </section>
        )}
      </div>
    </section>
  )
}
