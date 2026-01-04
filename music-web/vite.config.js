import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

// https://vite.dev/config/
const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

function contentTypeByExt(ext) {
  if (ext === '.mp3') return 'audio/mpeg'
  if (ext === '.lrc') return 'text/plain; charset=utf-8'
  if (ext === '.jpg' || ext === '.jpeg') return 'image/jpeg'
  if (ext === '.png') return 'image/png'
  return 'application/octet-stream'
}

function dataFolderPlugin() {
  const dataDir = path.resolve(__dirname, '..', 'data')

  return {
    name: 'data-folder-plugin',
    configureServer(server) {
      server.middlewares.use((req, res, next) => {
        const url = req.url || ''
        if (!url.startsWith('/data/')) return next()

        const pathname = url.split('?')[0]
        const rel = decodeURIComponent(pathname.replace(/^\/data\//, ''))
        const abs = path.resolve(dataDir, rel)
        if (!abs.startsWith(dataDir)) {
          res.statusCode = 403
          res.end('Forbidden')
          return
        }

        if (req.method === 'PUT') {
          try {
            fs.mkdirSync(path.dirname(abs), { recursive: true })
          } catch {
            res.statusCode = 500
            res.end('Create folder failed')
            return
          }

          const stream = fs.createWriteStream(abs)
          stream.on('error', () => {
            res.statusCode = 500
            res.end('Write failed')
          })
          stream.on('finish', () => {
            res.statusCode = 201
            res.end('OK')
          })
          req.pipe(stream)
          return
        }

        if (req.method !== 'GET' && req.method !== 'HEAD') return next()
        if (!fs.existsSync(abs) || !fs.statSync(abs).isFile()) {
          res.statusCode = 404
          res.end('Not Found')
          return
        }

        const ext = path.extname(abs).toLowerCase()
        const stat = fs.statSync(abs)
        const size = stat.size

        res.setHeader('Content-Type', contentTypeByExt(ext))
        res.setHeader('Accept-Ranges', 'bytes')

        const rangeHeader = req.headers.range
        if (typeof rangeHeader === 'string' && rangeHeader.startsWith('bytes=')) {
          const match = /^bytes=(\d*)-(\d*)$/.exec(rangeHeader.trim())
          if (match) {
            const startText = match[1]
            const endText = match[2]

            let start = startText ? Number.parseInt(startText, 10) : 0
            let end = endText ? Number.parseInt(endText, 10) : size - 1

            if (!startText && endText) {
              const suffixLen = Number.parseInt(endText, 10)
              if (Number.isFinite(suffixLen) && suffixLen > 0) {
                start = Math.max(0, size - suffixLen)
                end = size - 1
              }
            }

            if (
              !Number.isFinite(start) ||
              !Number.isFinite(end) ||
              start < 0 ||
              end < start ||
              start >= size
            ) {
              res.statusCode = 416
              res.setHeader('Content-Range', `bytes */${size}`)
              res.end()
              return
            }

            end = Math.min(end, size - 1)

            res.statusCode = 206
            res.setHeader('Content-Range', `bytes ${start}-${end}/${size}`)
            res.setHeader('Content-Length', String(end - start + 1))

            if (req.method === 'HEAD') {
              res.end()
              return
            }

            fs.createReadStream(abs, { start, end }).pipe(res)
            return
          }
        }

        res.statusCode = 200
        res.setHeader('Content-Length', String(size))
        if (req.method === 'HEAD') {
          res.end()
          return
        }
        fs.createReadStream(abs).pipe(res)
      })
    },
  }
}

export default defineConfig({
  plugins: [vue(), dataFolderPlugin()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
})
