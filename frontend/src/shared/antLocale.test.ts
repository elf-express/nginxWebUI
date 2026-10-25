import { expect, it } from 'vitest'
import enUS from 'ant-design-vue/es/locale/en_US'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import zhTW from 'ant-design-vue/es/locale/zh_TW'
import { antLocaleOf } from './antLocale'

it('依介面語系選 ant-design-vue 內建語系，空字串與 zh 是簡中', () => {
  expect(antLocaleOf('zh_TW')).toBe(zhTW)
  expect(antLocaleOf('en_US')).toBe(enUS)
  expect(antLocaleOf('zh')).toBe(zhCN)
  expect(antLocaleOf('')).toBe(zhCN)
  expect(antLocaleOf('fr_FR')).toBe(zhCN)
})

it('空表格文字取自對應語系', () => {
  expect(antLocaleOf('zh_TW').Empty?.description).toBe('無此資料')
  expect(antLocaleOf('en_US').Empty?.description).toBe('No data')
})
