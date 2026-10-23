import { describe, expect, it } from 'vitest'
import { blockedByKey, layerStateKey, parseHash, reasonKeysOf, sourceKeyOf } from './view'

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
  })
})
