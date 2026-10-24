import type { BlockedRow, GeoSite, WafStatus, WafSyncResult, WhitelistOutcome } from './api'

export type TabKey = 'ip' | 'bans' | 'waf' | 'conn'
export const TABS: TabKey[] = ['ip', 'bans', 'waf', 'conn']

/** #ip=<IP>、#bans、#waf、#conn；其他一律回 IP 查詢 */
export function parseHash(hash: string): { tab: TabKey; ip: string } {
  const text = hash.replace(/^#/, '')
  const [key, value = ''] = text.split('=', 2)
  const tab = (TABS as string[]).includes(key) ? (key as TabKey) : 'ip'
  return { tab, ip: tab === 'ip' ? safeDecode(value) : '' }
}

/** IP 查詢分頁的網址 hash；ip 為空時只有分頁 */
export function ipHash(ip: string): string {
  return ip ? `#ip=${encodeURIComponent(ip)}` : '#ip'
}

/**
 * IP 查詢分頁要不要發查詢：ip 與畫面上的不同時查；forced（其他分頁的「查這個 IP」）時同一個 IP 也重查。
 * ip 為空不查。
 */
export function shouldLookup(ip: string, shown: string, forced: boolean): boolean {
  return !!ip && (forced || ip !== shown)
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

const BLOCKED_STATES = ['blocked', 'match']

/** 層狀態標籤的顏色：白名單命中綠色、擋下紅色；查不到、無法查詢、有封鎖紀錄但 nginx 不會擋是警告色 */
export function layerColor(layer: LayerName, state: string): string {
  if (state === 'skipped' || state === 'none' || state === 'allowed') {
    return 'default'
  }
  if (layer === 'allowlist') {
    return 'success'
  }
  if (state === 'unknown' || state === 'unavailable' || state === 'notEnforced') {
    return 'warning'
  }
  return BLOCKED_STATES.includes(state) ? 'error' : 'default'
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

export type Outcome<T> = { ok: true; value: T } | { ok: false; error: unknown }

/**
 * 同一個畫面連續發請求時只採用最後一次的結果。回傳的函式每呼叫一次就是一次新請求：
 * 只有在這段期間沒有更新的請求時才呼叫 apply（成功或失敗都一樣），否則丟棄並回 false。
 * loading 之類的狀態放在 apply 裡結束，就只會由最後一次請求結束。
 */
export function createLatest() {
  let seq = 0
  return async function latest<T>(request: () => Promise<T>, apply: (o: Outcome<T>) => void): Promise<boolean> {
    const mine = ++seq
    let outcome: Outcome<T>
    try {
      outcome = { ok: true, value: await request() }
    } catch (error) {
      outcome = { ok: false, error }
    }
    if (mine !== seq) {
      return false
    }
    apply(outcome)
    return true
  }
}

const GEO_SOURCE: Record<GeoSite['source'], string> = {
  server: 'crowdsecGuardStr.geoSourceServer',
  global: 'crowdsecGuardStr.geoSourceGlobal',
  none: 'crowdsecGuardStr.geoSourceNone',
}

/** 國家層一個站台的說明：套用來源、模式、是否封鎖；blocked 省略表示 GeoIP 資料庫不可用、判斷不了 */
export function geoSiteKeys(d: GeoSite): string[] {
  if (d.source === 'none') {
    return [GEO_SOURCE.none]
  }
  if (!d.hasCountries) {
    return [GEO_SOURCE[d.source], 'crowdsecGuardStr.geoNoCountries']
  }
  const mode = d.mode === 0 ? 'crowdsecGuardStr.geoModeAllow' : 'crowdsecGuardStr.geoModeDeny'
  const result = d.blocked == null ? 'crowdsecGuardStr.geoUnknown' : d.blocked ? 'crowdsecGuardStr.geoBlocked' : 'crowdsecGuardStr.geoAllowed'
  return [GEO_SOURCE[d.source], mode, result]
}

/** 「最近被擋下的 IP」站台欄的補充：host 可能屬於多個站台時列出候選站台，其他行回空字串 */
export function ambiguousSiteText(row: BlockedRow, tr: Translate): string {
  const names = row.candidates ?? []
  return row.ambiguous && names.length > 0 ? tr('crowdsecGuardStr.ambiguousSite', names.join('、')) : ''
}

export function formatTime(ms: number | null | undefined): string {
  if (!ms) {
    return '—'
  }
  const d = new Date(ms)
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** WAF 事件分頁的期間選項（天）；0 表示保留期內全部 */
export const WAF_DAYS = [1, 7, 30, 0]
/** IP 查詢列出的最近 WAF 事件筆數（與後端 CrowdSecGuardController.RECENT_WAF_EVENTS 相同） */
export const WAF_RECENT_LIMIT = 10

export function wafCategoryKey(code: string): string {
  return `crowdsecGuardStr.wafCat_${code.replace(/-/g, '_')}`
}

export function wafActionKey(action: string): string {
  return action === 'block' ? 'crowdsecGuardStr.wafActionBlock' : 'crowdsecGuardStr.wafActionLog'
}

export function wafActionColor(action: string): string {
  return action === 'block' ? 'error' : 'default'
}

/** 規則顯示名稱：CRS 規則編號前加 CRS，vpatch 去掉 crowdsecurity/ 前綴 */
export function wafRuleLabel(r: { ruleName: string; ruleSource: string }): string {
  return r.ruleSource === 'crs' ? `CRS ${r.ruleName}` : r.ruleName.replace(/^crowdsecurity\//, '')
}

export function retentionValid(days: number | null | undefined): days is number {
  return typeof days === 'number' && Number.isInteger(days) && days >= 1 && days <= 365
}

/** 同步失敗的錯誤碼對應文案：管理憑證被拒、連不上、沒有 machine 憑證，其他用通用文案 */
export function wafErrorKey(code: string | null | undefined): string {
  if (code === 'crowdsec_machine_unauthorized') {
    return 'crowdsecStr.machineInvalid'
  }
  if (code === 'crowdsec_lapi_unavailable') {
    return 'crowdsecStr.statusUnreachable'
  }
  if (code === 'notConfigured.machine') {
    return 'crowdsecGuardStr.wafNotConfigured'
  }
  return 'crowdsecGuardStr.wafSyncFailed'
}

export interface WafNotice {
  key: 'notConfigured' | 'error' | 'gap'
  type: 'info' | 'warning' | 'error'
  text: string
}

/** WAF 事件分頁上方的提示：沒有 machine 憑證（不再提上次錯誤）、上次同步失敗、CrowdSec 已清掉部分紀錄 */
export function wafStatusNotices(s: WafStatus, tr: Translate): WafNotice[] {
  const list: WafNotice[] = []
  if (!s.configured) {
    list.push({ key: 'notConfigured', type: 'info', text: tr('crowdsecGuardStr.wafNotConfigured') })
  } else if (s.lastError) {
    list.push({ key: 'error', type: 'error', text: tr('crowdsecGuardStr.wafLastError', formatTime(s.lastErrorAt), tr(wafErrorKey(s.lastError))) })
  }
  if (s.gapFrom && s.gapTo) {
    list.push({ key: 'gap', type: 'warning', text: tr('crowdsecGuardStr.wafGap', formatTime(s.gapFrom), formatTime(s.gapTo)) })
  }
  return list
}

/** 「立即同步」的結果訊息 */
export function wafSyncMessage(r: WafSyncResult, tr: Translate): { type: 'success' | 'warning'; text: string } {
  if (r.outcome === 'ok') {
    return { type: 'success', text: tr('crowdsecGuardStr.wafSynced', r.added) }
  }
  if (r.outcome === 'busy') {
    return { type: 'warning', text: tr('crowdsecGuardStr.wafSyncBusy') }
  }
  if (r.outcome === 'notConfigured') {
    return { type: 'warning', text: tr('crowdsecGuardStr.wafNotConfigured') }
  }
  return { type: 'warning', text: tr(wafErrorKey(r.error)) }
}
