import { describe, expect, it } from 'vitest'
import {
  ambiguousSiteText,
  blockedByKey,
  createLatest,
  crowdsecErrorKey,
  geoSiteKeys,
  ipHash,
  layerColor,
  layerStateKey,
  parseHash,
  reasonKeysOf,
  shouldLookup,
  sourceKeyOf,
  verdictType,
  whitelistMessage,
  type Outcome,
} from './view'
import type { WhitelistOutcome } from './api'

// 測試用的翻譯：key 加參數，方便斷言用到哪一句
const tr = (key: string, ...args: (string | number)[]) => (args.length ? `${key}(${args.join(',')})` : key)

function outcome(patch: Partial<WhitelistOutcome> = {}): WhitelistOutcome {
  return { denyAllow: 'added', crowdsec: 'ok', deleted: 2, rangesKept: 0, ...patch }
}

describe('parseHash', () => {
  it('分頁與 IP', () => {
    expect(parseHash('#ip=203.0.113.5')).toEqual({ tab: 'ip', ip: '203.0.113.5' })
    expect(parseHash('#bans')).toEqual({ tab: 'bans', ip: '' })
    expect(parseHash('#conn')).toEqual({ tab: 'conn', ip: '' })
    expect(parseHash('')).toEqual({ tab: 'ip', ip: '' })
    expect(parseHash('#unknown')).toEqual({ tab: 'ip', ip: '' })
    expect(parseHash('#ip=2001%3Adb8%3A%3A1')).toEqual({ tab: 'ip', ip: '2001:db8::1' })
  })
})

describe('IP 查詢分頁何時要發查詢', () => {
  it('IP 變了才查；其他分頁的「查這個 IP」即使是同一個 IP 也要重查', () => {
    expect(shouldLookup('203.0.113.5', '', false)).toBe(true)
    expect(shouldLookup('203.0.113.5', '203.0.113.9', false)).toBe(true)
    expect(shouldLookup('203.0.113.5', '203.0.113.5', false)).toBe(false)
    expect(shouldLookup('203.0.113.5', '203.0.113.5', true)).toBe(true)
    expect(shouldLookup('', '203.0.113.5', true)).toBe(false)
  })
})

describe('IP 查詢的網址 hash', () => {
  it('#ip=<encodeURIComponent(ip)>，與 parseHash 互為反向', () => {
    expect(ipHash('203.0.113.5')).toBe('#ip=203.0.113.5')
    expect(ipHash('2001:db8::1')).toBe('#ip=2001%3Adb8%3A%3A1')
    expect(parseHash(ipHash('2001:db8::1'))).toEqual({ tab: 'ip', ip: '2001:db8::1' })
    expect(ipHash('')).toBe('#ip')
  })
})

describe('狀態碼對應原因', () => {
  it('444、403、503', () => {
    expect(reasonKeysOf({ 444: 2, 403: 1 })).toEqual(['crowdsecGuardStr.reason444', 'crowdsecGuardStr.reason403'])
    expect(reasonKeysOf({ 503: 1 })).toEqual(['crowdsecGuardStr.reason503'])
  })
})

describe('封鎖來源', () => {
  it('ASN 推送、WAF、手動、行為偵測', () => {
    expect(sourceKeyOf({ origin: 'cscli', scenario: 'nginxwebui:as-ban:AS4134' })).toBe('crowdsecGuardStr.sourceAsn')
    expect(sourceKeyOf({ origin: 'crowdsec', scenario: 'crowdsecurity/appsec-vpatch' })).toBe('crowdsecGuardStr.sourceWaf')
    expect(sourceKeyOf({ origin: 'crowdsec', scenario: 'crowdsecurity/crowdsec-appsec-outofband' })).toBe('crowdsecGuardStr.sourceWaf')
    expect(sourceKeyOf({ origin: 'cscli', scenario: 'nginxwebui:manual' })).toBe('crowdsecGuardStr.sourceManual')
    expect(sourceKeyOf({ origin: 'cscli', scenario: "manual 'ban' from 'localhost'" })).toBe('crowdsecGuardStr.sourceManual')
    expect(sourceKeyOf({ origin: 'crowdsec', scenario: 'crowdsecurity/http-probing' })).toBe('crowdsecGuardStr.sourceBehavior')
    expect(sourceKeyOf({ origin: 'CAPI', scenario: 'http:scan' })).toBe('crowdsecGuardStr.sourceCommunity')
  })
})

