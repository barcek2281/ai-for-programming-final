import { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { AppealRow, Review } from '../../api/types'
import { SOLO_NAMES } from '../../components/labels'
import { Badge, Button, Card, Empty, ErrorBox, formatDate, formatScore, InfoBox, Loading, PageHeader, Select, Tabs, TextArea } from '../../components/ui'

type Filter = 'OPEN' | 'ALL'

/** Апелляции студентов. По итогам апелляции балл можно повысить или оставить, но нельзя понизить. */
export default function AppealsPage() {
  const [filter, setFilter] = useState<Filter>('OPEN')
  const [rows, setRows] = useState<AppealRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setRows(await api<AppealRow[]>(`/appeals${filter === 'OPEN' ? '?status=OPEN' : ''}`))
      setError(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [filter])

  useEffect(() => { load() }, [load])

  return (
    <div>
      <PageHeader title="Апелляции" subtitle="Студент может подать апелляцию в течение 24 часов после подтверждения оценки." />
      <Tabs active={filter} onChange={setFilter} tabs={[{ id: 'OPEN', label: 'Открытые' }, { id: 'ALL', label: 'Все' }]} />
      <ErrorBox message={error} />
      {!rows ? <Loading /> : rows.length === 0 ? <Empty>Апелляций нет.</Empty> : (
        <div className="space-y-4">
          {rows.map(row => <AppealCard key={row.id} row={row} onResolved={load} />)}
        </div>
      )}
    </div>
  )
}

function AppealCard({ row, onResolved }: { row: AppealRow; onResolved: () => void }) {
  const [open, setOpen] = useState(false)

  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start gap-3">
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2 font-medium text-slate-900">
            {row.studentName}
            {row.status === 'OPEN' ? <Badge tone="amber">Открыта</Badge> : <Badge tone="green">Разобрана</Badge>}
          </div>
          <div className="text-xs text-slate-500">
            {row.courseTitle} · {row.examTitle} · подана {formatDate(row.createdAt)} · текущий балл {formatScore(row.finalScore)}
          </div>
        </div>
        <Link to={`/t/sessions/${row.sessionId}/review`}><Button size="sm" variant="secondary">Транскрипт и оценка</Button></Link>
      </div>
      <div className="mt-3 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-800">{row.reason}</div>

      {row.status === 'RESOLVED' ? (
        <div className="mt-3 text-sm">
          <span className="font-medium">Решение ({formatDate(row.resolvedAt)}):</span> {row.resolution}
        </div>
      ) : open ? (
        <ResolveForm row={row} onCancel={() => setOpen(false)} onResolved={onResolved} />
      ) : (
        <div className="mt-3"><Button size="sm" onClick={() => setOpen(true)}>Разобрать</Button></div>
      )}
    </Card>
  )
}

function ResolveForm({ row, onCancel, onResolved }: { row: AppealRow; onCancel: () => void; onResolved: () => void }) {
  const [review, setReview] = useState<Review | null>(null)
  const [levels, setLevels] = useState<Record<number, number>>({})
  const [resolution, setResolution] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<Review>(`/sessions/${row.sessionId}/review`).then(setReview).catch(e => setError(errorMessage(e)))
  }, [row.sessionId])

  async function submit() {
    setBusy(true)
    setError(null)
    try {
      await api(`/appeals/${row.id}/resolve`, { method: 'POST', body: { resolution, questionLevels: levels } })
      onResolved()
    } catch (e) {
      setError(errorMessage(e))
      setBusy(false)
    }
  }

  return (
    <div className="mt-4 space-y-3 border-t border-slate-100 pt-4">
      <InfoBox>
        Уровни можно менять по отдельным вопросам, но итоговый балл не может стать ниже текущего
        ({formatScore(row.finalScore)}) — как в политике экзаменов университета.
      </InfoBox>
      {!review ? <Loading /> : (
        <div className="space-y-2">
          {review.topics.flatMap(t => t.questions).map(q => {
            const current = q.finalSoloLevel ?? q.aiSoloLevel ?? 0
            return (
              <div key={q.id} className="flex flex-wrap items-center gap-3 text-sm">
                <span className="min-w-0 flex-1 truncate" title={q.question}>
                  <span className="text-slate-400">Вопрос {q.position}.</span> {q.question}
                </span>
                <Select
                  className="!w-60"
                  value={levels[q.id] ?? current}
                  onChange={e => {
                    const value = Number(e.target.value)
                    setLevels(prev => {
                      const next = { ...prev }
                      if (value === current) delete next[q.id]
                      else next[q.id] = value
                      return next
                    })
                  }}
                >
                  {SOLO_NAMES.map((name, i) => <option key={i} value={i}>{i} — {name}{i === current ? ' (сейчас)' : ''}</option>)}
                </Select>
              </div>
            )
          })}
        </div>
      )}
      <TextArea rows={2} value={resolution} onChange={e => setResolution(e.target.value)} placeholder="Решение по апелляции — его увидит студент" />
      <ErrorBox message={error} />
      <div className="flex gap-2">
        <Button loading={busy} disabled={!resolution.trim()} onClick={submit}>Сохранить решение</Button>
        <Button variant="secondary" onClick={onCancel}>Отмена</Button>
      </div>
    </div>
  )
}
