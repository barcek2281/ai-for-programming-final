import { useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { SessionState } from '../../api/types'
import { LANGUAGES, ModeBadge, SoloBadge } from '../../components/labels'
import { Button, Card, DevBadge, ErrorBox, InfoBox, PageHeader } from '../../components/ui'
import { loadIntro } from './JoinPage'

/** Перед интервью: правила, режим, рубрика SOLO простыми словами и согласие на обработку ответов. */
export default function StartPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const saved = loadIntro(id!)
  const [consent, setConsent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (!saved) return <Navigate to="/s/join" replace />
  const { intro, accessCode } = saved

  async function start() {
    setBusy(true)
    setError(null)
    try {
      const session = await api<SessionState>(`/exams/${intro.id}/sessions`, {
        method: 'POST',
        body: { consent: true, accessCode },
      })
      navigate(`/s/sessions/${session.id}`)
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={intro.title}
        subtitle={<span className="flex flex-wrap items-center gap-2">{intro.courseTitle} <ModeBadge mode={intro.mode} /></span>}
      />

      <div className="space-y-5">
        <Card className="p-5">
          <div className="grid gap-4 text-sm sm:grid-cols-3">
            <Fact label="Вопросов" value={String(intro.questionsCount)} />
            <Fact label="Время на ответ" value={`${intro.answerTimeLimitSec} с`} />
            <Fact label="Язык интервью" value={LANGUAGES[intro.language] ?? intro.language} />
          </div>
          <div className="mt-4 text-sm">
            <div className="mb-1 font-medium text-slate-700">Темы</div>
            <ul className="list-inside list-disc text-slate-600">
              {intro.topics.map(t => <li key={t}>{t}</li>)}
            </ul>
          </div>
        </Card>

        <Card className="p-5">
          <h2 className="mb-3 font-semibold">Правила</h2>
          <ol className="list-inside list-decimal space-y-2 text-sm text-slate-700">
            {intro.rules.map((rule, i) => <li key={i}>{rule}</li>)}
          </ol>
        </Card>

        <Card className="p-5">
          <h2 className="mb-1 font-semibold">Как оцениваются ответы</h2>
          <p className="mb-3 text-sm text-slate-500">
            Таксономия SOLO: оценивается не объём ответа, а глубина понимания. Правильный, но заученный ответ без
            объяснения не даёт высокого уровня.
          </p>
          <div className="space-y-2">
            {intro.soloRubric.map(level => (
              <div key={level.level} className="flex flex-col gap-1 text-sm sm:flex-row sm:items-start sm:gap-3">
                <div className="shrink-0 sm:w-56"><SoloBadge level={level.level} /></div>
                <div className="text-slate-600">{level.description}</div>
              </div>
            ))}
          </div>
        </Card>

        <Card className="p-5">
          <h2 className="mb-3 font-semibold">Настройки</h2>
          <div className="space-y-3 text-sm">
            <MockToggle label="Голос: отвечать устно и слушать вопросы" />
            <MockToggle label="Выбрать другой язык интервью (сейчас язык задаёт курс)" />
          </div>
        </Card>

        {intro.attemptUsed ? (
          <InfoBox tone="amber">
            Вы уже использовали попытку в этом экзамене. <Link className="font-medium underline" to="/s/history">Мои интервью</Link>
          </InfoBox>
        ) : (
          <Card className="p-5">
            {intro.activeSessionId ? (
              <InfoBox>У вас есть незаконченное интервью — оно продолжится с того же места.</InfoBox>
            ) : (
              <label className="flex cursor-pointer items-start gap-3 text-sm">
                <input type="checkbox" className="mt-0.5 h-4 w-4 accent-teal-700" checked={consent} onChange={e => setConsent(e.target.checked)} />
                <span>
                  Я согласен(на) на обработку моих ответов ИИ-моделью для проведения интервью и подготовки черновика
                  оценки. Моё имя, email и другие персональные данные в модель не передаются.
                </span>
              </label>
            )}
            <div className="mt-4 space-y-3">
              <ErrorBox message={error} />
              <Button onClick={start} loading={busy} disabled={!consent && !intro.activeSessionId}>
                {intro.activeSessionId ? 'Продолжить интервью' : 'Начать интервью'}
              </Button>
            </div>
          </Card>
        )}
      </div>
    </div>
  )
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wide text-slate-400">{label}</div>
      <div className="mt-0.5 text-base font-semibold text-slate-800">{value}</div>
    </div>
  )
}

/** Переключатель-макет: выключен и честно помечен «В разработке». */
function MockToggle({ label }: { label: string }) {
  return (
    <div className="flex items-center gap-3 text-slate-400">
      <span className="relative inline-block h-5 w-9 shrink-0 rounded-full bg-slate-200" aria-disabled>
        <span className="absolute left-0.5 top-0.5 h-4 w-4 rounded-full bg-white shadow" />
      </span>
      <span>{label}</span>
      <DevBadge />
    </div>
  )
}
