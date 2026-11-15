import { useState, type FormEvent } from 'react'
import { api, errorMessage } from '../api/client'
import type { Exam, ExamMode, ExamRequest, Topic } from '../api/types'
import { Button, ErrorBox, Field, Input, Modal, Select } from './ui'

type Props = {
  courseId: number
  topics: Topic[]
  /** Заранее выбранные темы (кнопка «Проверка по теме»). */
  preselected?: number[]
  defaultTitle?: string
  /** Если передан — правка существующего экзамена (только в статусе «Черновик»). */
  exam?: Exam
  onClose: () => void
  onCreated: (exam: Exam) => void
}

/** Создание экзамена: одна или несколько тем курса, режим, число вопросов. */
export default function ExamModal({ courseId, topics, preselected = [], defaultTitle = '', exam, onClose, onCreated }: Props) {
  const [title, setTitle] = useState(exam?.title ?? defaultTitle)
  const [mode, setMode] = useState<ExamMode>(exam?.mode ?? 'EXAM')
  const [topicIds, setTopicIds] = useState<number[]>(exam ? exam.topics.map(t => t.id) : preselected)
  const [questionsCount, setQuestionsCount] = useState(exam?.questionsCount ?? 4)
  const [minFollowups, setMinFollowups] = useState(exam?.minFollowups ?? 1)
  const [maxFollowups, setMaxFollowups] = useState(exam?.maxFollowups ?? 3)
  const [answerTimeLimitSec, setAnswerTimeLimitSec] = useState(exam?.answerTimeLimitSec ?? 120)
  const [sameQuestions, setSameQuestions] = useState(exam?.sameQuestions ?? true)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  function toggle(id: number) {
    setTopicIds(ids => (ids.includes(id) ? ids.filter(x => x !== id) : [...ids, id]))
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    if (topicIds.length === 0) {
      setError('Выберите хотя бы одну тему')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const body: ExamRequest = { title, mode, topicIds, questionsCount, minFollowups, maxFollowups, answerTimeLimitSec, sameQuestions }
      onCreated(await api<Exam>(exam ? `/exams/${exam.id}` : `/courses/${courseId}/exams`, { method: exam ? 'PUT' : 'POST', body }))
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={exam ? 'Изменить экзамен' : 'Новый экзамен'} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Название">
          <Input required autoFocus value={title} onChange={e => setTitle(e.target.value)} placeholder="Проверка по теме 3" />
        </Field>

        <div>
          <span className="mb-1 block text-sm font-medium text-slate-700">Темы экзамена</span>
          <div className="max-h-48 space-y-1 overflow-y-auto rounded-lg border border-slate-200 p-2">
            {topics.map(topic => (
              <label key={topic.id} className="flex cursor-pointer items-center gap-2 rounded px-2 py-1 text-sm hover:bg-slate-50">
                <input type="checkbox" className="accent-teal-700" checked={topicIds.includes(topic.id)} onChange={() => toggle(topic.id)} />
                <span className="flex-1">{topic.title}</span>
                <span className={`text-xs ${topic.approvedCardsCount > 0 ? 'text-slate-400' : 'text-amber-600'}`}>
                  утверждённых карточек: {topic.approvedCardsCount}
                </span>
              </label>
            ))}
          </div>
          <span className="mt-1 block text-xs text-slate-500">
            Одна тема — «проверка по теме», несколько — рубежный или итоговый экзамен. Вопросы берутся только из
            утверждённых карточек выбранных тем.
          </span>
        </div>

        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Режим" hint={mode === 'EXAM' ? '1 попытка, без подсказок, итог подтверждает преподаватель' : 'Без ограничений, с подсказками, отчёт сразу'}>
            <Select value={mode} onChange={e => setMode(e.target.value as ExamMode)}>
              <option value="EXAM">Экзамен</option>
              <option value="PRACTICE">Тренировка</option>
            </Select>
          </Field>
          <Field label="Число вопросов">
            <Input type="number" min={1} max={12} required value={questionsCount} onChange={e => setQuestionsCount(Number(e.target.value))} />
          </Field>
          <Field label="Углублений на вопрос (мин–макс)">
            <div className="flex items-center gap-2">
              <Input type="number" min={0} max={5} required value={minFollowups} onChange={e => setMinFollowups(Number(e.target.value))} />
              <span className="text-slate-400">–</span>
              <Input type="number" min={1} max={6} required value={maxFollowups} onChange={e => setMaxFollowups(Number(e.target.value))} />
            </div>
          </Field>
          <Field label="Время на ответ, секунд">
            <Input type="number" min={30} max={900} required value={answerTimeLimitSec} onChange={e => setAnswerTimeLimitSec(Number(e.target.value))} />
          </Field>
        </div>

        <label className="flex cursor-pointer items-start gap-2 text-sm">
          <input type="checkbox" className="mt-0.5 accent-teal-700" checked={sameQuestions} onChange={e => setSameQuestions(e.target.checked)} />
          <span>
            Одинаковые вопросы у всех студентов
            <span className="block text-xs text-slate-500">
              {sameQuestions
                ? 'Все получат один и тот же набор базовых вопросов. Углубляющие вопросы всё равно будут разными — они строятся на словах студента.'
                : 'У каждого студента свой случайный набор вопросов из утверждённых — сложнее подсказать соседу.'}
            </span>
          </span>
        </label>

        <ErrorBox message={error} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Отмена</Button>
          <Button type="submit" loading={busy}>{exam ? 'Сохранить' : 'Создать'}</Button>
        </div>
      </form>
    </Modal>
  )
}
