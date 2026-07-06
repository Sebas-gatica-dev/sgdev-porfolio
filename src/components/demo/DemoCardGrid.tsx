import { ArrowRight, Check } from 'lucide-react'
import type { Route } from '../../app/routing'
import { demoCards } from '../../data/siteContent'
import { PageLink } from '../PageLink'

export function DemoCardGrid({
  demos,
  onNavigate,
}: {
  demos: typeof demoCards
  onNavigate: (path: Route) => void
}) {
  return (
    <div className="demo-card-grid">
      {demos.map((demo) => (
        <PageLink
          className="demo-card demo-card-link"
          href={demo.route}
          onNavigate={onNavigate}
          aria-label={`Abrir ${demo.title}`}
          key={demo.title}
        >
          <div className="demo-card-top">
            <demo.icon size={24} />
            <span>{demo.status}</span>
          </div>
          <span className="demo-eyebrow">{demo.eyebrow}</span>
          <h3>{demo.title}</h3>
          <p>{demo.summary}</p>
          <ul>
            {demo.points.map((point) => (
              <li key={point}>
                <Check size={15} />
                {point}
              </li>
            ))}
          </ul>
          <span className="demo-card-action">
            Abrir demo
            <ArrowRight size={17} />
          </span>
        </PageLink>
      ))}
    </div>
  )
}
