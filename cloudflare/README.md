# VivaMind на Cloudflare (Workers Paid + Containers)

Адрес: <https://ai-for-programming-final.sabdpp17.workers.dev>

```
 /, /login, /t/..., /s/...  → Workers Static Assets (frontend/dist), Worker не вызывается
 /api/*                     → Worker → Durable Object "Backend" → контейнер Spring Boot (:8080)
                                                                    ├─ Neon: Postgres + pgvector
                                                                    ├─ R2: файлы курса (S3 API)
                                                                    └─ OpenRouter
```

- Backend — тот же `backend/Dockerfile`, без правок кода: R2 и Neon подключаются переменными окружения.
- Контейнер один (`max_instances: 1`) и засыпает через 15 минут без запросов. Первый запрос после сна
  ждёт запуска Spring (около 40 с; Worker ждёт до 3 минут). Фронтенд при открытии страницы шлёт
  `POST /api/_wake`, чтобы backend поднимался, пока человек вводит пароль.
- Postgres в контейнере держать нельзя: диск контейнера стирается при каждом засыпании.
- Деплой — из GitHub через Workers Builds: каждый push в `master` собирает фронтенд и образ backend и выкатывает их.

## 1. Neon и R2 (один раз)

1. **Neon** (<https://neon.com>, бесплатный план): создайте проект, регион **AWS Europe Central (Frankfurt)**.
   В Console → Connect выберите Java и возьмите хост **без** `-pooler`, пользователя и пароль.
   Расширение `vector` создаст миграция Flyway, вручную ничего не нужно.
   Совет: ограничьте автомасштабирование compute до 0.25–0.5 CU — тогда 100 CU-часов бесплатного плана
   хватит на 200–400 часов работы.
2. **R2**: дашборд Cloudflare → R2 → Create bucket → имя `vivamind`.
   Затем R2 → Manage API tokens → Create API token, права **Object Read & Write** на бакет `vivamind`.
   Сохраните Access Key ID, Secret Access Key и адрес S3 (`https://<ACCOUNT_ID>.r2.cloudflarestorage.com`).

## 2. Секреты — как Build variables

Пока Worker состоит только из статики, дашборд не даёт добавить ему рабочие секреты
(«Variables cannot be added to a Worker that only has static assets»). Поэтому секреты задаются как
**Build variables**: Worker → Settings → Build → Variables and secrets. Во время сборки это переменные
окружения, и `npm run deploy:ci` (скрипт `scripts/secrets-from-env.mjs`) загружает их в секреты Worker
вместе с деплоем. Пустые переменные скрипт пропускает, поэтому уже заданные секреты Worker не затираются.

Деплой **упадёт** с ошибкой «required secrets have not been set», если какого-то имени из
`secrets.required` в `wrangler.jsonc` нет ни в Build variables, ни в секретах Worker. Так задумано:
backend без базы не выкатится. Нужны все десять:

| Имя | Что |
| --- | --- |
| `OPENROUTER_API_KEY` | ключ OpenRouter |
| `DB_URL` | `jdbc:postgresql://<хост Neon без -pooler>/<база>?sslmode=require` |
| `DB_USER`, `DB_PASSWORD` | пользователь и пароль Neon |
| `S3_ENDPOINT` | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com` |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | ключи R2-токена |
| `JWT_SECRET` | длинная случайная строка (например, `openssl rand -base64 48`) |
| `SEED_ADMIN_PASSWORD` | пароль администратора `admin@vivamind.local` |
| `SEED_DEMO_PASSWORD` | пароль демо-аккаунтов `teacher@` / `student@vivamind.local` |

Когда у Worker уже есть код, секреты можно задавать и напрямую: Settings → **Variables and Secrets**,
тип **Secret**. Или из терминала: заполните `.env.production` по образцу `.env.production.example`, затем

```bash
npx wrangler login
```

```bash
npm run secrets
```

Тип **Text** не подходит: такие переменные затираются блоком `vars` из `wrangler.jsonc` при каждом деплое.
Несекретные настройки (модели, бакет, флаги JVM) меняйте в `vars` в `wrangler.jsonc`.

## 3. Настройки сборки (один раз)

Дашборд → Worker → Settings → **Build**:

| Поле | Значение |
| --- | --- |
| Git branch | `master` |
| Root directory | `cloudflare` |
| Build command | пусто — фронтенд собирает сам `wrangler deploy` (блок `build` в `wrangler.jsonc`) |
| Deploy command | `npm run deploy:ci` (переносит Build variables в секреты и деплоит) |

Сборки других веток (preview) контейнер не обновляют — образ выкатывает только `wrangler deploy` из `master`.

После этого `git push` в `master` запускает сборку. Первый деплой контейнера провижинится несколько минут:
в это время `/api` может отвечать 503 «There is no Container instance available».

## Если Workers Builds не может собрать образ

Если сборка падает с «Docker CLI is needed», деплойте с компьютера (нужен запущенный Docker Desktop):

```bash
npx wrangler login
```

```bash
npm install && npm run deploy
```

## Где смотреть

| Задача | Где |
| --- | --- |
| Лог сборки из GitHub | Worker → Deployments / Builds |
| Логи Worker в реальном времени | `npm run logs` |
| Логи Spring Boot, статус контейнера | Worker → Containers |

## Сколько это стоит

В $5/мес Workers Paid входит: 25 GiB-часов памяти, 375 vCPU-минут, 200 GB-часов диска контейнеров.
Память и диск считаются, **пока контейнер запущен** (по размеру инстанса), CPU — только за реальную работу.
Инстанс здесь: 0.5 vCPU, 2 GiB, 4 GB.

| Сценарий | Сверх $5 |
| --- | --- |
| До ~12 часов работы backend в месяц | $0 |
| ~40 часов в месяц (пара часов в учебный день) | ≈ $0.5 |
| Работает круглосуточно (никогда не засыпает) | ≈ $14 |

Час работы сверх включённого стоит ≈ $0.02 (память) плюс ≈ $0.001 (диск). Neon и R2 остаются на
бесплатных планах (R2 — 10 GB).

## На что обратить внимание

- **Размер инстанса.** В `wrangler.jsonc` задан кастомный `{ vcpu: 0.5, memory_mib: 2048, disk_mb: 4000 }`.
  Если деплой его не примет, поставьте `"instance_type": "basic"` (1/4 vCPU, 1 GiB). В простое backend
  занимает около 400 MiB, так что 1 GiB хватит, но старт будет дольше, а запас на разбор больших PDF меньше.
- **Демо-аккаунты.** `SEED_DEMO_USERS=true` создаёт `teacher@` и `student@vivamind.local` с паролем
  `SEED_DEMO_PASSWORD` — на публичном адресе их может открыть кто угодно. Для реальных занятий поставьте `false`.
- **Локальная разработка — как раньше**, через `docker compose` (см. корневой README). `wrangler dev`
  с контейнерами на Windows не работает, только из WSL.
