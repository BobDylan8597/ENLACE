import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// En desarrollo, Vite reenvía la API al servidor ENLACE. En la demo no hay Node:
// la web compilada (npm run build) la entrega el mismo servidor en su raíz.
const SERVIDOR = process.env.ENLACE_SERVIDOR ?? 'http://127.0.0.1:8000'
const RUTAS_API = [
  '/salud', '/ciudad', '/mapa', '/eventos', '/alertas', '/propuestas', '/celdas', '/reportes',
  '/checkins', '/sms', '/recomendaciones', '/asignaciones', '/equipos', '/bloqueos', '/rutas',
]

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      ...Object.fromEntries(RUTAS_API.map((ruta) => [ruta, SERVIDOR])),
      '/ws': { target: SERVIDOR, ws: true },
    },
  },
  // Las librerías del mapa pesan ~700 kB: se sirven en la red local, no por internet
  build: { chunkSizeWarningLimit: 1000 },
  test: { environment: 'jsdom' },
})
