// Русские подписи и бейджи для статусов, уровней SOLO, типов углублений.
import type { CardStatus, ExamMode, ExamStatus, FollowUpType, MaterialStatus, SessionStatus, Signals } from '../api/types'
import { Badge } from './ui'

export const SOLO_NAMES = ['Преструктурный', 'Одноструктурный', 'Многоструктурный', 'Реляционный', 'Расширенный абстрактный']

const soloStyles = [
  'bg-slate-200 text-slate-700',
  'bg-orange-100 text-orange-800',
  'bg-amber-100 text-amber-800',
  'bg-teal-100 text-teal-800',
  'bg-emerald-200 text-emerald-900',
]

/** Уровень SOLO 0–4. */
export function SoloBadge({ level, withName = true }: { level: number | null | undefined; withName?: boolean }) {
  if (level === null || level === undefined) {
    return <span className="inline-flex items-center rounded-md bg-slate-100 px-2 py-0.5 text-xs font-medium text-slate-500">нет уровня</span>
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-semibold ${soloStyles[level] ?? soloStyles[0]}`}>
      SOLO {level}
      {withName && <span className="font-normal">· {SOLO_NAMES[level]}</span>}
    </span>
  )
}

/** Средний уровень по теме (дробное число 0–4). */
export function AvgLevel({ value }: { value: number | null | undefined }) {
  if (value === null || value === undefined) return <span className="text-slate-400">—</span>
  const style = soloStyles[Math.min(4, Math.max(0, Math.round(value)))]
  return <span className={`inline-flex rounded-md px-2 py-0.5 text-xs font-semibold ${style}`}>{Number(value).toFixed(1)}</span>
}

export function ModeBadge({ mode }: { mode: ExamMode }) {
  return mode === 'EXAM' ? <Badge tone="violet">Экзамен</Badge> : <Badge tone="blue">Тренировка</Badge>
}

export function ExamStatusBadge({ status }: { status: ExamStatus }) {
  if (status === 'PUBLISHED') return <Badge tone="green">Опубликован</Badge>
  if (status === 'CLOSED') return <Badge tone="slate">Закрыт</Badge>
  return <Badge tone="amber">Черновик</Badge>
}

export function CardStatusBadge({ status }: { status: CardStatus }) {
  if (status === 'APPROVED') return <Badge tone="green">Утверждена</Badge>
  if (status === 'REJECTED') return <Badge tone="red">Отклонена</Badge>
  return <Badge tone="amber">Черновик</Badge>
}

export function MaterialStatusBadge({ status }: { status: MaterialStatus }) {
  if (status === 'READY') return <Badge tone="green">Готов</Badge>
  if (status === 'FAILED') return <Badge tone="red">Ошибка</Badge>
  if (status === 'PROCESSING') return <Badge tone="blue">Обрабатывается…</Badge>
  return <Badge tone="slate">Загружен</Badge>
}

export const SESSION_STATUS: Record<SessionStatus, string> = {
  IN_PROGRESS: 'Идёт интервью',
  COMPLETED: 'ИИ оценивает',
  AI_GRADED: 'Ждёт проверки',
  CONFIRMED: 'Подтверждено',
  ABANDONED: 'Не завершено',
}

export function SessionStatusBadge({ status, practice }: { status: SessionStatus; practice?: boolean }) {
  const tone = status === 'CONFIRMED' ? 'green' : status === 'AI_GRADED' ? (practice ? 'green' : 'amber') : status === 'ABANDONED' ? 'slate' : 'blue'
  const label = status === 'AI_GRADED' && practice ? 'Оценено ИИ' : SESSION_STATUS[status]
  return <Badge tone={tone}>{label}</Badge>
}

export const FOLLOW_UP_LABEL: Record<FollowUpType, string> = {
  WHY: 'Почему?',
  EXAMPLE: 'Пример',
  WHAT_IF: 'А что если…',
  FIND_ERROR: 'Найди ошибку',
  HINT: 'Подсказка',
}

export const LANGUAGES: Record<string, string> = { ru: 'Русский', kk: 'Қазақша', en: 'English' }

/** Сигналы честности. Показываются только преподавателю и всегда с подписью. */
export function SignalsLine({ signals }: { signals: Signals }) {
  const none = signals.pasteAttempts + signals.tabSwitches + signals.timeouts === 0
  if (none) return <span className="text-xs text-slate-400">нет</span>
  return (
    <span className="text-xs text-slate-600">
      вставки: <b>{signals.pasteAttempts}</b> · вкладки: <b>{signals.tabSwitches}</b> · таймауты: <b>{signals.timeouts}</b>
    </span>
  )
}
