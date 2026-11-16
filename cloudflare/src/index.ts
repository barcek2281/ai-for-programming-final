import { Container, getContainer } from '@cloudflare/containers'
import type { StopParams } from '@cloudflare/containers'

/** Переменные Worker'а (vars + secrets), которые уходят в контейнер как окружение Spring Boot. */
const BACKEND_ENV = [
  'OPENROUTER_API_KEY', 'OPENROUTER_BASE_URL', 'LLM_FAST_MODEL', 'LLM_STRONG_MODEL',
  'EMBEDDING_MODEL', 'EMBEDDING_DIM',
  'DB_URL', 'DB_USER', 'DB_PASSWORD',
  'S3_ENDPOINT', 'S3_REGION', 'S3_ACCESS_KEY', 'S3_SECRET_KEY', 'S3_BUCKET',
  'JWT_SECRET', 'VOICE_ENABLED',
  'SEED_ADMIN_EMAIL', 'SEED_ADMIN_PASSWORD', 'SEED_DEMO_USERS', 'SEED_DEMO_PASSWORD',
  'JAVA_TOOL_OPTIONS',
] as const

/**
 * Библиотека по умолчанию ждёт порт 20 с и отвечает 500. Spring Boot с Flyway, Hibernate и Spring AI
 * на 0.5 vCPU поднимается дольше, поэтому ждём до трёх минут.
 */
const BOOT_TIMEOUT_MS = 180_000
const INSTANCE_TIMEOUT_MS = 60_000

/** Один экземпляр backend на всё приложение. */
const BACKEND_NAME = 'backend'

const NO_STORE = { 'cache-control': 'no-store' }

/** Ошибка в формате backend ({error, message}) — фронтенд показывает поле message. */
function apiError(status: number, error: string, message: string): Response {
  return Response.json({ error, message }, { status, headers: NO_STORE })
}

export class Backend extends Container<Env> {
  defaultPort = 8080
  sleepAfter = '15m' // без запросов 15 минут — контейнер засыпает, оплата памяти и диска прекращается
  enableInternet = true // Neon, R2 и OpenRouter снаружи

  constructor(ctx: ConstructorParameters<typeof Container>[0], env: Env) {
    super(ctx, env)
    const source = env as unknown as Record<string, unknown>
    const vars: Record<string, string> = {}
    for (const key of BACKEND_ENV) {
      const value = source[key]
      if (typeof value === 'string' && value !== '') vars[key] = value
    }
    this.envVars = vars
  }

  override async fetch(request: Request): Promise<Response> {
    const { status } = await this.getState()
    if (status !== 'healthy' || !this.ctx.container?.running) {
      try {
        await this.startAndWaitForPorts({
          ports: this.defaultPort,
          cancellationOptions: {
            abort: request.signal,
            instanceGetTimeoutMS: INSTANCE_TIMEOUT_MS,
            portReadyTimeoutMS: BOOT_TIMEOUT_MS,
            waitInterval: 1_000,
          },
        })
      } catch (e) {
        console.error('backend did not become ready', e)
        return apiError(503, 'BACKEND_STARTING',
          'Сервер запускается или не смог запуститься. Повторите через минуту.')
      }
    }
    return super.fetch(request)
  }

  /** Запускает контейнер, не дожидаясь Spring: пока пользователь вводит пароль, backend успевает подняться. */
  async wake(): Promise<string> {
    const { status } = await this.getState()
    if (this.ctx.container?.running && (status === 'healthy' || status === 'running')) return status
    await this.start()
    return 'starting'
  }

  override onStart(): void {
    console.log('backend container started')
  }

  override onStop(params: StopParams): void {
    console.log('backend container stopped', JSON.stringify(params))
  }
}

export default {
  async fetch(request, env): Promise<Response> {
    const { pathname } = new URL(request.url)

    if (pathname === '/api/_wake') {
      if (request.method !== 'POST') return apiError(405, 'METHOD_NOT_ALLOWED', 'Только POST')
      try {
        const status = await getContainer(env.BACKEND, BACKEND_NAME).wake()
        return Response.json({ status }, { headers: NO_STORE })
      } catch (e) {
        console.error('wake failed', e)
        return apiError(503, 'BACKEND_STARTING', 'Сервер ещё не готов. Повторите через минуту.')
      }
    }

    if (pathname.startsWith('/api/')) {
      try {
        return await getContainer(env.BACKEND, BACKEND_NAME).fetch(request)
      } catch (e) {
        // Например, Durable Object перезапустился посреди запроса во время деплоя.
        console.error('backend proxy failed', e)
        return apiError(503, 'BACKEND_UNAVAILABLE', 'Сервер недоступен. Повторите через минуту.')
      }
    }

    // Сюда попадаем, только если запрос не совпал со статикой и не /api/* — отдаём SPA.
    return env.ASSETS.fetch(request)
  },
} satisfies ExportedHandler<Env>
