import { mount, flushPromises, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import { setMessages } from '../../shared/i18n'
import LogTailPage from './LogTailPage.vue'
import { mountLogTail } from './main'
import { SessionExpiredError } from '../../shared/http'

const { loadTail } = vi.hoisted(() => ({ loadTail: vi.fn() }))
vi.mock('./api', () => ({ loadTail }))

let wrapper: VueWrapper
const originalClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard')
const lines = ['  <script>bad()</script>', '\tend']
const data = (value = lines, state = 'ready') => ({ state, path: '/logs/access.log', lines: value, limit: 500 })
const labels = {
  'logStr.viewerTitle': 'Log viewer', 'logStr.copyVisible': 'Copy visible lines',
  'logStr.pauseDisplay': 'Pause display', 'logStr.resumeDisplay': 'Resume display',
  'logStr.followLatest': 'Follow latest', 'logStr.wrapLines': 'Wrap lines',
  'logStr.logContent': 'Log content', 'logStr.manualCopy': 'Copy manually',
  'logStr.clipboardDenied': 'Clipboard unavailable. Copy the selected text manually.',
  'logStr.copied': 'Copied', 'logStr.emptyLog': 'The log is empty',
  'logStr.ready': 'Reading log', 'logStr.missing': 'Log file does not exist',
  'logStr.unreadable': 'Unable to read log file', 'logStr.retry': 'Retry',
  'logStr.loading': 'Loading log', 'logStr.toolbar': 'Log controls',
  'logStr.visibleRange': 'Showing {0} lines (up to {1}); log excerpt only.',
}
const button = (name: string) => wrapper.get(`button[aria-label="${name}"]`)
async function render() {
  wrapper = mount(LogTailPage, { props: { id: 'saved-id' }, attachTo: document.body })
  await flushPromises()
}

beforeEach(() => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  setMessages(labels)
  loadTail.mockResolvedValue(data())
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: vi.fn().mockResolvedValue(undefined) } })
})
afterEach(() => {
  wrapper?.unmount()
  document.getSelection()?.removeAllRanges()
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
  if (originalClipboard) Object.defineProperty(navigator, 'clipboard', originalClipboard)
  else Reflect.deleteProperty(navigator, 'clipboard')
  setMessages({})
})