describe('層狀態文字', () => {
  it('每層每種狀態都有 key', () => {
    expect(layerStateKey('allowlist', 'match')).toBe('crowdsecGuardStr.allowlistMatch')
    expect(layerStateKey('geo', 'blocked')).toBe('crowdsecGuardStr.geoBlocked')
    expect(layerStateKey('geo', 'unknown')).toBe('crowdsecGuardStr.geoUnknown')
    expect(layerStateKey('denylist', 'skipped')).toBe('crowdsecGuardStr.skipped')
    expect(layerStateKey('crowdsec', 'unavailable')).toBe('crowdsecGuardStr.crowdsecUnavailable')
    expect(layerStateKey('crowdsec', 'notEnforced')).toBe('crowdsecGuardStr.crowdsecNotEnforced')
  })
  it('層狀態的標籤顏色：有封鎖紀錄但 nginx 不會擋是警告色', () => {
    expect(layerColor('crowdsec', 'notEnforced')).toBe('warning')
    expect(layerColor('crowdsec', 'blocked')).toBe('error')
    expect(layerColor('crowdsec', 'unavailable')).toBe('warning')
    expect(layerColor('crowdsec', 'none')).toBe('default')
    expect(layerColor('geo', 'unknown')).toBe('warning')
    expect(layerColor('denylist', 'match')).toBe('error')
    expect(layerColor('allowlist', 'match')).toBe('success')
    expect(layerColor('geo', 'skipped')).toBe('default')
    expect(layerColor('geo', 'allowed')).toBe('default')
  })
  it('結論', () => {
    expect(blockedByKey('none')).toBe('crowdsecGuardStr.verdictNone')
    expect(blockedByKey('allowlist')).toBe('crowdsecGuardStr.verdictAllowlist')
    expect(blockedByKey('crowdsec')).toBe('crowdsecGuardStr.verdictCrowdsec')
    expect(blockedByKey('undetermined')).toBe('crowdsecGuardStr.verdictUndetermined')
  })
  it('總結的提示樣式：沒被擋與白名單是綠色，無法完整判斷是警告，被擋是紅色', () => {
    expect(verdictType('none')).toBe('success')
    expect(verdictType('allowlist')).toBe('success')
    expect(verdictType('undetermined')).toBe('warning')
    expect(verdictType('geo')).toBe('error')
    expect(verdictType('denylist')).toBe('error')
    expect(verdictType('crowdsec')).toBe('error')
  })
})

