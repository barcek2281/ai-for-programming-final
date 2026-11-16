// Workers Builds: секреты Worker заданы как Build variables — во время сборки это обычные
// переменные окружения. Собираем из них .secrets.json, который `wrangler deploy --secrets-file`
// загружает как секреты нового Worker-версии (JSON, а не .env: в .env wrangler раскрывает $VAR).
//
// Берём только имена из secrets.required в wrangler.jsonc. Пустые и незаданные пропускаем,
// поэтому секреты, уже заданные в настройках Worker, этот скрипт не затирает.
import { readFileSync, writeFileSync } from 'node:fs'

const config = readFileSync(new URL('../wrangler.jsonc', import.meta.url), 'utf8')
const block = config.match(/"required"\s*:\s*\[([^\]]*)\]/)
if (!block) throw new Error('secrets.required not found in wrangler.jsonc')
const names = [...block[1].matchAll(/"([A-Z0-9_]+)"/g)].map((m) => m[1])

const secrets = {}
for (const name of names) {
  const value = process.env[name]
  if (value) secrets[name] = value
}

writeFileSync(new URL('../.secrets.json', import.meta.url), JSON.stringify(secrets))

const missing = names.filter((n) => !(n in secrets))
console.log(`secrets from build variables: ${Object.keys(secrets).length}/${names.length}`)
if (missing.length) console.log(`not in build variables (must already be set on the Worker): ${missing.join(', ')}`)
