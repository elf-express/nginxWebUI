<script setup lang="ts">
import { computed, inject, onMounted, ref } from 'vue'
import { Alert, Button, InputNumber, InputSearch, Select, Space, Table, Tag, message } from 'ant-design-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { fetchServers, fetchWafEvents, fetchWafStatus, saveWafRetention, syncWaf, type WafPage, type WafStatus } from './api'
import {
  WAF_DAYS,
  createLatest,
  formatTime,
  retentionValid,
  wafActionColor,
  wafActionKey,
  wafCategoryKey,
  wafRuleLabel,
  wafStatusNotices,
  wafSyncMessage,
} from './view'

const lookupIp = inject<(ip: string) => void>('lookupIp', () => {})
const PAGE_SIZE = 20
const servers = ref<{ id: string; name: string }[]>([])
const status = ref<WafStatus | null>(null)
const filters = ref({ serverId: '', category: '', action: '', ip: '', days: 7 })
const page = ref(1)
const loading = ref(false)
const error = ref('')
const data = ref<WafPage | null>(null)
const syncing = ref(false)
const retention = ref<number | undefined>(undefined)
const savingRetention = ref(false)

const columns = [
  { title: t('crowdsecGuardStr.wafTime'), key: 'eventTime' },
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: t('crowdsecGuardStr.site'), key: 'host' },
  { title: t('crowdsecGuardStr.wafUri'), key: 'uri' },
  { title: t('crowdsecGuardStr.wafRules'), key: 'rules' },
  { title: t('crowdsecGuardStr.wafAction'), key: 'result' },
  { title: t('crowdsecGuardStr.action'), key: 'ops' },
]

const notices = computed(() => (status.value ? wafStatusNotices(status.value, t) : []))

// 連續換篩選或換頁時，只採用最後一次請求的結果
const latest = createLatest()

async function load() {
  loading.value = true
  error.value = ''
  await latest(
    () => fetchWafEvents({ ...filters.value, page: page.value, limit: PAGE_SIZE }),
    (o) => {
      loading.value = false
      if (o.ok) {
        data.value = o.value
        return
      }
      data.value = null
      if (!(o.error instanceof SessionExpiredError)) {
        error.value = o.error instanceof Error && o.error.message ? o.error.message : t('commonStr.errorInfo')
      }
    },
  )
}

