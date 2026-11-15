import { Icon } from '../../components/ui'
import { useCallback, useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { QuestionReview, Review, TranscriptMessage } from '../../api/types'
import { AvgLevel, FOLLOW_UP_LABEL, ModeBadge, SessionStatusBadge, SOLO_NAMES, SoloBadge } from '../../components/labels'
import { Badge, Button, Card, ErrorBox, formatDate, formatScore, InfoBox, Input, Loading, PageHeader, Select, TextArea } from '../../components/ui'

type Highlight = { questionId: number; quote: string } | null

/**
 * Проверка сессии преподавателем (человек в контуре).
 * Слева — транскрипт по вопросам, сгруппированный по темам курса; справа — черновик оценки ИИ:
 * уровень, уверенность, обоснование, цитаты (клик подсвечивает место в транскрипте), чего не хватило.
 * Преподаватель может изменить уровень по любому вопросу и подтверждает итог.
 */
export default function ReviewPage() {
  const { id } = useParams()
  const [review, setReview] = useState<Review | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [highlight, setHighlight] = useState<Highlight>(null)
  const [comment, setComment] = useState('')
  const [manualScore, setManualScore] = useState('')
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    try {
      const r = await api<Review>(`/sessions/${id}/review`)
      setReview(r)
      setComment(c => c || r.teacherComment || '')
      setError(null)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  // Пока идёт интервью или ИИ-оценка — обновляем экран.
  const live = review?.status === 'IN_PROGRESS' || review?.status === 'COMPLETED'
  useEffect(() => {
    if (!live) return
    const timer = setInterval(load, 4000)
    return () => clearInterval(timer)
  }, [live, load])

  async function act(action: () => Promise<Review | void>) {
    setBusy(true)
    setError(null)
    try {
      const result = await action()
      if (result) setReview(result)
      else await load()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  function showQuote(questionId: number, quote: string) {
    setHighlight({ questionId, quote })
    // Даём React отрисовать подсветку и прокручиваем к ней.
    setTimeout(() => document.querySelector('mark.quote')?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 50)
  }

  if (error && !review) return <ErrorBox message={error} />
  if (!review) return <Loading />

  const practice = review.mode === 'PRACTICE'
  const editable = review.status === 'AI_GRADED'
  const questions = review.topics.flatMap(t => t.questions)
  const hasFailed = questions.some(q => q.state === 'CLOSED' && (!q.grade || q.grade.failed))
  const canRegrade = (review.status === 'AI_GRADED' && hasFailed) || review.status === 'COMPLETED'

  return (
    <div>
      <Link to={`/t/exams/${review.examId}/results`} className="text-sm text-slate-500 hover:text-brand-700">← {review.examTitle}</Link>
      <PageHeader
        title={review.studentName}
        subtitle={
          <span className="flex flex-wrap items-center gap-2">
            {review.studentEmail} <ModeBadge mode={review.mode} /> <SessionStatusBadge status={review.status} practice={practice} />
            {review.needsReview && editable && <Badge tone="red">Нужна проверка</Badge>}
            <span>· начато {formatDate(review.startedAt)}</span>
          </span>
        }
      />

      <div className="mb-5 grid gap-4 lg:grid-cols-3">
        {/* Баллы */}
        <Card className="p-4">
          <div className="grid grid-cols-2 gap-3 text-sm">
            <Stat label="Балл ИИ (черновик)" value={formatScore(review.aiScore)} />
            <Stat
              label={review.status === 'CONFIRMED' ? 'Итоговый балл' : 'Итог с вашими правками'}
              value={formatScore(review.status === 'CONFIRMED' ? review.finalScore : review.previewScore)}
              strong
            />
          </div>
          <div className="mt-3 space-y-1">
            {review.topics.map(t => (
              <div key={t.topicId} className="flex items-center justify-between gap-2 text-xs text-slate-600">
                <span className="truncate">{t.title}</span>
                <AvgLevel value={t.avgLevel} />
              </div>
            ))}
          </div>
          <p className="mt-3 text-[11px] leading-snug text-slate-400">
            Балл считает формула, а не ИИ: средний уровень SOLO ÷ 4 × 100.
          </p>
        </Card>

        {/* Сигналы честности */}
        <Card className="p-4">
          <h3 className="text-sm font-semibold">Сигналы честности</h3>
          <p className="mb-2 text-[11px] text-slate-400">сигналы, не доказательства · на балл автоматически не влияют</p>
          <div className="grid grid-cols-3 gap-2 text-center">
            <Signal label="попыток вставки" value={review.signals.pasteAttempts} />
            <Signal label="уходов со вкладки" value={review.signals.tabSwitches} />
            <Signal label="таймаутов" value={review.signals.timeouts} />
          </div>
        </Card>

        {/* Отчёт ИИ для студента */}
        <Card className="p-4">
          <h3 className="mb-1 text-sm font-semibold">Отчёт ИИ для студента</h3>
          {review.summary ? (
            <div className="space-y-1 text-xs text-slate-600">
              <p className="line-clamp-4">{review.summary.overall}</p>
              {review.summary.reread.length > 0 && (
                <p className="text-slate-500">Перечитать: {review.summary.reread.map(r => r.pages ? `${r.source} (стр. ${r.pages})` : r.source).join('; ')}</p>
              )}
            </div>
          ) : (
            <p className="text-xs text-slate-400">{live ? 'Появится после ИИ-оценки.' : 'Отчёт не сформирован.'}</p>
          )}
        </Card>
      </div>

      {review.status === 'COMPLETED' && <div className="mb-4"><InfoBox>ИИ оценивает ответы — страница обновится сама.</InfoBox></div>}
      {review.status === 'IN_PROGRESS' && <div className="mb-4"><InfoBox>Интервью ещё идёт. Оценки появятся по мере закрытия вопросов.</InfoBox></div>}
      <div className="mb-4"><ErrorBox message={error} /></div>

      {/* Вопросы по темам курса */}
      {review.topics.map(topic => (
        <section key={topic.topicId} className="mb-6">
          <h2 className="mb-2 text-sm font-semibold uppercase tracking-wide text-slate-500">Тема: {topic.title}</h2>
          <div className="space-y-4">
            {topic.questions.map(q => (
              <Card key={q.id} className="grid gap-0 overflow-hidden lg:grid-cols-2">
                <div className="border-b border-slate-100 p-4 lg:border-b-0 lg:border-r">
                  <div className="mb-3 text-xs font-medium text-slate-400">
                    Вопрос {q.position} · углублений: {q.followupsAsked}
                  </div>
                  <Transcript messages={q.messages} quote={highlight?.questionId === q.id ? highlight.quote : null} />
                </div>
                <GradePanel
                  q={q}
                  editable={editable}
                  onQuote={quote => showQuote(q.id, quote)}
                  onSave={(level, text) => act(() => api<Review>(`/sessions/${id}/questions/${q.id}`, {
                    method: 'PUT',
                    body: { finalSoloLevel: level, comment: text },
                  }))}
                />
              </Card>
            ))}
          </div>
        </section>
      ))}

      {/* Подтверждение */}
      <Card className="p-5">
        {practice ? (
          <p className="text-sm text-slate-600">
            Это тренировочная сессия: студент уже видит отчёт ИИ, оценка в журнал не идёт и подтверждения не требует.
          </p>
        ) : review.status === 'CONFIRMED' ? (
          <div className="text-sm text-slate-700">
            <b>Оценка подтверждена</b> {formatDate(review.reviewedAt)} — итог {formatScore(review.finalScore)} / 100.
            {review.teacherComment && <div className="mt-1 text-slate-600">Комментарий: {review.teacherComment}</div>}
            {review.appeal && (
              <div className="mt-2">
                Апелляция: {review.appeal.status === 'OPEN' ? 'ожидает решения' : 'разобрана'} —{' '}
                <Link className="text-brand-700 underline" to="/t/appeals">к апелляциям</Link>
              </div>
            )}
          </div>
        ) : (
          <div className="space-y-3">
            <h3 className="font-semibold">Подтверждение оценки</h3>
            <p className="text-sm text-slate-600">
              ИИ подготовил только черновик. Оценку ставите вы: меняйте уровень по любому вопросу выше или впишите итоговый балл сами. Итоговый балл появится, а студент увидит отчёт, только после вашего подтверждения.
            </p>
            <div className="flex flex-wrap items-center gap-3 text-sm">
              <span className="font-medium text-slate-700">Итоговый балл:</span>
              <Input
                type="number" min={0} max={100} step={0.1} className="!w-28" disabled={!editable}
                value={manualScore} placeholder={formatScore(review.previewScore)}
                onChange={e => setManualScore(e.target.value)}
              />
              <span className="text-xs text-slate-500">
                Оставьте пустым — возьмётся расчёт по уровням ({formatScore(review.previewScore)}). Или впишите свой балл от 0 до 100.
              </span>
            </div>
            <TextArea rows={2} value={comment} onChange={e => setComment(e.target.value)} placeholder="Комментарий студенту (необязательно)" disabled={!editable} />
            <div className="flex flex-wrap items-center gap-3">
              <Button
                loading={busy}
                disabled={!editable}
                onClick={() => act(() => api<Review>(`/sessions/${id}/confirm`, { method: 'POST', body: { comment, finalScore: manualScore === '' ? null : Number(manualScore) } }))}
              >
                <Icon name="check" /> Подтвердить оценку{editable ? ` — ${manualScore === '' ? formatScore(review.previewScore) : formatScore(Number(manualScore))} / 100` : ''}
              </Button>
              {canRegrade && (
                <Button variant="secondary" disabled={busy} onClick={() => act(() => api<void>(`/sessions/${id}/regrade`, { method: 'POST' }))}>
                  <Icon name="refresh" /> Перезапустить ИИ-оценку
                </Button>
              )}
            </div>
          </div>
        )}
      </Card>
    </div>
  )
}

function Stat({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div>
      <div className="text-[11px] uppercase tracking-wide text-slate-400">{label}</div>
      <div className={`tabular-nums ${strong ? 'text-2xl font-semibold text-slate-900' : 'text-xl text-slate-600'}`}>{value}</div>
    </div>
  )
}

function Signal({ label, value }: { label: string; value: number }) {
  return (
    <div className="rounded-lg bg-slate-50 px-2 py-2">
      <div className={`text-xl font-semibold tabular-nums ${value > 0 ? 'text-amber-700' : 'text-slate-400'}`}>{value}</div>
      <div className="text-[11px] leading-tight text-slate-500">{label}</div>
    </div>
  )
}

// ---------- Транскрипт ----------

function Transcript({ messages, quote }: { messages: TranscriptMessage[]; quote: string | null }) {
  return (
    <div className="space-y-2.5">
      {messages.map(m => (
        <div key={m.id} className={m.author === 'AI' ? '' : 'pl-4'}>
          <div className="mb-0.5 flex flex-wrap items-center gap-2 text-[11px] text-slate-400">
            <span className={`font-semibold ${m.author === 'AI' ? 'text-brand-700' : 'text-slate-600'}`}>
              {m.author === 'AI' ? 'VivaMind' : 'Студент'}
            </span>
            {m.followUpType && <span className="rounded bg-slate-100 px-1.5">{FOLLOW_UP_LABEL[m.followUpType]}</span>}
            {m.kind === 'DONT_KNOW' && <span className="rounded bg-slate-100 px-1.5">«Не знаю»</span>}
            {m.kind === 'TIMEOUT' && <span className="rounded bg-amber-100 px-1.5 text-amber-800">таймаут</span>}
            {m.flagged && <span className="rounded bg-red-100 px-1.5 text-red-700" title="ИИ отметил попытку манипуляции или ответ не по теме">⚑ не по теме / манипуляция</span>}
            {m.author === 'STUDENT' && m.responseMs !== null && <span>{Math.round(m.responseMs / 1000)} с</span>}
            {m.pasteAttempts > 0 && <span className="text-amber-700">вставки: {m.pasteAttempts}</span>}
            {m.tabSwitches > 0 && <span className="text-amber-700">вкладки: {m.tabSwitches}</span>}
          </div>
          <div className={`whitespace-pre-wrap rounded-lg px-3 py-2 text-sm leading-relaxed ${m.author === 'AI' ? 'bg-slate-50 text-slate-800' : 'bg-brand-50 text-slate-900'}`}>
            {m.text
              ? (m.author === 'STUDENT' && quote ? highlightQuote(m.text, quote) : m.text)
              : <span className="italic text-slate-400">(нет ответа)</span>}
          </div>
        </div>
      ))}
    </div>
  )
}

/** Подсвечивает цитату в ответе студента. Регистр и пробелы не важны — как в проверке цитат на сервере. */
function highlightQuote(text: string, quote: string): ReactNode {
  const cleaned = quote.trim().replace(/^["'«»“”„…. ]+|["'«»“”„… ]+$/g, '')
  if (!cleaned) return text
  const pattern = cleaned.split(/\s+/).map(word => word.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('\\s+')
  const match = new RegExp(pattern, 'iu').exec(text)
  if (!match) return text
  return (
    <>
      {text.slice(0, match.index)}
      <mark className="quote">{match[0]}</mark>
      {text.slice(match.index + match[0].length)}
    </>
  )
}

// ---------- Оценка по вопросу ----------

function GradePanel({ q, editable, onQuote, onSave }: {
  q: QuestionReview
  editable: boolean
  onQuote: (quote: string) => void
  onSave: (level: number, comment: string) => void
}) {
  const current = q.finalSoloLevel ?? q.aiSoloLevel
  const [level, setLevel] = useState<string>(current === null ? '' : String(current))
  const [comment, setComment] = useState(q.teacherComment ?? '')
  const [showCard, setShowCard] = useState(false)
  const grade = q.grade

  useEffect(() => {
    setLevel(current === null ? '' : String(current))
  }, [current])

  const dirty = level !== '' && (Number(level) !== current || comment !== (q.teacherComment ?? '') || q.finalSoloLevel === null && comment !== '')

  return (
    <div className="space-y-3 p-4">
      <div className="text-sm font-medium text-slate-900">{q.question}</div>

      {q.state !== 'CLOSED' ? (
        <p className="text-sm text-slate-400">Вопрос ещё не закрыт.</p>
      ) : !grade ? (
        <p className="text-sm text-slate-400">ИИ ещё оценивает этот вопрос…</p>
      ) : grade.failed ? (
        <InfoBox tone="amber">ИИ не смог оценить этот вопрос. Поставьте уровень вручную или перезапустите ИИ-оценку.</InfoBox>
      ) : (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-xs text-slate-500">ИИ:</span>
            <SoloBadge level={q.aiSoloLevel} />
            <span className="text-xs text-slate-500">
              уверенность <b className={q.aiConfidence !== null && q.aiConfidence < 0.6 ? 'text-amber-700' : 'text-slate-700'}>
                {q.aiConfidence === null ? '—' : `${Math.round(q.aiConfidence * 100)}%`}
              </b>
            </span>
          </div>
          {grade.reviewReasons.length > 0 && (
            <InfoBox tone="amber">
              <b>Проверьте внимательно:</b> {grade.reviewReasons.join('; ')}
            </InfoBox>
          )}
          {grade.rationale && <p className="text-sm text-slate-700">{grade.rationale}</p>}

          <div>
            <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Цитаты-доказательства</div>
            {grade.evidence.length === 0 ? <div className="text-sm text-slate-400">нет</div> : (
              <ul className="space-y-1.5">
                {grade.evidence.map((e, i) => (
                  <li key={i}>
                    <button
                      onClick={() => onQuote(e.quote)}
                      className="w-full rounded-md border-l-2 border-amber-300 bg-amber-50/60 px-2.5 py-1.5 text-left text-sm hover:bg-amber-100"
                      title="Показать в транскрипте"
                    >
                      <span className="italic text-slate-800">«{e.quote}»</span>
                      {e.fundamental && <span className="block text-xs text-slate-500">→ {e.fundamental}</span>}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>

          {grade.missingFundamentals.length > 0 && (
            <div>
              <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Чего не хватило</div>
              <ul className="list-inside list-disc text-sm text-slate-700">
                {grade.missingFundamentals.map((m, i) => <li key={i}>{m}</li>)}
              </ul>
            </div>
          )}
        </>
      )}

      <button onClick={() => setShowCard(!showCard)} className="text-xs font-medium text-brand-700 hover:underline">
        {showCard ? 'Скрыть карточку вопроса' : 'Показать ключевые идеи и рубрику'}
      </button>
      {showCard && (
        <div className="space-y-2 rounded-lg bg-slate-50 p-3 text-xs text-slate-700">
          <div>
            <b>Ключевые идеи:</b>
            <ul className="list-inside list-disc">{q.fundamentals.map((f, i) => <li key={i}>{f}</li>)}</ul>
          </div>
          <div>
            <b>Рубрика:</b>
            {['0', '1', '2', '3', '4'].map(l => <div key={l}><b>{l}</b> — {q.rubric[l]}</div>)}
          </div>
        </div>
      )}

      {/* Решение преподавателя */}
      {q.state === 'CLOSED' && (
        <div className="space-y-2 border-t border-slate-100 pt-3">
          <div className="flex flex-wrap items-center gap-2">
            <span className="text-sm font-medium text-slate-700">Итоговый уровень:</span>
            <Select className="!w-64" value={level} disabled={!editable} onChange={e => setLevel(e.target.value)}>
              <option value="" disabled>не выбран</option>
              {SOLO_NAMES.map((name, i) => <option key={i} value={i}>{i} — {name}</option>)}
            </Select>
            {q.finalSoloLevel !== null && q.finalSoloLevel !== q.aiSoloLevel && <Badge tone="violet">изменено вами</Badge>}
          </div>
          <TextArea rows={1} value={comment} disabled={!editable} onChange={e => setComment(e.target.value)} placeholder="Комментарий к вопросу (увидит студент)" />
          {editable && (
            <Button size="sm" variant="secondary" disabled={!dirty} onClick={() => onSave(Number(level), comment)}>
              <Icon name="save" /> Сохранить уровень
            </Button>
          )}
        </div>
      )}
    </div>
  )
}
