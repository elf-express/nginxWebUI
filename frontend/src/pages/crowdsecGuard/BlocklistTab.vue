<script setup lang="ts">
import { inject, onMounted, ref } from 'vue'
import { Alert, Button, Form, FormItem, Input, InputSearch, Modal, Popconfirm, RadioButton, RadioGroup, Space, Table, message } from 'ant-design-vue'
import { SessionExpiredError } from '../../shared/http'
import { t } from '../../shared/i18n'
import { notifyError } from '../../shared/notify'
import { banIp, fetchBans, unban, whitelistIp, type BansPage, type Decision } from './api'
import { formatTime, sourceKeyOf } from './view'

const lookupIp = inject<(ip: string) => void>('lookupIp', () => {})
const scope = ref<'local' | 'community'>('local')
const q = ref('')
const page = ref(1)
const loading = ref(false)
const error = ref('')
const data = ref<BansPage | null>(null)
const banOpen = ref(false)
const banForm = ref({ ip: '', duration: '4h', reason: '' })
const banning = ref(false)

const columns = [
  { title: t('crowdsecGuardStr.ipOrRange'), dataIndex: 'value', key: 'value' },
  { title: t('crowdsecGuardStr.source'), key: 'source' },
  { title: t('crowdsecGuardStr.reason'), dataIndex: 'scenario', key: 'scenario' },
  { title: t('crowdsecGuardStr.bannedAt'), key: 'createdAt' },
  { title: t('crowdsecGuardStr.remaining'), dataIndex: 'duration', key: 'duration' },
  { title: t('crowdsecGuardStr.action'), key: 'action' },
]

async function load() {
  loading.value = true
  error.value = ''
  try {
    data.value = await fetchBans(scope.value, q.value, page.value, 20)
  } catch (err) {
    data.value = null
    if (!(err instanceof SessionExpiredError)) {
      error.value = err instanceof Error && err.message ? err.message : t('commonStr.errorInfo')
    }
  } finally {
    loading.value = false
  }
}

function onScope() {
  page.value = 1
  void load()
}

function onPage(p: number) {
  page.value = p
  void load()
}

async function doUnban(d: Decision) {
  try {
    await unban(d.id, d.value)
    message.success(t('crowdsecGuardStr.unbanned'))
    await load()
  } catch (err) {
    notifyError(err)
  }
}

async function doWhitelist(d: Decision) {
  try {
    const o = await whitelistIp(d.value)
    const text = `${t(o.denyAllow === 'added' ? 'crowdsecGuardStr.wlAdded' : 'crowdsecGuardStr.wlExists')} ${t('crowdsecGuardStr.wlNeedApply')}`
    if (o.crowdsec === 'failed') {
      message.warning(`${t('crowdsecGuardStr.wlCrowdsecFailed')} ${text}`)
    } else {
      message.success(text)
    }
    await load()
  } catch (err) {
    notifyError(err)
  }
}

async function doBan() {
  banning.value = true
  try {
    await banIp(banForm.value.ip.trim(), banForm.value.duration.trim(), banForm.value.reason.trim())
    message.success(t('crowdsecGuardStr.banned'))
    banOpen.value = false
    banForm.value = { ip: '', duration: '4h', reason: '' }
    await load()
  } catch (err) {
    notifyError(err)
  } finally {
    banning.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="bans" aria-labelledby="bans-title">
    <h2 id="bans-title" class="sub-title">{{ t('crowdsecGuardStr.tabBans') }}</h2>
    <Space wrap class="bans-bar">
      <RadioGroup v-model:value="scope" button-style="solid" class="bans-scope" @change="onScope">
        <RadioButton value="local">{{ t('crowdsecGuardStr.scopeLocal') }}</RadioButton>
        <RadioButton value="community">{{ t('crowdsecGuardStr.scopeCommunity') }}</RadioButton>
      </RadioGroup>
      <InputSearch v-model:value="q" class="bans-search" :placeholder="t('crowdsecGuardStr.searchIp')" :aria-label="t('crowdsecGuardStr.searchIp')" @search="onScope" />
      <Button v-if="scope === 'local'" type="primary" class="bans-add" @click="banOpen = true">{{ t('crowdsecGuardStr.manualBan') }}</Button>
    </Space>
    <Alert v-if="error" type="error" show-icon :message="error" class="bans-error">
      <template #description><a href="#conn">{{ t('crowdsecGuardStr.goConn') }}</a></template>
    </Alert>
    <Alert v-if="scope === 'community'" type="info" show-icon :message="t('crowdsecGuardStr.communityNote')" class="bans-note" />
    <Table
      :columns="columns"
      :data-source="data?.records ?? []"
      :loading="loading"
      row-key="id"
      size="small"
      :pagination="{ current: page, pageSize: 20, total: data?.count ?? 0, onChange: onPage }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'source'">{{ t(sourceKeyOf(record)) }}</template>
        <template v-else-if="column.key === 'createdAt'">{{ formatTime(record.createdAt) }}</template>
        <template v-else-if="column.key === 'action'">
          <Space>
            <Button size="small" class="bans-lookup" @click="lookupIp(record.value.split('/')[0])">{{ t('crowdsecGuardStr.lookupThis') }}</Button>
            <template v-if="scope === 'local'">
              <Popconfirm v-if="record.scope.toLowerCase() === 'ip'" :title="t('crowdsecGuardStr.wlConfirm', record.value)" @confirm="doWhitelist(record as Decision)">
                <Button size="small" class="bans-whitelist">{{ t('crowdsecGuardStr.addWhitelist') }}</Button>
              </Popconfirm>
              <Popconfirm :title="t('crowdsecGuardStr.unbanConfirm', record.value)" @confirm="doUnban(record as Decision)">
                <Button size="small" danger class="bans-unban">{{ t('crowdsecGuardStr.unban') }}</Button>
              </Popconfirm>
            </template>
          </Space>
        </template>
      </template>
    </Table>

    <Modal v-model:open="banOpen" :title="t('crowdsecGuardStr.manualBan')" :confirm-loading="banning" @ok="doBan">
      <Form layout="vertical">
        <FormItem :label="t('crowdsecGuardStr.ipOrRange')"><Input v-model:value="banForm.ip" class="ban-ip" /></FormItem>
        <FormItem :label="t('crowdsecGuardStr.duration')"><Input v-model:value="banForm.duration" class="ban-duration" /></FormItem>
        <FormItem :label="t('crowdsecGuardStr.reason')"><Input v-model:value="banForm.reason" class="ban-reason" /></FormItem>
      </Form>
    </Modal>
  </section>
</template>

<style scoped>
.sub-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px;
}
.bans-bar,
.bans-error,
.bans-note {
  margin-bottom: 8px;
}
.bans-search {
  width: 240px;
}
</style>