function search() {
  page.value = 1
  void load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

async function loadStatus() {
  try {
    status.value = await fetchWafStatus()
    if (retention.value === undefined) {
      retention.value = status.value.retentionDays
    }
  } catch (err) {
    notifyError(err)
  }
}

async function doSync() {
  syncing.value = true
  try {
    const m = wafSyncMessage(await syncWaf(), t)
    if (m.type === 'success') {
      message.success(m.text)
    } else {
      message.warning(m.text, 8)
    }
    await Promise.all([loadStatus(), load()])
  } catch (err) {
    notifyError(err)
  } finally {
    syncing.value = false
  }
}

async function doRetention() {
  const days = retention.value
  if (!retentionValid(days)) {
    message.warning(t('crowdsecGuardStr.wafRetentionInvalid'))
    return
  }
  savingRetention.value = true
  try {
    await saveWafRetention(days)
    message.success(t('crowdsecGuardStr.wafRetentionSaved', days))
    retention.value = days
    await loadStatus()
  } catch (err) {
    notifyError(err)
  } finally {
    savingRetention.value = false
  }
}

onMounted(async () => {
  try {
    servers.value = await fetchServers()
  } catch (err) {
    notifyError(err)
  }
  await Promise.all([loadStatus(), load()])
})
</script>

<template>
  <section class="waf" aria-labelledby="waf-title">
    <h2 id="waf-title" class="sub-title">{{ t('crowdsecGuardStr.tabWaf') }}</h2>
    <div class="waf-status">
      <Space wrap>
        <span class="waf-last-sync">
          {{ status?.lastSyncAt ? t('crowdsecGuardStr.wafLastSync', formatTime(status.lastSyncAt)) : t('crowdsecGuardStr.wafNeverSynced') }}
        </span>
        <Button class="waf-sync" :loading="syncing" @click="doSync">{{ t('crowdsecGuardStr.wafSyncNow') }}</Button>
        <label for="waf-retention">{{ t('crowdsecGuardStr.wafRetention') }}</label>
        <InputNumber
          id="waf-retention"
          v-model:value="retention"
          class="waf-retention"
          :min="1"
          :max="365"
          :precision="0"
          :aria-label="t('crowdsecGuardStr.wafRetention')"
        />
        <Button class="waf-retention-save" :loading="savingRetention" @click="doRetention">{{ t('crowdsecGuardStr.wafRetentionSave') }}</Button>
      </Space>
    </div>
    <Alert v-for="n in notices" :key="n.key" :type="n.type" show-icon :message="n.text" :class="['waf-notice', `waf-notice-${n.key}`]" />
    <div class="waf-filters">
      <Space wrap>
        <Select v-model:value="filters.serverId" class="waf-site" :aria-label="t('crowdsecGuardStr.site')" style="min-width: 200px" @change="search">
          <Select.Option value="">{{ t('crowdsecGuardStr.allSites') }}</Select.Option>
          <Select.Option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }}</Select.Option>
        </Select>
        <Select v-model:value="filters.category" class="waf-category" :aria-label="t('crowdsecGuardStr.wafCategory')" style="min-width: 180px" @change="search">
          <Select.Option value="">{{ t('crowdsecGuardStr.wafAllCategories') }}</Select.Option>
          <Select.Option v-for="c in status?.categories ?? []" :key="c" :value="c">{{ t(wafCategoryKey(c)) }}</Select.Option>
        </Select>
        <Select v-model:value="filters.action" class="waf-action-filter" :aria-label="t('crowdsecGuardStr.wafAction')" style="min-width: 140px" @change="search">
          <Select.Option value="">{{ t('crowdsecGuardStr.wafAllActions') }}</Select.Option>
          <Select.Option value="block">{{ t('crowdsecGuardStr.wafActionBlock') }}</Select.Option>
          <Select.Option value="log">{{ t('crowdsecGuardStr.wafActionLog') }}</Select.Option>
        </Select>
        <Select v-model:value="filters.days" class="waf-days" :aria-label="t('crowdsecGuardStr.wafDays')" style="min-width: 140px" @change="search">
          <Select.Option v-for="d in WAF_DAYS" :key="d" :value="d">{{ t(`crowdsecGuardStr.wafDays_${d}`) }}</Select.Option>
        </Select>
        <InputSearch
          v-model:value="filters.ip"
          class="waf-ip"
          :placeholder="t('crowdsecGuardStr.searchIp')"
          :aria-label="t('crowdsecGuardStr.searchIp')"
          @search="search"
        />
      </Space>
    </div>
    <Alert v-if="error" type="error" show-icon :message="error" class="waf-error" />
    <Table
      class="waf-table"
      :columns="columns"
      :data-source="data?.records ?? []"
      :loading="loading"
      row-key="id"
      size="small"
      :pagination="{ current: page, pageSize: PAGE_SIZE, total: data?.count ?? 0, onChange: onPage }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'eventTime'">{{ formatTime(record.eventTime) }}</template>
        <template v-else-if="column.key === 'host'">{{ record.host || '—' }}</template>
        <template v-else-if="column.key === 'uri'">
          <span class="waf-uri" :title="record.uri">{{ record.uri || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'rules'">
          <Space wrap :size="4">
            <Tag v-for="(r, i) in record.rules" :key="i" class="waf-rule">{{ wafRuleLabel(r) }}</Tag>
          </Space>
        </template>
        <template v-else-if="column.key === 'result'">
          <Tag :color="wafActionColor(record.action)" class="waf-action" :data-action="record.action">{{ t(wafActionKey(record.action)) }}</Tag>
        </template>
        <template v-else-if="column.key === 'ops'">
          <Button size="small" class="waf-lookup" @click="lookupIp(record.ip)">{{ t('crowdsecGuardStr.lookupThis') }}</Button>
        </template>
      </template>
      <template #expandedRowRender="{ record }">
        <dl class="waf-detail">
          <dt>{{ t('crowdsecGuardStr.wafMethod') }}</dt>
          <dd class="waf-detail-method">{{ record.method || '—' }}</dd>
          <dt>{{ t('crowdsecGuardStr.wafUri') }}</dt>
          <dd class="waf-detail-uri">{{ record.uri || '—' }}</dd>
          <dt>{{ t('crowdsecGuardStr.site') }}</dt>
          <dd>{{ record.host || '—' }}</dd>
          <dt>User-Agent</dt>
          <dd class="waf-detail-ua">{{ record.userAgent || '—' }}</dd>
          <dt>{{ t('crowdsecGuardStr.country') }}</dt>
          <dd>{{ record.country || '—' }}</dd>
          <dt>{{ t('crowdsecGuardStr.wafRules') }}</dt>
          <dd>
            <ul class="waf-detail-rules">
              <li v-for="(r, i) in record.rules" :key="i">
                <strong>{{ t('crowdsecGuardStr.wafRuleLine', wafRuleLabel(r), t(wafCategoryKey(r.category))) }}</strong>
                <span v-if="r.message"> — {{ r.message }}</span>
              </li>
            </ul>
          </dd>
        </dl>
      </template>
    </Table>
  </section>
</template>

<style scoped>
.sub-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px;
}
.waf-status,
.waf-filters,
.waf-notice,
.waf-error {
  margin-bottom: 8px;
}
.waf-ip {
  width: 220px;
}
.waf-uri {
  display: inline-block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
.waf-detail {
  display: grid;
  grid-template-columns: max-content 1fr;
  gap: 4px 12px;
  margin: 0;
}
.waf-detail dt {
  color: #666;
}
.waf-detail dd {
  margin: 0;
  word-break: break-all;
}
.waf-detail-rules {
  margin: 0;
  padding-left: 18px;
}
</style>
