import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { homePath, useAuth } from '../../auth/AuthContext'
import { Logo } from '../../components/Layout'
import { Button, Card, ErrorBox, Field, Input } from '../../components/ui'

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (user) return <Navigate to={homePath(user)} replace />

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      navigate(homePath(await login(email, password)))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell
      title="Вход"
      lead="Устное интервью с ИИ, которое проверяет понимание, а не заучивание."
    >
      <form onSubmit={submit} className="space-y-4">
        <Field label="Email">
          <Input type="email" required autoFocus value={email} onChange={e => setEmail(e.target.value)} placeholder="you@university.kz" />
        </Field>
        <Field label="Пароль">
          <Input type="password" required value={password} onChange={e => setPassword(e.target.value)} />
        </Field>
        <ErrorBox message={error} />
        <Button type="submit" loading={busy} className="w-full">Войти</Button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-500">
        Студент и ещё нет аккаунта? <Link to="/register" className="font-medium text-brand-700 hover:underline">Зарегистрироваться</Link>
      </p>
      <div className="mt-5 rounded-lg bg-slate-50 px-3 py-2 text-xs leading-relaxed text-slate-500">
        Демо-аккаунты: <code>teacher@vivamind.local</code> и <code>student@vivamind.local</code>.
        Пароль — значение <code>SEED_DEMO_PASSWORD</code> из файла <code>.env</code>.
      </div>
    </AuthShell>
  )
}

export function AuthShell({ title, lead, children }: { title: string; lead: string; children: ReactNode }) {
  return (
    <div className="flex min-h-full items-center justify-center px-4 py-10">
      <div className="w-full max-w-sm">
        <div className="mb-6 flex flex-col items-center text-center">
          <Logo />
          <p className="mt-3 text-sm text-slate-500">{lead}</p>
        </div>
        <Card className="p-6">
          <h1 className="mb-4 text-lg font-semibold">{title}</h1>
          {children}
        </Card>
      </div>
    </div>
  )
}
