import { expect, it } from 'vitest'

// Space 的 wrap 屬性會內嵌 margin-bottom: -rowGap，換行改由頁面的全域樣式 .ant-space-horizontal 處理
const USES_WRAP = /<Space\b[^>]*\s:?wrap\b/

it('偵測 wrap 與 :wrap 兩種寫法', () => {
  expect(USES_WRAP.test('<Space wrap class="a">')).toBe(true)
  expect(USES_WRAP.test('<Space :size="4" wrap>')).toBe(true)
  expect(USES_WRAP.test('<Space :wrap="true">')).toBe(true)
  expect(USES_WRAP.test('<Space class="waf-bar">')).toBe(false)
})

it('頁面不使用 Space 的 wrap 屬性', () => {
  const files = import.meta.glob('../**/*.vue', { query: '?raw', import: 'default', eager: true }) as Record<string, string>
  expect(Object.keys(files).length).toBeGreaterThan(0)
  const offenders = Object.entries(files)
    .filter(([, source]) => USES_WRAP.test(source))
    .map(([file]) => file)
  expect(offenders).toEqual([])
})
