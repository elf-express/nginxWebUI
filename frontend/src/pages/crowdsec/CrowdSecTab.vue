<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import {
  Alert,
  Badge,
  Button,
  ConfigProvider,
  Descriptions,
  DescriptionsItem,
  Input,
  InputNumber,
  Radio,
  RadioGroup,
  Space,
  message,
} from 'ant-design-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { useCrowdSecStore, type ChallengeMode, type ChallengeStyle, type SecretView } from './store'
import {
  BADGE,
  challengeProblemKey,
  formatEpochSeconds,
  isValidExternalUrl,
  lightOf,
  parseGrace,
  parseTtl,
  sourceKey,
  statusKeys,
} from './view'

const store = useCrowdSecStore()
const loadError = ref(false)
const grace = ref<number | string | undefined>(undefined)
const saving = ref(false)
const savingChallenge = ref(false)
const form = reactive<{ mode: ChallengeMode; style: ChallengeStyle; externalUrl: string; ttl: number | string | undefined }>({
  mode: 'builtin',
  style: 'auto',
  externalUrl: '',
  ttl: 60,
})

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }

const light = computed(() => lightOf(store.probe))
const statusText = computed(() =>
  statusKeys(store.probe)
    .map((key) => t(key))
    .join(' / '),
)
const lastOkText = computed(() => formatEpochSeconds(store.config?.state?.lastOk) || t('crowdsecStr.never'))
const challengeOff = computed(() => !store.challenge?.enabled)
// 上次套用的失敗原因在前，目前設定的問題在後；都沒有時顯示「可以套用」
const challengeStatus = computed(() => {
  const c = store.challenge
  if (!c) {
    return ''
  }
  const parts: string[] = []
  const last = challengeProblemKey(c.lastError)
  if (last) {
    parts.push(t('crowdsecStr.challengeLastApply') + t(last, c.ticketSecret.file, c.ticketSecret.env))
  }
  const now = challengeProblemKey(c.problem)
  if (now) {
    parts.push(t(now, c.ticketSecret.file, c.ticketSecret.env))
  }
  return parts.length > 0 ? parts.join(' ') : t('crowdsecStr.challengeStatusOk')
})

function secretHint(secret: SecretView): string {
  return t('crowdsecStr.secretHint', secret.file, secret.env)
}

// InputNumber 清空時會送 null，這裡統一轉成 undefined
function onGraceInput(value: unknown) {
  grace.value = typeof value === 'number' || typeof value === 'string' ? value : undefined
}

function onTtlInput(value: unknown) {
  form.ttl = typeof value === 'number' || typeof value === 'string' ? value : undefined
}

function fillChallengeForm() {
  const c = store.challenge
  if (c) {
    form.mode = c.mode
    form.style = c.style
    form.externalUrl = c.externalUrl
    form.ttl = c.ttlMinutes
  }
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
    await store.loadChallenge()
  } catch (err) {
    if (!(err instanceof SessionExpiredError)) {
      console.error(err)
      loadError.value = true
    }
    return
  }
  grace.value = store.config?.graceSeconds
  fillChallengeForm()
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

async function saveChallenge() {
  const ttl = parseTtl(form.ttl)
  if (ttl === null) {
    message.error(t('crowdsecStr.invalidTtl'))
    return
  }
  const url = form.externalUrl.trim()
  if ((form.mode === 'external' || url !== '') && !isValidExternalUrl(url)) {
    message.error(t('crowdsecStr.invalidExternalUrl'))
    return
  }
  if (form.mode === 'external' && !store.challenge?.ticketSecret.configured) {
    message.error(t('crowdsecStr.ticketSecretRequired'))
    return
  }
  savingChallenge.value = true
  try {
    await store.saveChallenge({ mode: form.mode, style: form.style, externalUrl: url, ttlMinutes: ttl })
    fillChallengeForm()
    message.success(t('crowdsecStr.saved'))
  } catch (err) {
    notifyError(err)
  } finally {
    savingChallenge.value = false
  }
}

