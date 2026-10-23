import { describe, expect, it } from 'vitest'
import { blockedByKey, crowdsecErrorKey, layerStateKey, parseHash, reasonKeysOf, sourceKeyOf, verdictType, whitelistMessage } from './view'
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
