<script setup lang="ts">
import { onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { ConfigProvider, TabPane, Tabs } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import CrowdSecTab from '../crowdsec/CrowdSecTab.vue'
import BlocklistTab from './BlocklistTab.vue'
import IpLookupTab from './IpLookupTab.vue'
import WafEventsTab from './WafEventsTab.vue'
import { ipHash, parseHash, type TabKey } from './view'

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }
const active = ref<TabKey>('ip')
const queryIp = ref('')
// 每次「查這個 IP」加一，同一個 IP 也會重查
const lookupSeq = ref(0)

function syncFromHash() {
  const h = parseHash(window.location.hash)
  active.value = h.tab
  if (h.ip) {
    queryIp.value = h.ip
  }
}

function onTab(key: string | number) {
  active.value = key as TabKey
  history.replaceState(null, '', key === 'ip' ? ipHash(queryIp.value) : `#${key}`)
}

/** IP 查詢分頁送出查詢：只在它是目前分頁時同步網址；replaceState 不觸發 hashchange，不會再查一次 */
function onQuery(ip: string) {
  if (active.value === 'ip') {
    history.replaceState(null, '', ipHash(ip))
  }
}

/** 其他分頁的「查這個 IP」 */
function lookupIp(ip: string) {
  queryIp.value = ip
  lookupSeq.value += 1
  active.value = 'ip'
  history.replaceState(null, '', ipHash(ip))
}
provide('lookupIp', lookupIp)

onMounted(() => {
  syncFromHash()
  window.addEventListener('hashchange', syncFromHash)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', syncFromHash))
</script>

<template>
  <ConfigProvider :theme="theme">
    <main class="guard-page">
      <h1 class="guard-title">{{ t('crowdsecGuardStr.title') }}</h1>
      <p class="guard-intro">{{ t('crowdsecGuardStr.intro') }}</p>
      <Tabs :active-key="active" @change="onTab">
        <TabPane key="ip" :tab="t('crowdsecGuardStr.tabIp')">
          <IpLookupTab v-model:ip="queryIp" :refresh="lookupSeq" @query="onQuery" />
        </TabPane>
        <TabPane key="bans" :tab="t('crowdsecGuardStr.tabBans')">
          <BlocklistTab v-if="active === 'bans'" />
        </TabPane>
        <TabPane key="waf" :tab="t('crowdsecGuardStr.tabWaf')">
          <WafEventsTab v-if="active === 'waf'" />
        </TabPane>
        <TabPane key="conn" :tab="t('crowdsecGuardStr.tabConn')">
          <CrowdSecTab v-if="active === 'conn'" />
        </TabPane>
      </Tabs>
    </main>
  </ConfigProvider>
</template>

<style scoped>
.guard-page {
  padding: 15px;
}
.guard-title {
  font-size: 20px;
  font-weight: 500;
  margin: 0 0 4px;
}
.guard-intro {
  color: #666;
  margin: 0 0 12px;
}
</style>
