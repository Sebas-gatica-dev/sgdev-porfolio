import { Github, Linkedin, Mail, Send } from 'lucide-react'
import type { FormEvent } from 'react'
import { useState } from 'react'
import { sendContactMessage } from '../api/agentClient'
import { profileLinks } from '../data/siteContent'

export function ContactPage() {
  const [form, setForm] = useState({
    name: '',
    email: '',
    company: '',
    message: '',
  })
  const [status, setStatus] = useState<'idle' | 'submitting' | 'success' | 'error'>('idle')
  const [statusMessage, setStatusMessage] = useState('')

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setStatus('submitting')
    setStatusMessage('')

    try {
      const response = await sendContactMessage(form)
      setStatus('success')
      setStatusMessage(response.message)
      setForm({
        name: '',
        email: '',
        company: '',
        message: '',
      })
    } catch (error) {
      setStatus('error')
      setStatusMessage(error instanceof Error ? error.message : 'No pude enviar el mensaje.')
    }
  }

  return (
    <section className="contact-page">
      <div className="contact-copy">
        <div className="section-kicker">
          <Mail size={18} />
          Contacto
        </div>
        <h1>Potencia proyectos y negocios con IA.</h1>
        <p>
          Brindo servicios de desarrollo full stack para empresas y particulares. Si queres evaluar
          una integracion, un MVP o una automatizacion con IA, podes escribirme y te respondere a la
          brevedad.
        </p>

        <div className="contact-actions" aria-label="Canales de contacto">
          <a href={profileLinks.linkedin} target="_blank" rel="noreferrer">
            <Linkedin size={19} />
            LinkedIn
          </a>
          <a href={profileLinks.github} target="_blank" rel="noreferrer">
            <Github size={19} />
            GitHub
          </a>
        </div>
      </div>

      <form className="contact-form-panel" onSubmit={handleSubmit}>
        <div className="contact-form-header">
          <span className="contact-form-icon" aria-hidden="true">
            <Send size={18} />
          </span>
          <div>
            <span className="contact-form-channel">Email</span>
            <strong>Comunicate por mail</strong>
            <small>Compartime el contexto del proyecto y coordinamos los proximos pasos.</small>
          </div>
        </div>

        <label>
          Nombre
          <input
            name="name"
            autoComplete="name"
            value={form.name}
            onChange={(event) => setForm({ ...form, name: event.target.value })}
            required
          />
        </label>

        <label>
          Email
          <input
            name="email"
            type="email"
            autoComplete="email"
            value={form.email}
            onChange={(event) => setForm({ ...form, email: event.target.value })}
            required
          />
        </label>

        <label>
          Empresa
          <input
            name="company"
            autoComplete="organization"
            value={form.company}
            onChange={(event) => setForm({ ...form, company: event.target.value })}
          />
        </label>

        <label>
          Mensaje
          <textarea
            name="message"
            rows={5}
            value={form.message}
            onChange={(event) => setForm({ ...form, message: event.target.value })}
            required
          />
        </label>

        <button type="submit" disabled={status === 'submitting'}>
          <Send size={18} />
          {status === 'submitting' ? 'Enviando...' : 'Enviar consulta'}
        </button>

        {statusMessage && (
          <p className={`contact-form-status contact-form-status-${status}`}>{statusMessage}</p>
        )}
      </form>
    </section>
  )
}
