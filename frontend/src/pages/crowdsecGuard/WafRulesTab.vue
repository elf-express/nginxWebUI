<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Alert, Button, InputSearch, Select, Space, Switch, Table, Tag, message } from 'ant-design-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { applyWaf, fetchServers, fetchWafApplyStatus, fetchWafRules, type WafApplyStatus, type WafCategory, type WafRule, type WafRules } from './api'
import { actionLabelKey, actionOptions, categoryName, changesOf, currentOf, editKey, onlyHitsMode, resumeRequestId, statusMessage, visibleCategories, visibleRules, type Edit, type WafScope } from './wafRules'

const props = defineProps<{ focus: string; focusSeq: number }>()

const POLL_MS = 2000
const POLL_MAX = 15

const data = ref<WafRules | null>(null)
const servers = ref<{ id: string; name: string }[]>([])
const loading = ref(false)
const error = ref('')
const q = ref('')
const showAll = ref(false)
const expanded = ref<string[]>([])
const edits = ref(new Map<string, Edit>())
const applying = ref(false)
const status = ref<WafApplyStatus | null>(null)
let timer: ReturnType<typeof setTimeout> | undefined
// 分頁卸載後不再輪詢、不再更新畫面
let disposed = false

const changes = computed(() => (data.value ? changesOf(edits.value, data.value) : []))
const serverOptions = computed(() => servers.value.map((s) => ({ value: s.id, label: s.name })))
const sections = computed(() => {
  const d = data.value
  if (!d) {
    return []
  }
  return (['vpatch', 'crs'] as const).map((source) => ({
    source,
    categories: visibleCategories(d.categories.filter((c) => c.source === source), d.rules, q.value, showAll.value),
  }))
})
const statusMsg = computed(() => (status.value ? statusMessage(status.value, t) : null))
// 只列命中且沒有搜尋字時才用「近 7 天沒有命中」的空狀態，其餘用 ant 預設
const tableLocale = computed(() => (onlyHitsMode(q.value, showAll.value) ? { emptyText: t('crowdsecWafStr.emptyHits') } : undefined))

const categoryColumns = [
  { title: t('crowdsecWafStr.category'), key: 'name' },
  { title: t('crowdsecWafStr.ruleCount'), dataIndex: 'ruleCount', key: 'ruleCount', width: 90 },
  { title: t('crowdsecWafStr.hits'), dataIndex: 'hits', key: 'hits', width: 110 },
  { title: t('crowdsecWafStr.action'), key: 'action', width: 210 },
  { title: t('crowdsecWafStr.exclude'), key: 'exclude', width: 260 },
]
const ruleColumns = [
  { title: t('crowdsecWafStr.rule'), key: 'rule' },
  { title: t('crowdsecWafStr.hits'), dataIndex: 'hits', key: 'hits', width: 110 },
  { title: t('crowdsecWafStr.action'), key: 'action', width: 210 },
  { title: t('crowdsecWafStr.exclude'), key: 'exclude', width: 260 },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    const [rules, list] = await Promise.all([fetchWafRules(), fetchServers()])
    if (disposed) {
      return
    }
    data.value = rules
    servers.value = list
    edits.value = new Map()
    if (!applying.value) {
      status.value = rules.last.state === 'none' ? null : rules.last
    }
    // 重新進入分頁時，上一次套用還在等結果就接著輪詢
    const resumeId = resumeRequestId(rules.last, applying.value)
    if (resumeId) {
      applying.value = true
      poll(resumeId, 0)
    }
  } catch (err) {
    if (!(err instanceof SessionExpiredError)) {
      error.value = err instanceof Error && err.message ? err.message : t('commonStr.errorInfo')
    }
  } finally {
    loading.value = false
  }
}

/** 「誤判？調整規則」或網址 #rules=<規則>：搜尋這條規則、顯示全部、展開它的分類 */
function applyFocus() {
  const d = data.value
  if (!props.focus || !d) {
    return
  }
  const rule = d.rules.find((r) => r.key === props.focus)
  if (!rule) {
    message.warning(t('crowdsecWafStr.ruleNotFound', props.focus))
    return
  }
  q.value = rule.key
  showAll.value = true
  expanded.value = [rule.category]
}

