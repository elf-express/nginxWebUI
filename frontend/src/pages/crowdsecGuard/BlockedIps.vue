<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Alert, Button, Select, Space, Table, Tag } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { fetchBlocked, fetchServers, type BlockedResult, type BlockedRow } from './api'
import { ambiguousSiteText, createLatest, formatTime, reasonKeysOf } from './view'

const emit = defineEmits<{ lookup: [ip: string] }>()
const servers = ref<{ id: string; name: string }[]>([])
const serverId = ref('')
const minutes = ref(60)
const loading = ref(false)
const data = ref<BlockedResult | null>(null)
const SPANS = [60, 360, 1440, 10080]

const columns = [
  { title: 'IP', dataIndex: 'ip', key: 'ip' },
  { title: t('crowdsecGuardStr.site'), dataIndex: 'host', key: 'host' },
  { title: t('crowdsecGuardStr.count'), dataIndex: 'count', key: 'count' },
  { title: t('crowdsecGuardStr.reason'), key: 'reason' },
  { title: t('crowdsecGuardStr.lastTime'), key: 'lastTime' },
  { title: t('crowdsecGuardStr.action'), key: 'action' },
]

// 連續切換站台或時間範圍時，只採用最後一次請求的結果
const latest = createLatest()

async function load() {
  loading.value = true
  await latest(
    () => fetchBlocked(serverId.value, minutes.value),
    (o) => {
      loading.value = false
      if (o.ok) {
        data.value = o.value
      } else {
        notifyError(o.error)
      }
    },
  )
}

onMounted(async () => {
  try {
    servers.value = await fetchServers()
  } catch (err) {
    notifyError(err)
  }
  await load()
})
</script>

<template>
  <section class="blocked" aria-labelledby="blocked-title">
    <h2 id="blocked-title" class="sub-title">{{ t('crowdsecGuardStr.blockedTitle') }}</h2>
    <Space class="blocked-filters">
      <Select v-model:value="serverId" class="blocked-site" :aria-label="t('crowdsecGuardStr.site')" style="min-width: 220px" @change="load">
        <Select.Option value="">{{ t('crowdsecGuardStr.allSites') }}</Select.Option>
        <Select.Option v-for="s in servers" :key="s.id" :value="s.id">{{ s.name }}</Select.Option>
      </Select>
      <Select v-model:value="minutes" class="blocked-span" :aria-label="t('crowdsecGuardStr.span')" style="min-width: 140px" @change="load">
        <Select.Option v-for="m in SPANS" :key="m" :value="m">{{ t(`crowdsecGuardStr.span_${m}`) }}</Select.Option>
      </Select>
      <Button :loading="loading" @click="load">{{ t('crowdsecGuardStr.refresh') }}</Button>
    </Space>
    <Alert v-if="data && !data.hostSupported" type="info" show-icon class="blocked-note" :message="t('crowdsecGuardStr.noHostColumn')" />
    <p v-if="data && data.coverFrom != null" class="blocked-cover">
      {{ t('crowdsecGuardStr.coverage', formatTime(data.coverFrom), formatTime(data.coverTo)) }}
    </p>
    <Table
      :columns="columns"
      :data-source="data?.rows ?? []"
      :loading="loading"
      :row-key="(r: BlockedRow) => r.ip + (r.host ?? '')"
      size="small"
      :pagination="{ pageSize: 20 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'host'">
          {{ record.host ?? '—' }}
          <div v-if="ambiguousSiteText(record as BlockedRow, t)" class="blocked-ambiguous">{{ ambiguousSiteText(record as BlockedRow, t) }}</div>
        </template>
        <template v-else-if="column.key === 'reason'">
          <Tag v-for="k in reasonKeysOf(record.statuses)" :key="k">{{ t(k) }}</Tag>
        </template>
        <template v-else-if="column.key === 'lastTime'">{{ formatTime(record.lastTime) }}</template>
        <template v-else-if="column.key === 'action'">
          <Button size="small" class="blocked-lookup" @click="emit('lookup', record.ip)">{{ t('crowdsecGuardStr.lookupThis') }}</Button>
        </template>
      </template>
    </Table>
  </section>
</template>

<style scoped>
.blocked {
  margin-top: 24px;
}
.sub-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px;
}
.blocked-filters,
.blocked-note {
  margin-bottom: 8px;
}
.blocked-cover {
  color: #888;
  font-size: 12px;
}
.blocked-ambiguous {
  color: #d48806;
  font-size: 12px;
}
</style>
