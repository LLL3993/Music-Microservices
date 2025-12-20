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
        if (!fs.existsSync(abs) || !fs.statSync(abs).isFile()) {
          res.statusCode = 404
          res.end('Not Found')
          return
        }

        const ext = path.extname(abs).toLowerCase()
        res.setHeader('Content-Type', contentTypeByExt(ext))
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
