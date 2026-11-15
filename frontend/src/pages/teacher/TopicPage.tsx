import { Icon } from '../../components/ui'
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage } from '../../api/client'
import type { Card as CardT, CardStatus, Excerpt, FollowUp, FollowUpType, Material, Outcome, Topic } from '../../api/types'
import ExamModal from '../../components/ExamModal'
import { CardStatusBadge, FOLLOW_UP_LABEL, MaterialStatusBadge } from '../../components/labels'
import { Badge, Button, Card, Empty, ErrorBox, Field, formatDate, InfoBox, Input, Loading, PageHeader, Select, Tabs, TextArea } from '../../components/ui'

type Tab = 'outcomes' | 'materials' | 'cards'

/** Тема курса: цели обучения · материалы (PDF → RAG) · карточки вопросов. */
export default function TopicPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const [topic, setTopic] = useState<Topic | null>(null)
  const [outcomes, setOutcomes] = useState<Outcome[]>([])
  const [tab, setTab] = useState<Tab>('outcomes')
  const [error, setError] = useState<string | null>(null)
  const [examModal, setExamModal] = useState(false)

  const load = useCallback(async () => {
    try {
      const [t, o] = await Promise.all([api<Topic>(`/topics/${id}`), api<Outcome[]>(`/topics/${id}/outcomes`)])
      setTopic(t)
      setOutcomes(o)
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [id])

  useEffect(() => { load() }, [load])

  if (error && !topic) return <ErrorBox message={error} />
  if (!topic) return <Loading />

  return (
    <div>
      <Link to={`/t/courses/${topic.courseId}`} className="text-sm text-slate-500 hover:text-brand-700">← {topic.courseTitle}</Link>
      <PageHeader
        title={topic.title}
        subtitle="Тема курса"
        actions={
          <Button onClick={() => setExamModal(true)} title="Создать экзамен только по этой теме">
            <Icon name="plus" /> Проверка по теме
          </Button>
        }
      />

      <Tabs
        active={tab}
        onChange={setTab}
        tabs={[
          { id: 'outcomes', label: `Цели обучения (${outcomes.length})` },
          { id: 'materials', label: `Материалы (${topic.materialsCount})` },
          { id: 'cards', label: `Вопросы (${topic.approvedCardsCount}/${topic.cardsCount})` },
        ]}
      />

      {tab === 'outcomes' && <OutcomesTab topicId={topic.id} outcomes={outcomes} reload={load} />}
      {tab === 'materials' && <MaterialsTab topicId={topic.id} onChange={load} />}
      {tab === 'cards' && <CardsTab topicId={topic.id} outcomes={outcomes} onChange={load} />}

      {examModal && (
        <ExamModal
          courseId={topic.courseId}
          topics={[topic]}
          preselected={[topic.id]}
          defaultTitle={`Проверка по теме: ${topic.title}`}
          onClose={() => setExamModal(false)}
          onCreated={() => navigate(`/t/courses/${topic.courseId}?tab=exams`)}
        />
      )}
    </div>
  )
}

// ---------- Цели обучения ----------

function OutcomesTab({ topicId, outcomes, reload }: { topicId: number; outcomes: Outcome[]; reload: () => Promise<void> }) {
  const [text, setText] = useState('')
  const [editing, setEditing] = useState<{ id: number; text: string } | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function run(action: () => Promise<unknown>) {
    try {
      await action()
      setError(null)
      await reload()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  function create(e: FormEvent) {
    e.preventDefault()
    run(async () => {
      await api(`/topics/${topicId}/outcomes`, { method: 'POST', body: { text } })
      setText('')
    })
  }

  return (
    <div className="space-y-4">
      <p className="text-sm text-slate-500">
        Цель обучения — что студент должен уметь объяснить или применить. По каждой цели ИИ генерирует карточки вопросов
        (constructive alignment: вопросы строятся от целей).
      </p>
      <ErrorBox message={error} />
      {outcomes.length === 0 ? <Empty>Целей пока нет.</Empty> : (
        <Card className="divide-y divide-slate-100">
          {outcomes.map(o => (
            <div key={o.id} className="flex items-start gap-3 px-4 py-3">
              <Badge tone="brand">{o.code}</Badge>
              {editing?.id === o.id ? (
                <div className="flex-1 space-y-2">
                  <TextArea rows={2} value={editing.text} onChange={e => setEditing({ id: o.id, text: e.target.value })} />
                  <div className="flex gap-2">
                    <Button size="sm" onClick={() => run(async () => {
                      await api(`/outcomes/${o.id}`, { method: 'PUT', body: { code: o.code, text: editing.text } })
                      setEditing(null)
                    })}>Сохранить</Button>
                    <Button size="sm" variant="ghost" onClick={() => setEditing(null)}>Отмена</Button>
                  </div>
                </div>
              ) : (
                <>
                  <div className="flex-1 text-sm text-slate-800">{o.text}</div>
                  <Button size="sm" variant="ghost" title="Изменить цель" onClick={() => setEditing({ id: o.id, text: o.text })}><Icon name="edit" /></Button>
                  <Button size="sm" variant="ghost" onClick={() => {
                    if (confirm('Удалить цель и её карточки?')) run(() => api(`/outcomes/${o.id}`, { method: 'DELETE' }))
                  }} title="Удалить цель"><Icon name="trash" /></Button>
                </>
              )}
            </div>
          ))}
        </Card>
      )}
      <form onSubmit={create} className="flex gap-2">
        <Input required value={text} onChange={e => setText(e.target.value)} placeholder="Например: объяснить, как constructive alignment связывает цели, занятия и оценивание" />
        <Button type="submit" className="shrink-0"><Icon name="plus" /> Добавить цель</Button>
      </form>
    </div>
  )
}

// ---------- Материалы ----------

function MaterialsTab({ topicId, onChange }: { topicId: number; onChange: () => void }) {
  const [materials, setMaterials] = useState<Material[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [uploading, setUploading] = useState(false)
  const fileInput = useRef<HTMLInputElement>(null)

  const load = useCallback(async () => {
    try {
      setMaterials(await api<Material[]>(`/topics/${topicId}/materials`))
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [topicId])

  useEffect(() => { load() }, [load])

  // Пока какой-то файл обрабатывается — опрашиваем статус.
  const processing = materials?.some(m => m.status === 'UPLOADED' || m.status === 'PROCESSING') ?? false
  useEffect(() => {
    if (!processing) return
    const timer = setInterval(load, 2500)
    return () => clearInterval(timer)
  }, [processing, load])

  async function upload(file: File) {
    setUploading(true)
    setError(null)
    try {
      const form = new FormData()
      form.append('file', file)
      await api(`/topics/${topicId}/materials`, { method: 'POST', form })
      await load()
      onChange()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setUploading(false)
      if (fileInput.current) fileInput.current.value = ''
    }
  }

  async function remove(m: Material) {
    if (!confirm(`Удалить «${m.fileName}» и его фрагменты из поиска?`)) return
    try {
      await api(`/materials/${m.id}`, { method: 'DELETE' })
      await load()
      onChange()
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <p className="text-sm text-slate-500">
          PDF, DOCX или PPTX с текстом, до 50 МБ. Файл режется на фрагменты; ИИ задаёт вопросы и проверяет ответы только по ним.
        </p>
        <input ref={fileInput} type="file" accept=".pdf,.docx,.pptx" className="hidden" onChange={e => e.target.files?.[0] && upload(e.target.files[0])} />
        <Button loading={uploading} onClick={() => fileInput.current?.click()}><Icon name="upload" /> Загрузить файл</Button>
      </div>
      <ErrorBox message={error} />

      {!materials ? <Loading /> : materials.length === 0 ? <Empty>Материалов пока нет.</Empty> : (
        <Card className="divide-y divide-slate-100">
          {materials.map(m => (
            <div key={m.id} className="flex flex-wrap items-center gap-3 px-4 py-3">
              <div className="min-w-0 flex-1">
                <div className="truncate font-medium text-slate-900">{m.fileName}</div>
                <div className="text-xs text-slate-500">
                  {formatDate(m.uploadedAt)}{m.pages ? ` · страниц с текстом: ${m.pages}` : ''}
                </div>
                {m.error && <div className="mt-1 text-xs text-red-700">{m.error}</div>}
              </div>
              <MaterialStatusBadge status={m.status} />
              <Button size="sm" variant="ghost" title="Удалить материал" onClick={() => remove(m)}><Icon name="trash" /></Button>
            </div>
          ))}
        </Card>
      )}

      <SearchDebug topicId={topicId} />
    </div>
  )
}

/** Отладка RAG: что находит поиск по материалам темы. */
function SearchDebug({ topicId }: { topicId: number }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<Excerpt[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function search(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setResults(await api<Excerpt[]>(`/topics/${topicId}/search?q=${encodeURIComponent(query)}`))
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="p-4">
      <h3 className="mb-1 text-sm font-semibold">Проверить поиск по материалам</h3>
      <p className="mb-3 text-xs text-slate-500">Показывает 5 фрагментов темы, ближайших к запросу, — те же, что получит ИИ.</p>
      <form onSubmit={search} className="flex gap-2">
        <Input required value={query} onChange={e => setQuery(e.target.value)} placeholder="Запрос, например: уровни таксономии SOLO" />
        <Button type="submit" variant="secondary" loading={busy} className="shrink-0"><Icon name="search" /> Найти</Button>
      </form>
      <div className="mt-3 space-y-2">
        <ErrorBox message={error} />
        {results?.length === 0 && <div className="text-sm text-slate-500">Ничего не найдено. Есть ли материалы в статусе «Готов»?</div>}
        {results?.map(r => <ExcerptView key={r.id} excerpt={r} />)}
      </div>
    </Card>
  )
}

function ExcerptView({ excerpt }: { excerpt: Excerpt }) {
  return (
    <div className="rounded-lg border border-slate-200 px-3 py-2 text-sm">
      <div className="mb-1 text-xs font-medium text-slate-500">{excerpt.fileName}{excerpt.page ? ` · стр. ${excerpt.page}` : ''}</div>
      <div className="line-clamp-4 whitespace-pre-wrap text-slate-700">{excerpt.text}</div>
    </div>
  )
}

// ---------- Карточки ----------

function CardsTab({ topicId, outcomes, onChange }: { topicId: number; outcomes: Outcome[]; onChange: () => void }) {
  const [cards, setCards] = useState<CardT[] | null>(null)
  const [filter, setFilter] = useState<CardStatus | ''>('')
  const [generatingFor, setGeneratingFor] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    try {
      setCards(await api<CardT[]>(`/topics/${topicId}/cards${filter ? `?status=${filter}` : ''}`))
    } catch (e) {
      setError(errorMessage(e))
    }
  }, [topicId, filter])

  useEffect(() => { load() }, [load])

  async function generate(outcome: Outcome) {
    setGeneratingFor(outcome.id)
    setError(null)
    try {
      await api(`/outcomes/${outcome.id}/cards/generate`, { method: 'POST' })
      await load()
      onChange()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setGeneratingFor(null)
    }
  }

  const changed = async () => {
    await load()
    onChange()
  }

  return (
    <div className="space-y-5">
      <InfoBox>
        Вопрос для экзамена хранится вместе с тем, что нужно ИИ, чтобы вести интервью и оценивать: ключевые идеи
        правильного ответа, типичные ошибки, идеи углублений и рубрика уровней 0–4. Вопрос можно написать самому или
        попросить ИИ предложить черновик по материалам темы.
      </InfoBox>

      <Card className="p-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold">Свой вопрос</h3>
            <p className="text-xs text-slate-500">Напишите вопрос сами — он сразу считается утверждённым.</p>
          </div>
          <Button size="sm" disabled={outcomes.length === 0 || creating} onClick={() => setCreating(true)}><Icon name="plus" /> Добавить свой вопрос</Button>
        </div>
      </Card>
      {creating && (
        <CardEditor outcomes={outcomes} onCancel={() => setCreating(false)} onSaved={async () => { setCreating(false); await changed() }} />
      )}

      <Card className="p-4">
        <h3 className="mb-1 text-sm font-semibold">Вопрос от ИИ по цели обучения</h3>
        <p className="mb-3 text-xs text-slate-500">
          ИИ пишет черновик по материалам темы (5–15 секунд). В экзамен попадают только вопросы, которые вы проверили и утвердили.
        </p>
        {outcomes.length === 0 ? (
          <div className="text-sm text-slate-500">Сначала добавьте цели обучения.</div>
        ) : (
          <div className="space-y-2">
            {outcomes.map(o => (
              <div key={o.id} className="flex items-center gap-3">
                <Badge tone="brand">{o.code}</Badge>
                <span className="min-w-0 flex-1 truncate text-sm text-slate-700">{o.text}</span>
                <Button size="sm" variant="secondary" loading={generatingFor === o.id} disabled={generatingFor !== null} onClick={() => generate(o)}>
                  {generatingFor === o.id ? 'ИИ пишет…' : <><Icon name="sparkles" /> Сгенерировать</>}
                </Button>
              </div>
            ))}
          </div>
        )}
      </Card>

      <ErrorBox message={error} />

      <div className="flex items-center gap-3">
        <h3 className="text-sm font-semibold">Вопросы темы</h3>
        <Select value={filter} onChange={e => setFilter(e.target.value as CardStatus | '')} className="!w-44">
          <option value="">Все статусы</option>
          <option value="DRAFT">Черновики</option>
          <option value="APPROVED">Утверждённые</option>
          <option value="REJECTED">Отклонённые</option>
        </Select>
      </div>

      {!cards ? <Loading /> : cards.length === 0 ? <Empty>Вопросов пока нет.</Empty> : (
        <div className="space-y-2">
          {cards.map(card => (
            <CardView key={card.id} card={card} outcome={outcomes.find(o => o.id === card.outcomeId)} onChange={changed} />
          ))}
        </div>
      )}
    </div>
  )
}

function CardView({ card, outcome, onChange }: { card: CardT; outcome?: Outcome; onChange: () => Promise<void> }) {
  const [editing, setEditing] = useState(false)
  const [open, setOpen] = useState(false)
  const [sources, setSources] = useState<Excerpt[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function setStatus(verb: 'approve' | 'reject') {
    setBusy(true)
    try {
      await api(`/cards/${card.id}/${verb}`, { method: 'POST' })
      await onChange()
    } catch (e) {
      setError(errorMessage(e))
    } finally {
      setBusy(false)
    }
  }

  async function loadSources() {
    try {
      setSources(await api<Excerpt[]>(`/cards/${card.id}/sources`))
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  if (editing) {
    return <CardEditor card={card} onCancel={() => setEditing(false)} onSaved={async () => { setEditing(false); await onChange() }} />
  }

  return (
    <Card className="px-5 py-3">
      <div className="mb-2 flex flex-wrap items-center gap-2">
        {outcome && <Badge tone="brand">{outcome.code}</Badge>}
        <CardStatusBadge status={card.status} />
        {card.aiGenerated
          ? <Badge title="Черновик написан ИИ и проверяется преподавателем">Черновик ИИ</Badge>
          : <Badge tone="blue">Написан преподавателем</Badge>}
        {!open && card.status !== 'APPROVED' && <Button size="sm" className="ml-auto" loading={busy} onClick={() => setStatus('approve')}><Icon name="check" /> Утвердить</Button>}
        {open && <span className="ml-auto text-xs text-slate-400">изменён {formatDate(card.updatedAt)}</span>}
      </div>
      <button onClick={() => setOpen(!open)} className="flex w-full items-start gap-2 text-left" title={open ? 'Свернуть' : 'Развернуть'}>
        <span className="mt-0.5 text-slate-400">{open ? '▾' : '▸'}</span>
        <span className="text-base font-medium text-slate-900">{card.question}</span>
      </button>

      {open && (<>
      <div className="mt-4 grid gap-4 md:grid-cols-2">
        <Section title="Ключевые идеи (fundamentals)" items={card.fundamentals} />
        <Section title="Типичные ошибки" items={card.misconceptions} />
      </div>

      <div className="mt-4">
        <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Идеи углублений</div>
        <ul className="space-y-1 text-sm text-slate-700">
          {card.followUpIdeas.map((f, i) => (
            <li key={i}><span className="mr-2 rounded bg-slate-100 px-1.5 py-0.5 text-xs">{FOLLOW_UP_LABEL[f.type]}</span>{f.text}</li>
          ))}
        </ul>
      </div>

      <div className="mt-4">
        <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">Рубрика SOLO</div>
        <div className="space-y-1 text-sm">
          {['0', '1', '2', '3', '4'].map(level => (
            <div key={level} className="flex gap-2">
              <span className="w-5 shrink-0 font-semibold text-slate-500">{level}</span>
              <span className="text-slate-700">{card.rubric[level]}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-4">
        {sources === null ? (
          <button onClick={loadSources} className="text-xs font-medium text-brand-700 hover:underline">
            Показать источники ({card.sourceChunkIds.length} фрагм.)
          </button>
        ) : sources.length === 0 ? (
          <div className="text-xs text-slate-500">Фрагменты-источники не найдены (материал могли удалить).</div>
        ) : (
          <div className="space-y-2">{sources.map(s => <ExcerptView key={s.id} excerpt={s} />)}</div>
        )}
      </div>

      <div className="mt-4 space-y-2">
        <ErrorBox message={error} />
        <div className="flex flex-wrap gap-2">
          {card.status !== 'APPROVED' && <Button size="sm" loading={busy} onClick={() => setStatus('approve')}><Icon name="check" /> Утвердить</Button>}
          <Button size="sm" variant="secondary" onClick={() => setEditing(true)}><Icon name="edit" /> Редактировать</Button>
          {card.status !== 'REJECTED' && <Button size="sm" variant="danger" disabled={busy} onClick={() => setStatus('reject')}><Icon name="x" /> Отклонить</Button>}
        </div>
      </div>
      </>)}
    </Card>
  )
}

function Section({ title, items }: { title: string; items: string[] }) {
  return (
    <div>
      <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</div>
      {items.length === 0
        ? <div className="text-sm text-slate-400">—</div>
        : <ul className="list-inside list-disc space-y-1 text-sm text-slate-700">{items.map((s, i) => <li key={i}>{s}</li>)}</ul>}
    </div>
  )
}

const FOLLOW_UP_TYPES: FollowUpType[] = ['WHY', 'EXAMPLE', 'WHAT_IF', 'FIND_ERROR']

/** Правка карточки преподавателем: списки редактируются «по одному пункту на строку». */
const DEFAULT_RUBRIC: Record<string, string> = {
  '0': 'Ответ не по существу или повторяет вопрос',
  '1': 'Назван один верный аспект',
  '2': 'Названо несколько верных аспектов, но без связи между ними',
  '3': 'Аспекты связаны: объяснено, почему и как',
  '4': 'Обобщение, свой пример или перенос на новую ситуацию',
}

/** Без card — создание своего вопроса (тогда нужен список целей обучения), с card — правка. */
function CardEditor({ card, outcomes = [], onCancel, onSaved }: {
  card?: CardT
  outcomes?: Outcome[]
  onCancel: () => void
  onSaved: () => Promise<void>
}) {
  const [outcomeId, setOutcomeId] = useState<number>(card?.outcomeId ?? outcomes[0]?.id ?? 0)
  const [question, setQuestion] = useState(card?.question ?? '')
  const [fundamentals, setFundamentals] = useState(card?.fundamentals.join('\n') ?? '')
  const [misconceptions, setMisconceptions] = useState(card?.misconceptions.join('\n') ?? '')
  const [ideas, setIdeas] = useState<FollowUp[]>(card?.followUpIdeas ?? [{ type: 'WHY', text: '' }])
  const [rubric, setRubric] = useState<Record<string, string>>({ ...(card?.rubric ?? DEFAULT_RUBRIC) })
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const lines = (value: string) => value.split('\n').map(s => s.trim()).filter(Boolean)

  async function save(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api(card ? `/cards/${card.id}` : `/outcomes/${outcomeId}/cards`, {
        method: card ? 'PUT' : 'POST',
        body: {
          question,
          fundamentals: lines(fundamentals),
          misconceptions: lines(misconceptions),
          followUpIdeas: ideas.filter(i => i.text.trim()),
          rubric,
        },
      })
      await onSaved()
    } catch (err) {
      setError(errorMessage(err))
      setBusy(false)
    }
  }

  return (
    <Card className="border-brand-200 p-5">
      <form onSubmit={save} className="space-y-4">
        {!card && (
          <Field label="Цель обучения, к которой относится вопрос">
            <Select value={outcomeId} onChange={e => setOutcomeId(Number(e.target.value))}>
              {outcomes.map(o => <option key={o.id} value={o.id}>{o.code} — {o.text}</option>)}
            </Select>
          </Field>
        )}
        <Field label="Вопрос" hint="Открытый вопрос на объяснение или применение, не на определение">
          <TextArea rows={2} required value={question} onChange={e => setQuestion(e.target.value)} />
        </Field>
        <div className="grid gap-4 md:grid-cols-2">
          <Field label="Ключевые идеи правильного ответа" hint="По одной на строку; по ним ИИ судит, понял ли студент">
            <TextArea rows={5} required value={fundamentals} onChange={e => setFundamentals(e.target.value)} />
          </Field>
          <Field label="Типичные ошибки" hint="По одной на строку">
            <TextArea rows={5} value={misconceptions} onChange={e => setMisconceptions(e.target.value)} />
          </Field>
        </div>

        <div>
          <span className="mb-1 block text-sm font-medium text-slate-700">Идеи углублений</span>
          <div className="space-y-2">
            {ideas.map((idea, i) => (
              <div key={i} className="flex gap-2">
                <Select className="!w-40 shrink-0" value={idea.type} onChange={e => setIdeas(ideas.map((x, j) => j === i ? { ...x, type: e.target.value as FollowUpType } : x))}>
                  {FOLLOW_UP_TYPES.map(t => <option key={t} value={t}>{FOLLOW_UP_LABEL[t]}</option>)}
                </Select>
                <Input value={idea.text} onChange={e => setIdeas(ideas.map((x, j) => j === i ? { ...x, text: e.target.value } : x))} />
                <Button type="button" size="sm" variant="ghost" onClick={() => setIdeas(ideas.filter((_, j) => j !== i))}>✕</Button>
              </div>
            ))}
            <Button type="button" size="sm" variant="secondary" onClick={() => setIdeas([...ideas, { type: 'WHY', text: '' }])}>+ идея</Button>
          </div>
        </div>

        <div>
          <span className="mb-1 block text-sm font-medium text-slate-700">Рубрика SOLO для этого вопроса</span>
          <div className="space-y-2">
            {['0', '1', '2', '3', '4'].map(level => (
              <div key={level} className="flex items-start gap-2">
                <span className="mt-2 w-5 shrink-0 text-sm font-semibold text-slate-500">{level}</span>
                <TextArea rows={1} required value={rubric[level] ?? ''} onChange={e => setRubric({ ...rubric, [level]: e.target.value })} />
              </div>
            ))}
          </div>
        </div>

        {card && <InfoBox>Правка не меняет статус: утверждённый вопрос остаётся утверждённым.</InfoBox>}
        <ErrorBox message={error} />
        <div className="flex gap-2">
          <Button type="submit" loading={busy}>Сохранить</Button>
          <Button type="button" variant="secondary" onClick={onCancel}>Отмена</Button>
        </div>
      </form>
    </Card>
  )
}
