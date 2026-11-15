import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { ExamIntro } from '../../api/types'
import { Button, Card, ErrorBox, Input, PageHeader } from '../../components/ui'

/** Данные экзамена и код доступа храним до старта в sessionStorage — чтобы экран правил пережил перезагрузку. */
export function saveIntro(intro: ExamIntro, accessCode: string) {
  sessionStorage.setItem(`vm_exam_${intro.id}`, JSON.stringify({ intro, accessCode }))
}

export function loadIntro(examId: string): { intro: ExamIntro; accessCode: string } | null {
  const raw = sessionStorage.getItem(`vm_exam_${examId}`)
  return raw ? JSON.parse(raw) : null
}

export default function JoinPage() {
  const navigate = useNavigate()
  const [code, setCode] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const accessCode = code.trim().toUpperCase()
      const intro = await api<ExamIntro>('/exams/join', { method: 'POST', body: { accessCode } })
      saveIntro(intro, accessCode)
      navigate(`/s/exams/${intro.id}/start`)
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-md">
      <PageHeader title="Войти в экзамен" subtitle="Введите код, который дал преподаватель." />
      <Card className="p-6">
        <form onSubmit={submit} className="space-y-4">
          <Input
            autoFocus
            required
            value={code}
            onChange={e => setCode(e.target.value.toUpperCase())}
            placeholder="КОД"
            maxLength={8}
            className="text-center font-mono text-2xl tracking-[0.3em] uppercase"
          />
          <ErrorBox message={error} />
          <Button type="submit" loading={busy} className="w-full">Продолжить</Button>
        </form>
      </Card>
      <p className="mt-4 text-center text-xs text-slate-500">
        На следующем шаге вы увидите правила и рубрику оценивания. Интервью начнётся только после вашего согласия.
      </p>
    </div>
  )
}
