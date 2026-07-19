import { Github, Info, Linkedin, Mail, Moon, Sparkles, Sun } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import type { Route } from './app/routing'
import {
  getPageTitle,
  isPrimaryRouteActive,
  resolveRoute,
  routePathFromLocation,
  withBasePath,
} from './app/routing'
import { getInitialTheme, isMobileThemeLocked, persistTheme } from './app/theme'
import { AboutPortfolioModal } from './components/AboutPortfolioModal'
import { PageLink } from './components/PageLink'
import { primaryNavItems, profileLinks } from './data/siteContent'
import { ContactPage } from './pages/ContactPage'
import { AgentChatDemoPage, DocumentDemoPage, MedicalAppointmentDemoPage } from './pages/DemoDetailPages'
import { DemosPage } from './pages/DemosPage'
import { HomePage } from './pages/HomePage'
import { ProjectDetailPage } from './pages/ProjectDetailPage'
import { ProjectsPage } from './pages/ProjectsPage'

function App() {
  const [route, setRoute] = useState<Route>(() => resolveRoute(window.location.pathname))
  const [theme, setTheme] = useState(getInitialTheme)
  const [isAboutModalOpen, setIsAboutModalOpen] = useState(false)
  const pageTitle = useMemo(() => getPageTitle(route), [route])

  useEffect(() => {
    persistTheme(theme)
  }, [theme])

  useEffect(() => {
    const mediaQuery = window.matchMedia('(max-width: 720px)')
    const forceLightThemeOnMobile = () => {
      if (isMobileThemeLocked()) {
        setTheme('light')
      }
    }

    forceLightThemeOnMobile()
    mediaQuery.addEventListener('change', forceLightThemeOnMobile)
    return () => mediaQuery.removeEventListener('change', forceLightThemeOnMobile)
  }, [])

  useEffect(() => {
    const currentRoute = resolveRoute(window.location.pathname)

    if (routePathFromLocation(window.location.pathname) !== currentRoute) {
      window.history.replaceState({}, '', withBasePath(currentRoute))
    }

    const handlePopState = () => setRoute(resolveRoute(window.location.pathname))
    window.addEventListener('popstate', handlePopState)
    return () => window.removeEventListener('popstate', handlePopState)
  }, [])

  useEffect(() => {
    if (!isAboutModalOpen) {
      return
    }

    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsAboutModalOpen(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)

    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [isAboutModalOpen])

  function navigate(path: Route) {
    window.history.pushState({}, '', withBasePath(path))
    setRoute(path)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  function toggleTheme() {
    setTheme((current) => (current === 'dark' ? 'light' : 'dark'))
  }

  return (
    <div className="app-shell">
      <header className="nav">
        <PageLink className="brand" href="/" onNavigate={navigate} aria-label="Ir a inicio">
          <span>SG</span>
          <strong>Sebastian Gatica</strong>
        </PageLink>
        <div className="nav-actions">
          <nav className="desktop-nav" aria-label="Navegacion principal">
            {primaryNavItems.map((item) => (
              <PageLink
                href={item.href}
                key={item.href}
                onNavigate={navigate}
                active={isPrimaryRouteActive(item.href, route)}
              >
                {item.label}
              </PageLink>
            ))}
          </nav>
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Cambiar a tema claro' : 'Cambiar a tema oscuro'}
            title={theme === 'dark' ? 'Tema claro' : 'Tema oscuro'}
          >
            {theme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}
          </button>
        </div>
      </header>

      <nav className="mobile-tabbar" aria-label="Navegacion principal movil">
        {primaryNavItems.map((item) => (
          <PageLink
            href={item.href}
            key={item.href}
            onNavigate={navigate}
            active={isPrimaryRouteActive(item.href, route)}
          >
            <item.icon size={19} />
            <span>{item.label}</span>
          </PageLink>
        ))}
      </nav>

      <main className="page-shell" id="top">
        {route === '/' && <HomePage onNavigate={navigate} />}
        {route === '/demos' && <DemosPage onNavigate={navigate} />}
        {(route === '/demos/chat' || route === '/demo') && (
          <AgentChatDemoPage title={pageTitle} onNavigate={navigate} />
        )}
        {route === '/demos/turnos' && <MedicalAppointmentDemoPage onNavigate={navigate} />}
        {route === '/demos/documentos' && <DocumentDemoPage onNavigate={navigate} />}
        {route === '/proyectos' && <ProjectsPage onNavigate={navigate} />}
        {route.startsWith('/proyectos/') && (
          <ProjectDetailPage slug={route.replace('/proyectos/', '')} onNavigate={navigate} />
        )}
        {route === '/contacto' && <ContactPage />}
      </main>

      <footer className="footer">
        <div>
          <strong>Sebastian Gatica</strong>
          <span>Portfolio interactivo de demos con IA aplicada.</span>
        </div>
        <div className="footer-links">
          <PageLink href="/" onNavigate={navigate}>
            <Sparkles size={18} />
            Inicio
          </PageLink>
          <PageLink href="/demos" onNavigate={navigate}>
            <Sparkles size={18} />
            Demos
          </PageLink>
          <PageLink href="/proyectos" onNavigate={navigate}>
            <Sparkles size={18} />
            Proyectos
          </PageLink>
          <PageLink href="/contacto" onNavigate={navigate}>
            <Mail size={18} />
            Contacto
          </PageLink>
          <a href={profileLinks.linkedin} target="_blank" rel="noreferrer">
            <Linkedin size={18} />
            LinkedIn
          </a>
          <a href={profileLinks.github} target="_blank" rel="noreferrer">
            <Github size={18} />
            GitHub
          </a>
          <button
            className="footer-about-button"
            type="button"
            onClick={() => setIsAboutModalOpen(true)}
          >
            <Info size={17} />
            Sobre este porfolio
          </button>
        </div>
      </footer>

      {isAboutModalOpen && <AboutPortfolioModal onClose={() => setIsAboutModalOpen(false)} />}
    </div>
  )
}

export default App
