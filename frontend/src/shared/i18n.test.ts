import { beforeEach, expect, it } from 'vitest'
import { setMessages, t, te } from './i18n'

beforeEach(() => {
  setMessages({
    'moduleStr.depAutoEnabled': '已自動啟用依賴模組：{0}',
    'paramStr.block': 'server { listen 80; }',
    'demoStr.two': '{1}-{0}',
  })
})

it('依位置替換 {0}、{1}', () => {
  expect(t('moduleStr.depAutoEnabled', 'ndk_http_module.so')).toBe('已自動啟用依賴模組：ndk_http_module.so')
  expect(t('demoStr.two', 'a', 'b')).toBe('b-a')
})

it('缺參數時保留佔位字', () => {
  expect(t('demoStr.two', 'a')).toBe('{1}-a')
})

it('nginx 區塊的大括號維持字面值', () => {
  expect(t('paramStr.block')).toBe('server { listen 80; }')
})

it('找不到 key 時回 key 本身', () => {
  expect(t('nope.key')).toBe('nope.key')
  expect(te('nope.key')).toBe(false)
  expect(te('paramStr.block')).toBe(true)
})
