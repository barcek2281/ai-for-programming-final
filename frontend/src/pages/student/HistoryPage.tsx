import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { HistoryRow } from '../../api/types'
import { ModeBadge, SessionStatusBadge } from '../../components/labels'
import { Button, Card, Empty, ErrorBox, formatDate, formatScore, Loading, PageHeader } from '../../components/ui'

export default function HistoryPage() {
  const [rows, setRows] = useState<HistoryRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<HistoryRow[]>('/me/sessions').then(setRows).catch(e => setError(errorMessage(e)))
  }, [])

  if (error) return <ErrorBox message={error} />
  if (!rows) return <Loading />

  return (
    <div>
      <PageHeader title="Мои интервью" actions={<Link to="/s/join"><Button>Войти в экзамен</Button></Link>} />
      {rows.length === 0 ? (
        <Empty>Вы ещё не проходили интервью. Введите код экзамена, чтобы начать.</Empty>
      ) : (
        <Card className="divide-y divide-slate-100">
          {rows.map(row => (
            <div key={row.id} className="flex flex-wrap items-center gap-4 px-5 py-4">
              <div className="min-w-0 flex-1">
                <div className="flex items-center gap-2 font-medium text-slate-900">
                  <span className="truncate">{row.examTitle}</span> <ModeBadge mode={row.mode} />
                </div>
                <div className="text-xs text-slate-500">{row.courseTitle} · {formatDate(row.startedAt)}</div>
              </div>
              <SessionStatusBadge status={row.status} practice={row.mode === 'PRACTICE'} />
              <div className="w-16 text-right font-semibold tabular-nums">{formatScore(row.score)}</div>
              {row.status === 'IN_PROGRESS'
                ? <Link to={`/s/sessions/${row.id}`}><Button size="sm">Продолжить</Button></Link>
                : <Link to={`/s/sessions/${row.id}/report`}><Button size="sm" variant="secondary">Отчёт</Button></Link>}
            </div>
          ))}
        </Card>
      )}
    </div>
  )
}
