import { beforeEach, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('../../shared/http', () => ({
  get: vi.fn(),
  post: vi.fn(),
}))

import { get, post } from '../../shared/http'
import { useCrowdSecStore, type CrowdSecConfig } from './store'

function makeConfig(): CrowdSecConfig {
  return {
    lapiUrl: 'http://crowdsec:8080',
    lapiUrlEnv: 'CROWDSEC_LAPI_URL',
    bouncerKey: {
      masked: '••••••a3f9',
      source: 'ENV',
      configured: true,
      file: '/run/secrets/crowdsec_bouncer_key',
      env: 'CROWDSEC_BOUNCER_KEY',
    },
    machineId: 'nginxwebui',
    machinePassword: {
      masked: '',
      source: 'NONE',
      configured: false,
      file: '/run/secrets/crowdsec_machine_password',
      env: 'CROWDSEC_MACHINE_PASSWORD',
    },
    graceSeconds: 120,
    state: null,
    configured: false,
  }
}

beforeEach(() => {
  setActivePinia(createPinia())
  vi.mocked(get).mockReset()
  vi.mocked(post).mockReset().mockResolvedValue(undefined)
})

it('load 讀 getConfig', async () => {
  vi.mocked(get).mockResolvedValue(makeConfig())
  const store = useCrowdSecStore()
  await store.load()
  expect(get).toHaveBeenCalledWith('/adminPage/crowdsec/getConfig')
  expect(store.config?.bouncerKey.masked).toBe('••••••a3f9')
})

it('test 讀 status，結束後 testing 復原', async () => {
  vi.mocked(get).mockResolvedValue({ lapi: 'ok', bouncerKey: 'ok', machine: 'ok' })
  const store = useCrowdSecStore()
  const pending = store.test()
  expect(store.testing).toBe(true)
  await pending
  expect(get).toHaveBeenCalledWith('/adminPage/crowdsec/status')
  expect(store.probe?.machine).toBe('ok')
  expect(store.testing).toBe(false)
})

it('test 失敗也會復原 testing 並拋出', async () => {
  vi.mocked(get).mockRejectedValue(new Error('boom'))
  const store = useCrowdSecStore()
  await expect(store.test()).rejects.toThrow('boom')
  expect(store.testing).toBe(false)
})

it('saveGrace 只送 graceSeconds 並更新本地值', async () => {
  vi.mocked(get).mockResolvedValue(makeConfig())
  const store = useCrowdSecStore()
  await store.load()
  await store.saveGrace(45)
  expect(post).toHaveBeenCalledWith('/adminPage/crowdsec/saveConfig', { graceSeconds: 45 })
  expect(store.config?.graceSeconds).toBe(45)
})
