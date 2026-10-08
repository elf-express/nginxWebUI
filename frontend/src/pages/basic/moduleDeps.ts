// key 依賴 value（value 必須先載入），與舊版 static/js/adminPage/basic/index.js 相同
export const MODULE_DEPS: Record<string, string> = {
  'ngx_stream_geoip2_module.so': 'ngx_stream_module.so',
  'ngx_http_lua_module.so': 'ndk_http_module.so',
}

// 回傳除了自己以外要一併切換的模組名稱
export function cascadeToggles(name: string, enabling: boolean, enabled: Set<string>, available: Set<string>): string[] {
  if (enabling) {
    const dep = MODULE_DEPS[name]
    return dep && available.has(dep) && !enabled.has(dep) ? [dep] : []
  }
  return Object.entries(MODULE_DEPS)
    .filter(([child, parent]) => parent === name && enabled.has(child))
    .map(([child]) => child)
}
