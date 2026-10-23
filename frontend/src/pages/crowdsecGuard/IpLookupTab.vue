<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Alert, Button, Descriptions, DescriptionsItem, InputSearch, Popconfirm, Space, Tag, message } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { lookupIp, whitelistIp, type LookupResult } from './api'
import BlockedIps from './BlockedIps.vue'
import { blockedByKey, layerStateKey, sourceKeyOf, verdictType, whitelistMessage } from './view'

const ip = defineModel<string>('ip', { default: '' })
// 從空字串開始：分頁第一次掛載時 ip 可能已有值（網址 hash 或其他分頁帶來），要讓 watch 立刻查
const input = ref('')
const loading = ref(false)
const data = ref<LookupResult | null>(null)
const whitelisting = ref(false)

const BLOCKED_STATES = ['blocked', 'match']
const layers = computed(() => data.value?.result.layers ?? [])
const verdict = computed(() => verdictType(data.value?.result.blockedBy ?? 'none'))

function color(layer: string, state: string): string {
  if (state === 'skipped' || state === 'none' || state === 'allowed') {
    return 'default'
  }
  if (layer === 'allowlist') {
    return 'success'
  }
  if (state === 'unknown' || state === 'unavailable') {
    return 'warning'
  }
  return BLOCKED_STATES.includes(state) ? 'error' : 'default'
}

async function run(value: string) {
  const target = value.trim()
  if (!target) {
    return
  }
  input.value = target
  ip.value = target
  loading.value = true
  try {
    data.value = await lookupIp(target)
  } catch (err) {
    data.value = null
    notifyError(err)
  } finally {
    loading.value = false
  }
}

async function addWhitelist() {
  if (!data.value) {
    return
  }
  whitelisting.value = true
  try {
    const o = await whitelistIp(data.value.result.ip)
    const m = whitelistMessage(o, t)
    // 警告訊息較長（失敗原因、黑名單衝突），多停留一會兒
    if (m.type === 'warning') {
      message.warning(m.text, 8)
    } else {
      message.success(m.text)
    }
    await run(data.value.result.ip)
  } catch (err) {
    notifyError(err)
  } finally {
    whitelisting.value = false
  }
}

function geoScope(scope: string): string {
  return scope === 'global' ? t('crowdsecGuardStr.allSites') : (data.value?.serverNames[scope] ?? scope)
}

watch(
  ip,
  (v) => {
    if (v && v !== input.value) {
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
            <Tag :color="color(l.layer, l.state)">{{ t(layerStateKey(l.layer, l.state)) }}</Tag>
            <span v-if="l.lists?.length">{{ l.lists.join('、') }}</span>
            <span v-if="l.reason" class="ip-reason">{{ t(`crowdsecGuardStr.reason_${l.reason.replace(/\./g, '_')}`) }}</span>
          </Space>
          <ul v-if="l.layer === 'geo' && l.details?.length" class="ip-detail">
            <li v-for="d in l.details" :key="d.scope">
              {{ geoScope(d.scope) }}：{{ t(d.mode === 0 ? 'crowdsecGuardStr.geoModeAllow' : 'crowdsecGuardStr.geoModeDeny') }}，
              {{ t(d.blocked ? 'crowdsecGuardStr.geoBlocked' : 'crowdsecGuardStr.geoAllowed') }}
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
</style>
