import type { Locale } from 'ant-design-vue/es/locale'
import enUS from 'ant-design-vue/es/locale/en_US'
import zhCN from 'ant-design-vue/es/locale/zh_CN'
import zhTW from 'ant-design-vue/es/locale/zh_TW'

/** /adminPage/i18n 回傳的 lang（zh_CN、zh_TW、en_US，空字串或 zh 為簡中）對應 ant-design-vue 的內建語系 */
export function antLocaleOf(lang: string): Locale {
  switch (lang) {
    case 'zh_TW':
      return zhTW
    case 'en_US':
      return enUS
    default:
      return zhCN
  }
}
