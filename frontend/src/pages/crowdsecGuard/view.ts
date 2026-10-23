export type TabKey = 'ip' | 'bans' | 'conn'
export const TABS: TabKey[] = ['ip', 'bans', 'conn']

/** #ip=<IP>、#bans、#conn；其他一律回 IP 查詢 */
export function parseHash(hash: string): { tab: TabKey; ip: string } {
  const text = hash.replace(/^#/, '')
  const [key, value = ''] = text.split('=', 2)
  const tab = (TABS as string[]).includes(key) ? (key as TabKey) : 'ip'
  return { tab, ip: tab === 'ip' ? safeDecode(value) : '' }
}

function safeDecode(value: string): string {
  try {
    return decodeURIComponent(value)
  } catch {
    return ''
  }
}

const REASON: Record<string, string> = {
  '444': 'crowdsecGuardStr.reason444',
  '403': 'crowdsecGuardStr.reason403',
  '503': 'crowdsecGuardStr.reason503',
}

export function reasonKeysOf(statuses: Record<string, number>): string[] {
  return ['444', '403', '503'].filter((s) => (statuses[s] ?? 0) > 0).map((s) => REASON[s])
}

export interface DecisionLike {
  origin?: string
  scenario?: string
}

export function sourceKeyOf(d: DecisionLike): string {
  const scenario = d.scenario ?? ''
  if (d.origin === 'CAPI' || d.origin === 'lists') {
    return 'crowdsecGuardStr.sourceCommunity'
  }
  if (scenario.startsWith('nginxwebui:as-ban:')) {
    return 'crowdsecGuardStr.sourceAsn'
  }
  if (scenario.includes('appsec')) {
    return 'crowdsecGuardStr.sourceWaf'
  }
  if (d.origin === 'cscli' || scenario.startsWith('nginxwebui:')) {
    return 'crowdsecGuardStr.sourceManual'
  }
  return 'crowdsecGuardStr.sourceBehavior'
}

export type LayerName = 'allowlist' | 'geo' | 'denylist' | 'crowdsec'

export function layerStateKey(layer: LayerName, state: string): string {
  if (state === 'skipped') {
    return 'crowdsecGuardStr.skipped'
  }
  const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1)
  return `crowdsecGuardStr.${layer}${cap(state)}`
}

export function blockedByKey(blockedBy: string): string {
  const cap = blockedBy.charAt(0).toUpperCase() + blockedBy.slice(1)
  return `crowdsecGuardStr.verdict${cap}`
}

export function formatTime(ms: number | null | undefined): string {
  if (!ms) {
    return '—'
  }
  const d = new Date(ms)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
