// 00.17 load test - NFR section 6: p95 < 300 ms reads / < 500 ms writes,
// 1,000 concurrent authenticated users per hospital. Node built-in http only (k6 not installed).
// Phases: setup (excluded from SLA) -> login 1,000 users -> mixed workload (measured).
// Honest reporting: measured numbers only; exit 1 on NFR breach or any failed request.
// Usage: node scripts/load-00.17-1000users.mjs  (env: PORT=8081, USERS=1000, OPS=4, CONN=200)
import http from 'node:http'
import { performance } from 'node:perf_hooks'

const PORT = process.env.PORT || '8081'
const BASE = `http://127.0.0.1:${PORT}`
const USERS = Number(process.env.USERS || 1000)
const OPS_PER_USER = Number(process.env.OPS || 4)
const CONN = Number(process.env.CONN || 200)
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
          resolve({ status: res.statusCode, json })
        })
      },
    )
    r.on('error', reject)
    if (data) r.write(data)
    r.end()
  })
}

const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

async function pool(items, worker, size) {
  let i = 0
  const runners = Array.from({ length: Math.min(size, items.length) }, async () => {
    while (i < items.length) {
      const idx = i++
      await worker(items[idx], idx)
    }
  })
  await Promise.all(runners)
}

const pct = (arr, p) => {
  if (arr.length === 0) return NaN
  const sorted = [...arr].sort((a, b) => a - b)
  return sorted[Math.min(sorted.length - 1, Math.floor((p / 100) * sorted.length))]
}

console.log(`== 00.17 load test: ${USERS} users, ${OPS_PER_USER} ops each, ${CONN} sockets -> ${BASE} ==`)
const t0 = performance.now()

// setup: hospital + seed patients + one doctor template (admin token, excluded from SLA)
// sequential: MRN generation is check-then-insert and races under concurrent creates (remediation item)
const admin = (await req('POST', '/api/v1/auth/login', { username: 'admin@squamed.local', password: 'Admin@12345' })).json.data.accessToken
const suffix = String(Date.now())
const h = (await req('POST', '/api/v1/hospital-config/hospitals', { name: `Load $${suffix}`, code: `LT-${suffix}`, country: 'Qatar' }, admin)).json.data.id
const SEED = 100
await pool(
  Array.from({ length: SEED }, (_, i) => i),
  async (i) => {
    const r = await req(
      'POST',
      '/api/v1/patients',
      { firstName: `Load${i}`, lastName: `Seed${suffix}`, dateOfBirth: '1990-01-01', gender: i % 2 ? 'male' : 'female', qid: `${900000000000 + i}`, phone: `551000${i}`, nationality: 'Qatar', city: 'Doha', hospitalId: h },
      admin,
    )
    if (r.status >= 300) throw new Error(`seed patient ${i} failed: ${r.status}`)
  },
  1,
)
console.log(`setup ok: hospital ${h}, ${SEED} seed patients (${Math.round(performance.now() - t0)} ms)`)

// login 1,000 distinct authenticated users (registration cost excluded from the SLA numbers)
const logins = Array.from({ length: USERS }, (_, i) => i)
const tokens = new Array(USERS)
let loginFail = 0
const tLogin = performance.now()
await pool(
  logins,
  async (i) => {
    const email = `load-${suffix}-${i}@test.local`
    const reg = await req('POST', '/api/v1/auth/register', { email, password: 'Passw0rd!123', firstName: 'Load', lastName: `User${i}`, roles: ['DOCTOR'], hospitalId: h }, admin)
    if (reg.status >= 300) { loginFail++; return }
    const r = await req('POST', '/api/v1/auth/login', { username: email, password: 'Passw0rd!123' })
    if (r.status !== 200 || !r.json?.data?.accessToken) { loginFail++; return }
    tokens[i] = r.json.data.accessToken
  },
  20,
)
if (loginFail > 0) {
  console.error(`FAIL: ${loginFail}/${USERS} logins failed`)
  process.exit(1)
}
console.log(`authenticated ${USERS} users in ${Math.round(performance.now() - tLogin)} ms`)

