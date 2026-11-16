import { useState, type FormEvent } from 'react'
import { AuthApiError, login } from '../api/authClient'
import type { Route } from '../app/routing'

type LoginPageProps = {
  onNavigate: (path: Route) => void
}

export function LoginPage({ onNavigate }: LoginPageProps) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      await login(username.trim(), password)
      setPassword('')
      onNavigate('/admin')
    } catch (loginError) {
      setPassword('')
      setError(
        loginError instanceof AuthApiError
          ? loginError.message
          : 'No se pudo iniciar sesión. Intentá nuevamente.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <section className="login-page">
      <div className="login-card">
        <h1>Administración</h1>
        <p>Ingresá tus credenciales para gestionar proyectos.</p>
        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="username">Usuario</label>
            <input
              id="username"
              name="username"
              type="text"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              required
              autoComplete="username"
              autoFocus
            />
          </div>
          <div className="form-group">
            <label htmlFor="password">Contraseña</label>
            <input
              id="password"
              name="password"
              type="password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              required
              autoComplete="current-password"
            />
          </div>
          {error && (
            <p className="login-error" role="alert">
              {error}
            </p>
          )}
          <button type="submit" disabled={loading}>
            {loading ? 'Ingresando...' : 'Ingresar'}
          </button>
        </form>
      </div>
    </section>
  )
}
