// Типы ответов backend (раздел 10 ТЗ).

export type Role = 'ADMIN' | 'TEACHER' | 'STUDENT'

export interface User {
  id: number
  email: string
  fullName: string
  role: Role
}

export interface AuthResponse {
  token: string
  user: User
}

// ---------- Курс ----------

export interface Course {
  id: number
  title: string
  description: string | null
  language: string
  createdAt: string
}

export interface Topic {
  id: number
  courseId: number
  title: string
  description: string | null
  position: number
  outcomesCount: number
  materialsCount: number
  cardsCount: number
  approvedCardsCount: number
  courseTitle: string
  courseLanguage: string
}

export interface Outcome {
  id: number
  topicId: number
  code: string
  text: string
  position: number
}

export type MaterialStatus = 'UPLOADED' | 'PROCESSING' | 'READY' | 'FAILED'

export interface Material {
  id: number
  fileName: string
  status: MaterialStatus
  error: string | null
  pages: number | null
  uploadedAt: string
}

export interface Excerpt {
  id: string
  fileName: string
  page: number | null
  text: string
}

// ---------- Карточки ----------

export type FollowUpType = 'WHY' | 'EXAMPLE' | 'WHAT_IF' | 'FIND_ERROR' | 'HINT'

export interface FollowUp {
  type: FollowUpType
  text: string
}

export type CardStatus = 'DRAFT' | 'APPROVED' | 'REJECTED'

export interface Card {
  id: number
  topicId: number
  outcomeId: number
  question: string
  fundamentals: string[]
  misconceptions: string[]
  followUpIdeas: FollowUp[]
  rubric: Record<string, string>
  sourceChunkIds: string[]
  status: CardStatus
  aiGenerated: boolean
  updatedAt: string
}

// ---------- Экзамен ----------

export type ExamMode = 'PRACTICE' | 'EXAM'
export type ExamStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED'

export interface Exam {
  id: number
  courseId: number
  courseTitle: string
  title: string
  mode: ExamMode
  questionsCount: number
  minFollowups: number
  maxFollowups: number
  answerTimeLimitSec: number
  accessCode: string
  status: ExamStatus
  createdAt: string
  topics: { id: number; title: string }[]
  sessionsCount: number
  sameQuestions: boolean
}

export interface ExamRequest {
  title: string
  mode: ExamMode
  topicIds: number[]
  questionsCount: number
  minFollowups: number
  maxFollowups: number
  answerTimeLimitSec: number
  sameQuestions: boolean
}

export interface SoloLevelInfo {
  level: number
  name: string
  description: string
}

export interface ExamIntro {
  id: number
  title: string
  courseTitle: string
  language: string
  mode: ExamMode
  topics: string[]
  questionsCount: number
  maxFollowups: number
  answerTimeLimitSec: number
  rules: string[]
  soloRubric: SoloLevelInfo[]
  activeSessionId: number | null
  attemptUsed: boolean
}

// ---------- Интервью ----------

export type SessionStatus = 'IN_PROGRESS' | 'COMPLETED' | 'AI_GRADED' | 'CONFIRMED' | 'ABANDONED'
export type MessageKind = 'BASE_QUESTION' | 'FOLLOW_UP' | 'ANSWER' | 'DONT_KNOW' | 'TIMEOUT' | 'SYSTEM'
export type Author = 'AI' | 'STUDENT'

export interface QuestionInfo {
  position: number
  total: number
  courseTopic: string
}

export interface ChatMessage {
  id: number
  author: Author
  kind: MessageKind
  followUpType: FollowUpType | null
  text: string
  questionPosition: number | null
  createdAt: string
}

export interface SessionState {
  id: number
  examId: number
  examTitle: string
  mode: ExamMode
  sessionStatus: SessionStatus
  question: QuestionInfo | null
  messages: ChatMessage[]
  answerDeadline: string | null
  answerTimeLimitSec: number
  awaitingAi: boolean
  serverTime: string
}

