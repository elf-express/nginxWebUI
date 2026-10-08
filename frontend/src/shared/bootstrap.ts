import { createApp, type Component } from 'vue'
import { createPinia } from 'pinia'
import { SessionExpiredError } from './http'
import { loadMessages } from './i18n'

export function legacyUrl(location: Pick<Location, 'pathname' | 'search'>): string {
  const params = new URLSearchParams(location.search)
  params.set('legacy', '1')
  return `${location.pathname}?${params.toString()}`
}

// 字典拿不到或掛載失敗就回舊版 Freemarker 頁；頁面資料載入失敗由頁面自己顯示錯誤狀態
export async function bootstrap(root: Component, selector = '#app'): Promise<void> {
  try {
    await loadMessages()
    const app = createApp(root)
    app.use(createPinia())
    app.mount(selector)
  } catch (err) {
    if (err instanceof SessionExpiredError) {
      return
    }
    console.error(err)
    window.location.replace(legacyUrl(window.location))
  }
}