// 內建：開 Java 產生的同一份驗證頁（不能通過）；外部驗證站：直接開外部網址
function previewChallenge() {
  const url =
    form.mode === 'external' && isValidExternalUrl(form.externalUrl)
      ? form.externalUrl.trim()
      : `/adminPage/crowdsec/challengePreview?style=${form.style}`
  window.open(url, '_blank', 'noopener')
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

    <section
      v-if="store.challenge && !loadError"
      class="crowdsec-tab crowdsec-challenge"
      aria-labelledby="crowdsec-challenge-title"
    >
      <h2 id="crowdsec-challenge-title" class="crowdsec-title">{{ t('crowdsecStr.challengeTitle') }}</h2>
      <Alert
        v-if="challengeOff"
        class="crowdsec-challenge-disabled"
        type="info"
        show-icon
        :message="t('crowdsecStr.challengeDisabled')"
      />

      <Descriptions class="crowdsec-challenge-desc" bordered size="small" :column="1">
        <DescriptionsItem :label="t('crowdsecStr.challengeMode')">
          <RadioGroup
            v-model:value="form.mode"
            class="crowdsec-challenge-mode"
            :disabled="challengeOff"
            :aria-label="t('crowdsecStr.challengeMode')"
          >
            <Radio value="builtin">{{ t('crowdsecStr.challengeModeBuiltin') }}</Radio>
            <Radio value="external">{{ t('crowdsecStr.challengeModeExternal') }}</Radio>
          </RadioGroup>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeStyle')">
          <RadioGroup
            v-model:value="form.style"
            class="crowdsec-challenge-style"
            :disabled="challengeOff"
            :aria-label="t('crowdsecStr.challengeStyle')"
          >
            <Radio value="auto">{{ t('crowdsecStr.challengeStyleAuto') }}</Radio>
            <Radio value="checkbox">{{ t('crowdsecStr.challengeStyleCheckbox') }}</Radio>
          </RadioGroup>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeExternalUrl')">
          <Input
            v-model:value="form.externalUrl"
            class="crowdsec-challenge-url"
            :disabled="challengeOff || form.mode !== 'external'"
            :aria-label="t('crowdsecStr.challengeExternalUrl')"
            placeholder="https://verify.example.workers.dev/"
          />
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeTtl')">
          <InputNumber
            :value="form.ttl"
            class="crowdsec-challenge-ttl"
            :disabled="challengeOff"
            :aria-label="t('crowdsecStr.challengeTtl')"
            @update:value="onTtlInput"
          />
          <div class="crowdsec-help">{{ t('crowdsecStr.challengeTtlHelp') }}</div>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeCookieSecret')">
          <span class="crowdsec-challenge-cookie">
            <template v-if="store.challenge.cookieSecret.configured">
              <code>{{ store.challenge.cookieSecret.masked }}</code>
              <span class="crowdsec-source">{{ t(sourceKey(store.challenge.cookieSecret.source)) }}</span>
            </template>
            <template v-else>{{ t('crowdsecStr.challengeWillGenerate') }}</template>
          </span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeTicketSecret')">
          <span class="crowdsec-challenge-ticket">
            <template v-if="store.challenge.ticketSecret.configured">
              <code>{{ store.challenge.ticketSecret.masked }}</code>
              <span class="crowdsec-source">{{ t(sourceKey(store.challenge.ticketSecret.source)) }}</span>
            </template>
            <template v-else>
              {{ t('crowdsecStr.notConfigured') }} — {{ secretHint(store.challenge.ticketSecret) }}
            </template>
          </span>
        </DescriptionsItem>

        <DescriptionsItem :label="t('crowdsecStr.challengeStatus')">
          <span class="crowdsec-challenge-status">{{ challengeStatus }}</span>
        </DescriptionsItem>
      </Descriptions>

      <Space class="crowdsec-challenge-actions" wrap>
        <Button
          type="primary"
          class="crowdsec-challenge-save"
          :disabled="challengeOff"
          :loading="savingChallenge"
          @click="saveChallenge"
        >
          {{ t('crowdsecStr.challengeSave') }}
        </Button>
        <Button class="crowdsec-challenge-preview" :disabled="challengeOff" @click="previewChallenge">
          {{ t('crowdsecStr.challengePreview') }}
        </Button>
      </Space>
      <div class="crowdsec-help">{{ t('crowdsecStr.challengeHelp') }}</div>
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
.crowdsec-challenge-disabled,
.crowdsec-challenge-actions {
  margin: 12px 0;
}
</style>