export interface AnswerResponse {
  sessionStatus: SessionStatus
  question: QuestionInfo | null
  aiMessage: ChatMessage
  answerDeadline: string | null
  serverTime: string
}

// ---------- Оценивание ----------

export interface Evidence {
  quote: string
  fundamental: string
}

export interface StoredGrade {
  soloLevel: number | null
  confidence: number | null
  rationale: string | null
  evidence: Evidence[]
  missingFundamentals: string[]
  studentFeedback: string | null
  removedQuotes: number
  needsReview: boolean
  reviewReasons: string[]
  failed: boolean
}

export interface SessionSummary {
  overall: string
  strengths: string[]
  gaps: string[]
  reread: { source: string; pages: string; why: string }[]
}

export interface Signals {
  pasteAttempts: number
  tabSwitches: number
  timeouts: number
}

export interface Appeal {
  id: number
  sessionId: number
  reason: string
  status: 'OPEN' | 'RESOLVED'
  resolution: string | null
  createdAt: string
  resolvedAt: string | null
}

export interface TranscriptMessage {
  id: number
  author: Author
  kind: MessageKind
  followUpType: FollowUpType | null
  text: string
  responseMs: number | null
  pasteAttempts: number
  tabSwitches: number
  flagged: boolean
  createdAt: string
}

export interface QuestionReview {
  id: number
  position: number
  question: string
  fundamentals: string[]
  rubric: Record<string, string>
  state: 'PENDING' | 'ACTIVE' | 'CLOSED'
  followupsAsked: number
  aiSoloLevel: number | null
  aiConfidence: number | null
  grade: StoredGrade | null
  finalSoloLevel: number | null
  teacherComment: string | null
  messages: TranscriptMessage[]
}

export interface TopicReview {
  topicId: number
  title: string
  avgLevel: number | null
  questions: QuestionReview[]
}

export interface Review {
  sessionId: number
  examId: number
  examTitle: string
  courseId: number
  courseTitle: string
  mode: ExamMode
  status: SessionStatus
  studentName: string
  studentEmail: string
  aiScore: number | null
  finalScore: number | null
  previewScore: number | null
  needsReview: boolean
  teacherComment: string | null
  summary: SessionSummary | null
  signals: Signals
  topics: TopicReview[]
  appeal: Appeal | null
  startedAt: string
  finishedAt: string | null
  reviewedAt: string | null
}

export interface SessionRow {
  id: number
  studentName: string
  studentEmail: string
  status: SessionStatus
  aiScore: number | null
  finalScore: number | null
  needsReview: boolean
  signals: Signals
  topicLevels: { topicId: number; title: string; avgLevel: number | null }[]
  openAppeal: boolean
  startedAt: string
  finishedAt: string | null
}

export interface ReportQuestion {
  position: number
  question: string
  level: number | null
  changedByTeacher: boolean
  feedback: string | null
  evidence: Evidence[]
  missingFundamentals: string[]
  teacherComment: string | null
  transcript: { author: Author; kind: MessageKind; text: string }[]
}

export interface Report {
  available: boolean
  message: string | null
  sessionId: number
  examTitle: string
  courseTitle: string
  mode: ExamMode
  status: SessionStatus
  score: number | null
  confirmed: boolean
  summary: SessionSummary | null
  topics: { title: string; avgLevel: number | null; questions: ReportQuestion[] }[]
  teacherComment: string | null
  appeal: Appeal | null
  canAppeal: boolean
  appealDeadline: string | null
}

export interface HistoryRow {
  id: number
  examTitle: string
  courseTitle: string
  mode: ExamMode
  status: SessionStatus
  score: number | null
  reportAvailable: boolean
  startedAt: string
  finishedAt: string | null
}

export interface AppealRow {
  id: number
  sessionId: number
  examTitle: string
  courseTitle: string
  studentName: string
  reason: string
  status: 'OPEN' | 'RESOLVED'
  resolution: string | null
  finalScore: number | null
  createdAt: string
  resolvedAt: string | null
}
