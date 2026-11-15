import { useEffect, useState, type FormEvent } from 'react'
import { api, errorMessage } from '../../api/client'
import type { User } from '../../api/types'
import { Button, Card, Empty, ErrorBox, Field, Input, Loading, PageHeader } from '../../components/ui'

/** Администратор создаёт аккаунты преподавателей. */
export default function TeachersPage() {
  const [teachers, setTeachers] = useState<User[] | null>(null)
  const [form, setForm] = useState({ fullName: '', email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = () => api<User[]>('/admin/teachers').then(setTeachers).catch(e => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api('/admin/teachers', { method: 'POST', body: form })
      setForm({ fullName: '', email: '', password: '' })
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <PageHeader title="Преподаватели" subtitle="Студенты регистрируются сами, преподавателей добавляет администратор." />
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <div>
          {!teachers ? <Loading /> : teachers.length === 0 ? <Empty>Преподавателей пока нет.</Empty> : (
            <Card className="divide-y divide-slate-100">
              {teachers.map(t => (
                <div key={t.id} className="px-5 py-3">
                  <div className="font-medium">{t.fullName}</div>
                  <div className="text-sm text-slate-500">{t.email}</div>
                </div>
              ))}
            </Card>
          )}
        </div>
        <Card className="h-fit p-5">
          <h2 className="mb-3 font-semibold">Новый преподаватель</h2>
          <form onSubmit={submit} className="space-y-3">
            <Field label="Имя и фамилия">
              <Input required value={form.fullName} onChange={e => setForm({ ...form, fullName: e.target.value })} />
            </Field>
            <Field label="Email">
              <Input type="email" required value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} />
            </Field>
            <Field label="Пароль" hint="Не короче 6 символов">
              <Input type="password" required minLength={6} value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} />
            </Field>
            <ErrorBox message={error} />
            <Button type="submit" loading={busy} className="w-full">Создать</Button>
          </form>
        </Card>
      </div>
    </div>
  )
}
