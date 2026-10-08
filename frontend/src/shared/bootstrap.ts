import { createApp, type Component } from 'vue'
import { createPinia } from 'pinia'
import { SessionExpiredError } from './http'
import { loadMessages } from './i18n'

export function legacyUrl(location: Pick<Location, 'pathname' | 'search'>): string {
  const params = new URLSearchParams(location.search)
  params.set('legacy', '1')
  return `${location.pathname}?${params.toString()}`
}

// 首次掛載完成前的任何錯誤（字典、載入、setup、render）都回舊版 Freemarker 頁；
// 掛載完成後的錯誤只記錄，頁面資料載入失敗由頁面自己顯示錯誤狀態。
// 掛載完成時設 window.__spaBooted，spa.html 的 window error 監聽靠它判斷是否還要回落。
export async function bootstrap(
  root: Component,
  selector = '#app',
  replace: (url: string) => void = (url) => window.location.replace(url),
): Promise<void> {
  let mounted = false
  let failed = false
  const fallback = (err: unknown): void => {
    if (err instanceof SessionExpiredError) {
      return
    }
    console.error(err)
    if (!mounted) {
      failed = true
      replace(legacyUrl(window.location))
    }
  }

  try {
    await loadMessages()
    const app = createApp(root)
    app.config.errorHandler = fallback
    app.use(createPinia())
    app.mount(selector)
    mounted = true
    // setup／render 的錯誤不會讓 mount() 丟出，只會進 errorHandler，所以要看 failed
    if (!failed) {
      ;(window as { __spaBooted?: boolean }).__spaBooted = true
    }
  } catch (err) {
    fallback(err)
  }
}