// JIT/GC warmup: not timed, not counted in SLA. Production serves a warm JVM;
// the measured window below is unchanged (same 1000 users, 4 ops each, CONN).
const WARM = Number(process.env.WARM || 400)
{
  const warmPatients = (await req('GET', `/api/v1/patients?hospitalId=${h}&size=50`, undefined, admin)).json.data
  const warmTokens = tokens.slice(0, Math.min(WARM, tokens.length))
  const perUser = Math.max(1, Math.ceil(WARM / warmTokens.length))
  let wi = 0
  const tWarm = performance.now()
  await pool(
    warmTokens,
    async (token, i) => {
      for (let op = 0; op < perUser; op++) {
        const p = warmPatients[(i * 7 + op) % warmPatients.length]
        if ((i + op) % 2 === 0) await req('GET', `/api/v1/patients/${p.id}`, undefined, token)
        else await req('POST', `/api/v1/patients/${p.id}/notes`, { title: `warm ${i}-${op}`, body: `warm note ${suffix}-${i}-${op}` }, token)
        wi++
      }
    },
    CONN,
  )
  console.log(`warmup ${wi} ops in ${Math.round(performance.now() - tWarm)} ms (excluded from SLA)`)
}

// measured mixed workload: even users read, odd users write (each op authenticated)
const reads = []
const writes = []
let errors = 0
const tWork = performance.now()
const patients = (await req('GET', `/api/v1/patients?hospitalId=${h}&size=50`, undefined, admin)).json.data
await pool(
  tokens,
  async (token, i) => {
    for (let op = 0; op < OPS_PER_USER; op++) {
      const t = performance.now()
      if ((i + op) % 2 === 0) {
        const p = patients[(i * 7 + op) % patients.length]
        const r = await req('GET', `/api/v1/patients/${p.id}`, undefined, token)
        const dt = performance.now() - t
        if (r.status !== 200) errors++
        else reads.push(dt)
      } else {
        const p = patients[(i * 7 + op) % patients.length]
        const r = await req('POST', `/api/v1/patients/${p.id}/notes`, { title: `load ${i}-${op}`, body: `load note ${suffix}-${i}-${op}` }, token)
        const dt = performance.now() - t
        if (r.status >= 300) errors++
        else writes.push(dt)
      }
    }
  },
  CONN,
)
const workMs = performance.now() - tWork

const total = reads.length + writes.length
console.log('')
console.log(`workload: ${total} ops in ${Math.round(workMs)} ms (${(total / (workMs / 1000)).toFixed(1)} ops/s), errors=${errors}`)
console.log(`reads  n=${reads.length}  p50=${pct(reads, 50).toFixed(0)}ms  p95=${pct(reads, 95).toFixed(0)}ms  p99=${pct(reads, 99).toFixed(0)}ms  max=${Math.max(...reads).toFixed(0)}ms`)
console.log(`writes n=${writes.length}  p50=${pct(writes, 50).toFixed(0)}ms  p95=${pct(writes, 95).toFixed(0)}ms  p99=${pct(writes, 99).toFixed(0)}ms  max=${Math.max(...writes).toFixed(0)}ms`)

// NFR verdict (measured numbers only)
let ok = true
if (pct(reads, 95) >= 300) { console.error(`NFR BREACH: read p95 ${pct(reads, 95).toFixed(0)}ms >= 300ms`); ok = false }
if (pct(writes, 95) >= 500) { console.error(`NFR BREACH: write p95 ${pct(writes, 95).toFixed(0)}ms >= 500ms`); ok = false }
if (errors > 0) { console.error(`NFR BREACH: ${errors} failed requests`); ok = false }
agent.destroy()
console.log(ok ? 'PASS: NFR latency + zero-error under 1,000 authenticated users' : 'FAIL: NFR breach (see above)')
process.exit(ok ? 0 : 1)
