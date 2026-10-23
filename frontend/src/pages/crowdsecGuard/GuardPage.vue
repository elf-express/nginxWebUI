<script setup lang="ts">
import { onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { ConfigProvider, TabPane, Tabs } from 'ant-design-vue'
import { t } from '../../shared/i18n'
import CrowdSecTab from '../crowdsec/CrowdSecTab.vue'
import BlocklistTab from './BlocklistTab.vue'
import IpLookupTab from './IpLookupTab.vue'
import { parseHash, type TabKey } from './view'

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }
const active = ref<TabKey>('ip')
const queryIp = ref('')

function syncFromHash() {
  const h = parseHash(window.location.hash)
  active.value = h.tab
  if (h.ip) {
    queryIp.value = h.ip
  }
}

function onTab(key: string | number) {
  active.value = key as TabKey
  history.replaceState(null, '', `#${key}`)
}

/** 其他分頁的「查這個 IP」 */
function lookupIp(ip: string) {
  queryIp.value = ip
  active.value = 'ip'
  history.replaceState(null, '', `#ip=${encodeURIComponent(ip)}`)
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
          <IpLookupTab v-model:ip="queryIp" />
        </TabPane>
        <TabPane key="bans" :tab="t('crowdsecGuardStr.tabBans')">
          <BlocklistTab v-if="active === 'bans'" />
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