describe('加入白名單的結果訊息', () => {
  it('新增＋CrowdSec 刪除成功：成功樣式，列出刪除筆數與要套用設定', () => {
    expect(whitelistMessage(outcome(), tr)).toEqual({
      type: 'success',
      text: 'crowdsecGuardStr.wlAdded crowdsecGuardStr.wlDeleted(2) crowdsecGuardStr.wlNeedApply',
    })
  })
  it('已在白名單內、有網段封鎖保留', () => {
    const m = whitelistMessage(outcome({ denyAllow: 'exists', deleted: 0, rangesKept: 3 }), tr)
    expect(m.type).toBe('success')
    expect(m.text).toBe('crowdsecGuardStr.wlExists crowdsecGuardStr.wlDeleted(0) crowdsecGuardStr.wlRangesKept(3) crowdsecGuardStr.wlNeedApply')
  })
  it('CrowdSec 未設定', () => {
    const m = whitelistMessage(outcome({ crowdsec: 'notConfigured', deleted: 0 }), tr)
    expect(m.type).toBe('success')
    expect(m.text).toBe('crowdsecGuardStr.wlAdded crowdsecGuardStr.wlCrowdsecNotConfigured crowdsecGuardStr.wlNeedApply')
  })
  it('CrowdSec 刪除失敗依錯誤碼選文案，樣式是警告', () => {
    const m = whitelistMessage(outcome({ crowdsec: 'failed', error: 'crowdsec_machine_unauthorized' }), tr)
    expect(m.type).toBe('warning')
    expect(m.text).toBe('crowdsecGuardStr.wlAdded crowdsecStr.machineInvalid crowdsecGuardStr.wlNeedApply')
    expect(whitelistMessage(outcome({ crowdsec: 'failed', error: 'crowdsec_lapi_unavailable' }), tr).text).toContain('crowdsecStr.statusUnreachable')
    expect(whitelistMessage(outcome({ crowdsec: 'failed', error: 'crowdsec_error' }), tr).text).toContain('crowdsecGuardStr.wlCrowdsecFailed')
    expect(whitelistMessage(outcome({ crowdsec: 'failed' }), tr).text).toContain('crowdsecGuardStr.wlCrowdsecFailed')
  })
  it('黑名單有這個 IP 的精確行：警告樣式並列出名單名稱', () => {
    const m = whitelistMessage(outcome({ denyConflicts: ['bad ips', 'other'] }), tr)
    expect(m.type).toBe('warning')
    expect(m.text).toBe('crowdsecGuardStr.wlAdded crowdsecGuardStr.wlDeleted(2) crowdsecGuardStr.wlDenyConflict(bad ips、other) crowdsecGuardStr.wlNeedApply')
  })
  it('沒有 denyConflicts 欄位（後端省略空值）當作沒有衝突', () => {
    expect(whitelistMessage(outcome({ denyConflicts: null }), tr).type).toBe('success')
    expect(whitelistMessage(outcome({ denyConflicts: [] }), tr).text).not.toContain('wlDenyConflict')
  })
})

describe('CrowdSec 錯誤碼對應文案', () => {
  it('管理憑證無效、連不上、其他', () => {
    expect(crowdsecErrorKey('crowdsec_machine_unauthorized')).toBe('crowdsecStr.machineInvalid')
    expect(crowdsecErrorKey('crowdsec_lapi_unavailable')).toBe('crowdsecStr.statusUnreachable')
    expect(crowdsecErrorKey('crowdsec_error')).toBe('crowdsecGuardStr.wlCrowdsecFailed')
    expect(crowdsecErrorKey(undefined)).toBe('crowdsecGuardStr.wlCrowdsecFailed')
    expect(crowdsecErrorKey(null)).toBe('crowdsecGuardStr.wlCrowdsecFailed')
  })
})

