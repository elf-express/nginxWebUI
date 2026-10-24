<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Alert, Button, Descriptions, DescriptionsItem, InputSearch, Popconfirm, Space, Tag, message } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { lookupIp, whitelistIp, type LookupResult } from './api'
import BlockedIps from './BlockedIps.vue'
import {
  WAF_RECENT_LIMIT,
  blockedByKey,
  createLatest,
  formatTime,
  geoSiteKeys,
  layerColor,
  layerStateKey,
  shouldLookup,
  sourceKeyOf,
  verdictType,
  wafActionColor,
  wafActionKey,
  wafRuleLabel,
  whitelistMessage,
} from './view'

const ip = defineModel<string>('ip', { default: '' })
// refresh 每變一次就重查目前的 ip（其他分頁的「查這個 IP」，即使是同一個 IP）
const props = withDefaults(defineProps<{ refresh?: number }>(), { refresh: 0 })
// 每次送出查詢都通知外層，由外層決定要不要同步網址 hash
const emit = defineEmits<{ query: [ip: string] }>()
// 從空字串開始：分頁第一次掛載時 ip 可能已有值（網址 hash 或其他分頁帶來），要讓 watch 立刻查
const input = ref('')
const loading = ref(false)
const data = ref<LookupResult | null>(null)
const whitelisting = ref(false)

const layers = computed(() => data.value?.result.layers ?? [])
const verdict = computed(() => verdictType(data.value?.result.blockedBy ?? 'none'))
const wafEvents = computed(() => data.value?.wafEvents ?? [])

// 連續查不同 IP 時，只採用最後一次查詢的結果
const latest = createLatest()

async function run(value: string) {
  const target = value.trim()
  if (!target) {
    return
  }
  input.value = target
  ip.value = target
  emit('query', target)
  loading.value = true
  await latest(
    () => lookupIp(target),
    (o) => {
      loading.value = false
      if (o.ok) {
        data.value = o.value
      } else {
        data.value = null
        notifyError(o.error)
      }
    },
  )
}

async function addWhitelist() {
  if (!data.value) {
    return
  }
  const target = data.value.result.ip
  whitelisting.value = true
  try {
    const o = await whitelistIp(target)
    const m = whitelistMessage(o, t)
    // 警告訊息較長（失敗原因、黑名單衝突），多停留一會兒
    if (m.type === 'warning') {
      message.warning(m.text, 8)
    } else {
      message.success(m.text)
    }
    // 加白名單期間已改查別的 IP 時不重查，免得舊 IP 的結果蓋掉新的查詢
    if (ip.value === target) {
      await run(target)
    }
  } catch (err) {
    notifyError(err)
  } finally {
    whitelisting.value = false
  }
}

/** 國家層明細的站台名稱：serverNames 沒有或是空字串時顯示站台 id */
function siteName(scope: string): string {
  return data.value?.serverNames[scope] || scope
}

watch(
  [ip, () => props.refresh],
  ([v, n], old) => {
    if (shouldLookup(v, input.value, old !== undefined && n !== old[1])) {
      void run(v)
    }
  },
  { immediate: true },
)
</script>

