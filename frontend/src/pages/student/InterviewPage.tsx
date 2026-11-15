import { Icon } from '../../components/ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, ApiError, errorMessage } from '../../api/client'
import type { AnswerResponse, ChatMessage, SessionState } from '../../api/types'
import { FOLLOW_UP_LABEL, ModeBadge } from '../../components/labels'
import { Logo } from '../../components/Layout'
import { Button, ErrorBox, Loading, TextArea } from '../../components/ui'

type AnswerKind = 'ANSWER' | 'DONT_KNOW' | 'TIMEOUT'

/**
 * Экран интервью: чат, таймер ответа, «Отправить» и «Не знаю».
 * Время считает сервер (answerDeadline), поэтому перезагрузка страницы не ломает ни сессию, ни таймер.
 * В режиме EXAM вставка текста блокируется и считается, переключения вкладок считаются;
 * счётчики уходят на сервер вместе с ответом как «сигналы, не доказательства».
 */
export default function InterviewPage() {
  const { id } = useParams()
  const [state, setState] = useState<SessionState | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [sendError, setSendError] = useState<string | null>(null)
  const [text, setText] = useState('')
  const [sending, setSending] = useState(false)
  const [remainingMs, setRemainingMs] = useState<number | null>(null)
  const [pasteBlocked, setPasteBlocked] = useState(false)

  const clockOffset = useRef(0)                 // время сервера минус время браузера
  const shownAt = useRef(Date.now())            // когда студент увидел текущий вопрос ИИ
  const pasteAttempts = useRef(0)
  const tabSwitches = useRef(0)
  const timeoutSentFor = useRef<string | null>(null)
  const sendingRef = useRef(false)
  const textRef = useRef('')
  const stateRef = useRef<SessionState | null>(null)
  const bottomRef = useRef<HTMLDivElement>(null)

  textRef.current = text
  stateRef.current = state

  const lastAiId = state ? [...state.messages].reverse().find(m => m.author === 'AI')?.id ?? 0 : 0
  const draftKey = `vm_draft_${id}_${lastAiId}`
  const inProgress = state?.sessionStatus === 'IN_PROGRESS'

  const load = useCallback(async () => {
    try {
      const s = await api<SessionState>(`/sessions/${id}`)
      clockOffset.current = new Date(s.serverTime).getTime() - Date.now()
      setState(s)
      setLoadError(null)
    } catch (e) {
      setLoadError(errorMessage(e))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  // Новый вопрос ИИ: сбрасываем счётчики сигналов и восстанавливаем черновик (если страницу перезагрузили).
  useEffect(() => {
    if (!lastAiId) return
    shownAt.current = Date.now()
    pasteAttempts.current = 0
    tabSwitches.current = 0
    setPasteBlocked(false)
    setText(sessionStorage.getItem(draftKey) ?? '')
  }, [lastAiId, draftKey])

  // Ответ уже отправлен (например, до перезагрузки страницы), ждём реплику ИИ.
  useEffect(() => {
    if (!state?.awaitingAi || sending) return
    const timer = setInterval(load, 2500)
    return () => clearInterval(timer)
  }, [state?.awaitingAi, sending, load])

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [state?.messages.length, sending])

  // Переключения вкладок — только в режиме экзамена.
  useEffect(() => {
    const onVisibility = () => {
      const s = stateRef.current
      if (document.hidden && s?.mode === 'EXAM' && s.sessionStatus === 'IN_PROGRESS') tabSwitches.current += 1
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [])

  const submit = useCallback(async (kind: AnswerKind) => {
    const current = stateRef.current
    if (!current || sendingRef.current || current.sessionStatus !== 'IN_PROGRESS') return
    const answerText = kind === 'DONT_KNOW' ? '' : textRef.current.trim()
    if (kind === 'ANSWER' && !answerText) return

    sendingRef.current = true
    setSending(true)
    setSendError(null)
    const previousDeadline = current.answerDeadline
    const key = draftKey
    // Сразу показываем ответ в чате; если отправка не удастся — уберём его и вернём текст в поле.
    const optimistic: ChatMessage = {
      id: -Date.now(),
      author: 'STUDENT',
      kind,
      followUpType: null,
      text: kind === 'DONT_KNOW' ? 'Не знаю' : answerText,
      questionPosition: current.question?.position ?? null,
      createdAt: new Date().toISOString(),
    }
    setState(s => s && { ...s, messages: [...s.messages, optimistic], answerDeadline: null })
    setText('')

    try {
      const res = await api<AnswerResponse>(`/sessions/${id}/answers`, {
        method: 'POST',
        body: {
          text: answerText,
          kind,
          responseMs: Date.now() - shownAt.current,
          pasteAttempts: pasteAttempts.current,
          tabSwitches: tabSwitches.current,
        },
      })
      clockOffset.current = new Date(res.serverTime).getTime() - Date.now()
      sessionStorage.removeItem(key)
      setState(s => s && {
        ...s,
        sessionStatus: res.sessionStatus,
        question: res.question,
        messages: [...s.messages, res.aiMessage],
        answerDeadline: res.answerDeadline,
        awaitingAi: false,
      })
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        await load()                                  // состояние на сервере другое — просто перечитываем
      } else {
        setState(s => s && { ...s, messages: s.messages.filter(m => m.id !== optimistic.id), answerDeadline: previousDeadline })
        setText(answerText)
        setSendError(errorMessage(e))
      }
    } finally {
      sendingRef.current = false
      setSending(false)
    }
  }, [id, draftKey, load])

  const submitRef = useRef(submit)
  submitRef.current = submit

  // Таймер. Когда время вышло, набранный текст уходит автоматически с kind=TIMEOUT.
  const deadline = inProgress ? state?.answerDeadline ?? null : null
  useEffect(() => {
    if (!deadline) {
      setRemainingMs(null)
      return
    }
    const deadlineMs = new Date(deadline).getTime()
    const tick = () => {
      const left = deadlineMs - (Date.now() + clockOffset.current)
      setRemainingMs(Math.max(0, left))
      if (left <= 0 && timeoutSentFor.current !== deadline && !sendingRef.current) {
        timeoutSentFor.current = deadline
        submitRef.current('TIMEOUT')
      }
    }
    tick()
    const timer = setInterval(tick, 250)
    return () => clearInterval(timer)
  }, [deadline])

  if (loadError && !state) {
    return (
      <div className="mx-auto max-w-md p-8">
        <ErrorBox message={loadError} />
        <Link to="/s/history" className="mt-4 inline-block text-sm text-brand-700 underline">К моим интервью</Link>
      </div>
    )
  }
  if (!state) return <div className="p-8"><Loading /></div>

  const exam = state.mode === 'EXAM'
  const thinking = inProgress && (sending || state.awaitingAi)
  const limitMs = state.answerTimeLimitSec * 1000
  const urgent = remainingMs !== null && remainingMs <= 15000

  return (
    <div className="flex h-full flex-col bg-slate-100">
      {/* Шапка: прогресс и таймер */}
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-3xl items-center gap-4 px-4 py-3">
          <span className="hidden sm:block"><Logo /></span>
          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-2 truncate text-sm font-medium text-slate-800">
              <span className="truncate">{state.examTitle}</span> <ModeBadge mode={state.mode} />
            </div>
            {state.question && (
              <div className="truncate text-xs text-slate-500">
                Вопрос {state.question.position} из {state.question.total} · Тема: {state.question.courseTopic}
              </div>
            )}
          </div>
          {remainingMs !== null && !thinking && (
            <div className={`rounded-lg px-3 py-1.5 font-mono text-lg font-semibold tabular-nums ${urgent ? 'bg-red-50 text-red-700' : 'bg-slate-100 text-slate-800'}`}>
              {formatTime(remainingMs)}
            </div>
          )}
        </div>
        <div className="h-1 bg-slate-100">
          {remainingMs !== null && !thinking && (
            <div
              className={`h-1 transition-[width] duration-200 ${urgent ? 'bg-red-500' : 'bg-brand-600'}`}
              style={{ width: `${Math.min(100, (remainingMs / limitMs) * 100)}%` }}
            />
          )}
        </div>
      </header>

      {/* Чат */}
      <div className="flex-1 overflow-y-auto">
        <div className="mx-auto max-w-3xl space-y-3 px-4 py-6">
          {state.messages.map((m, i) => {
            const previous = state.messages[i - 1]
            const newQuestion = m.kind === 'BASE_QUESTION' && m.questionPosition !== previous?.questionPosition
            return (
              <div key={m.id}>
                {newQuestion && (
                  <div className="my-4 flex items-center gap-3 text-xs font-medium uppercase tracking-wide text-slate-400">
                    <span className="h-px flex-1 bg-slate-300" />
                    Вопрос {m.questionPosition}{state.question ? ` из ${state.question.total}` : ''}
                    <span className="h-px flex-1 bg-slate-300" />
                  </div>
                )}
                <Bubble message={m} />
              </div>
            )
          })}
          {thinking && (
            <div className="flex items-center gap-2 text-sm text-slate-500">
              <span className="flex gap-1 rounded-2xl bg-white px-4 py-3 shadow-sm">
                <span className="vm-dot h-1.5 w-1.5 rounded-full bg-slate-400" />
                <span className="vm-dot h-1.5 w-1.5 rounded-full bg-slate-400" />
                <span className="vm-dot h-1.5 w-1.5 rounded-full bg-slate-400" />
              </span>
              VivaMind думает…
            </div>
          )}
          <div ref={bottomRef} />
        </div>
      </div>

      {/* Поле ответа или итог */}
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto max-w-3xl px-4 py-4">
          {inProgress ? (
            <div className="space-y-2">
              <ErrorBox message={sendError} />
              <TextArea
                rows={4}
                autoFocus
                value={text}
                disabled={thinking}
                placeholder="Ответьте своими словами: объясните, почему и как…"
                onChange={e => {
                  setText(e.target.value)
                  sessionStorage.setItem(draftKey, e.target.value)
                }}
                onPaste={e => {
                  if (!exam) return
                  e.preventDefault()
                  pasteAttempts.current += 1
                  setPasteBlocked(true)
                }}
                onDrop={e => {
                  if (!exam) return
                  e.preventDefault()
                  pasteAttempts.current += 1
                  setPasteBlocked(true)
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) submit('ANSWER')
                }}
              />
              {pasteBlocked && (
                <div className="text-xs text-amber-700">
                  В режиме экзамена вставка текста заблокирована. Попытка учтена как сигнал для преподавателя.
                </div>
              )}
              <div className="flex items-center gap-2">
                <Button onClick={() => submit('ANSWER')} disabled={thinking || !text.trim()}><Icon name="send" /> Отправить</Button>
                <Button variant="secondary" onClick={() => submit('DONT_KNOW')} disabled={thinking}>Не знаю</Button>
                <span className="ml-auto hidden text-xs text-slate-400 sm:inline">Ctrl + Enter — отправить</span>
              </div>
            </div>
          ) : (
            <Finished state={state} />
          )}
        </div>
      </footer>
    </div>
  )
}

function Bubble({ message }: { message: ChatMessage }) {
  if (message.kind === 'SYSTEM') {
    return <div className="rounded-xl bg-emerald-50 px-4 py-3 text-center text-sm text-emerald-900">{message.text}</div>
  }
  if (message.author === 'AI') {
    return (
      <div className="flex justify-start">
        <div className="max-w-[85%] rounded-2xl rounded-tl-sm bg-white px-4 py-3 shadow-sm">
          <div className="mb-1 flex items-center gap-2 text-xs font-medium text-brand-700">
            VivaMind
            {message.followUpType && (
              <span className="rounded-full bg-brand-50 px-2 py-0.5 text-[11px] font-normal text-brand-800">
                {FOLLOW_UP_LABEL[message.followUpType]}
              </span>
            )}
          </div>
          <div className="whitespace-pre-wrap text-[15px] leading-relaxed text-slate-900">{message.text}</div>
        </div>
      </div>
    )
  }
  return (
    <div className="flex justify-end">
      <div className="max-w-[85%] rounded-2xl rounded-tr-sm bg-brand-700 px-4 py-3 text-white shadow-sm">
        <div className="whitespace-pre-wrap text-[15px] leading-relaxed">
          {message.text || <span className="italic opacity-70">(нет ответа)</span>}
        </div>
        {message.kind === 'TIMEOUT' && <div className="mt-1 text-xs opacity-75">время вышло</div>}
      </div>
    </div>
  )
}

function Finished({ state }: { state: SessionState }) {
  if (state.sessionStatus === 'ABANDONED') {
    return (
      <div className="flex flex-wrap items-center gap-3 text-sm text-slate-600">
        Экзамен закрыт преподавателем, интервью не завершено.
        <Link to="/s/history"><Button variant="secondary">Мои интервью</Button></Link>
      </div>
    )
  }
  return (
    <div className="flex flex-wrap items-center gap-3">
      <div className="flex-1 text-sm text-slate-600">
        {state.mode === 'EXAM'
          ? 'Интервью завершено. ИИ готовит черновик оценки; отчёт вы увидите после проверки преподавателем.'
          : 'Интервью завершено. ИИ оценивает ответы — отчёт будет готов примерно через минуту.'}
      </div>
      <Link to={`/s/sessions/${state.id}/report`}><Button>{state.mode === 'EXAM' ? 'Статус отчёта' : 'Открыть отчёт'}</Button></Link>
      <Link to="/s/history"><Button variant="secondary">Мои интервью</Button></Link>
    </div>
  )
}

function formatTime(ms: number): string {
  const total = Math.ceil(ms / 1000)
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}
