import { Icon } from '../../components/ui'
import { useCallback, useEffect, useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { Course, Exam, Topic } from '../../api/types'
import ExamModal from '../../components/ExamModal'
import { ExamStatusBadge, LANGUAGES, ModeBadge } from '../../components/labels'
import { Badge, Button, Card, DevBadge, Empty, ErrorBox, Input, Loading, PageHeader, Tabs } from '../../components/ui'
import { CourseModal } from './CoursesPage'

type Tab = 'topics' | 'exams'

export default function CoursePage() {
  const { id } = useParams()
  const [params, setParams] = useSearchParams()
  const tab: Tab = params.get('tab') === 'exams' ? 'exams' : 'topics'

  const [course, setCourse] = useState<Course | null>(null)
  const [topics, setTopics] = useState<Topic[]>([])
  const [exams, setExams] = useState<Exam[]>([])
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [creatingExam, setCreatingExam] = useState(false)
  const [editingExam, setEditingExam] = useState<Exam | null>(null)

  const load = useCallback(async () => {
    try {
      const [c, t, e] = await Promise.all([
        api<Course>(`/courses/${id}`),
        api<Topic[]>(`/courses/${id}/topics`),
        api<Exam[]>(`/courses/${id}/exams`),
      ])
      setCourse(c)
      setTopics(t)
      setExams(e)
      setError(null)
    } catch (err) {
      setError(errorMessage(err))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  if (error && !course) return <ErrorBox message={error} />
  if (!course) return <Loading />

  return (
    <div>
      <Link to="/t/courses" className="text-sm text-slate-500 hover:text-brand-700">← Курсы</Link>
      <PageHeader
        title={course.title}
        subtitle={<span className="flex items-center gap-2"><Badge>{LANGUAGES[course.language] ?? course.language}</Badge>{course.description}</span>}
        actions={
          <>
            <Button variant="secondary" onClick={() => setEditing(true)}><Icon name="edit" /> Изменить курс</Button>
            <Button variant="secondary" disabled title="Макет: функция запланирована">Аналитика по группам</Button>
            <DevBadge />
          </>
        }
      />
      <ErrorBox message={error} />

      <Tabs
        active={tab}
        onChange={t => setParams(t === 'exams' ? { tab: 'exams' } : {})}
        tabs={[
          { id: 'topics', label: `Темы (${topics.length})` },
          { id: 'exams', label: `Экзамены (${exams.length})` },
        ]}
      />

      {tab === 'topics'
        ? <TopicsTab courseId={course.id} topics={topics} reload={load} setError={setError} />
        : <ExamsTab exams={exams} canCreate={topics.length > 0} onCreate={() => setCreatingExam(true)} onEdit={setEditingExam} reload={load} setError={setError} />}

      {editing && <CourseModal course={course} onClose={() => setEditing(false)} onSaved={() => { setEditing(false); load() }} />}
      {editingExam && (
        <ExamModal
          courseId={course.id}
          topics={topics}
          exam={editingExam}
          onClose={() => setEditingExam(null)}
          onCreated={() => { setEditingExam(null); load() }}
        />
      )}
      {creatingExam && (
        <ExamModal
          courseId={course.id}
          topics={topics}
          onClose={() => setCreatingExam(false)}
          onCreated={() => { setCreatingExam(false); load() }}
        />
      )}
    </div>
  )
}

// ---------- Вкладка «Темы» ----------

function TopicsTab({ courseId, topics, reload, setError }: {
  courseId: number
  topics: Topic[]
  reload: () => Promise<void>
  setError: (e: string | null) => void
}) {
  const [title, setTitle] = useState('')
  const [busy, setBusy] = useState(false)

  async function create(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    try {
      await api(`/courses/${courseId}/topics`, { method: 'POST', body: { title } })
      setTitle('')
      await reload()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  /** Меняем местами позиции двух соседних тем. */
  async function move(index: number, direction: -1 | 1) {
    const a = topics[index]
    const b = topics[index + direction]
    if (!a || !b) return
    try {
      await Promise.all([
        api(`/topics/${a.id}`, { method: 'PUT', body: { title: a.title, description: a.description, position: b.position } }),
        api(`/topics/${b.id}`, { method: 'PUT', body: { title: b.title, description: b.description, position: a.position } }),
      ])
      await reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function remove(topic: Topic) {
    if (!confirm(`Удалить тему «${topic.title}» вместе с целями, материалами и карточками?`)) return
    try {
      await api(`/topics/${topic.id}`, { method: 'DELETE' })
      await reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="space-y-4">
      {topics.length === 0 ? (
        <Empty>Тем пока нет. Тема — это раздел или неделя курса: к ней привязаны материалы, цели обучения и карточки.</Empty>
      ) : (
        <Card className="divide-y divide-slate-100">
          {topics.map((topic, i) => (
            <div key={topic.id} className="flex items-center gap-3 px-4 py-3">
              <div className="flex flex-col">
                <button disabled={i === 0} onClick={() => move(i, -1)} className="px-1 text-xs text-slate-400 hover:text-slate-800 disabled:opacity-20" title="Выше">▲</button>
                <button disabled={i === topics.length - 1} onClick={() => move(i, 1)} className="px-1 text-xs text-slate-400 hover:text-slate-800 disabled:opacity-20" title="Ниже">▼</button>
              </div>
              <Link to={`/t/topics/${topic.id}`} className="min-w-0 flex-1">
                <div className="truncate font-medium text-slate-900 hover:text-brand-700">{topic.title}</div>
                <div className="mt-0.5 flex flex-wrap gap-x-4 text-xs text-slate-500">
                  <span>цели: {topic.outcomesCount}</span>
                  <span>материалы: {topic.materialsCount}</span>
                  <span className={topic.approvedCardsCount === 0 ? 'text-amber-600' : ''}>
                    вопросы: {topic.approvedCardsCount} утв. из {topic.cardsCount}
                  </span>
                </div>
              </Link>
              <Link to={`/t/topics/${topic.id}`}><Button size="sm" variant="secondary">Открыть</Button></Link>
              <Button size="sm" variant="ghost" title="Удалить тему" onClick={() => remove(topic)}><Icon name="trash" /></Button>
            </div>
          ))}
        </Card>
      )}
      <form onSubmit={create} className="flex gap-2">
        <Input required value={title} onChange={e => setTitle(e.target.value)} placeholder="Новая тема, например «Неделя 3: Constructive alignment»" />
        <Button type="submit" loading={busy} className="shrink-0"><Icon name="plus" /> Добавить тему</Button>
      </form>
    </div>
  )
}

// ---------- Вкладка «Экзамены» ----------

function ExamsTab({ exams, canCreate, onCreate, onEdit, reload, setError }: {
  exams: Exam[]
  canCreate: boolean
  onCreate: () => void
  onEdit: (exam: Exam) => void
  reload: () => Promise<void>
  setError: (e: string | null) => void
}) {
  async function action(exam: Exam, verb: 'publish' | 'close') {
    if (verb === 'close' && !confirm('Закрыть экзамен? Незаконченные интервью будут прерваны.')) return
    try {
      await api(`/exams/${exam.id}/${verb}`, { method: 'POST' })
      setError(null)
      await reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function remove(exam: Exam) {
    const warning = exam.sessionsCount > 0
      ? `Удалить экзамен «${exam.title}»? Вместе с ним безвозвратно удалятся ${exam.sessionsCount} сессий студентов: ответы, оценки и апелляции.`
      : `Удалить экзамен «${exam.title}»?`
    if (!confirm(warning)) return
    try {
      await api(`/exams/${exam.id}`, { method: 'DELETE' })
      setError(null)
      await reload()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <p className="text-sm text-slate-500">Экзамен покрывает одну или несколько тем. Студенты входят по коду.</p>
        <Button onClick={onCreate} disabled={!canCreate} title={canCreate ? '' : 'Сначала добавьте темы'}><Icon name="plus" /> Создать экзамен</Button>
      </div>
      {exams.length === 0 ? <Empty>Экзаменов пока нет.</Empty> : (
        <div className="space-y-3">
          {exams.map(exam => (
            <Card key={exam.id} className="flex flex-wrap items-center gap-4 p-4">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-medium text-slate-900">{exam.title}</span>
                  <ModeBadge mode={exam.mode} />
                  <ExamStatusBadge status={exam.status} />
                </div>
                <div className="mt-1 text-xs text-slate-500">
                  Темы: {exam.topics.map(t => t.title).join(' · ')}
                </div>
                <div className="text-xs text-slate-500">
                  {exam.questionsCount} вопр. · углублений {exam.minFollowups}–{exam.maxFollowups} · {exam.answerTimeLimitSec} с на ответ · {exam.sameQuestions ? 'вопросы одинаковые у всех' : 'у каждого свой набор вопросов'} · сессий: {exam.sessionsCount}
                </div>
              </div>
              <div className="text-center">
                <div className="text-[10px] uppercase tracking-wide text-slate-400">код доступа</div>
                <div className="font-mono text-xl font-semibold tracking-widest text-slate-900">{exam.accessCode}</div>
              </div>
              <div className="flex gap-2">
                {exam.status === 'DRAFT' && <Button size="sm" onClick={() => action(exam, 'publish')}><Icon name="send" /> Опубликовать</Button>}
                {exam.status === 'DRAFT' && <Button size="sm" variant="secondary" title="Изменить экзамен" onClick={() => onEdit(exam)}><Icon name="edit" /></Button>}
                {exam.status === 'PUBLISHED' && <Button size="sm" variant="danger" onClick={() => action(exam, 'close')}><Icon name="lock" /> Закрыть</Button>}
                <Link to={`/t/exams/${exam.id}/results`}><Button size="sm" variant="secondary"><Icon name="chart" /> Результаты</Button></Link>
                <Button size="sm" variant="ghost" title="Удалить экзамен" onClick={() => remove(exam)}><Icon name="trash" /></Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  )
}
