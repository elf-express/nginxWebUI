import type { WafApplyStatus, WafCategory, WafRule, WafRules, WafSource } from './api'
import { wafCategoryKey, type Translate } from './view'

export type WafScope = 'rule' | 'category'

export interface Edit {
  scope: WafScope
  ruleKey: string
  action: string
  excludedServerIds: string[]
}

export interface Change {
  scope: WafScope
  ruleKey: string
  action: string
  excludedServerIds: string
}

const ACTIONS: Record<WafSource, string[]> = { vpatch: ['block', 'log', 'off'], crs: ['count', 'log', 'off'] }

export function actionOptions(source: WafSource, scope: WafScope): string[] {
  return scope === 'rule' ? ['inherit', ...ACTIONS[source]] : [...ACTIONS[source]]
}

export function actionLabelKey(action: string): string {
  return `crowdsecWafStr.action${action.charAt(0).toUpperCase()}${action.slice(1)}`
}

/** vpatch:<產品> 顯示產品（_other 顯示「其他」）；CRS 類別（crs-sqli…）沿用 WAF 事件分頁的翻譯 */
export function categoryName(key: string, tr: Translate): string {
  if (key.startsWith('vpatch:')) {
    const product = key.slice('vpatch:'.length)
    return product === '_other' ? tr('crowdsecWafStr.catOther') : product
  }
  return tr(wafCategoryKey(key))
}

export function matches(rule: WafRule, q: string): boolean {
  const needle = q.trim().toLowerCase()
  if (!needle) {
    return true
  }
  return [rule.key, rule.label, rule.description, rule.category, ...rule.cves].some((s) => (s ?? '').toLowerCase().includes(needle))
}

/** 預設只列近 7 天有命中的規則；有搜尋字或「顯示全部」時不看命中數 */
export function visibleRules(rules: WafRule[], category: string, q: string, showAll: boolean): WafRule[] {
  const searching = q.trim() !== ''
  return rules.filter((r) => r.category === category && matches(r, q) && (showAll || searching || r.hits > 0))
}

export function visibleCategories(categories: WafCategory[], rules: WafRule[], q: string, showAll: boolean): WafCategory[] {
  return categories.filter((c) => visibleRules(rules, c.key, q, showAll).length > 0)
}

export function editKey(scope: WafScope, key: string): string {
  return `${scope}|${key}`
}

export function originalOf(scope: WafScope, row: WafRule | WafCategory): Edit {
  const fallback = scope === 'rule' ? 'inherit' : row.action
  return { scope, ruleKey: row.key, action: row.setting ?? fallback, excludedServerIds: [...row.excludedServerIds] }
}

export function currentOf(edits: Map<string, Edit>, scope: WafScope, row: WafRule | WafCategory): Edit {
  return edits.get(editKey(scope, row.key)) ?? originalOf(scope, row)
}

function same(a: Edit, b: Edit): boolean {
  const ids = (e: Edit) => [...e.excludedServerIds].sort().join(',')
  return a.action === b.action && ids(a) === ids(b)
}

/** 只回傳和原值不同的編輯 */
export function changesOf(edits: Map<string, Edit>, data: WafRules): Change[] {
  const out: Change[] = []
  for (const e of edits.values()) {
    const row = e.scope === 'rule' ? data.rules.find((r) => r.key === e.ruleKey) : data.categories.find((c) => c.key === e.ruleKey)
    if (!row || same(e, originalOf(e.scope, row))) {
      continue
    }
    out.push({ scope: e.scope, ruleKey: e.ruleKey, action: e.action, excludedServerIds: e.excludedServerIds.join(',') })
  }
  return out
}

export function statusMessage(s: WafApplyStatus, tr: Translate): { type: 'success' | 'error' | 'info' | 'warning'; text: string } | null {
  switch (s.state) {
    case 'ok':
      return { type: 'success', text: tr('crowdsecWafStr.applyOk', s.at ?? '') }
    case 'failed':
      return { type: 'error', text: tr('crowdsecWafStr.applyFailed', s.message ?? '') }
    case 'pending':
      return { type: 'info', text: tr('crowdsecWafStr.applying') }
    case 'timeout':
      return { type: 'warning', text: tr('crowdsecWafStr.applyPending') }
    default:
      return null
  }
}

/** 預設只列命中的模式：沒有搜尋字且沒開「顯示全部」 */
export function onlyHitsMode(q: string, showAll: boolean): boolean {
  return !showAll && q.trim() === ''
}

/** 載入時上一次套用還在等結果、且這個畫面沒有在輪詢，就回傳要接著輪詢的套用編號 */
export function resumeRequestId(last: WafApplyStatus, applying: boolean): string | null {
  return !applying && last.state === 'pending' && last.requestId ? last.requestId : null
}
