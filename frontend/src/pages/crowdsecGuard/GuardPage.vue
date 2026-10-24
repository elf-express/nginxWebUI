<script setup lang="ts">
import { onBeforeUnmount, onMounted, provide, ref } from 'vue'
import { ConfigProvider, TabPane, Tabs } from 'ant-design-vue'
import { antLocaleOf } from '../../shared/antLocale'
import { getLang, t } from '../../shared/i18n'
import CrowdSecTab from '../crowdsec/CrowdSecTab.vue'
import BlocklistTab from './BlocklistTab.vue'
import IpLookupTab from './IpLookupTab.vue'
import WafEventsTab from './WafEventsTab.vue'
import WafRulesTab from './WafRulesTab.vue'
import { ipHash, parseHash, rulesHash, type TabKey } from './view'

// 主色對齊舊版 Layui 的 layui-btn-normal
const theme = { token: { colorPrimary: '#1e9fff' } }
const locale = antLocaleOf(getLang())
const active = ref<TabKey>('ip')
const queryIp = ref('')
// 每次「查這個 IP」加一，同一個 IP 也會重查
const lookupSeq = ref(0)
// WAF 規則分頁要聚焦的規則；每次「誤判？調整規則」加一，同一條規則也會重新聚焦
const focusRule = ref('')
const focusSeq = ref(0)

function syncFromHash() {
  const h = parseHash(window.location.hash)
  active.value = h.tab
  if (h.ip) {
    queryIp.value = h.ip
  }
  if (h.tab === 'rules' && h.rule) {
    focusRule.value = h.rule
    focusSeq.value += 1
  }
}

function onTab(key: string | number) {
  active.value = key as TabKey
  history.replaceState(null, '', key === 'ip' ? ipHash(queryIp.value) : key === 'rules' ? rulesHash(focusRule.value) : `#${key}`)
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

/** WAF 事件分頁的「誤判？調整規則」 */
function tuneRule(rule: string) {
  focusRule.value = rule
  focusSeq.value += 1
  active.value = 'rules'
  history.replaceState(null, '', rulesHash(rule))
}
provide('tuneRule', tuneRule)

onMounted(() => {
  syncFromHash()
  window.addEventListener('hashchange', syncFromHash)
})
onBeforeUnmount(() => window.removeEventListener('hashchange', syncFromHash))
</script>

<template>
  <ConfigProvider :theme="theme" :locale="locale">
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
        <TabPane key="rules" :tab="t('crowdsecGuardStr.tabRules')">
          <WafRulesTab v-if="active === 'rules'" :focus="focusRule" :focus-seq="focusSeq" />
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

<style>
/* Space 一律允許換行 */
.ant-space-horizontal {
  flex-wrap: wrap;
}
</style>
