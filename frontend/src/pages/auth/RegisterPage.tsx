import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { errorMessage } from '../../api/client'
import { homePath, useAuth } from '../../auth/AuthContext'
import { Button, ErrorBox, Field, Input } from '../../components/ui'
import { AuthShell } from './LoginPage'

export default function RegisterPage() {
  const { user, register } = useAuth()
  const navigate = useNavigate()
  const [fullName, setFullName] = useState('')
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
      navigate(homePath(await register(email, password, fullName)))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AuthShell title="Регистрация студента" lead="Аккаунты преподавателей создаёт администратор.">
      <form onSubmit={submit} className="space-y-4">
        <Field label="Имя и фамилия">
          <Input required autoFocus value={fullName} onChange={e => setFullName(e.target.value)} />
        </Field>
        <Field label="Email">
          <Input type="email" required value={email} onChange={e => setEmail(e.target.value)} />
        </Field>
        <Field label="Пароль" hint="Не короче 6 символов">
          <Input type="password" required minLength={6} value={password} onChange={e => setPassword(e.target.value)} />
        </Field>
        <ErrorBox message={error} />
        <Button type="submit" loading={busy} className="w-full">Создать аккаунт</Button>
      </form>
      <p className="mt-4 text-center text-sm text-slate-500">
        Уже есть аккаунт? <Link to="/login" className="font-medium text-brand-700 hover:underline">Войти</Link>
      </p>
    </AuthShell>
  )
}