// 表格插槽的 record 型別是 Record<string, any>，這裡收 unknown 再轉回列型別
type Row = WafRule | WafCategory

function current(scope: WafScope, row: unknown): Edit {
  return currentOf(edits.value, scope, row as Row)
}

function update(scope: WafScope, row: unknown, patch: Partial<Edit>) {
  const next = new Map(edits.value)
  next.set(editKey(scope, (row as Row).key), { ...current(scope, row), ...patch })
  edits.value = next
}

function categoryOf(rule: WafRule): WafCategory | undefined {
  return data.value?.categories.find((c) => c.key === rule.category)
}

function options(scope: WafScope, rowValue: unknown) {
  const row = rowValue as Row
  return actionOptions(row.source, scope).map((a) => {
    if (a !== 'inherit') {
      return { value: a, label: t(actionLabelKey(a)) }
    }
    const c = categoryOf(row as WafRule)
    return { value: a, label: t('crowdsecWafStr.actionInherit', t(actionLabelKey(c ? current('category', c).action : row.action))) }
  })
}

function skippedOf(ids: string[]): string[] {
  const map = data.value?.skippedNames ?? {}
  return ids.flatMap((id) => map[id] ?? [])
}

function rowName(row: unknown, scope: WafScope): string {
  const key = (row as Row).key
  return scope === 'rule' ? key : categoryName(key, t)
}

async function doApply() {
  if (changes.value.length === 0) {
    message.info(t('crowdsecWafStr.noChanges'))
    return
  }
  applying.value = true
  try {
    const r = await applyWaf(JSON.stringify(changes.value))
    if (disposed) {
      return
    }
    status.value = { state: 'pending', requestId: r.requestId }
    await load()
    if (disposed) {
      return
    }
    poll(r.requestId, 0)
  } catch (err) {
    applying.value = false
    if (!disposed) {
      notifyError(err)
    }
  }
}

function poll(requestId: string, n: number) {
  timer = setTimeout(async () => {
    if (disposed) {
      return
    }
    try {
      const s = await fetchWafApplyStatus(requestId)
      if (disposed) {
        return
      }
      if (s.state === 'pending' && n + 1 < POLL_MAX) {
        poll(requestId, n + 1)
        return
      }
      status.value = s.state === 'pending' ? { ...s, state: 'timeout' } : s
    } catch (err) {
      if (disposed) {
        return
      }
      status.value = { requestId, ...status.value, state: 'timeout' }
      notifyError(err)
    }
    applying.value = false
  }, POLL_MS)
}

watch(() => props.focusSeq, applyFocus)
// 聚焦只在第一次載入完成與 focusSeq 變動時執行，套用後重新載入不重設搜尋與展開
onMounted(async () => {
  await load()
  if (!disposed) {
    applyFocus()
  }
})
onBeforeUnmount(() => {
  disposed = true
  clearTimeout(timer)
})
</script>

