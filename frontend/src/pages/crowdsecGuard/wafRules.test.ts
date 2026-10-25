import { describe, expect, it } from 'vitest'
import type { WafCategory, WafRule, WafRules } from './api'
import { actionLabelKey, actionOptions, categoryName, changesOf, currentOf, editKey, canApply, notApplied, onlyHitsMode, resumeRequestId, statusMessage, visibleCategories, visibleRules, visibleStatusMessage, type Edit } from './wafRules'

const tr = (k: string, ...a: (string | number)[]) => (a.length ? `${k}(${a.join(',')})` : k)

const rule = (p: Partial<WafRule>): WafRule => ({
  key: '942100', source: 'crs', category: 'crs-sqli', label: 'SQLi', description: '', cves: [], hits: 0,
  action: 'count', setting: null, excludedServerIds: [], ...p,
})
const cat = (p: Partial<WafCategory>): WafCategory => ({
  key: 'crs-sqli', source: 'crs', ruleCount: 1, hits: 0, action: 'count', setting: null, excludedServerIds: [], ...p,
})
const RULES = [
  rule({ key: '942100', hits: 2 }),
  rule({ key: '942200' }),
  rule({ key: 'crowdsecurity/vpatch-CVE-2017-9841', source: 'vpatch', category: 'vpatch:PHPUnit', label: 'PHPUnit - RCE', cves: ['CVE-2017-9841'], action: 'block' }),
]
const CATS = [cat({ key: 'vpatch:PHPUnit', source: 'vpatch', action: 'block' }), cat({ ruleCount: 2, hits: 2 })]
const DATA: WafRules = { available: true, rules: RULES, categories: CATS, skippedNames: {}, last: { state: 'none' } }

