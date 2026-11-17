import { createApp, type Component } from 'vue'
import { legacyUrl } from '../../shared/bootstrap'
import { SessionExpiredError } from '../../shared/http'
import { loadMessages } from '../../shared/i18n'

/** This entry owns its mount state; other SPA entries cannot suppress its fallback. */
export async function mountLogTail(
  loadPage: () => Promise<{ default: Component }> = () => import('./LogTailPage.vue'),
  replace: (url: string) => void = (url) => window.location.replace(url),
): Promise<void> {
  let mounted = false
  let failed = false
  const fallback = (error: unknown): void => {
    if (error instanceof SessionExpiredError) return
    console.error(error)
    if (!mounted && !failed) {
      failed = true
      replace(legacyUrl(window.location))
    }
  }
  try {
    const host = document.getElementById('logTailApp')
    if (!host) return
    const [page] = await Promise.all([loadPage(), loadMessages()])
    const app = createApp(page.default, { id: host.dataset.logId ?? '' })
    app.config.errorHandler = fallback
    app.mount(host)
    mounted = !failed
  } catch (error) {
    fallback(error)
  }
}

void mountLogTail()
