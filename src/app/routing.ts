export type Route =
  | '/'
  | '/demos'
  | '/demos/chat'
  | '/demos/turnos'
  | '/demos/documentos'
  | '/demo'
  | '/contacto'

export const APP_BASE_PATH = normalizeBasePath(import.meta.env.BASE_URL)

export const routes: Route[] = [
  '/',
  '/demos',
  '/demos/chat',
  '/demos/turnos',
  '/demos/documentos',
  '/demo',
  '/contacto',
]

export function resolveRoute(pathname: string): Route {
  const routePath = routePathFromLocation(pathname)
  return routes.includes(routePath as Route) ? (routePath as Route) : '/'
}

export function routePathFromLocation(pathname: string) {
  const base = APP_BASE_PATH.replace(/\/$/, '')

  if (!base) {
    return pathname || '/'
  }

  if (pathname === base) {
    return '/'
  }

  if (pathname.startsWith(`${base}/`)) {
    return pathname.slice(base.length) || '/'
  }

  return pathname || '/'
}

export function withBasePath(path: Route) {
  const base = APP_BASE_PATH.replace(/\/$/, '')

  if (!base) {
    return path
  }

  return path === '/' ? `${base}/` : `${base}${path}`
}

export function assetPath(path: string) {
  return `${APP_BASE_PATH}${path.replace(/^\/+/, '')}`
}

export function normalizeBasePath(value: string) {
  const trimmed = value.trim()

  if (!trimmed || trimmed === '/') {
    return '/'
  }

  return `/${trimmed.replace(/^\/+|\/+$/g, '')}/`
}

export function getPageTitle(route: Route) {
  if (route === '/') {
    return 'Inicio'
  }
  if (route === '/demos' || route === '/demo') {
    return 'Demos'
  }
  if (route === '/demos/chat') {
    return 'Demo principal'
  }
  if (route === '/demos/turnos') {
    return 'Demo turnos'
  }
  if (route === '/demos/documentos') {
    return 'Demo documentos'
  }
  if (route === '/contacto') {
    return 'Contacto'
  }
  return 'Inicio'
}

export function isPrimaryRouteActive(itemPath: Route, currentRoute: Route) {
  if (itemPath === '/demos') {
    return currentRoute === '/demos' || currentRoute.startsWith('/demos') || currentRoute === '/demo'
  }

  return currentRoute === itemPath
}
