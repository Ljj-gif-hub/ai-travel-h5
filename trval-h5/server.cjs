const http = require('http')
const fs = require('fs')
const path = require('path')

const DIST = path.join(__dirname, 'dist')
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'application/javascript',
  '.css': 'text/css',
  '.json': 'application/json',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.jpg': 'image/jpeg',
  '.webmanifest': 'application/manifest+json',
}

http.createServer((req, res) => {
  let filePath = path.join(DIST, req.url.split('?')[0])

  // SPA fallback: non-file routes → index.html
  if (!path.extname(filePath)) {
    filePath = path.join(DIST, 'index.html')
  }

  const ext = path.extname(filePath)
  res.setHeader('Content-Type', MIME[ext] || 'application/octet-stream')
  res.setHeader('Access-Control-Allow-Origin', '*')

  fs.createReadStream(filePath)
    .on('error', () => {
      // Ultimate fallback: serve index.html for SPA routes
      res.setHeader('Content-Type', 'text/html; charset=utf-8')
      fs.createReadStream(path.join(DIST, 'index.html'))
        .on('error', () => { res.statusCode = 404; res.end('Not Found') })
        .pipe(res)
    })
    .pipe(res)
}).listen(5173, () => console.log('http://localhost:5173'))
