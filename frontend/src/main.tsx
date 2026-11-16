import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App'
import { AuthProvider } from './auth/AuthContext'
import './index.css'

// На Cloudflare backend засыпает без запросов. Будим его сразу при открытии страницы,
// чтобы он поднимался, пока пользователь вводит пароль. Локально (vite, nginx) этот путь
// обрабатывает сам backend и отвечает ошибкой — её просто игнорируем.
fetch('/api/_wake', { method: 'POST' }).catch(() => {})

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
