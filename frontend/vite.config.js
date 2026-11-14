import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'path'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // 读取 .env 环境变量（VITE_AGENT_Address 等）
  const env = loadEnv(mode, process.cwd(), '')

  // 对话智能体服务地址（独立 FastAPI 服务，默认 dev 端口 8000）
  // 开发环境由 Vite 代理将 /api/agent 转发到该地址；生产环境建议用反向代理并按需覆盖。
  // 注意：必须用 127.0.0.1 而非 localhost —— 智能体服务仅监听 IPv4(127.0.0.1)，
  //       而 localhost 会优先解析为 IPv6(::1)，导致 Node 代理连接失败返回 500。
  const agentTarget = env.VITE_AGENT_Address || 'http://127.0.0.1:8000'

  return {
    plugins: [vue()],
    // 将 3D 模型文件作为静态资源处理（import 时返回可访问 URL）
    assetsInclude: ['**/*.glb', '**/*.gltf', '**/*.obj', '**/*.fbx', '**/*.bin'],
    resolve: {
      alias: {
        '@': resolve(__dirname, 'src'),
        // 添加这一行，让Vue使用包含编译器的版本
        'vue': 'vue/dist/vue.esm-bundler.js'
      }
    },
    build: {
      sourcemap: false, // 禁用生产环境的sourcemap生成
    },
    server: {
      fs: {
        // 允许访问项目根目录外的文件
        allow: ['..']
      },
      proxy: {
        // 智能体接口（/api/agent/**）转发到独立对话智能体服务，避免跨域并复用同源相对路径
        '/api/agent': {
          target: agentTarget,
          changeOrigin: true
        }
      }
    },
    optimizeDeps: {
      // 排除flv.min.js从依赖优化，避免sourcemap错误
      exclude: ['flvjs']
    }
  }
})
