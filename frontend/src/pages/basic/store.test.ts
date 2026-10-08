import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../shared/http', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

import { get, post } from '../../shared/http'
import { useBasicStore, type PageData } from './store'

function makePageData(): PageData {
  return {
    basicList: [{ id: '1', name: 'worker_processes', value: 'auto' }],
    moduleList: [
      { id: 'm1', name: 'ngx_stream_module.so', descrKey: 'descrStream', enable: false },
      { id: 'm2', name: 'ngx_stream_geoip2_module.so', descrKey: 'descrStreamGeoip2', enable: false },
    ],
    modulesOnDisk: ['ngx_stream_module.so', 'ngx_stream_geoip2_module.so'],
    isLinux: true,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(get).mockReset().mockImplementation(async () => makePageData())
  vi.mocked(post).mockReset().mockResolvedValue(undefined)
})

it('load 填入頁面資料', async () => {
  const store = useBasicStore()
  await store.load()
  expect(store.loaded).toBe(true)
  expect(store.basicList[0].name).toBe('worker_processes')
  expect(get).toHaveBeenCalledWith('/adminPage/basic/pageData')
})

it('save 新增時送空 id，編輯時送原 id，完成後重抓資料', async () => {
  const store = useBasicStore()
  await store.save({ id: '', name: 'a', value: 'b' })
  expect(post).toHaveBeenCalledWith('/adminPage/basic/addOver', { id: '', name: 'a', value: 'b' })
  await store.save({ id: '9', name: 'a', value: 'c' })
  expect(post).toHaveBeenLastCalledWith('/adminPage/basic/addOver', { id: '9', name: 'a', value: 'c' })
  expect(get).toHaveBeenCalledTimes(2)
})

it('remove 以逗號串接 id', async () => {
  const store = useBasicStore()
  await store.remove(['1', '2'])
  expect(post).toHaveBeenCalledWith('/adminPage/basic/del', { id: '1,2' })
})

it('move 送出 setOrder', async () => {
  const store = useBasicStore()
  await store.move('1', -1)
  expect(post).toHaveBeenCalledWith('/adminPage/basic/setOrder', { id: '1', count: -1 })
})

it('toggleModule 啟用時先送自己再送依賴，並更新狀態', async () => {
  const store = useBasicStore()
  await store.load()
  const cascaded = await store.toggleModule('ngx_stream_geoip2_module.so', true)
  expect(cascaded).toEqual(['ngx_stream_module.so'])
  expect(vi.mocked(post).mock.calls).toEqual([
    ['/adminPage/basic/setModuleEnable', { id: 'm2', enable: 1 }],
    ['/adminPage/basic/setModuleEnable', { id: 'm1', enable: 1 }],
  ])
  expect(store.moduleList.every((m) => m.enable)).toBe(true)
})
