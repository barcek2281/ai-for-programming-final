// Клиент API: fetch + JWT в заголовке Authorization.

const BASE = '/api/v1'
const TOKEN_KEY = 'vm_token'

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

type Options = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  form?: FormData
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers['Authorization'] = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(BASE + path, {
      method: options.method ?? 'GET',
      headers,
      body: options.form ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
    })
  } catch {
    throw new ApiError(0, 'Нет связи с сервером. Проверьте, что backend запущен.')
  }

  if (response.status === 401 && token) {
    // Токен истёк или недействителен — выходим на экран входа.
    setToken(null)
    window.dispatchEvent(new Event('vm-unauthorized'))
  }

  const text = await response.text()
  const data = text ? safeJson(text) : null
  if (!response.ok) {
    const fromServer = data && typeof data === 'object' && 'message' in data
      ? String((data as { message: unknown }).message)
      : ''
    throw new ApiError(response.status, fromServer || `Ошибка ${response.status}`)
  }
  return data as T
}

function safeJson(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}

export function errorMessage(e: unknown): string {
  return e instanceof Error ? e.message : 'Неизвестная ошибка'
}
