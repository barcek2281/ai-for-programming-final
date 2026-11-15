import { useCallback, useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { Exam, SessionRow } from '../../api/types'
import { AvgLevel, ExamStatusBadge, ModeBadge, SessionStatusBadge, SignalsLine } from '../../components/labels'
import { Badge, Button, Card, DevBadge, Empty, ErrorBox, formatDate, formatScore, Loading, PageHeader } from '../../components/ui'

/** Таблица результатов экзамена: балл ИИ, итог, средний уровень по темам, флаг проверки, сигналы. */
export default function ExamResultsPage() {
  const { id } = useParams()
  const [exam, setExam] = useState<Exam | null>(null)
  const [rows, setRows] = useState<SessionRow[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      const [e, r] = await Promise.all([api<Exam>(`/exams/${id}`), api<SessionRow[]>(`/exams/${id}/sessions`)])
      setExam(e)
      setRows(r)
      setError(null)
    } catch (err) {
      setError(errorMessage(err))
    }
  }, [id])

  useEffect(() => {
    load()
    const timer = setInterval(load, 10000)      // студенты проходят интервью прямо сейчас — обновляем таблицу
    return () => clearInterval(timer)
  }, [load])

  if (error && !exam) return <ErrorBox message={error} />
  if (!exam || !rows) return <Loading />

  const practice = exam.mode === 'PRACTICE'

  return (
    <div>
      <Link to={`/t/courses/${exam.courseId}?tab=exams`} className="text-sm text-slate-500 hover:text-brand-700">← {exam.courseTitle}</Link>
      <PageHeader
        title={exam.title}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            <ModeBadge mode={exam.mode} /> <ExamStatusBadge status={exam.status} />
            <span>код <b className="font-mono tracking-widest text-slate-800">{exam.accessCode}</b></span>
            <span>· темы: {exam.topics.map(t => t.title).join(' · ')}</span>
          </span>
        }
        actions={
          <>
            <Button variant="secondary" disabled title="Макет: функция запланирована">Экспорт в Moodle</Button>
            <Button variant="secondary" disabled title="Макет: функция запланирована">Аналитика по группам</Button>
            <DevBadge />
          </>
        }
      />
      <ErrorBox message={error} />

      {rows.length === 0 ? <Empty>Пока никто не проходил этот экзамен.</Empty> : (
        <Card className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs uppercase tracking-wide text-slate-500">
                <th className="px-4 py-3 font-medium">Студент</th>
                <th className="px-3 py-3 font-medium">Статус</th>
                <th className="px-3 py-3 text-right font-medium">ИИ-балл</th>
                <th className="px-3 py-3 text-right font-medium">Итог</th>
                {exam.topics.map(t => (
                  <th key={t.id} className="max-w-32 truncate px-3 py-3 font-medium" title={`Средний уровень SOLO по теме «${t.title}»`}>{t.title}</th>
                ))}
                <th className="px-3 py-3 font-medium">
                  Сигналы
                  <div className="text-[10px] font-normal normal-case tracking-normal text-slate-400">сигналы, не доказательства</div>
                </th>
                <th className="px-3 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {rows.map(row => (
                <tr key={row.id} className="hover:bg-slate-50">
                  <td className="px-4 py-3">
                    <div className="font-medium text-slate-900">{row.studentName}</div>
                    <div className="text-xs text-slate-500">{row.studentEmail} · {formatDate(row.startedAt)}</div>
                  </td>
                  <td className="px-3 py-3">
                    <div className="flex flex-col items-start gap-1">
                      <SessionStatusBadge status={row.status} practice={practice} />
                      {row.needsReview && row.status === 'AI_GRADED' && <Badge tone="red">Нужна проверка</Badge>}
                      {row.openAppeal && <Badge tone="violet">Апелляция</Badge>}
                    </div>
                  </td>
                  <td className="px-3 py-3 text-right tabular-nums">{formatScore(row.aiScore)}</td>
                  <td className="px-3 py-3 text-right font-semibold tabular-nums">{formatScore(row.finalScore)}</td>
                  {row.topicLevels.map(level => (
                    <td key={level.topicId} className="px-3 py-3"><AvgLevel value={level.avgLevel} /></td>
                  ))}
                  <td className="px-3 py-3"><SignalsLine signals={row.signals} /></td>
                  <td className="px-3 py-3 text-right">
                    <Link to={`/t/sessions/${row.id}/review`}>
                      <Button size="sm" variant={row.status === 'AI_GRADED' && !practice ? 'primary' : 'secondary'}>
                        {row.status === 'AI_GRADED' && !practice ? 'Проверить' : 'Открыть'}
                      </Button>
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      <p className="mt-3 text-xs text-slate-500">
        В столбцах тем — средний уровень SOLO (0–4) по вопросам этой темы. Итоговый балл появляется только после вашего подтверждения.
      </p>
    </div>
  )
}