<template>
  <section class="ip-tab" aria-labelledby="ip-title">
    <h2 id="ip-title" class="sub-title">{{ t('crowdsecGuardStr.tabIp') }}</h2>
    <InputSearch
      v-model:value="input"
      class="ip-input"
      :placeholder="t('crowdsecGuardStr.ipPlaceholder')"
      :aria-label="t('crowdsecGuardStr.ipPlaceholder')"
      :enter-button="t('crowdsecGuardStr.lookup')"
      :loading="loading"
      @search="run"
    />

    <div v-if="data" class="ip-result">
      <Alert
        class="ip-verdict"
        :type="verdict"
        :data-verdict="verdict"
        show-icon
        :message="t(blockedByKey(data.result.blockedBy), data.result.ip)"
      >
        <template #action>
          <Popconfirm v-if="data.result.blockedBy !== 'allowlist'" :title="t('crowdsecGuardStr.wlConfirm', data.result.ip)" @confirm="addWhitelist">
            <Button class="ip-whitelist" type="primary" :loading="whitelisting">{{ t('crowdsecGuardStr.addWhitelist') }}</Button>
          </Popconfirm>
        </template>
      </Alert>

      <Descriptions bordered size="small" :column="1" class="ip-meta">
        <DescriptionsItem :label="t('crowdsecGuardStr.country')">{{ data.result.country ?? '—' }}</DescriptionsItem>
        <DescriptionsItem label="ASN">
          {{ data.result.asn ? `AS${data.result.asn.number} ${data.result.asn.org}` : '—' }}
        </DescriptionsItem>
      </Descriptions>

      <ol class="ip-layers">
        <li v-for="l in layers" :key="l.layer" class="ip-layer" :data-layer="l.layer" :data-state="l.state">
          <Space wrap>
            <strong>{{ t(`crowdsecGuardStr.layer_${l.layer}`) }}</strong>
            <Tag :color="layerColor(l.layer, l.state)">{{ t(layerStateKey(l.layer, l.state)) }}</Tag>
            <span v-if="l.lists?.length">{{ l.lists.join('、') }}</span>
            <span v-if="l.reason" class="ip-reason">{{ t(`crowdsecGuardStr.reason_${l.reason.replace(/\./g, '_')}`) }}</span>
          </Space>
          <ul v-if="l.layer === 'geo' && l.details?.length" class="ip-detail">
            <li v-for="d in l.details" :key="d.scope" class="ip-geo-site" :data-source="d.source">
              {{ siteName(d.scope) }}：{{ geoSiteKeys(d).map((k) => t(k)).join('，') }}
            </li>
          </ul>
          <ul v-if="l.decisions?.length" class="ip-detail">
            <li v-for="d in l.decisions" :key="d.id">
              {{ d.type }} {{ d.value }} — {{ t(sourceKeyOf(d)) }}（{{ d.scenario }}），{{ t('crowdsecGuardStr.remaining') }} {{ d.duration }}
            </li>
          </ul>
        </li>
      </ol>
      <p class="ip-order-note">{{ t('crowdsecGuardStr.orderNote') }}</p>
      <section class="ip-waf" aria-labelledby="ip-waf-title">
        <h3 id="ip-waf-title" class="ip-waf-title">{{ t('crowdsecGuardStr.wafRecentTitle') }}</h3>
        <p v-if="!wafEvents.length" class="ip-waf-none">{{ t('crowdsecGuardStr.wafRecentNone') }}</p>
        <template v-else>
          <ul class="ip-waf-list">
            <li v-for="e in wafEvents" :key="e.id" class="ip-waf-item" :data-action="e.action">
              <Space wrap :size="4">
                <span>{{ formatTime(e.eventTime) }}</span>
                <span>{{ e.host || '—' }}</span>
                <span class="ip-waf-uri">{{ e.method || '' }} {{ e.uri || '' }}</span>
                <span>{{ e.rules.map((r) => wafRuleLabel(r)).join(', ') }}</span>
                <Tag :color="wafActionColor(e.action)">{{ t(wafActionKey(e.action)) }}</Tag>
              </Space>
            </li>
          </ul>
          <p class="ip-order-note">{{ t('crowdsecGuardStr.wafRecentNote', WAF_RECENT_LIMIT) }}</p>
        </template>
      </section>
    </div>

    <BlockedIps @lookup="run" />
  </section>
</template>

<style scoped>
.sub-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px;
}
.ip-input {
  max-width: 420px;
}
.ip-result {
  margin: 16px 0;
  max-width: 960px;
}
.ip-verdict,
.ip-meta {
  margin-bottom: 12px;
}
.ip-layers {
  padding-left: 20px;
}
.ip-layer {
  margin-bottom: 8px;
}
.ip-detail {
  color: #555;
  margin: 4px 0 0;
}
.ip-order-note {
  color: #888;
  font-size: 12px;
}
.ip-waf-title {
  font-size: 14px;
  font-weight: 500;
  margin: 16px 0 4px;
}
.ip-waf-list {
  padding-left: 20px;
  margin: 0;
}
.ip-waf-uri {
  word-break: break-all;
}
.ip-waf-none {
  color: #888;
}
</style>
