export type StaticRoute =
  | '/'
  | '/demos'
  | '/demos/chat'
  | '/demos/turnos'
  | '/demos/documentos'
  | '/demo'
  | '/proyectos'
  | '/contacto'

export type Route = StaticRoute | `/proyectos/${string}`

export const APP_BASE_PATH = normalizeBasePath(import.meta.env.BASE_URL)

export const routes: StaticRoute[] = [
  '/',
  '/demos',
  '/demos/chat',
  '/demos/turnos',
  '/demos/documentos',
  '/demo',
  '/proyectos',
  '/contacto',
]

export function resolveRoute(pathname: string): Route {
  const routePath = routePathFromLocation(pathname)
  if (routes.includes(routePath as StaticRoute)) {
    return routePath as StaticRoute
  }

  if (/^\/proyectos\/[a-z0-9][a-z0-9-]{1,118}[a-z0-9]$/.test(routePath)) {
    return routePath as Route
  }

  return '/'
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
  if (route === '/proyectos') {
    return 'Proyectos'
  }
  if (route.startsWith('/proyectos/')) {
    return 'Proyecto'
  }
  return 'Inicio'
}

export function isPrimaryRouteActive(itemPath: Route, currentRoute: Route) {
  if (itemPath === '/demos') {
    return currentRoute === '/demos' || currentRoute.startsWith('/demos') || currentRoute === '/demo'
  }

  if (itemPath === '/proyectos') {
    return currentRoute === '/proyectos' || currentRoute.startsWith('/proyectos/')
  }

  return currentRoute === itemPath
}
