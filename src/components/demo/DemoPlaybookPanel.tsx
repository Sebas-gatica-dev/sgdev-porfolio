import { BrainCircuit } from 'lucide-react'
import { demoPlaybooks } from '../../data/portfolio'

export function DemoPlaybookPanel({ playbookId }: { playbookId: keyof typeof demoPlaybooks }) {
  const playbook = demoPlaybooks[playbookId]

  return (
    <aside className="demo-playbook-card">
      <div className="demo-playbook-header">
        <BrainCircuit size={20} />
        <div>
          <span>Como se prueba</span>
          <strong>{playbook.agent}</strong>
        </div>
      </div>
      <dl>
        <div>
          <dt>Workflow</dt>
          <dd>{playbook.workflow}</dd>
        </div>
        <div>
          <dt>Tools</dt>
          <dd>{playbook.tools}</dd>
        </div>
        <div>
          <dt>State</dt>
          <dd>{playbook.state}</dd>
        </div>
        <div>
          <dt>Evaluacion</dt>
          <dd>{playbook.evaluation}</dd>
        </div>
      </dl>
    </aside>
  )
}
