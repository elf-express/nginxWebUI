import { describe, expect, it } from 'vitest'
import {
  challengeProblemKey,
  formatEpochSeconds,
  isValidExternalUrl,
  lightOf,
  parseGrace,
  parseTtl,
  sourceKey,
  statusKeys,
} from './view'

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

describe('statusKeys 與狀態燈一致', () => {
  it('lapi 正常但單一憑證檢測連不到：橘燈、顯示連不到、不顯示正常', () => {
    for (const probe of [
      { lapi: 'ok', bouncerKey: 'ok', machine: 'unreachable' },
      { lapi: 'ok', bouncerKey: 'unreachable', machine: 'ok' },
    ] as const) {
      expect(lightOf(probe)).toBe('orange')
      const keys = statusKeys(probe)
      expect(keys).toContain('crowdsecStr.statusUnreachable')
      expect(keys).not.toContain('crowdsecStr.statusOk')
    }
  })

  it('兩組憑證都連不到時 statusUnreachable 只出現一次', () => {
    expect(statusKeys({ lapi: 'ok', bouncerKey: 'unreachable', machine: 'unreachable' })).toEqual([
      'crowdsecStr.statusUnreachable',
    ])
  })

  it('燈不是綠時絕不回 statusOk', () => {
    const results = ['ok', 'invalid', 'unreachable', 'notConfigured', 'unchecked'] as const
    for (const lapi of results) {
      for (const bouncerKey of results) {
        for (const machine of results) {
          const probe = { lapi, bouncerKey, machine }
          if (lightOf(probe) !== 'green') {
            expect(statusKeys(probe)).not.toContain('crowdsecStr.statusOk')
          }
        }
      }
    }
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

describe('parseTtl', () => {
  it('5 到 1440 的整數才接受', () => {
    expect(parseTtl(5)).toBe(5)
    expect(parseTtl(' 1440 ')).toBe(1440)
    expect(parseTtl(4)).toBeNull()
    expect(parseTtl(1441)).toBeNull()
    expect(parseTtl(1.5)).toBeNull()
    expect(parseTtl('')).toBeNull()
    expect(parseTtl(undefined)).toBeNull()
  })
})

describe('isValidExternalUrl', () => {
  it('http(s) 完整網址才接受', () => {
    expect(isValidExternalUrl('https://verify.example.workers.dev/')).toBe(true)
    expect(isValidExternalUrl(' http://fake-ext:8090/check?site=a ')).toBe(true)
    expect(isValidExternalUrl('ftp://verify.example/')).toBe(false)
    expect(isValidExternalUrl('https://')).toBe(false)
    expect(isValidExternalUrl('https://verify.example/a b')).toBe(false)
    expect(isValidExternalUrl('https://verify.example/#x')).toBe(false)
    expect(isValidExternalUrl('javascript:alert(1)')).toBe(false)
  })
})

describe('challengeProblemKey', () => {
  it('失敗代碼對應 i18n key，沒有問題回 null', () => {
    expect(challengeProblemKey('TICKET_SECRET_MISSING')).toBe('crowdsecStr.challengeErrTicket')
    expect(challengeProblemKey('EXTERNAL_URL_MISSING')).toBe('crowdsecStr.challengeErrUrl')
    expect(challengeProblemKey('ALTCHA_MISSING')).toBe('crowdsecStr.challengeErrAltcha')
    expect(challengeProblemKey('WRITE_FAILED')).toBe('crowdsecStr.challengeErrWrite')
    expect(challengeProblemKey(null)).toBeNull()
    expect(challengeProblemKey('other')).toBeNull()
  })
})

describe('sourceKey GENERATED', () => {
  it('自動產生的金鑰顯示來源', () => {
    expect(sourceKey('GENERATED')).toBe('crowdsecStr.sourceGenerated')
  })
})
