import basicSsl from '@vitejs/plugin-basic-ssl'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// Con PERA_DEV_HTTPS=1 el servidor de desarrollo sirve HTTPS con un certificado propio. El móvil
// solo deja usar la cámara en directo (escáner del almacén y del TPV) desde una página segura;
// sin HTTPS, la aplicación ofrece hacer una foto del código, que funciona igualmente.
const devHttps = process.env.PERA_DEV_HTTPS === '1'

export default defineConfig({
  plugins: [react(), ...(devHttps ? [basicSsl()] : [])],
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  preview: {
    host: '0.0.0.0',
    port: 4173,
  },
  test: {
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    css: true,
  },
})
