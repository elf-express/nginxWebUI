import { expect, it } from 'vitest'
import { cascadeToggles } from './moduleDeps'

const available = new Set(['ngx_stream_module.so', 'ngx_stream_geoip2_module.so', 'ndk_http_module.so', 'ngx_http_lua_module.so'])

it('啟用時連帶啟用尚未啟用的依賴模組', () => {
  expect(cascadeToggles('ngx_stream_geoip2_module.so', true, new Set(), available)).toEqual(['ngx_stream_module.so'])
  expect(cascadeToggles('ngx_stream_geoip2_module.so', true, new Set(['ngx_stream_module.so']), available)).toEqual([])
})

it('依賴模組不在磁碟上時不連帶啟用', () => {
  expect(cascadeToggles('ngx_http_lua_module.so', true, new Set(), new Set(['ngx_http_lua_module.so']))).toEqual([])
})

it('停用時連帶停用已啟用的相依模組', () => {
  expect(cascadeToggles('ngx_stream_module.so', false, new Set(['ngx_stream_geoip2_module.so']), available)).toEqual(['ngx_stream_geoip2_module.so'])
  expect(cascadeToggles('ngx_stream_module.so', false, new Set(), available)).toEqual([])
})
