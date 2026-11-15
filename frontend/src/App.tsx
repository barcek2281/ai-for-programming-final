import { Navigate, Outlet, Route, Routes, useLocation } from 'react-router-dom'
import { homePath, useAuth } from './auth/AuthContext'
import type { Role } from './api/types'
import Layout from './components/Layout'
import { Loading } from './components/ui'
import LoginPage from './pages/auth/LoginPage'
import RegisterPage from './pages/auth/RegisterPage'
import JoinPage from './pages/student/JoinPage'
import StartPage from './pages/student/StartPage'
import InterviewPage from './pages/student/InterviewPage'
import ReportPage from './pages/student/ReportPage'
import HistoryPage from './pages/student/HistoryPage'
import CoursesPage from './pages/teacher/CoursesPage'
import CoursePage from './pages/teacher/CoursePage'
import TopicPage from './pages/teacher/TopicPage'
import ExamResultsPage from './pages/teacher/ExamResultsPage'
import ReviewPage from './pages/teacher/ReviewPage'
import AppealsPage from './pages/teacher/AppealsPage'
import TeachersPage from './pages/admin/TeachersPage'

/** Пускает только пользователя с нужной ролью; остальных отправляет на вход или на их домашнюю страницу. */
function RequireRole({ role }: { role: Role }) {
  const { user, loading } = useAuth()
  const location = useLocation()
  if (loading) return <div className="p-8"><Loading /></div>
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (user.role !== role) return <Navigate to={homePath(user)} replace />
  return <Outlet />
}

function Home() {
  const { user, loading } = useAuth()
  if (loading) return <div className="p-8"><Loading /></div>
  return <Navigate to={user ? homePath(user) : '/login'} replace />
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      <Route element={<RequireRole role="STUDENT" />}>
        {/* Экран интервью — без общей шапки, чтобы ничто не отвлекало */}
        <Route path="/s/sessions/:id" element={<InterviewPage />} />
        <Route element={<Layout />}>
          <Route path="/s/join" element={<JoinPage />} />
          <Route path="/s/exams/:id/start" element={<StartPage />} />
          <Route path="/s/sessions/:id/report" element={<ReportPage />} />
          <Route path="/s/history" element={<HistoryPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="TEACHER" />}>
        <Route element={<Layout />}>
          <Route path="/t/courses" element={<CoursesPage />} />
          <Route path="/t/courses/:id" element={<CoursePage />} />
          <Route path="/t/topics/:id" element={<TopicPage />} />
          <Route path="/t/exams/:id/results" element={<ExamResultsPage />} />
          <Route path="/t/sessions/:id/review" element={<ReviewPage />} />
          <Route path="/t/appeals" element={<AppealsPage />} />
        </Route>
      </Route>

      <Route element={<RequireRole role="ADMIN" />}>
        <Route element={<Layout />}>
          <Route path="/admin" element={<TeachersPage />} />
        </Route>
      </Route>

      <Route path="*" element={<Home />} />
    </Routes>
  )
}
