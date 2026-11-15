import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { Report, ReportQuestion } from '../../api/types'
import { AvgLevel, ModeBadge, SoloBadge } from '../../components/labels'
import { Button, Card, ErrorBox, formatDate, formatScore, InfoBox, Loading, PageHeader, Spinner, TextArea } from '../../components/ui'

/** Отчёт студента: по темам курса и вопросам — уровень, что получилось (цитаты), пробелы, что перечитать. */
export default function ReportPage() {
  const { id } = useParams()
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setReport(await api<Report>(`/sessions/${id}/report`))
      setError(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  // Пока ИИ оценивает (или преподаватель проверяет) — тихо перепроверяем.
  const waiting = report && !report.available && (report.status === 'COMPLETED' || report.status === 'AI_GRADED')
  useEffect(() => {
    if (!waiting) return
    const timer = setInterval(load, report?.status === 'COMPLETED' ? 4000 : 15000)
    return () => clearInterval(timer)
  }, [waiting, report?.status, load])

  if (error && !report) return <ErrorBox message={error} />
  if (!report) return <Loading />

  if (!report.available) {
    return (
      <div className="mx-auto max-w-xl">
        <PageHeader title={report.examTitle} subtitle={report.courseTitle} />
        <Card className="flex items-center gap-4 p-6 text-sm text-slate-700">
          {report.status === 'COMPLETED' && <Spinner />}
          <div>{report.message}</div>
        </Card>
        <Link to="/s/history" className="mt-4 inline-block text-sm text-brand-700 hover:underline">← Мои интервью</Link>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        title={report.examTitle}
        subtitle={<span className="flex items-center gap-2">{report.courseTitle} <ModeBadge mode={report.mode} /></span>}
      />

      <div className="space-y-5">
        {/* Итог */}
        <Card className="p-5">
          <div className="flex flex-wrap items-center gap-6">
            <div>
              <div className="text-4xl font-semibold tabular-nums text-slate-900">
                {formatScore(report.score)}<span className="text-lg text-slate-400"> / 100</span>
              </div>
              <div className="mt-1 text-xs text-slate-500">
                {report.confirmed
                  ? 'Итоговый балл — подтверждён преподавателем'
                  : 'Черновой балл по оценке ИИ (тренировка, в журнал не идёт)'}
              </div>
            </div>
            <div className="flex-1 space-y-1.5">
              {report.topics.map(t => (
                <div key={t.title} className="flex items-center justify-between gap-3 text-sm">
                  <span className="truncate text-slate-600">{t.title}</span>
                  <span className="flex items-center gap-2 text-xs text-slate-400">средний уровень <AvgLevel value={t.avgLevel} /></span>
                </div>
              ))}
            </div>
          </div>
          {report.teacherComment && (
            <div className="mt-4 rounded-lg bg-slate-50 px-3 py-2 text-sm">
              <span className="font-medium">Комментарий преподавателя: </span>{report.teacherComment}
            </div>
          )}
        </Card>

        {/* Текстовый отчёт ИИ */}
        {report.summary && (
          <Card className="space-y-4 p-5">
            <p className="text-sm leading-relaxed text-slate-800">{report.summary.overall}</p>
            <div className="grid gap-4 sm:grid-cols-2">
              <List title="Что получилось" items={report.summary.strengths} tone="green" />
              <List title="Пробелы" items={report.summary.gaps} tone="amber" />
            </div>
            {report.summary.reread.length > 0 && (
              <div>
                <h3 className="mb-2 text-sm font-semibold">Что перечитать</h3>
                <ul className="space-y-2">
                  {report.summary.reread.map((r, i) => (
                    <li key={i} className="rounded-lg border border-slate-200 px-3 py-2 text-sm">
                      <span className="font-medium">{r.source}</span>
                      {r.pages && <span className="text-slate-500">, стр. {r.pages}</span>}
                      <div className="text-slate-600">{r.why}</div>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </Card>
        )}

        {/* По темам и вопросам */}
        {report.topics.map(topic => (
          <section key={topic.title}>
            <h2 className="mb-2 flex items-center gap-2 text-sm font-semibold uppercase tracking-wide text-slate-500">
              {topic.title}
            </h2>
            <div className="space-y-3">
              {topic.questions.map(q => <QuestionCard key={q.position} q={q} />)}
            </div>
          </section>
        ))}

        <AppealBlock report={report} onChange={load} />
      </div>
    </div>
  )
}

function QuestionCard({ q }: { q: ReportQuestion }) {
  const [open, setOpen] = useState(false)
  return (
    <Card className="p-5">
      <div className="mb-2 flex flex-wrap items-center gap-2">
        <span className="text-xs font-medium text-slate-400">Вопрос {q.position}</span>
        <SoloBadge level={q.level} />
        {q.changedByTeacher && <span className="text-xs text-slate-500">уровень изменён преподавателем</span>}
      </div>
      <div className="font-medium text-slate-900">{q.question}</div>
      {q.feedback && <p className="mt-2 text-sm text-slate-700">{q.feedback}</p>}

      {q.evidence.length > 0 && (
        <div className="mt-3">
          <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-emerald-700">Что получилось</div>
          <ul className="space-y-1.5">
            {q.evidence.map((e, i) => (
              <li key={i} className="border-l-2 border-emerald-300 pl-3 text-sm">
                <span className="italic text-slate-800">«{e.quote}»</span>
                {e.fundamental && <span className="block text-xs text-slate-500">→ {e.fundamental}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
      {q.missingFundamentals.length > 0 && (
        <div className="mt-3">
          <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-amber-700">Чего не хватило</div>
          <ul className="list-inside list-disc text-sm text-slate-700">
            {q.missingFundamentals.map((m, i) => <li key={i}>{m}</li>)}
          </ul>
        </div>
      )}
      {q.teacherComment && (
        <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm">
          <span className="font-medium">Преподаватель: </span>{q.teacherComment}
        </div>
      )}

      <button onClick={() => setOpen(!open)} className="mt-3 text-xs font-medium text-brand-700 hover:underline">
        {open ? 'Скрыть мой диалог' : 'Показать мой диалог по этому вопросу'}
      </button>
      {open && (
        <div className="mt-2 space-y-1.5 rounded-lg bg-slate-50 p-3 text-sm">
          {q.transcript.map((line, i) => (
            <div key={i}>
              <span className={`mr-2 text-xs font-semibold ${line.author === 'AI' ? 'text-brand-700' : 'text-slate-500'}`}>
                {line.author === 'AI' ? 'VivaMind' : 'Вы'}
              </span>
              <span className="whitespace-pre-wrap text-slate-800">{line.text || '(нет ответа)'}</span>
            </div>
          ))}
        </div>
      )}
    </Card>
  )
}

function List({ title, items, tone }: { title: string; items: string[]; tone: 'green' | 'amber' }) {
  return (
    <div>
      <h3 className={`mb-1 text-sm font-semibold ${tone === 'green' ? 'text-emerald-700' : 'text-amber-700'}`}>{title}</h3>
      {items.length === 0
        ? <div className="text-sm text-slate-400">—</div>
        : <ul className="list-inside list-disc space-y-1 text-sm text-slate-700">{items.map((s, i) => <li key={i}>{s}</li>)}</ul>}
    </div>
  )
}

/** Апелляция: 24 часа после подтверждения оценки. По её итогам балл нельзя понизить. */
function AppealBlock({ report, onChange }: { report: Report; onChange: () => void }) {
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!report.confirmed) return null

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      await api(`/sessions/${report.sessionId}/appeal`, { method: 'POST', body: { reason } })
      onChange()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="p-5">
      <h2 className="mb-2 font-semibold">Апелляция</h2>
      {report.appeal ? (
        <div className="space-y-2 text-sm">
          <div><span className="text-slate-500">Ваше обращение ({formatDate(report.appeal.createdAt)}):</span> {report.appeal.reason}</div>
          {report.appeal.status === 'OPEN'
            ? <InfoBox>Апелляция на рассмотрении у преподавателя.</InfoBox>
            : <InfoBox tone="green"><b>Решение:</b> {report.appeal.resolution}</InfoBox>}
        </div>
      ) : report.canAppeal ? (
        <div className="space-y-3">
          <p className="text-sm text-slate-600">
            Если вы не согласны с оценкой, опишите причину до {formatDate(report.appealDeadline)}. По итогам апелляции
            балл может вырасти или остаться прежним, но не может быть понижен.
          </p>
          <TextArea rows={3} value={reason} onChange={e => setReason(e.target.value)} placeholder="С чем вы не согласны и почему" />
          <ErrorBox message={error} />
          <Button onClick={submit} loading={busy} disabled={!reason.trim()}>Подать апелляцию</Button>
        </div>
      ) : (
        <p className="text-sm text-slate-500">Срок подачи апелляции (24 часа после подтверждения оценки) истёк.</p>
      )}
    </Card>
  )
}
