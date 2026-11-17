import { createApp } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { setMessages } from '../../shared/i18n'
import { setRedirect } from '../../shared/http'
import { createGuid, useLogTail } from './useLogTail'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((yes, no) => { resolve = yes; reject = no })
  return { promise, resolve, reject }
}

function response(lines: string[], state = 'ready'): Response {
  return {
    ok: true, status: 200, redirected: false,
    json: async () => ({ success: true, status: '200', obj: { state, path: '/logs/access.log', lines, limit: 500 } }),
  } as Response
}

const unmounts: (() => void)[] = []
function viewer(guid?: string) {
  let result!: ReturnType<typeof useLogTail>
  const host = document.createElement('div')
  const app = createApp({ setup() { result = useLogTail('saved-7', guid); return () => null } })
  app.mount(host)
  unmounts.push(() => app.unmount())
  return result
}

async function settle() {
  await vi.advanceTimersByTimeAsync(0)
}

function navigation() {
  const replace = vi.fn()
  const double = Object.create(window) as Window
  Object.defineProperty(double, 'location', { value: {
    pathname: '/adminPage/log/tail', search: '?id=saved-7&remote=8&name=a%26b',
    href: 'http://localhost/adminPage/log/tail?id=saved-7&remote=8&name=a%26b', replace,
  } })
  vi.stubGlobal('window', double)
  return replace
}

beforeEach(() => {
  vi.useFakeTimers()
  setMessages({ 'commonStr.error': 'Request failed' })
})

afterEach(() => {
  unmounts.splice(0).forEach((unmount) => unmount())
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  setMessages({})
})

