import { Icon } from '../../components/ui'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { Course } from '../../api/types'
import { LANGUAGES } from '../../components/labels'
import { Badge, Button, Card, Empty, ErrorBox, Field, Input, Loading, Modal, PageHeader, Select, TextArea } from '../../components/ui'

export default function CoursesPage() {
  const [courses, setCourses] = useState<Course[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [creating, setCreating] = useState(false)

  const load = () => api<Course[]>('/courses').then(setCourses).catch(e => setError(errorMessage(e)))
  useEffect(() => { load() }, [])

  return (
    <div>
      <PageHeader
        title="Курсы"
        subtitle="Курс → темы → цели обучения → карточки вопросов → экзамен по выбранным темам."
        actions={<Button onClick={() => setCreating(true)}><Icon name="plus" /> Создать курс</Button>}
      />
      <ErrorBox message={error} />
      {!courses ? <Loading /> : courses.length === 0 ? (
        <Empty>Курсов пока нет. Создайте первый — затем добавьте темы и загрузите материалы.</Empty>
      ) : (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {courses.map(course => (
            <Link key={course.id} to={`/t/courses/${course.id}`}>
              <Card className="h-full p-5 transition-shadow hover:shadow-md">
                <div className="mb-2 flex items-start justify-between gap-2">
                  <h2 className="font-semibold text-slate-900">{course.title}</h2>
                  <Badge>{LANGUAGES[course.language] ?? course.language}</Badge>
                </div>
                <p className="line-clamp-3 text-sm text-slate-500">{course.description || 'Без описания'}</p>
              </Card>
            </Link>
          ))}
        </div>
      )}
      {creating && <CourseModal onClose={() => setCreating(false)} onSaved={() => { setCreating(false); load() }} />}
    </div>
  )
}

export function CourseModal({ course, onClose, onSaved }: { course?: Course; onClose: () => void; onSaved: () => void }) {
  const [title, setTitle] = useState(course?.title ?? '')
  const [description, setDescription] = useState(course?.description ?? '')
  const [language, setLanguage] = useState(course?.language ?? 'ru')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function submit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(course ? `/courses/${course.id}` : '/courses', {
        method: course ? 'PUT' : 'POST',
        body: { title, description, language },
      })
      onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Modal title={course ? 'Курс' : 'Новый курс'} onClose={onClose}>
      <form onSubmit={submit} className="space-y-4">
        <Field label="Название">
          <Input required autoFocus value={title} onChange={e => setTitle(e.target.value)} placeholder="Pedagogy of Higher Education" />
        </Field>
        <Field label="Описание">
          <TextArea rows={3} value={description} onChange={e => setDescription(e.target.value)} />
        </Field>
        <Field label="Язык курса" hint="На этом языке ИИ задаёт вопросы и пишет отчёт. Материалы лучше загружать на нём же.">
          <Select value={language} onChange={e => setLanguage(e.target.value)}>
            {Object.entries(LANGUAGES).map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </Select>
        </Field>
        <ErrorBox message={error} />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>Отмена</Button>
          <Button type="submit" loading={busy}>{course ? 'Сохранить' : 'Создать'}</Button>
        </div>
      </form>
    </Modal>
  )
}
