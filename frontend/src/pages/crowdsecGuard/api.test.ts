import { afterEach, expect, it, vi } from 'vitest'
import { fetchBans, fetchWafEvents, lookupIp, saveWafRetention, syncWaf, whitelistIp } from './api'

function ok(obj: unknown): Response {
  return { ok: true, status: 200, redirected: false, url: 'http://localhost/x', json: async () => ({ success: true, status: '200', obj }) } as Response
}

afterEach(() => vi.unstubAllGlobals())

it('IP 查詢帶 ip 參數', async () => {
  const fetchMock = vi.fn().mockResolvedValue(ok({ result: { ip: '203.0.113.5', layers: [] }, serverNames: {} }))
  vi.stubGlobal('fetch', fetchMock)
  const r = await lookupIp('203.0.113.5')
  expect(r.result.ip).toBe('203.0.113.5')
  expect(fetchMock.mock.calls[0][0]).toBe('/adminPage/crowdsecGuard/ipLookup?ip=203.0.113.5')
})

it('封鎖名單帶 scope 與分頁', async () => {
  const fetchMock = vi.fn().mockResolvedValue(ok({ records: [], count: 0, createdAvailable: false }))
  vi.stubGlobal('fetch', fetchMock)
  await fetchBans('community', '1.2', 2, 20)
  expect(fetchMock.mock.calls[0][0]).toBe('/adminPage/crowdsecGuard/bans?scope=community&q=1.2&page=2&limit=20')
})

it('加入白名單用 POST', async () => {
  const fetchMock = vi.fn().mockResolvedValue(ok({ denyAllow: 'added', crowdsec: 'ok', deleted: 1, rangesKept: 0 }))
  vi.stubGlobal('fetch', fetchMock)
  await whitelistIp('203.0.113.5')
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
})

it('WAF 事件帶篩選與分頁', async () => {
  const fetchMock = vi.fn().mockResolvedValue(ok({ records: [], count: 0 }))
  vi.stubGlobal('fetch', fetchMock)
  await fetchWafEvents({ serverId: '', category: 'crs-sqli', action: 'log', ip: '198.51.100.9', days: 7, page: 2, limit: 20 })
  expect(fetchMock.mock.calls[0][0]).toBe(
    '/adminPage/crowdsecGuard/waf/events?serverId=&category=crs-sqli&action=log&ip=198.51.100.9&days=7&page=2&limit=20',
  )
})

it('立即同步與保留天數用 POST', async () => {
  const fetchMock = vi.fn().mockResolvedValue(ok({ outcome: 'ok', added: 0 }))
  vi.stubGlobal('fetch', fetchMock)
  await syncWaf()
  await saveWafRetention(14)
  expect(fetchMock.mock.calls[0][0]).toBe('/adminPage/crowdsecGuard/waf/sync')
  expect(fetchMock.mock.calls[0][1].method).toBe('POST')
  expect(fetchMock.mock.calls[1][0]).toBe('/adminPage/crowdsecGuard/waf/retention')
  expect(String(fetchMock.mock.calls[1][1].body)).toBe('days=14')
})
