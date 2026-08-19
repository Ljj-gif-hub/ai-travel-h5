/* 本地验证服务器：静态托管 dist，/api 与 /uploads 代理到线上 */
const http = require('http')
const fs = require('fs')
const path = require('path')

const DIST = path.join(__dirname, '..', 'dist')
const UPSTREAM = 'http://8.148.223.54'
const PORT = 5399

const MIME = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.svg': 'image/svg+xml', '.png': 'image/png', '.jpg': 'image/jpeg', '.webp': 'image/webp', '.ico': 'image/x-icon', '.webmanifest': 'application/manifest+json', '.woff2': 'font/woff2' }

async function proxy(req, res) {
  const opts = { method: req.method, headers: { ...req.headers, host: UPSTREAM.replace('http://', '') } }
  const up = http.request(UPSTREAM + req.url, opts, (ur) => {
    res.writeHead(ur.statusCode, ur.headers)
    ur.pipe(res)
  })
  up.on('error', () => { res.writeHead(502); res.end('proxy error') })
  req.pipe(up)
}

const server = http.createServer((req, res) => {
  if (req.url.startsWith('/api/') || req.url.startsWith('/uploads/')) return proxy(req, res)
  let p = decodeURIComponent(req.url.split('?')[0])
  if (p === '/') p = '/index.html'
  const file = path.join(DIST, p)
  if (!file.startsWith(DIST)) { res.writeHead(403); return res.end() }
  fs.stat(file, (err, st) => {
    const target = !err && st.isFile() ? file : path.join(DIST, 'index.html')
    res.writeHead(200, { 'Content-Type': MIME[path.extname(target)] || 'application/octet-stream', 'Cache-Control': 'no-cache' })
    fs.createReadStream(target).pipe(res)
  })
})
server.listen(PORT, () => console.log('verify server on http://127.0.0.1:' + PORT))
