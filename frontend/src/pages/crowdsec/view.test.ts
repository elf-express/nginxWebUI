import { describe, expect, it } from 'vitest'
import { formatEpochSeconds, lightOf, parseGrace, sourceKey, statusKeys } from './view'

describe('lightOf', () => {
  it('尚未檢測為灰', () => {
    expect(lightOf(null)).toBe('gray')
  })

  it('連不到或沒有位址為紅', () => {
    expect(lightOf({ lapi: 'unreachable', bouncerKey: 'unreachable', machine: 'unreachable' })).toBe('red')
    expect(lightOf({ lapi: 'notConfigured', bouncerKey: 'notConfigured', machine: 'notConfigured' })).toBe('red')
  })

  it('全部正常為綠', () => {
    expect(lightOf({ lapi: 'ok', bouncerKey: 'ok', machine: 'ok' })).toBe('green')
  })

  it('連得到但憑證無效或未設定為橘', () => {
    expect(lightOf({ lapi: 'ok', bouncerKey: 'ok', machine: 'invalid' })).toBe('orange')
    expect(lightOf({ lapi: 'ok', bouncerKey: 'notConfigured', machine: 'ok' })).toBe('orange')
  })
})

describe('statusKeys', () => {
  it('依情況回對應的 i18n key', () => {
    expect(statusKeys(null)).toEqual(['crowdsecStr.checking'])
    expect(statusKeys({ lapi: 'notConfigured', bouncerKey: 'notConfigured', machine: 'notConfigured' })).toEqual([
      'crowdsecStr.notConfigured',
    ])
    expect(statusKeys({ lapi: 'unreachable', bouncerKey: 'unreachable', machine: 'unreachable' })).toEqual([
      'crowdsecStr.statusUnreachable',
    ])
    expect(statusKeys({ lapi: 'ok', bouncerKey: 'ok', machine: 'ok' })).toEqual(['crowdsecStr.statusOk'])
    expect(statusKeys({ lapi: 'ok', bouncerKey: 'invalid', machine: 'notConfigured' })).toEqual([
      'crowdsecStr.bouncerKeyInvalid',
      'crowdsecStr.machineNotConfigured',
    ])
    expect(statusKeys({ lapi: 'ok', bouncerKey: 'notConfigured', machine: 'invalid' })).toEqual([
      'crowdsecStr.bouncerKeyNotConfigured',
      'crowdsecStr.machineInvalid',
    ])
  })
})

describe('parseGrace', () => {
  it('接受 10 到 3600 的整數', () => {
    expect(parseGrace(10)).toBe(10)
    expect(parseGrace('3600')).toBe(3600)
    expect(parseGrace(' 45 ')).toBe(45)
  })

  it('拒絕範圍外、小數與空值', () => {
    for (const value of [9, 3601, 45.5, '', null, undefined, 'abc']) {
      expect(parseGrace(value)).toBeNull()
    }
  })
})

describe('formatEpochSeconds', () => {
  it('0 或空值回空字串', () => {
    expect(formatEpochSeconds(0)).toBe('')
    expect(formatEpochSeconds(undefined)).toBe('')
    expect(formatEpochSeconds(null)).toBe('')
  })

  it('格式為本地時間 yyyy-MM-dd HH:mm:ss', () => {
    const seconds = Math.floor(new Date(2026, 9, 22, 9, 5, 7).getTime() / 1000)
    expect(formatEpochSeconds(seconds)).toBe('2026-10-22 09:05:07')
  })
})

describe('sourceKey', () => {
  it('對應來源的 i18n key', () => {
    expect(sourceKey('SECRETS_FILE')).toBe('crowdsecStr.sourceSecretsFile')
    expect(sourceKey('ENV')).toBe('crowdsecStr.sourceEnv')
    expect(sourceKey('NONE')).toBe('crowdsecStr.notConfigured')
  })
})
