import { get, post } from '../../shared/http'

const BASE = '/adminPage/crowdsecGuard'

// 後端序列化會省略值為 null 的欄位，所以可能為 null 的欄位一律可選，判斷用 == null 或 ??

/** 國家層一個啟用的 HTTP 站台：scope 是站台 id，source 是實際套用的規則來源 */
export interface GeoSite {
  scope: string
  source: 'server' | 'global' | 'none'
  mode?: number | null
  hasCountries: boolean
  /** GeoIP 資料庫不可用、判斷不了時省略 */
  blocked?: boolean | null
}

export interface Layer {
  layer: 'allowlist' | 'geo' | 'denylist' | 'crowdsec'
  state: string
  lists: string[]
  details: GeoSite[]
  decisions: Decision[]
  reason?: string | null
}

export interface Decision {
  id: number
  type: string
  scope: string
  value: string
  origin: string
  scenario: string
  duration: string
  createdAt?: number | null
}

export interface LookupResult {
  result: {
    ip: string
    country?: string | null
    asn?: { number: number; org: string } | null
    layers: Layer[]
    blockedBy: string
  }
  serverNames: Record<string, string>
  /** 這個 IP 最近的 WAF 事件（最多 10 筆，新到舊） */
  wafEvents?: WafEvent[] | null
}

export interface BlockedRow {
  ip: string
  host?: string | null
  count: number
  lastTime: number
  statuses: Record<string, number>
  /** host 在同一優先層級有多個候選站台（例如不同埠的同名站台），分不出是哪一個 */
  ambiguous?: boolean
  /** 候選站台名稱；不是 ambiguous 時後端省略 */
  candidates?: string[] | null
}

export interface BlockedResult {
  rows: BlockedRow[]
  hostSupported: boolean
  coverFrom?: number | null
  coverTo?: number | null
  truncated: boolean
}

export interface BansPage {
  records: Decision[]
  count: number
  createdAvailable: boolean
}

export interface WhitelistOutcome {
  denyAllow: 'added' | 'exists'
  crowdsec: 'ok' | 'notConfigured' | 'failed'
  deleted: number
  rangesKept: number
  error?: string | null
  /** 有一行剛好是這個 IP 的黑名單名稱；沒有時後端省略 */
  denyConflicts?: string[] | null
}

export const lookupIp = (ip: string) => get<LookupResult>(`${BASE}/ipLookup`, { ip })
export const fetchServers = () => get<{ id: string; name: string }[]>(`${BASE}/servers`)
export const fetchBlocked = (serverId: string, minutes: number) => get<BlockedResult>(`${BASE}/blocked`, { serverId, minutes })
export const fetchBans = (scope: 'local' | 'community', q: string, page: number, limit: number) =>
  get<BansPage>(`${BASE}/bans`, { scope, q, page, limit })
export const banIp = (ip: string, duration: string, reason: string) => post(`${BASE}/ban`, { ip, duration, reason })
export const unban = (decisionId: number, value: string) => post(`${BASE}/unban`, { decisionId, value })
export const whitelistIp = (ip: string) => post<WhitelistOutcome>(`${BASE}/whitelist`, { ip })

export interface WafRule {
  ruleName: string
  ruleSource: 'vpatch' | 'crs' | string
  category: string
  message?: string | null
}

export interface WafEvent {
  id: string
  eventTime: number
  ip: string
  country?: string | null
  host?: string | null
  method?: string | null
  uri?: string | null
  userAgent?: string | null
  ruleSource: string
  action: 'block' | 'log' | string
  message?: string | null
  rules: WafRule[]
}

export interface WafPage {
  records: WafEvent[]
  count: number
}

export interface WafStatus {
  configured: boolean
  retentionDays: number
  lastSyncAt?: number | null
  syncedUntil?: number | null
  lastError?: string | null
  lastErrorAt?: number | null
  /** CrowdSec 已清掉、可能漏抄的期間；沒有缺口時省略 */
  gapFrom?: number | null
  gapTo?: number | null
  categories: string[]
}

export interface WafSyncResult {
  outcome: 'ok' | 'notConfigured' | 'busy' | 'failed'
  added: number
  error?: string | null
}

/** days 為 0 表示保留期內全部 */
export type WafQuery = {
  serverId: string
  category: string
  action: string
  ip: string
  days: number
  page: number
  limit: number
}

const WAF = `${BASE}/waf`
export const fetchWafEvents = (q: WafQuery) => get<WafPage>(`${WAF}/events`, q)
export const fetchWafStatus = () => get<WafStatus>(`${WAF}/status`)
export const syncWaf = () => post<WafSyncResult>(`${WAF}/sync`)
export const saveWafRetention = (days: number) => post(`${WAF}/retention`, { days })