/** 可以從外部決定何時成功或失敗的 Promise，用來模擬回應先後順序 */
function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('只採用最後一次請求的結果', () => {
  it('較早發出、較晚回來的成功回應不覆蓋新結果', async () => {
    const latest = createLatest()
    const applied: Outcome<string>[] = []
    const first = deferred<string>()
    const second = deferred<string>()
    const p1 = latest(() => first.promise, (o) => applied.push(o))
    const p2 = latest(() => second.promise, (o) => applied.push(o))
    second.resolve('new')
    expect(await p2).toBe(true)
    first.resolve('old')
    expect(await p1).toBe(false)
    expect(applied).toEqual([{ ok: true, value: 'new' }])
  })

  it('新請求失敗時，較晚回來的舊成功回應也不覆蓋', async () => {
    const latest = createLatest()
    const applied: Outcome<string>[] = []
    const first = deferred<string>()
    const second = deferred<string>()
    const p1 = latest(() => first.promise, (o) => applied.push(o))
    const p2 = latest(() => second.promise, (o) => applied.push(o))
    const boom = new Error('boom')
    second.reject(boom)
    await p2
    first.resolve('old')
    await p1
    expect(applied).toEqual([{ ok: false, error: boom }])
  })

  it('較早請求的失敗晚到時也丟棄', async () => {
    const latest = createLatest()
    const applied: Outcome<string>[] = []
    const first = deferred<string>()
    const second = deferred<string>()
    const p1 = latest(() => first.promise, (o) => applied.push(o))
    const p2 = latest(() => second.promise, (o) => applied.push(o))
    second.resolve('new')
    await p2
    first.reject(new Error('late'))
    expect(await p1).toBe(false)
    expect(applied).toEqual([{ ok: true, value: 'new' }])
  })

  it('依序完成的請求各自採用；請求本身同步丟例外也當成失敗', async () => {
    const latest = createLatest()
    const applied: Outcome<number>[] = []
    await latest(() => Promise.resolve(1), (o) => applied.push(o))
    await latest(() => Promise.resolve(2), (o) => applied.push(o))
    const err = new Error('sync')
    await latest<number>(
      () => {
        throw err
      },
      (o) => applied.push(o),
    )
    expect(applied).toEqual([
      { ok: true, value: 1 },
      { ok: true, value: 2 },
      { ok: false, error: err },
    ])
  })

  it('不同元件各自建立，互不影響', async () => {
    const a = createLatest()
    const b = createLatest()
    const applied: string[] = []
    const pa = a(() => Promise.resolve('a'), (o) => o.ok && applied.push(o.value))
    const pb = b(() => Promise.resolve('b'), (o) => o.ok && applied.push(o.value))
    await Promise.all([pa, pb])
    expect(applied.sort()).toEqual(['a', 'b'])
  })
})

describe('最近被擋下的 IP：分不出站台的行', () => {
  const base = { ip: '203.0.113.5', host: 'a.example.com', count: 1, lastTime: 1, statuses: {} }
  it('有多個候選站台：列出候選站台名稱', () => {
    expect(ambiguousSiteText({ ...base, ambiguous: true, candidates: ['a.example.com (80)', 'a.example.com (8080)'] }, tr)).toBe(
      'crowdsecGuardStr.ambiguousSite(a.example.com (80)、a.example.com (8080))',
    )
  })
  it('只有一個候選或後端省略欄位：不顯示', () => {
    expect(ambiguousSiteText({ ...base, ambiguous: false }, tr)).toBe('')
    expect(ambiguousSiteText(base, tr)).toBe('')
    expect(ambiguousSiteText({ ...base, ambiguous: true, candidates: null }, tr)).toBe('')
  })
})

describe('國家層每個站台的說明文字', () => {
  it('沒有套用規則', () => {
    expect(geoSiteKeys({ scope: '1', source: 'none', hasCountries: false, blocked: false })).toEqual(['crowdsecGuardStr.geoSourceNone'])
  })
  it('規則沒有國家：不做國家封鎖', () => {
    expect(geoSiteKeys({ scope: '1', source: 'server', mode: 0, hasCountries: false, blocked: false })).toEqual([
      'crowdsecGuardStr.geoSourceServer',
      'crowdsecGuardStr.geoNoCountries',
    ])
  })
  it('套用規則：來源、模式、結果', () => {
    expect(geoSiteKeys({ scope: '1', source: 'global', mode: 0, hasCountries: true, blocked: true })).toEqual([
      'crowdsecGuardStr.geoSourceGlobal',
      'crowdsecGuardStr.geoModeAllow',
      'crowdsecGuardStr.geoBlocked',
    ])
    expect(geoSiteKeys({ scope: '1', source: 'server', mode: 1, hasCountries: true, blocked: false })).toEqual([
      'crowdsecGuardStr.geoSourceServer',
      'crowdsecGuardStr.geoModeDeny',
      'crowdsecGuardStr.geoAllowed',
    ])
  })
  it('GeoIP 資料庫不可用：後端省略 blocked，顯示查不到國家', () => {
    expect(geoSiteKeys({ scope: '1', source: 'global', mode: 0, hasCountries: true })).toEqual([
      'crowdsecGuardStr.geoSourceGlobal',
      'crowdsecGuardStr.geoModeAllow',
      'crowdsecGuardStr.geoUnknown',
    ])
  })
})