describe('wafRules', () => {
  it('依來源提供處理方式，單條多一個跟分類相同', () => {
    expect(actionOptions('vpatch', 'category')).toEqual(['block', 'log', 'off'])
    expect(actionOptions('crs', 'rule')).toEqual(['inherit', 'count', 'log', 'off'])
    expect(actionLabelKey('count')).toBe('crowdsecWafStr.actionCount')
  })

  it('分類名稱：產品直接顯示，CRS 類別與其他走翻譯', () => {
    expect(categoryName('vpatch:Atlassian Confluence', tr)).toBe('Atlassian Confluence')
    expect(categoryName('vpatch:_other', tr)).toBe('crowdsecWafStr.catOther')
    expect(categoryName('crs-sqli', tr)).toBe('crowdsecGuardStr.wafCat_crs_sqli')
    expect(categoryName('crs-other', tr)).toBe('crowdsecGuardStr.wafCat_crs_other')
  })

  it('預設只列有命中的規則；搜尋或顯示全部時不看命中數', () => {
    expect(visibleRules(RULES, 'crs-sqli', '', false).map((r) => r.key)).toEqual(['942100'])
    expect(visibleRules(RULES, 'crs-sqli', '', true).map((r) => r.key)).toEqual(['942100', '942200'])
    expect(visibleRules(RULES, 'vpatch:PHPUnit', 'cve-2017', false).map((r) => r.key)).toEqual(['crowdsecurity/vpatch-CVE-2017-9841'])
    expect(visibleCategories(CATS, RULES, '', false).map((c) => c.key)).toEqual(['crs-sqli'])
    expect(visibleCategories(CATS, RULES, 'phpunit', false).map((c) => c.key)).toEqual(['vpatch:PHPUnit'])
  })

  it('沒有編輯時取原值：單條沒存是 inherit，分類沒存是有效處理方式', () => {
    const edits = new Map<string, Edit>()
    expect(currentOf(edits, 'rule', RULES[0]).action).toBe('inherit')
    expect(currentOf(edits, 'category', CATS[0]).action).toBe('block')
  })

  it('只送出和原值不同的變更，排除站台不看順序', () => {
    const edits = new Map<string, Edit>()
    edits.set(editKey('rule', '942100'), { scope: 'rule', ruleKey: '942100', action: 'off', excludedServerIds: [] })
    edits.set(editKey('category', 'crs-sqli'), { scope: 'category', ruleKey: 'crs-sqli', action: 'count', excludedServerIds: [] })
    edits.set(editKey('rule', '942200'), { scope: 'rule', ruleKey: '942200', action: 'inherit', excludedServerIds: ['b', 'a'] })
    expect(changesOf(edits, DATA)).toEqual([
      { scope: 'rule', ruleKey: '942100', action: 'off', excludedServerIds: '' },
      { scope: 'rule', ruleKey: '942200', action: 'inherit', excludedServerIds: 'b,a' },
    ])
    const data2: WafRules = { ...DATA, rules: [rule({ key: '942200', excludedServerIds: ['a', 'b'] })] }
    const e2 = new Map<string, Edit>([[editKey('rule', '942200'), { scope: 'rule', ruleKey: '942200', action: 'inherit', excludedServerIds: ['b', 'a'] }]])
    expect(changesOf(e2, data2)).toEqual([])
  })

  it('套用狀態的訊息', () => {
    expect(statusMessage({ state: 'ok', at: 'T' }, tr)).toEqual({ type: 'success', text: 'crowdsecWafStr.applyOk(T)' })
    expect(statusMessage({ state: 'failed', message: 'bad' }, tr)).toEqual({ type: 'error', text: 'crowdsecWafStr.applyFailed(bad)' })
    expect(statusMessage({ state: 'pending' }, tr)).toEqual({ type: 'info', text: 'crowdsecWafStr.applying' })
    expect(statusMessage({ state: 'timeout' }, tr)).toEqual({ type: 'warning', text: 'crowdsecWafStr.applyPending' })
  })

  it('重新進入分頁時，只有沒在輪詢且上次套用仍在等待才接著輪詢', () => {
    expect(resumeRequestId({ state: 'pending', requestId: 'r1' }, false)).toBe('r1')
    expect(resumeRequestId({ state: 'pending', requestId: 'r1' }, true)).toBeNull()
    expect(resumeRequestId({ state: 'pending' }, false)).toBeNull()
    expect(resumeRequestId({ state: 'ok', requestId: 'r1' }, false)).toBeNull()
    expect(resumeRequestId({ state: 'none' }, false)).toBeNull()
  })

  it('設定尚未套用到 CrowdSec 時，沒有變更也能套用；已同步或沒有 inSync 時沒有變更就不送', () => {
    expect(notApplied({ ...DATA, inSync: false })).toBe(true)
    expect(notApplied({ ...DATA, inSync: true })).toBe(false)
    expect(notApplied(DATA)).toBe(false)
    expect(notApplied(null)).toBe(false)
    const change = { scope: 'rule' as const, ruleKey: '942100', action: 'off', excludedServerIds: '' }
    expect(canApply([], { ...DATA, inSync: false })).toBe(true)
    expect(canApply([], { ...DATA, inSync: true })).toBe(false)
    expect(canApply([], DATA)).toBe(false)
    expect(canApply([change], { ...DATA, inSync: true })).toBe(true)
    expect(canApply([change], null)).toBe(true)
    expect(canApply([], null)).toBe(false)
  })

  it('只列命中的空狀態只在沒有搜尋字且沒開顯示全部時使用', () => {
    expect(onlyHitsMode('', false)).toBe(true)
    expect(onlyHitsMode('  ', false)).toBe(true)
    expect(onlyHitsMode('sqli', false)).toBe(false)
    expect(onlyHitsMode('', true)).toBe(false)
  })

  it('尚未套用時不顯示上一次的套用成功，其他狀態照常顯示', () => {
    const ok = { type: 'success', text: 'ok' }
    const failed = { type: 'error', text: 'x' }
    const pending = { type: 'info', text: '...' }
    const stale = { ...DATA, inSync: false }
    const synced = { ...DATA, inSync: true }
    expect(visibleStatusMessage(ok, stale, false)).toBeNull()
    expect(visibleStatusMessage(failed, stale, false)).toBe(failed)
    expect(visibleStatusMessage(pending, stale, false)).toBe(pending)
    expect(visibleStatusMessage(ok, stale, true)).toBe(ok)
    expect(visibleStatusMessage(ok, synced, false)).toBe(ok)
    expect(visibleStatusMessage(ok, DATA, false)).toBe(ok)
    expect(visibleStatusMessage(ok, null, false)).toBe(ok)
    expect(visibleStatusMessage(null, stale, false)).toBeNull()
  })
})
