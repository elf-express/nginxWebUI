import { expect, it } from 'vitest'
import { validateBasicForm } from './form'

it('名稱或值為空時回對應的 i18n key', () => {
  expect(validateBasicForm({ id: '', name: '', value: 'x' })).toBe('basicStr.nameNotice')
  expect(validateBasicForm({ id: '', name: '  ', value: 'x' })).toBe('basicStr.nameNotice')
  expect(validateBasicForm({ id: '', name: 'worker_processes', value: '' })).toBe('basicStr.valueNotice')
  expect(validateBasicForm({ id: '', name: 'worker_processes', value: 'auto' })).toBeNull()
})
