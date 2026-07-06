import { ArrowLeft } from 'lucide-react'
import type { Route } from '../../app/routing'
import { PageLink } from '../PageLink'

export function DemoBackLink({ onNavigate }: { onNavigate: (path: Route) => void }) {
  return (
    <PageLink className="demo-back-link" href="/demos" onNavigate={onNavigate}>
      <ArrowLeft size={17} />
      Volver a demos
    </PageLink>
  )
}
