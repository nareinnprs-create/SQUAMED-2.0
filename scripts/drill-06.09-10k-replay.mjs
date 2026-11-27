// 06.09 store-and-forward drill: queue 10,000 writes during degraded mode, replay them, and prove
// no loss and no duplication. Honest reporting: measured numbers only; exit 1 on any loss/duplication.
// Usage: node scripts/drill-06.09-10k-replay.mjs  (env: PORT=8080, WRITES=10000, CONN=25)
import http from 'node:http'
import { performance } from 'node:perf_hooks'

const PORT = process.env.PORT || '8080'
const BASE = `http://127.0.0.1:${PORT}`
const WRITES = Number(process.env.WRITES || 10000)
const CONN = Number(process.env.CONN || 25)
const agent = new http.Agent({ keepAlive: true, maxSockets: CONN })

function req(method, path, body, token) {
  return new Promise((resolve, reject) => {
    const data = body === undefined ? null : JSON.stringify(body)
    const r = http.request(
      `${BASE}${path}`,
      {
        method,
        agent,
        headers: {
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
          ...(data ? { 'Content-Type': 'application/json', 'Content-Length': Buffer.byteLength(data) } : {}),
        },
      },
      (res) => {
        const chunks = []
        res.on('data', (c) => chunks.push(c))
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString()
          let json = null
          try { json = JSON.parse(text) } catch { /* non-JSON */ }
          resolve({ status: res.statusCode, json, text })
        })
      },
    )
    r.on('error', reject)
    if (data) r.write(data)
    r.end()
  })
}

async function login() {
  const res = await req('POST', '/api/v1/auth/login', { username: 'admin@squamed.local', password: 'Admin@12345' })
  const token = res.json?.data?.accessToken
  if (!token) {
    console.error('FAIL: admin login', res.status)
    process.exit(1)
  }
  return token
}
let admin = await login()

const suffix = Date.now().toString(36)
const activate = await req('POST', '/api/v1/resilience/degraded/activate', { module: 'emar', reason: `drill ${suffix}` }, admin)
if (activate.status >= 300) {
  console.error('FAIL: could not activate degraded mode', activate.status, activate.text?.slice(0, 200))
  process.exit(1)
}

const started = performance.now()
let queued = 0
let failed = 0
const keys = []
let next = 0
async function worker() {
  while (next < WRITES) {
    const i = next++
    const key = `drill-${suffix}-${i}`
    keys.push(key)
    const res = await req(
      'POST',
      '/api/v1/resilience/degraded/queue',
      {
        module: 'emar',
        operation: 'administer',
        idempotencyKey: key,
        payload: { drill: suffix, seq: i },
        appendOnly: true,
        occurredAt: new Date().toISOString(),
      },
      admin,
    )
    if (res.status < 300) queued++
    else failed++
  }
}
await Promise.all(Array.from({ length: CONN }, worker))
const queueMs = performance.now() - started
console.log(`queued ${queued}/${WRITES} writes in ${Math.round(queueMs)} ms, failed=${failed}`)
if (failed > 0) {
  console.error('FAIL: queueing lost writes')
  process.exit(1)
}

const replayStart = performance.now()
const replay = await req('POST', '/api/v1/resilience/degraded/replay', undefined, admin)
const replayMs = performance.now() - replayStart
if (replay.status >= 300) {
  console.error('FAIL: replay', replay.status, replay.text?.slice(0, 300))
  process.exit(1)
}
const replayed = replay.json?.data?.replayed ?? 0
const replayFailed = replay.json?.data?.failed ?? 0
console.log(`replay: replayed=${replayed} failed=${replayFailed} in ${Math.round(replayMs)} ms`)

// The replay of 10k writes outlives the access token, so refresh before the idempotency check.
admin = await login()
const second = await req('POST', '/api/v1/resilience/degraded/replay', undefined, admin)
const secondReplayed = second.json?.data?.replayed ?? -1
console.log(`second replay: replayed=${secondReplayed} (must be 0 - idempotent)`)

const pending = await req('GET', '/api/v1/resilience/degraded/queue?status=PENDING', undefined, admin)
const pendingCount = Array.isArray(pending.json?.data) ? pending.json.data.length : -1
console.log(`pending after replay: ${pendingCount}`)

const release = await req('POST', '/api/v1/resilience/degraded/release', { module: 'emar' }, admin)
if (release.status >= 300) console.error('WARN: could not release degraded mode', release.status)

let ok = true
if (replayed !== WRITES) { console.error(`FAIL: replayed ${replayed} of ${WRITES}`); ok = false }
if (replayFailed !== 0) { console.error(`FAIL: ${replayFailed} replays failed`); ok = false }
if (secondReplayed !== 0) { console.error(`FAIL: second replay replayed ${secondReplayed} - duplication`); ok = false }
if (pendingCount !== 0) { console.error(`FAIL: ${pendingCount} writes still pending`); ok = false }
console.log(ok ? 'PASS: 10k replay drill - no loss, no duplication' : 'FAIL: see above')
process.exit(ok ? 0 : 1)