<template>
  <section class="waf-rules" aria-labelledby="waf-rules-title">
    <h2 id="waf-rules-title" class="sub-title">{{ t('crowdsecGuardStr.tabRules') }}</h2>
    <p class="waf-intro">{{ t('crowdsecWafStr.intro') }}</p>
    <p class="waf-help">{{ t('crowdsecWafStr.actionHelp') }}</p>
    <Alert v-if="error" type="error" show-icon :message="error" class="waf-error" />
    <Alert v-else-if="data && !data.available" type="warning" show-icon class="waf-no-catalog"
      :message="t('crowdsecWafStr.noCatalog')" :description="t('crowdsecWafStr.noCatalogHint')" />
    <template v-else-if="data">
      <Space wrap class="waf-bar">
        <InputSearch v-model:value="q" class="waf-search" allow-clear :placeholder="t('crowdsecWafStr.search')" :aria-label="t('crowdsecWafStr.search')" />
        <span class="waf-show-all">
          <Switch v-model:checked="showAll" class="waf-show-all-switch" :aria-label="t('crowdsecWafStr.showAll')" />
          {{ t('crowdsecWafStr.showAll') }}
        </span>
        <Button type="primary" class="waf-apply" :loading="applying" @click="doApply">{{ t('crowdsecWafStr.apply') }}</Button>
        <span v-if="changes.length > 0" class="waf-changes">{{ t('crowdsecWafStr.changes', changes.length) }}</span>
      </Space>
      <p class="waf-catalog">{{ t('crowdsecWafStr.catalogInfo', data.generatedAt ?? '—', data.crowdsec ?? '—') }}</p>
      <p v-if="onlyHitsMode(q, showAll)" class="waf-only-hits">{{ t('crowdsecWafStr.onlyHits') }}</p>
      <Alert v-if="statusMsg" :type="statusMsg.type" show-icon :message="statusMsg.text" class="waf-status" />
      <div v-for="sec in sections" :key="sec.source" class="waf-section" :data-source="sec.source">
        <h3 class="waf-section-title">{{ t(sec.source === 'vpatch' ? 'crowdsecWafStr.sourceVpatch' : 'crowdsecWafStr.sourceCrs') }}</h3>
        <Table v-model:expanded-row-keys="expanded" :columns="categoryColumns" :data-source="sec.categories" row-key="key"
          size="small" :pagination="false" :loading="loading" :locale="tableLocale">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">{{ categoryName(record.key, t) }}</template>
            <template v-else-if="column.key === 'action'">
              <Select class="waf-cat-action" size="small" style="width: 190px" :value="current('category', record).action"
                :options="options('category', record)" :aria-label="t('crowdsecWafStr.actionOf', rowName(record, 'category'))"
                @change="(v: unknown) => update('category', record, { action: v as string })" />
            </template>
            <template v-else-if="column.key === 'exclude'">
              <Select class="waf-cat-exclude" size="small" mode="multiple" style="width: 240px" :options="serverOptions"
                :value="current('category', record).excludedServerIds" :aria-label="t('crowdsecWafStr.excludeOf', rowName(record, 'category'))"
                @change="(v: unknown) => update('category', record, { excludedServerIds: v as string[] })" />
            </template>
          </template>
          <template #expandedRowRender="{ record: catRow }">
            <Table class="waf-rule-table" :columns="ruleColumns" :data-source="visibleRules(data.rules, catRow.key, q, showAll)"
              row-key="key" size="small" :pagination="false">
              <template #bodyCell="{ column, record: rule }">
                <template v-if="column.key === 'rule'">
                  <div class="waf-rule-key">{{ rule.key }}</div>
                  <div class="waf-rule-label">{{ rule.label }}</div>
                  <div v-if="rule.description" class="waf-rule-desc">{{ rule.description }}</div>
                  <Tag v-for="c in rule.cves" :key="c" class="waf-cve">{{ c }}</Tag>
                </template>
                <template v-else-if="column.key === 'action'">
                  <Select class="waf-rule-action" size="small" style="width: 190px" :value="current('rule', rule).action"
                    :options="options('rule', rule)" :aria-label="t('crowdsecWafStr.actionOf', rule.key)"
                    @change="(v: unknown) => update('rule', rule, { action: v as string })" />
                </template>
                <template v-else-if="column.key === 'exclude'">
                  <Select class="waf-rule-exclude" size="small" mode="multiple" style="width: 240px" :options="serverOptions"
                    :value="current('rule', rule).excludedServerIds" :aria-label="t('crowdsecWafStr.excludeOf', rule.key)"
                    @change="(v: unknown) => update('rule', rule, { excludedServerIds: v as string[] })" />
                  <div v-if="skippedOf(current('rule', rule).excludedServerIds).length > 0" class="waf-skipped">
                    {{ t('crowdsecWafStr.skippedNames', skippedOf(current('rule', rule).excludedServerIds).join(' ')) }}
                  </div>
                </template>
              </template>
            </Table>
          </template>
        </Table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.sub-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px;
}
.waf-section-title {
  font-size: 14px;
  font-weight: 500;
  margin: 16px 0 8px;
}
.waf-bar,
.waf-error,
.waf-no-catalog,
.waf-status {
  margin-bottom: 8px;
}
.waf-search {
  width: 260px;
}
.waf-intro,
.waf-help,
.waf-catalog,
.waf-only-hits,
.waf-rule-desc,
.waf-skipped {
  color: #595959;
  margin: 0 0 6px;
}
.waf-rule-key {
  font-family: monospace;
}
</style>
