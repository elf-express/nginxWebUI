import type { WhitelistOutcome } from './api'

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

export type VerdictType = 'success' | 'warning' | 'error'

/** 總結提示的樣式：沒被擋與白名單放行是綠色，有層查不到結果（undetermined）是警告，被某層擋下是紅色 */
export function verdictType(blockedBy: string): VerdictType {
  if (blockedBy === 'none' || blockedBy === 'allowlist') {
    return 'success'
  }
  return blockedBy === 'undetermined' ? 'warning' : 'error'
}

/** CrowdSec 刪除失敗的錯誤碼對應文案：管理憑證被拒、連不上，其他用通用文案 */
export function crowdsecErrorKey(error: string | null | undefined): string {
  if (error === 'crowdsec_machine_unauthorized') {
    return 'crowdsecStr.machineInvalid'
  }
  if (error === 'crowdsec_lapi_unavailable') {
    return 'crowdsecStr.statusUnreachable'
  }
  return 'crowdsecGuardStr.wlCrowdsecFailed'
}

export type Translate = (key: string, ...args: (string | number)[]) => string

/** 「加入白名單」成功後的訊息：IP 查詢與封鎖名單兩個分頁共用 */
export function whitelistMessage(o: WhitelistOutcome, tr: Translate): { type: 'success' | 'warning'; text: string } {
  const parts = [tr(o.denyAllow === 'added' ? 'crowdsecGuardStr.wlAdded' : 'crowdsecGuardStr.wlExists')]
  if (o.crowdsec === 'ok') {
    parts.push(tr('crowdsecGuardStr.wlDeleted', o.deleted))
    if (o.rangesKept > 0) {
      parts.push(tr('crowdsecGuardStr.wlRangesKept', o.rangesKept))
    }
  } else if (o.crowdsec === 'failed') {
    parts.push(tr(crowdsecErrorKey(o.error)))
  } else {
    parts.push(tr('crowdsecGuardStr.wlCrowdsecNotConfigured'))
  }
  const conflicts = o.denyConflicts ?? []
  if (conflicts.length > 0) {
    parts.push(tr('crowdsecGuardStr.wlDenyConflict', conflicts.join('、')))
  }
  parts.push(tr('crowdsecGuardStr.wlNeedApply'))
  return { type: o.crowdsec === 'failed' || conflicts.length > 0 ? 'warning' : 'success', text: parts.join(' ') }
}

export function formatTime(ms: number | null | undefined): string {
  if (!ms) {
    return '—'
  }
  const d = new Date(ms)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}