describe('useLogTail', () => {
  it('keeps a single in-flight request even when retry is pressed', async () => {
    // Arrange
    const pending = deferred<Response>()
    let concurrent = 0
    let maxConcurrentRequests = 0
    const fetchMock = vi.fn(() => {
      concurrent++
      maxConcurrentRequests = Math.max(maxConcurrentRequests, concurrent)
      return pending.promise.finally(() => concurrent--)
    })
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer('fixed-token')

    // Act
    state.retry()
    await vi.advanceTimersByTimeAsync(5000)

    // Assert
    expect(maxConcurrentRequests).toBe(1)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(state.loading.value).toBe(true)
    pending.resolve(response(['done']))
    await settle()
    expect(state.loading.value).toBe(false)
  })

  it('waits 1000ms after settlement before polling again', async () => {
    // Arrange
    const pending = deferred<Response>()
    const fetchMock = vi.fn().mockReturnValueOnce(pending.promise).mockResolvedValue(response(['next']))
    vi.stubGlobal('fetch', fetchMock)
    viewer()
    await vi.advanceTimersByTimeAsync(2500)

    // Act
    pending.resolve(response(['first']))
    await settle()
    await vi.advanceTimersByTimeAsync(999)

    // Assert
    expect(fetchMock).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(1)
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it.each(['paused', 'following'] as const)('freezes the visible snapshot while %s still polls latest', async (mode) => {
    // Arrange
    const fetchMock = vi.fn().mockResolvedValueOnce(response(['first'])).mockResolvedValue(response(['latest']))
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer()
    await settle()
    const frozen = state.visibleLines.value

    // Act
    if (mode === 'paused') state.setPaused(true)
    else state.setFollowing(false)
    await vi.advanceTimersByTimeAsync(1000)

    // Assert
    expect(state.visibleLines.value).toBe(frozen)
    expect(state.visibleLines.value).toEqual(['first'])
    expect(state.latest.value?.lines).toEqual(['latest'])
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it.each(['paused', 'following'] as const)('publishes the latest snapshot once %s resumes', async (mode) => {
    // Arrange
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response(['first'])).mockResolvedValue(response(['latest'])))
    const state = viewer()
    await settle()
    if (mode === 'paused') state.setPaused(true)
    else state.setFollowing(false)
    await vi.advanceTimersByTimeAsync(1000)

    // Act
    if (mode === 'paused') state.setPaused(false)
    else state.setFollowing(true)

    // Assert
    expect(state.visibleLines.value).toEqual(['latest'])
  })

  it('only resumes publishing after both pause and selection freeze are released', async () => {
    // Arrange
    vi.stubGlobal('fetch', vi.fn().mockResolvedValueOnce(response(['first'])).mockResolvedValue(response(['latest'])))
    const state = viewer()
    await settle()
    state.setPaused(true)
    state.setFollowing(false)
    await vi.advanceTimersByTimeAsync(1000)

    // Act
    state.setPaused(false)

    // Assert
    expect(state.visibleLines.value).toEqual(['first'])
    state.setFollowing(true)
    expect(state.visibleLines.value).toEqual(['latest'])
  })

  it('aborts on unmount and ignores a response that settles after disposal', async () => {
    // Arrange
    const pending = deferred<Response>()
    const fetchMock = vi.fn().mockReturnValue(pending.promise)
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer()
    const abortSignal = fetchMock.mock.calls[0][1].signal as AbortSignal

    // Act
    unmounts.pop()!()
    pending.resolve(response(['too late']))
    await settle()
    state.retry()
    await vi.advanceTimersByTimeAsync(5000)

    // Assert
    expect(abortSignal.aborted).toBe(true)
    expect(state.latest.value).toBeNull()
    expect(fetchMock).toHaveBeenCalledTimes(1)
    expect(state.error.value).toBe('')
  })

  it('clears a scheduled poll on unmount', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response(['first'])))
    viewer()
    await settle()

    unmounts.pop()!()
    await vi.advanceTimersByTimeAsync(2000)

    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('uses a distinct stable guid for each viewer and preserves an explicit token', async () => {
    // Arrange
    const fetchMock = vi.fn().mockResolvedValue(response([]))
    vi.stubGlobal('fetch', fetchMock)

    // Act
    viewer()
    viewer()
    viewer('explicit-token')
    await settle()
    await vi.advanceTimersByTimeAsync(1000)

    // Assert
    const bodies = fetchMock.mock.calls.map((call) => call[1].body as URLSearchParams)
    const guidA = bodies[0].get('guid')
    const guidB = bodies[1].get('guid')
    expect(guidA).not.toBe(guidB)
    expect(bodies[2].get('guid')).toBe('explicit-token')
    expect(bodies[3].get('guid')).toBe(guidA)
    expect(bodies[0].get('id')).toBe('saved-7')
  })

  it('falls back to the complete legacy URL only before any successful response', async () => {
    const replace = navigation()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 404 }))

    const state = viewer()
    await settle()

    expect(replace).toHaveBeenCalledWith('/adminPage/log/tail?id=saved-7&remote=8&name=a%26b&legacy=1')
    expect(state.error.value).toBe('')
    await vi.advanceTimersByTimeAsync(2000)
    expect(fetch).toHaveBeenCalledTimes(1)
  })

  it('shows an HTTP 404 after success as retryable and keeps polling', async () => {
    // Arrange
    const replace = navigation()
    const fetchMock = vi.fn().mockResolvedValueOnce(response(['first'])).mockResolvedValue({ ok: false, status: 404 })
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer()
    await settle()

    // Act
    await vi.advanceTimersByTimeAsync(1000)

    // Assert
    expect(state.error.value).toBe('Request failed')
    expect(state.visibleLines.value).toEqual(['first'])
    expect(replace).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1000)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it('shows a localized server error and permits immediate retry without a duplicate timer', async () => {
    // Arrange
    const fetchMock = vi.fn().mockResolvedValueOnce({ ok: true, redirected: false,
      json: async () => ({ success: false, status: '500', msg: 'Invalid saved log' }),
    }).mockResolvedValue(response(['recovered']))
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer()
    await settle()
    expect(state.error.value).toBe('Invalid saved log')
    await vi.advanceTimersByTimeAsync(500)

    // Act
    state.retry()
    await settle()
    await vi.advanceTimersByTimeAsync(500)

    // Assert
    expect(state.visibleLines.value).toEqual(['recovered'])
    expect(state.error.value).toBe('')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    await vi.advanceTimersByTimeAsync(500)
    expect(fetchMock).toHaveBeenCalledTimes(3)
  })

  it.each(['network', 'http500', 'abort'])('retries %s failures after settlement without legacy fallback', async (kind) => {
    // Arrange
    const replace = navigation()
    const fetchMock = vi.fn().mockResolvedValue(response(['recovered']))
    if (kind === 'http500') fetchMock.mockResolvedValueOnce({ ok: false, status: 500 })
    else fetchMock.mockRejectedValueOnce(kind === 'abort' ? new DOMException('', 'AbortError') : new TypeError('offline'))
    vi.stubGlobal('fetch', fetchMock)
    const state = viewer()

    // Act
    await settle()

    // Assert
    expect(state.error.value).toBe(kind === 'abort' ? '' : 'Request failed')
    expect(replace).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(1000)
    expect(state.visibleLines.value).toEqual(['recovered'])
  })

  it.each(['missing', 'unreadable'])('publishes %s as data and does not fall back', async (status) => {
    const replace = navigation()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response([], status)))

    const state = viewer()
    await settle()

    expect(state.latest.value?.state).toBe(status)
    expect(state.visibleLines.value).toEqual([])
    expect(state.error.value).toBe('')
    expect(replace).not.toHaveBeenCalled()
  })

  it.each(['/adminPage/login', '/adminPage/login/noServer'])('preserves the session redirect to %s and stops polling', async (path) => {
    // Arrange
    const redirect = vi.fn()
    setRedirect(redirect)
    const replace = navigation()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ redirected: true, url: `http://localhost${path}` }))

    // Act
    const state = viewer()
    await settle()
    await vi.advanceTimersByTimeAsync(2000)

    // Assert
    expect(redirect).toHaveBeenCalledWith(path)
    expect(state.error.value).toBe('')
    expect(replace).not.toHaveBeenCalled()
    expect(fetch).toHaveBeenCalledTimes(1)
  })
})

describe('createGuid', () => {
  it('prefers crypto.randomUUID when it is available', () => {
    vi.stubGlobal('crypto', { randomUUID: () => 'c6871c33-8027-4161-aa47-9ac58259caff' })

    expect(createGuid()).toBe('c6871c33-8027-4161-aa47-9ac58259caff')
  })

  it('creates distinct RFC 4122 UUIDs with cryptographic bytes in an insecure context', () => {
    // Arrange
    const secureRandom = crypto.getRandomValues.bind(crypto)
    const getRandomValues = vi.fn((bytes: Uint8Array) => secureRandom(bytes))
    vi.stubGlobal('crypto', { getRandomValues })

    // Act
    const guidA = createGuid()
    const guidB = createGuid()

    // Assert
    expect(guidA).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/)
    expect(guidA).not.toBe(guidB)
    expect(getRandomValues).toHaveBeenCalledTimes(2)
  })
})
