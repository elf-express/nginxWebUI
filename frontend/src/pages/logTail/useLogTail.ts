import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'
import { legacyUrl } from '../../shared/bootstrap'
import { ApiError, SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { loadTail } from './api'
import type { LogTailData } from './types'

export function createGuid(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 0x0f) | 0x40
  bytes[8] = (bytes[8] & 0x3f) | 0x80
  const hex = Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function useLogTail(id: string, guid = createGuid()) {
  const latest = shallowRef<LogTailData | null>(null)
  const visibleLines = shallowRef<string[]>([])
  const paused = ref(false)
  const following = ref(true)
  const loading = ref(false)
  const error = ref('')
  let timer: ReturnType<typeof setTimeout> | undefined
  let controller: AbortController | undefined
  let disposed = false
  let stopped = false

  function publish(): void {
    if (!paused.value && following.value && latest.value) {
      visibleLines.value = latest.value.lines
    }
  }

  function setPaused(value: boolean): void {
    paused.value = value
    publish()
  }

  function setFollowing(value: boolean): void {
    following.value = value
    publish()
  }

  async function poll(): Promise<void> {
    if (disposed || stopped || loading.value) return
    clearTimeout(timer)
    loading.value = true
    controller = new AbortController()
    try {
      const data = await loadTail(id, guid, controller.signal)
      if (disposed) return
      latest.value = data
      error.value = ''
      publish()
    } catch (err) {
      if (disposed) return
      if (err instanceof SessionExpiredError) {
        stopped = true
      } else if (err instanceof ApiError && err.status === 404 && latest.value === null) {
        stopped = true
        window.location.replace(legacyUrl(window.location))
      } else if (!(typeof err === 'object' && err !== null && 'name' in err && err.name === 'AbortError')) {
        error.value = (err instanceof ApiError && err.message) || t('commonStr.error')
      }
    } finally {
      controller = undefined
      loading.value = false
      if (!disposed && !stopped) timer = setTimeout(() => { void poll() }, 1000)
    }
  }

  function retry(): void {
    void poll()
  }

  onMounted(retry)
  onBeforeUnmount(() => {
    disposed = true
    clearTimeout(timer)
    controller?.abort()
  })

  return { latest, visibleLines, paused, following, loading, error, setPaused, setFollowing, retry }
}
