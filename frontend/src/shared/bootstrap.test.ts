import { afterEach, beforeEach, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { bootstrap, legacyUrl } from './bootstrap'

vi.mock('./i18n', () => ({ loadMessages: vi.fn().mockResolvedValue('zh') }))

beforeEach(() => {
  document.body.innerHTML = '<div id="app"></div>'
  vi.spyOn(console, 'error').mockImplementation(() => {})
  window.history.replaceState(null, '', '/adminPage/basic')
})

afterEach(() => {
  vi.restoreAllMocks()
  delete (window as { __spaBooted?: boolean }).__spaBooted
})

it('保留原有參數並加上 legacy=1', () => {
  expect(legacyUrl({ pathname: '/adminPage/basic', search: '' } as Location)).toBe('/adminPage/basic?legacy=1')
  expect(legacyUrl({ pathname: '/adminPage/basic', search: '?a=1' } as Location)).toBe('/adminPage/basic?a=1&legacy=1')
})

it('首次掛載前 setup 丟錯就回舊版頁', async () => {
  const replace = vi.fn()
  const Broken = defineComponent({
    setup() {
      throw new Error('boom')
    },
  })

  await bootstrap(Broken, '#app', replace)

  expect(replace).toHaveBeenCalledWith('/adminPage/basic?legacy=1')
  expect((window as { __spaBooted?: boolean }).__spaBooted).toBeUndefined()
})

it('掛載成功後的錯誤只記錄，不轉址', async () => {
  const replace = vi.fn()
  const Page = defineComponent({
    setup() {
      return () =>
        h('button', {
          id: 'trigger',
          onClick: () => {
            throw new Error('late')
          },
        })
    },
  })

  await bootstrap(Page, '#app', replace)
  expect((window as { __spaBooted?: boolean }).__spaBooted).toBe(true)

  document.querySelector<HTMLButtonElement>('#trigger')!.click()

  expect(replace).not.toHaveBeenCalled()
  expect(console.error).toHaveBeenCalled()
})
