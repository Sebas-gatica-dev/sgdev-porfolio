import { BrainCircuit, X } from 'lucide-react'

export function AboutPortfolioModal({ onClose }: { onClose: () => void }) {
  return (
    <div
      className="about-modal-backdrop"
      role="presentation"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose()
        }
      }}
    >
      <section
        className="about-modal"
        role="dialog"
        aria-modal="true"
        aria-labelledby="about-portfolio-title"
      >
        <header className="about-modal-header">
          <div className="about-modal-title-block">
            <span className="about-modal-mark">
              <BrainCircuit size={22} />
            </span>
            <div>
              <span className="about-modal-kicker">IA aplicada</span>
              <h2 id="about-portfolio-title">Sobre este porfolio</h2>
            </div>
          </div>
          <button
            type="button"
            className="about-modal-close"
            onClick={onClose}
            aria-label="Cerrar"
            autoFocus
          >
            <X size={18} />
          </button>
        </header>

        <div className="about-modal-body">
          <p className="about-modal-lead">
            Este porfolio fue creado con ayuda de herramientas de IA, principalmente Codex.
            En realidad, fue el comienzo de algo mucho más profundo.
          </p>

          <ul className="about-modal-points">
            <li>
              Sus demos se alimentan principalmente de modelos mini de OpenAI, y pronto voy a
              subir nuevos proyectos en OpenClaw.
            </li>
            <li>
              Google ADK funciona como un orquestador de instancias de LLM ligadas a distintas
              integraciones, prompts y funcionalidades conectables al backend.
            </li>
            <li>
              En mi experiencia laboral, también creamos herramientas para ayudar a otros equipos
              a codear: agentes generadores de código que consumían metadata empresarial cargada
              de forma vectorial.
            </li>
          </ul>
        </div>
      </section>
    </div>
  )
}
