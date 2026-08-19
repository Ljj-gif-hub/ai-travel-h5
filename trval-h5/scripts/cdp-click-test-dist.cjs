/* CDP 真实点击测试：agent-planner 出发地点击是否跳转 /city-select */
const EDGE = process.env.EDGE_PATH
const PORT = 9222
const sleep = (ms) => new Promise((r) => setTimeout(r, ms))

function b64url(obj) {
  return Buffer.from(JSON.stringify(obj)).toString('base64url')
}

async function main() {
  // 1. 启动 Edge headless
  const { spawn } = require('child_process')
  const profile = `${process.env.TEMP}\\cdp-profile-${Date.now()}`
  const edge = spawn(EDGE, [
    '--headless=new', '--disable-gpu', '--no-first-run', '--no-proxy-server',
    `--remote-debugging-port=${PORT}`,
    `--user-data-dir=${profile}`,
    '--window-size=414,896',
    'about:blank',
  ], { stdio: 'ignore' })

  // 2. 等 CDP 就绪
  let targets = null
  for (let i = 0; i < 30; i++) {
    await sleep(500)
    try {
      const res = await fetch(`http://127.0.0.1:${PORT}/json`)
      targets = await res.json()
      if (targets.length) break
    } catch {}
  }
  if (!targets) throw new Error('CDP not ready')
  const page = targets.find((t) => t.type === 'page')
  console.log('CDP target:', page.url)

  const ws = new WebSocket(page.webSocketDebuggerUrl)
  await new Promise((r, j) => { ws.onopen = r; ws.onerror = j })

  let msgId = 0
  const pending = new Map()
  ws.onmessage = (ev) => {
    const m = JSON.parse(ev.data)
    if (m.id && pending.has(m.id)) {
      pending.get(m.id)(m)
      pending.delete(m.id)
      return
    }
    if (m.method === 'Runtime.exceptionThrown') {
      const d = m.params.exceptionDetails
      console.log('[page-exception]', (d.exception?.description || d.text || '').slice(0, 300))
    }
    if (m.method === 'Log.entryAdded') {
      const e = m.params.entry
      if (e.level === 'error') console.log('[console.error]', (e.text || '').slice(0, 300))
    }
  }
  const send = (method, params = {}) => new Promise((resolve) => {
    const id = ++msgId
    pending.set(id, resolve)
    ws.send(JSON.stringify({ id, method, params }))
  })
  const evalJs = async (expr, awaitPromise = false) => {
    const r = await send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise })
    if (r.result?.exceptionDetails) return { error: r.result.exceptionDetails.exception?.description || r.result.exceptionDetails.text }
    return r.result?.result?.value
  }
  const waitFor = async (expr, timeoutMs = 20000) => {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      const v = await evalJs(expr)
      if (v) return true
      await sleep(500)
    }
    return false
  }

  await send('Page.enable')
  await send('Runtime.enable')
  await send('Log.enable')

  // 3. 先打开登录页（同源），注入假 token（未过期）
  await send('Page.navigate', { url: 'http://127.0.0.1:5299/#/login' })
  await sleep(3000)
  console.log('step1 href:', await evalJs('location.href'))
  await waitFor(`location.origin === 'http://127.0.0.1:5299' && document.readyState === 'complete'`, 15000)
  await sleep(2000)
  const exp = Math.floor(Date.now() / 1000) + 86400
  const fakeJwt = `${b64url({ alg: 'HS256', typ: 'JWT' })}.${b64url({ sub: 'test', exp })}.fakesig`
  await evalJs(`sessionStorage.setItem('TOKEN', ${JSON.stringify(fakeJwt)}); sessionStorage.setItem('REFRESH_TOKEN', 'fake-refresh'); 'ok'`)
  console.log('token injected')

  // 4. 打开 agent-planner
  await send('Page.navigate', { url: 'http://127.0.0.1:5299/#/agent-planner' })
  await sleep(3000)
  console.log('step2 href:', await evalJs('location.href'))
  await waitFor(`!!document.querySelector('.planner-page')`, 15000)
  console.log('当前 hash:', await evalJs('location.hash'))
  console.log('planner-page:', await evalJs(`!!document.querySelector('.planner-page')`))
  console.log('form-row count:', await evalJs(`document.querySelectorAll('.form-row').length`))
  console.log('origin row:', await evalJs(`!!document.querySelector('.form-row.clickable')`))

  // 5. 点击第一个 form-row（出发地）
  const clickResult = await evalJs(`(() => {
    const row = document.querySelector('.form-row.clickable')
    if (!row) return 'row-not-found'
    row.click()
    return 'clicked'
  })()`)
  console.log('click:', clickResult)

  await sleep(2500)
  console.log('hash after click:', await evalJs('location.hash'))
  console.log('city-select-page:', await evalJs(`!!document.querySelector('.city-select-page')`))

  ws.close()
  edge.kill()
}

main().catch((e) => { console.error('FATAL:', e.message); process.exit(1) })

