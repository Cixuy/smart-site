/**
 * 高德地图 JS API 2.0 异步加载工具
 * 
 * 安全密钥已在 index.html 中通过 window._AMapSecurityConfig 声明，
 * 会在脚本加载前被高德读取。
 */

const AMAP_KEY = '7725a92dec57b32466feab3b1c5e8b52'
const AMAP_VERSION = '2.0'

// 默认需要加载的插件
const DEFAULT_PLUGINS = [
  'AMap.Geocoder',
  'AMap.Geolocation',
  'AMap.PlaceSearch',
  'AMap.AutoComplete'
]

let loadingPromise = null

/**
 * 加载高德地图 JS API，返回 AMap 全局对象
 * @param {string[]} [plugins] - 附加插件列表
 * @returns {Promise<object>} AMap
 */
export function loadAMap(plugins = []) {
  // 已加载完成
  if (window.AMap) return Promise.resolve(window.AMap)

  // 正在加载中，复用同一个 Promise
  if (loadingPromise) return loadingPromise

  loadingPromise = new Promise((resolve, reject) => {
    const allPlugins = [...new Set([...DEFAULT_PLUGINS, ...plugins])]
    const pluginParam = allPlugins.join(',')

    const script = document.createElement('script')
    script.type = 'text/javascript'
    script.src = `https://webapi.amap.com/maps?v=${AMAP_VERSION}&key=${AMAP_KEY}${pluginParam ? '&plugin=' + encodeURIComponent(pluginParam) : ''}`
    script.async = true
    script.onerror = () => {
      loadingPromise = null
      reject(new Error('高德地图 JS API 加载失败'))
    }

    // 轮询等待 AMap 就绪
    script.onload = () => {
      let attempts = 0
      const check = () => {
        attempts++
        if (window.AMap && window.AMap.Geocoder) {
          resolve(window.AMap)
        } else if (attempts > 80) {
          loadingPromise = null
          reject(new Error('AMap 初始化超时'))
        } else {
          setTimeout(check, 100)
        }
      }
      check()
    }

    document.head.appendChild(script)
  })

  return loadingPromise
}

/**
 * 判断 AMap 是否已加载
 */
export function isAMapReady() {
  return !!(window.AMap && window.AMap.Geocoder)
}
