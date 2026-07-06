import Atropos from 'atropos/react'
import { ArrowRight, ShieldCheck, Sparkles, Mail } from 'lucide-react'
import type { Route } from '../app/routing'
import { assetPath } from '../app/routing'
import { PageLink } from '../components/PageLink'
import { capabilityLayers, qualityGates, stack } from '../data/portfolio'
import {
  demoCards,
  professionalCapabilities,
  professionalProfile,
  professionalStackRows,
  professionalStats,
} from '../data/siteContent'

export function HomePage({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <>
      <section className="hero-band">
        <div className="hero-copy">
          <div className="section-kicker">
            <Sparkles size={18} />
            Sebastian Gatica
          </div>
          <h1>Java Full Stack + IA aplicada.</h1>
          <p className="hero-lead">
            Soy Sebastian Gatica. Desarrollo soluciones a medida con Spring, React,
            LLMs, voz realtime, APIs y flujos de negocio usables.
          </p>

          <div className="hero-actions">
            <PageLink className="button button-primary" href="/demos" onNavigate={onNavigate}>
              Ver demos
              <ArrowRight size={18} />
            </PageLink>
            <PageLink className="button button-secondary" href="/contacto" onNavigate={onNavigate}>
              Contacto
              <Mail size={18} />
            </PageLink>
          </div>

          <div className="stack-strip" aria-label="Stack principal">
            {stack.slice(0, 8).map((item) => (
              <span key={item}>{item}</span>
            ))}
          </div>
        </div>

        <Atropos
          className="hero-tilt tilt-wrap"
          activeOffset={18}
          rotateXMax={4}
          rotateYMax={6}
          shadow={false}
          highlight={false}
          aria-label="Resumen del portfolio"
        >
          <div className="hero-visual">
            <div className="visual-header" data-atropos-offset="-1">
              <img src={assetPath('favicon.svg')} alt="" />
              <div>
                <strong>SG AI portfolio</strong>
                <span>React, WebFlux, LLMs y workflows funcionales.</span>
              </div>
            </div>

            <div className="signal-grid" aria-label="Indicadores">
              <div className="metric" data-atropos-offset="2">
                <strong>3</strong>
                <span>Demos integradas</span>
              </div>
              <div className="metric" data-atropos-offset="3">
                <strong>SSE</strong>
                <span>Streaming real</span>
              </div>
              <div className="metric" data-atropos-offset="3">
                <strong>RT</strong>
                <span>Voz realtime</span>
              </div>
              <div className="metric" data-atropos-offset="2">
                <strong>PDF</strong>
                <span>Analisis, procesamiento de documentos</span>
              </div>
            </div>

            <div className="flow-lines" aria-label="Flujo tecnico" data-atropos-offset="4">
              <span>React</span>
              <ArrowRight size={16} />
              <span>WebFlux</span>
              <ArrowRight size={16} />
              <span>LLMs</span>
            </div>

            <div className="runtime-mini-map" data-atropos-offset="3">
              {capabilityLayers.slice(0, 5).map((layer) => (
                <article key={layer.title}>
                  <layer.icon size={18} />
                  <strong>{layer.title}</strong>
                  <span>{layer.signal}</span>
                </article>
              ))}
            </div>

            <div className="hero-demo-list" data-atropos-offset="2">
              {demoCards.slice(0, 3).map((demo) => (
                <PageLink
                  className="hero-demo-link"
                  href={demo.route}
                  key={demo.title}
                  onNavigate={onNavigate}
                >
                  <demo.icon size={18} />
                  <span>{demo.title}</span>
                  <ArrowRight size={16} />
                </PageLink>
              ))}
            </div>
          </div>
        </Atropos>
      </section>

      <ProfessionalProfileSection />

      <section className="skill-snapshot-band">
        <div className="skill-snapshot-copy">
          <div className="section-kicker">
            <ShieldCheck size={18} />
            Criterio Tecnico
          </div>
          <h2>El desarrollar flujos utiles, optimizar procesos.</h2>
          <p>
            El portfolio prioriza interaccion real, datos vivos, fallback y acciones seguras
            para que cada demo muestre criterios de producto, solo la punta del iceberg de funcionalidades que se pueden implementar para optimizar o imnovar.
          </p>
        </div>

        <div className="skill-pill-grid">
          {qualityGates.map((gate) => (
            <article className="skill-pill" key={gate.title}>
              <gate.icon size={20} />
              <div>
                <strong>{gate.title}</strong>
                <span>{gate.detail}</span>
              </div>
            </article>
          ))}
        </div>
      </section>

      <section className="home-demos-cta" aria-label="Ver mas demos">
        <span>Tambien podes probar las experiencias completas.</span>
        <PageLink href="/demos" onNavigate={onNavigate}>
          Conoce las demos
          <ArrowRight size={16} />
        </PageLink>
      </section>
    </>
  )
}

function ProfessionalProfileSection() {
  return (
    <section className="professional-profile-band">
      <div className="professional-profile-top">
        <div className="professional-profile-copy">
          <div className="section-kicker">
            <ShieldCheck size={18} />
            Perfil profesional
          </div>
          <h2>Construyo software full stack con criterio de backend, producto e IA.</h2>
        </div>

        <div className="professional-capability-grid">
          {professionalCapabilities.map((capability) => (
            <article className="professional-capability-card" key={capability.title}>
              <capability.icon size={21} />
              <div>
                <strong>{capability.title}</strong>
                <span>{capability.text}</span>
              </div>
            </article>
          ))}
        </div>
      </div>

      <div className="professional-summary-panel">
        <div className="professional-summary-copy">
          <div className="section-kicker">
            <Sparkles size={18} />
            Resumen profesional
          </div>
          <h3>Desarrollador Full Stack Java con enfoque en producto e IA.</h3>
          <div className="professional-profile-text">
            {professionalProfile.map((item) => (
              <p key={item}>{item}</p>
            ))}
          </div>

          <div className="professional-stat-grid">
            {professionalStats.map((stat) => (
              <article key={stat.value}>
                <stat.icon size={21} />
                <div>
                  <strong>{stat.value}</strong>
                  <span>{stat.label}</span>
                </div>
              </article>
            ))}
          </div>
        </div>

        <div className="professional-stack-list">
          {professionalStackRows.map((row) => (
            <article className="professional-stack-row" key={row.title}>
              <span className="professional-stack-mark">
                <row.icon size={31} />
              </span>
              <div className="professional-stack-copy">
                <strong>{row.title}</strong>
                <span>{row.text}</span>
              </div>
              <div className="professional-tech-tags">
                {row.tags.map((tag) => (
                  <span key={`${row.title}-${tag}`}>{tag}</span>
                ))}
              </div>
            </article>
          ))}
        </div>
      </div>
    </section>
  )
}