describe('LogTailPage', () => {
  it('literalMarkupDoesNotExecute: renders raw text in an accessible viewer', async () => {
    await render()

    expect(wrapper.get('h1').text()).toBe('Log viewer')
    expect(wrapper.get('pre').element.textContent).toBe('  <script>bad()</script>\n\tend')
    expect(wrapper.find('script').exists()).toBe(false)
    expect(wrapper.get('[role="region"]').attributes('aria-label')).toBe('Log content')
    expect(loadTail).toHaveBeenCalledWith('saved-id', expect.any(String), expect.any(AbortSignal))
  })

  it('whitespaceAndWrap: preserves whitespace and toggles wrapping', async () => {
    await render()

    await wrapper.get('[role="switch"]').trigger('click')

    expect(wrapper.get('pre').classes()).toContain('wrap-lines')
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
    expect(wrapper.get('[data-testid="visible-range"]').text()).toBe('Showing 2 lines (up to 500); log excerpt only.')
  })

  it('copyUsesVisibleSnapshot: copies frozen lines with LF and no final LF', async () => {
    // Arrange
    await render()
    await button('Pause display').trigger('click')
    loadTail.mockResolvedValue(data(['new background lines']))
    await vi.advanceTimersByTimeAsync(1000)
    // Act
    await button('Copy visible lines').trigger('click')
    await flushPromises()
    // Assert
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(lines.join('\n'))
    expect(wrapper.get('[data-testid="visible-range"]').text()).toBe('Showing 2 lines (up to 500); log excerpt only.')
    expect(wrapper.get('[role="status"]').text()).toContain('Copied')
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
  })

  it('clipboardDeniedShowsReadonlyTextarea: retains the clicked snapshot after async denial', async () => {
    // Arrange
    let reject!: (reason: unknown) => void
    vi.mocked(navigator.clipboard.writeText).mockImplementation(() => new Promise((_, no) => { reject = no }))
    await render()
    await button('Copy visible lines').trigger('click')
    loadTail.mockResolvedValue(data(['new snapshot']))
    await vi.advanceTimersByTimeAsync(1000)
    // Act
    reject(new Error('denied'))
    await flushPromises()
    // Assert
    const manualTextarea = wrapper.get('textarea').element as HTMLTextAreaElement
    expect(manualTextarea.readOnly).toBe(true)
    expect(manualTextarea.value).toBe(lines.join('\n'))
    expect(manualTextarea.selectionStart).toBe(0)
    expect(manualTextarea.selectionEnd).toBe(manualTextarea.value.length)
    expect(wrapper.get('[role="alert"]').text()).toContain('Clipboard unavailable')
  })

  it('falls back to manual copy when the Clipboard API is unavailable', async () => {
    Object.defineProperty(navigator, 'clipboard', { configurable: true, value: undefined })
    await render()

    await button('Copy visible lines').trigger('click')
    await flushPromises()

    expect((wrapper.get('textarea').element as HTMLTextAreaElement).value).toBe(lines.join('\n'))
  })

  it('scrollUpAndSelectionFreeze: scroll up freezes text nodes while polling continues', async () => {
    // Arrange
    await render()
    const region = wrapper.get('[role="region"]')
    Object.defineProperties(region.element, { scrollHeight: { value: 500 }, clientHeight: { value: 100 } })
    const node = wrapper.get('code').element.firstChild
    // Act
    await region.trigger('scroll')
    loadTail.mockResolvedValue(data(['new background lines']))
    await vi.advanceTimersByTimeAsync(1000)
    // Assert
    expect(loadTail).toHaveBeenCalledTimes(2)
    expect(wrapper.get('code').element.firstChild).toBe(node)
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
    expect(button('Follow latest').attributes('aria-pressed')).toBe('false')
  })

  it('keeps an active selection and its text node stable through background polling', async () => {
    // Arrange
    await render()
    const node = wrapper.get('code').element.firstChild!
    const range = document.createRange()
    range.setStart(node, 0)
    range.setEnd(node, 5)
    document.getSelection()!.addRange(range)
    // Act
    document.dispatchEvent(new Event('selectionchange'))
    loadTail.mockResolvedValue(data(['latest']))
    await vi.advanceTimersByTimeAsync(1000)
    // Assert
    expect(wrapper.get('code').element.firstChild).toBe(node)
    expect(document.getSelection()!.toString()).toBe(lines.join('\n').slice(0, 5))
    expect(button('Follow latest').attributes('aria-pressed')).toBe('false')
  })

  it('toggles following off without stopping polling or replacing text nodes, then publishes latest when enabled', async () => {
    // Arrange
    await render()
    const node = wrapper.get('code').element.firstChild
    expect(button('Follow latest').attributes('aria-pressed')).toBe('true')
    // Act
    await button('Follow latest').trigger('click')
    expect(button('Follow latest').attributes('aria-pressed')).toBe('false')
    loadTail.mockResolvedValue(data(['latest background snapshot']))
    await vi.advanceTimersByTimeAsync(1000)
    // Assert
    expect(loadTail).toHaveBeenCalledTimes(2)
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
    expect(wrapper.get('code').element.firstChild).toBe(node)
    await button('Follow latest').trigger('click')
    expect(button('Follow latest').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('pre').element.textContent).toBe('latest background snapshot')
  })

  it('does not clear a viewer selection when explicitly disabling following', async () => {
    // Arrange
    await render()
    const range = document.createRange()
    range.selectNodeContents(wrapper.get('code').element)
    document.getSelection()!.addRange(range)
    // Act
    await button('Follow latest').trigger('click')
    // Assert
    expect(button('Follow latest').attributes('aria-pressed')).toBe('false')
    expect(document.getSelection()!.toString()).toBe(lines.join('\n'))
  })

  it('explicitFollowResumes: resumes only when both pause and follow permit publishing', async () => {
    // Arrange
    await render()
    const region = wrapper.get('[role="region"]')
    Object.defineProperties(region.element, { scrollHeight: { value: 500 }, clientHeight: { value: 100 } })
    await region.trigger('scroll')
    await button('Pause display').trigger('click')
    loadTail.mockResolvedValue(data(['latest']))
    await vi.advanceTimersByTimeAsync(1000)
    // Act
    await button('Follow latest').trigger('click')
    // Assert
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
    await button('Resume display').trigger('click')
    await nextTick()
    expect(wrapper.get('pre').element.textContent).toBe('latest')
  })

  it.each([
    ['missing', 'Log file does not exist'], ['unreadable', 'Unable to read log file'], ['ready', 'The log is empty'],
  ])('missingVsEmptyVsError: distinguishes %s from other empty states', async (state, label) => {
    loadTail.mockResolvedValue(data([], state))

    await render()

    expect(wrapper.text()).toContain(label)
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
  })

  it('shows a retry action after request errors without clearing the displayed snapshot', async () => {
    // Arrange
    await render()
    loadTail.mockRejectedValue(new Error('network'))
    await vi.advanceTimersByTimeAsync(1000)
    expect(wrapper.get('[role="alert"]').text()).toContain('commonStr.error')
    expect(wrapper.get('pre').element.textContent).toBe(lines.join('\n'))
    // Act
    loadTail.mockResolvedValue(data(['recovered']))
    await button('Retry').trigger('click')
    await flushPromises()
    // Assert
    expect(wrapper.find('[role="alert"]').exists()).toBe(false)
    expect(wrapper.get('pre').element.textContent).toBe('recovered')
  })

  it('ignores collapsed selections and selections outside the log region', async () => {
    // Arrange
    await render()
    document.dispatchEvent(new Event('selectionchange'))
    const range = document.createRange()
    range.selectNodeContents(wrapper.get('h1').element)
    document.getSelection()!.addRange(range)
    // Act
    document.dispatchEvent(new Event('selectionchange'))
    loadTail.mockResolvedValue(data(['latest']))
    await vi.advanceTimersByTimeAsync(1000)
    // Assert
    expect(wrapper.get('pre').element.textContent).toBe('latest')
    expect(button('Follow latest').attributes('aria-pressed')).toBe('true')
  })

  it('explicit follow clears an active viewer selection before replacing its text', async () => {
    // Arrange
    await render()
    const range = document.createRange()
    range.selectNodeContents(wrapper.get('code').element)
    document.getSelection()!.addRange(range)
    document.dispatchEvent(new Event('selectionchange'))
    loadTail.mockResolvedValue(data(['latest']))
    await vi.advanceTimersByTimeAsync(1000)
    // Act
    await button('Follow latest').trigger('click')
    // Assert
    expect(document.getSelection()!.isCollapsed).toBe(true)
    expect(wrapper.get('pre').element.textContent).toBe('latest')
  })

  it('disables copying while loading and exposes the loading state', async () => {
    loadTail.mockReturnValue(new Promise(() => {}))
    await render()

    expect(button('Copy visible lines').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[role="region"]').attributes('aria-busy')).toBe('true')
    expect(wrapper.text()).toContain('Loading log')
  })
})

describe('log tail bootstrap', () => {
  afterEach(() => {
    document.getElementById('logTailApp')?.remove()
    window.history.replaceState(null, '', '/')
    Reflect.deleteProperty(window, '__spaBooted')
  })

  it('does nothing when its own root is absent', async () => {
    const loadPage = vi.fn()

    await mountLogTail(loadPage)

    expect(loadPage).not.toHaveBeenCalled()
  })

  it('preserves the complete query when the page import fails, independently of other SPA flags', async () => {
    // Arrange
    const host = document.createElement('div')
    host.id = 'logTailApp'
    document.body.append(host)
    window.history.replaceState(null, '', '/adminPage/log/tail?id=a%26b&protocol=http&remote=7&extra=x%3Dy')
    ;(window as Window & { __spaBooted?: boolean }).__spaBooted = true
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    vi.spyOn(console, 'error').mockImplementation(() => {})
    const replace = vi.fn()
    // Act
    await mountLogTail(() => Promise.reject(new Error('chunk failed')), replace)
    // Assert
    const url = new URL(replace.mock.calls[0][0], window.location.origin)
    expect(Object.fromEntries(url.searchParams)).toEqual({ id: 'a&b', protocol: 'http', remote: '7', extra: 'x=y', legacy: '1' })
    vi.unstubAllGlobals()
    window.history.replaceState(null, '', '/')
  })

  it('lets the shared HTTP session redirect own authentication failures', async () => {
    // Arrange
    const host = document.createElement('div')
    host.id = 'logTailApp'
    document.body.append(host)
    vi.stubGlobal('fetch', vi.fn().mockReturnValue(new Promise(() => {})))
    const replace = vi.fn()
    // Act
    await mountLogTail(() => Promise.reject(new SessionExpiredError()), replace)
    // Assert
    expect(replace).not.toHaveBeenCalled()
    vi.unstubAllGlobals()
  })
})
