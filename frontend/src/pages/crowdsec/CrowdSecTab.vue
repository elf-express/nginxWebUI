<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Alert, Badge, Button, ConfigProvider, Descriptions, DescriptionsItem, InputNumber, Space, message } from 'ant-design-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { useCrowdSecStore, type SecretView } from './store'
import { BADGE, formatEpochSeconds, lightOf, parseGrace, sourceKey, statusKeys } from './view'

const store = useCrowdSecStore()
const loadError = ref(false)
const grace = ref<number | string | undefined>(undefined)
const saving = ref(false)

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }

const light = computed(() => lightOf(store.probe))
const statusText = computed(() =>
  statusKeys(store.probe)
    .map((key) => t(key))
    .join(' / '),
)
const lastOkText = computed(() => formatEpochSeconds(store.config?.state?.lastOk) || t('crowdsecStr.never'))

function secretHint(secret: SecretView): string {
  return t('crowdsecStr.secretHint', secret.file, secret.env)
}

// InputNumber 清空時會送 null，這裡統一轉成 undefined
function onGraceInput(value: unknown) {
  grace.value = typeof value === 'number' || typeof value === 'string' ? value : undefined
}

async function testConnection() {
  try {
    await store.test()
  } catch (err) {
    notifyError(err)
  }
}

async function reload() {
  loadError.value = false
  try {
    await store.load()
  } catch (err) {
    if (!(err instanceof SessionExpiredError)) {
      console.error(err)
      loadError.value = true
    }
    return
  }
  grace.value = store.config?.graceSeconds
  await testConnection()
}

async function saveGrace() {
  const seconds = parseGrace(grace.value)
  if (seconds === null) {
    message.error(t('crowdsecStr.invalidGrace'))
    return
  }
  saving.value = true
  try {
    await store.saveGrace(seconds)
    message.success(t('crowdsecStr.saved'))
  } catch (err) {
    notifyError(err)
  } finally {
    saving.value = false
  }
}

onMounted(reload)
</script>

<template>
  <ConfigProvider :theme="theme">
    <section class="crowdsec-tab" aria-labelledby="crowdsec-title">
      <h2 id="crowdsec-title" class="crowdsec-title">{{ t('crowdsecStr.connTitle') }}</h2>

      <Alert v-if="loadError" type="error" show-icon :message="t('spaStr.loadFailed')">
        <template #action>
          <Button size="small" @click="reload">{{ t('spaStr.reload') }}</Button>
        </template>
      </Alert>

      <Descriptions v-else-if="store.config" class="crowdsec-desc" bordered size="small" :column="1">
        <DescriptionsItem :label="t('crowdsecStr.status')">
          <Space wrap>
            <span class="crowdsec-light" :data-light="light">
              <Badge :status="BADGE[light]" :text="statusText" />
            </span>
            <Button class="crowdsec-test" :loading="store.testing" @click="testConnection">
              {{ t('crowdsecStr.testConn') }}
            </Button>
          </Space>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.lastOk')">
          <span class="crowdsec-last-ok">{{ lastOkText }}</span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.lapiUrl')">
          <span class="crowdsec-lapi">
            <template v-if="store.config.lapiUrl">{{ store.config.lapiUrl }}</template>
            <template v-else>
              {{ t('crowdsecStr.notConfigured') }} — {{ t('crowdsecStr.envHint', store.config.lapiUrlEnv) }}
            </template>
          </span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.bouncerKey')">
          <span class="crowdsec-bouncer">
            <template v-if="store.config.bouncerKey.configured">
              <code>{{ store.config.bouncerKey.masked }}</code>
              <span class="crowdsec-source">{{ t(sourceKey(store.config.bouncerKey.source)) }}</span>
            </template>
            <template v-else>{{ t('crowdsecStr.notConfigured') }} — {{ secretHint(store.config.bouncerKey) }}</template>
          </span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.machineCred')">
          <span class="crowdsec-machine">
            {{ t('crowdsecStr.machineId') }}: <code>{{ store.config.machineId }}</code>
            <template v-if="store.config.machinePassword.configured">
              <code class="crowdsec-masked">{{ store.config.machinePassword.masked }}</code>
              <span class="crowdsec-source">{{ t(sourceKey(store.config.machinePassword.source)) }}</span>
            </template>
            <template v-else>
              <span class="crowdsec-source">
                {{ t('crowdsecStr.notConfigured') }} — {{ secretHint(store.config.machinePassword) }}
              </span>
            </template>
          </span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.graceSeconds')">
          <Space wrap>
            <InputNumber
              :value="grace"
              class="crowdsec-grace"
              :aria-label="t('crowdsecStr.graceSeconds')"
              @update:value="onGraceInput"
            />
            <Button type="primary" class="crowdsec-save" :loading="saving" @click="saveGrace">
              {{ t('crowdsecStr.save') }}
            </Button>
          </Space>
          <div class="crowdsec-help">{{ t('crowdsecStr.graceHelp') }}</div>
        </DescriptionsItem>
      </Descriptions>
    </section>
  </ConfigProvider>
</template>

<style scoped>
.crowdsec-tab {
  padding: 10px 0;
  max-width: 960px;
}
.crowdsec-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 12px;
}
.crowdsec-source,
.crowdsec-masked {
  margin-left: 8px;
}
.crowdsec-source {
  color: #666;
}
.crowdsec-help {
  margin-top: 6px;
  color: #666;
  font-size: 12px;
}
</style>
