import type { Probe, SecretSource } from './store'

export type Light = 'green' | 'orange' | 'red' | 'gray'

export const MIN_GRACE = 10
export const MAX_GRACE = 3600

/** 狀態燈：沒有位址或連不到為紅；連得到且兩組憑證都正常為綠；其餘為橘；尚未檢測為灰 */
export function lightOf(probe: Probe | null): Light {
  if (!probe) {
    return 'gray'
  }
  if (probe.lapi !== 'ok') {
    return 'red'
  }
  if (probe.bouncerKey === 'ok' && probe.machine === 'ok') {
    return 'green'
  }
  return 'orange'
}

export const BADGE: Record<Light, 'success' | 'warning' | 'error' | 'default'> = {
  green: 'success',
  orange: 'warning',
  red: 'error',
  gray: 'default',
}

/** 狀態說明的 i18n key */
export function statusKeys(probe: Probe | null): string[] {
  if (!probe) {
    return ['crowdsecStr.checking']
  }
  if (probe.lapi === 'notConfigured') {
    return ['crowdsecStr.notConfigured']
  }
  if (probe.lapi === 'unreachable') {
    return ['crowdsecStr.statusUnreachable']
  }
  const keys: string[] = []
  if (probe.bouncerKey === 'invalid') {
    keys.push('crowdsecStr.bouncerKeyInvalid')
  } else if (probe.bouncerKey === 'notConfigured') {
    keys.push('crowdsecStr.bouncerKeyNotConfigured')
  }
  if (probe.machine === 'invalid') {
    keys.push('crowdsecStr.machineInvalid')
  } else if (probe.machine === 'notConfigured') {
    keys.push('crowdsecStr.machineNotConfigured')
  }
  if ((probe.bouncerKey === 'unreachable' || probe.machine === 'unreachable') && !keys.includes('crowdsecStr.statusUnreachable')) {
    keys.push('crowdsecStr.statusUnreachable')
  }
  if (keys.length > 0) {
    return keys
  }
  // 燈不是綠時不可顯示「正常」
  return lightOf(probe) === 'green' ? ['crowdsecStr.statusOk'] : ['crowdsecStr.statusUnreachable']
}

export function sourceKey(source: SecretSource): string {
  if (source === 'SECRETS_FILE') {
    return 'crowdsecStr.sourceSecretsFile'
  }
  if (source === 'ENV') {
    return 'crowdsecStr.sourceEnv'
  }
  return 'crowdsecStr.notConfigured'
}

/** 寬限秒數：10 到 3600 的整數，其他回 null */
export function parseGrace(value: unknown): number | null {
  const n = typeof value === 'number' ? value : Number(String(value ?? '').trim())
  if (!Number.isInteger(n) || n < MIN_GRACE || n > MAX_GRACE) {
    return null
  }
  return n
}

/** epoch 秒轉本地時間 yyyy-MM-dd HH:mm:ss；0 或空值回空字串 */
export function formatEpochSeconds(seconds: number | null | undefined): string {
  if (!seconds || seconds <= 0) {
    return ''
  }
  const d = new Date(seconds * 1000)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
