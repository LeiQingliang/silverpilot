import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const backendTarget = env.VITE_BACKEND_TARGET || 'http://127.0.0.1:8083'
  const createBackendProxy = () => ({
    '/api': {
      target: backendTarget,
      changeOrigin: true,
      rewrite: (path) => path.replace(/^\/api/, '')
    },
    '/image': {
      target: backendTarget,
      changeOrigin: true
    },
    '/file': {
      target: backendTarget,
      changeOrigin: true
    },
    '/video': {
      target: backendTarget,
      changeOrigin: true
    }
  })

  return {
    plugins: [
      vue(),
      Components({
        dts: false,
        dirs: [],
        // Component CSS is loaded once in main.js before the design system.
        // This prevents route-level style flashes and guarantees token overrides win.
        resolvers: [ElementPlusResolver({ importStyle: false, directives: true })]
      })
    ],
    server: {
      port: 8081,
      strictPort: true,
      host: '127.0.0.1',
      open: process.env.SILVERPILOT_SKIP_BROWSER === '1' ? false : '/login',
      headers: { 'X-SilverPilot-Dev-Server': 'cecsmsui-v1' },
      proxy: createBackendProxy()
    },
    preview: {
      port: 8081,
      strictPort: true,
      host: '127.0.0.1',
      proxy: createBackendProxy()
    },
    build: {
      chunkSizeWarningLimit: 600
    },
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    }
  }
})
