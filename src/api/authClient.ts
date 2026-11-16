export type AdminSession = {
  authenticated: boolean
  username: string | null
}

export class AuthApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'AuthApiError'
    this.status = status
  }
}

const BASE_PATH = import.meta.env.BASE_URL.replace(/\/$/, '')
const API_BASE_PATH = `${BASE_PATH}/api`

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
    // El servidor puede responder sin cuerpo JSON; se usa el mensaje por defecto.
  }
  return fallback
}

function toSession(payload: unknown): AdminSession {
  const candidate = payload as { authenticated?: unknown; username?: unknown } | null
  return {
    authenticated: candidate?.authenticated === true,
    username: typeof candidate?.username === 'string' ? candidate.username : null,
  }
}

/** Nunca rompe la UI: ante error de red o respuesta inválida devuelve sesión anónima. */
export async function getSession(): Promise<AdminSession> {
  try {
    const response = await fetch(apiPath('/auth/session'), {
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
    if (!response.ok) {
      return { authenticated: false, username: null }
    }
    return toSession(await response.json())
  } catch {
    return { authenticated: false, username: null }
  }
}

export async function login(username: string, password: string): Promise<AdminSession> {
  let response: Response
  try {
    response = await fetch(apiPath('/auth/login'), {
      method: 'POST',
      credentials: 'include',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ username, password }),
    })
  } catch {
    throw new AuthApiError('No se pudo conectar con el servidor. Reintentá en unos segundos.', 0)
  }

  if (!response.ok) {
    const message = await readMessage(
      response,
      response.status === 401
        ? 'Usuario o contraseña incorrectos.'
        : `No se pudo iniciar sesión (${response.status}).`,
    )
    throw new AuthApiError(message, response.status)
  }

  const session = toSession(await response.json())
  if (!session.authenticated) {
    throw new AuthApiError('El servidor no confirmó la sesión. Reintentá.', 200)
  }
  return session
}

export async function logout(): Promise<void> {
  let response: Response
  try {
    response = await fetch(apiPath('/auth/logout'), {
      method: 'POST',
      credentials: 'include',
      headers: { Accept: 'application/json' },
    })
  } catch {
    throw new AuthApiError('No se pudo conectar con el servidor para cerrar sesión.', 0)
  }

  // 401 significa que ya no hay sesión: el resultado esperado del logout ya está dado.
  if (response.ok || response.status === 401) {
    return
  }
  throw new AuthApiError(
    await readMessage(response, `No se pudo cerrar la sesión (${response.status}).`),
    response.status,
  )
}
