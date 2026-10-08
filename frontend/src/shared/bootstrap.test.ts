import { expect, it } from 'vitest'
import { legacyUrl } from './bootstrap'

it('保留原有參數並加上 legacy=1', () => {
  expect(legacyUrl({ pathname: '/adminPage/basic', search: '' } as Location)).toBe('/adminPage/basic?legacy=1')
  expect(legacyUrl({ pathname: '/adminPage/basic', search: '?a=1' } as Location)).toBe('/adminPage/basic?a=1&legacy=1')
})
