import { Icon } from './ui'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import type { Role } from '../api/types'

const NAV: Record<Role, { to: string; label: string }[]> = {
  STUDENT: [
    { to: '/s/join', label: 'Войти в экзамен' },
    { to: '/s/history', label: 'Мои интервью' },
  ],
  TEACHER: [
    { to: '/t/courses', label: 'Курсы' },
    { to: '/t/appeals', label: 'Апелляции' },
  ],
  ADMIN: [{ to: '/admin', label: 'Преподаватели' }],
}

const ROLE_LABEL: Record<Role, string> = { STUDENT: 'Студент', TEACHER: 'Преподаватель', ADMIN: 'Администратор' }

export function Logo() {
  return (
    <span className="flex items-center gap-2 text-lg font-semibold tracking-tight text-slate-900">
      <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-brand-700 text-sm font-bold text-white">V</span>
      VivaMind
    </span>
  )
}

/** Общая рамка страниц: шапка с навигацией по роли + содержимое. */
export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  if (!user) return null

  return (
    <div className="flex min-h-full flex-col">
      <header className="border-b border-slate-200 bg-white">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-x-6 gap-y-2 px-4 py-3">
          <Link to="/"><Logo /></Link>
          <nav className="order-last flex w-full items-center gap-1 overflow-x-auto sm:order-none sm:w-auto sm:flex-1">
            {NAV[user.role].map(item => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  `whitespace-nowrap rounded-lg px-3 py-1.5 text-sm font-medium ${isActive ? 'bg-brand-50 text-brand-800' : 'text-slate-600 hover:bg-slate-100'}`
                }
              >
                {item.label}
              </NavLink>
            ))}
          </nav>
          <div className="ml-auto text-right text-sm leading-tight sm:ml-0">
            <div className="font-medium text-slate-800">{user.fullName}</div>
            <div className="text-xs text-slate-500">{ROLE_LABEL[user.role]}</div>
          </div>
          <button
            onClick={() => { logout(); navigate('/login') }}
            title="Выйти"
            className="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm text-slate-600 hover:bg-slate-100"
          >
            <Icon name="logout" /> <span className="hidden sm:inline">Выйти</span>
          </button>
        </div>
      </header>
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 py-4 text-center text-xs text-slate-400">
        VivaMind · MVP · ИИ готовит черновик оценки, итог подтверждает преподаватель
      </footer>
    </div>
  )
}
