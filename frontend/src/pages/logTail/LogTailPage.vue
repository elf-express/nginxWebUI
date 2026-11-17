<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Alert, Button, ConfigProvider, Empty, Space, Spin, Switch, theme } from 'ant-design-vue'
import { antLocaleOf } from '../../shared/antLocale'
import { getLang, t } from '../../shared/i18n'
import { useLogTail } from './useLogTail'

const props = defineProps<{ id: string }>()
const { latest, visibleLines, paused, following, loading, error, setPaused, setFollowing, retry } = useLogTail(props.id)
const { token } = theme.useToken()
const locale = antLocaleOf(getLang())
const viewportRef = ref<HTMLElement | null>(null)
const manualRef = ref<HTMLTextAreaElement | null>(null)
const wrap = ref(false)
const manualText = ref<string | null>(null)
const copied = ref(false)
const visibleText = computed(() => visibleLines.value.join('\n'))
const pauseLabel = computed(() => t(paused.value ? 'logStr.resumeDisplay' : 'logStr.pauseDisplay'))
const stateLabel = computed(() => {
  const keys = { ready: 'logStr.ready', missing: 'logStr.missing', unreadable: 'logStr.unreadable' } as const
  return latest.value ? t(keys[latest.value.state]) : t('logStr.loading')
})
const emptyLabel = computed(() => latest.value?.state === 'ready' ? t('logStr.emptyLog') : stateLabel.value)
const showEmpty = computed(() => !!latest.value && visibleLines.value.length === 0 && !error.value)
const pageStyle = computed(() => ({
  color: token.value.colorText, background: token.value.colorBgContainer,
  fontFamily: token.value.fontFamily, borderColor: token.value.colorBorder,
}))

function hasViewerSelection(): boolean {
  const selection = document.getSelection()
  const region = viewportRef.value
  return !!(selection && !selection.isCollapsed && region &&
    region.contains(selection.anchorNode) && region.contains(selection.focusNode))
}

function freezeSelection(): void {
  if (hasViewerSelection()) setFollowing(false)
}

function onScroll(): void {
  const region = viewportRef.value
  if (region && region.scrollHeight - region.scrollTop - region.clientHeight > 4) setFollowing(false)
}

function toggleFollowing(): void {
  const enabled = !following.value
  if (enabled && hasViewerSelection()) document.getSelection()?.removeAllRanges()
  setFollowing(enabled)
  if (enabled) void scrollToLatest()
}

async function scrollToLatest(): Promise<void> {
  await nextTick()
  const region = viewportRef.value
  if (region && following.value && !paused.value) region.scrollTop = region.scrollHeight
}

async function copyVisible(lines: readonly string[]): Promise<void> {
  const snapshot = lines.join('\n')
  copied.value = false
  manualText.value = null
  try {
    await navigator.clipboard.writeText(snapshot)
    copied.value = true
  } catch {
    manualText.value = snapshot
    await nextTick()
    manualRef.value?.focus()
    manualRef.value?.select()
  }
}

watch(visibleLines, () => { void scrollToLatest() })
onMounted(() => document.addEventListener('selectionchange', freezeSelection))
onBeforeUnmount(() => document.removeEventListener('selectionchange', freezeSelection))
defineExpose({ copyVisible })
</script>

<template>
  <ConfigProvider :locale="locale">
    <main class="log-tail-page" :style="pageStyle">
      <header class="viewer-header">
        <h1>{{ t('logStr.viewerTitle') }}</h1>
        <p class="log-path">{{ latest?.path }}</p>
        <div role="toolbar" :aria-label="t('logStr.toolbar')" class="toolbar">
          <Space class="toolbar-space" :size="[8, 8]">
            <Button :aria-label="pauseLabel" :aria-pressed="paused" @click="setPaused(!paused)">{{ pauseLabel }}</Button>
            <Button :aria-label="t('logStr.followLatest')" :aria-pressed="following" @click="toggleFollowing">{{ t('logStr.followLatest') }}</Button>
            <Button :aria-label="t('logStr.copyVisible')" :disabled="loading" @click="copyVisible(visibleLines)">{{ t('logStr.copyVisible') }}</Button>
            <label class="wrap-control">
              <Switch v-model:checked="wrap" :aria-label="t('logStr.wrapLines')" />
              <span>{{ t('logStr.wrapLines') }}</span>
            </label>
          </Space>
          <p data-testid="visible-range" class="viewer-range">{{ t('logStr.visibleRange', visibleLines.length, latest?.limit ?? 500) }}</p>
        </div>
        <p role="status" class="viewer-status">{{ copied ? t('logStr.copied') : stateLabel }}</p>
        <Alert v-if="error" type="error" show-icon role="alert" :message="error">
          <template #action><Button :aria-label="t('logStr.retry')" :disabled="loading" @click="retry">{{ t('logStr.retry') }}</Button></template>
        </Alert>
        <div v-if="manualText !== null" class="manual-copy">
          <Alert role="alert" type="warning" :message="t('logStr.clipboardDenied')" />
          <label for="log-manual-copy">{{ t('logStr.manualCopy') }}</label>
          <textarea id="log-manual-copy" ref="manualRef" readonly :value="manualText" :aria-label="t('logStr.manualCopy')" />
        </div>
      </header>
      <section ref="viewportRef" class="log-viewport" role="region" :aria-label="t('logStr.logContent')" :aria-busy="loading" tabindex="0" @scroll="onScroll">
        <Spin v-if="!latest && loading" :tip="t('logStr.loading')"><div class="loading-placeholder" /></Spin>
        <Empty v-if="showEmpty" :description="emptyLabel" />
        <pre :class="{ 'wrap-lines': wrap }"><code>{{ visibleText }}</code></pre>
      </section>
    </main>
  </ConfigProvider>
</template>

<style scoped>
.log-tail-page { position: fixed; inset: 0; display: flex; flex-direction: column; min-width: 0; min-height: 0; }
.viewer-header { flex: none; padding: 16px; border-bottom: 1px solid; border-color: inherit; max-height: 65%; overflow-y: auto; }
h1 { margin: 0 0 8px; font-size: 20px; line-height: 1.4; }
.log-path { margin: 0 0 12px; overflow-wrap: anywhere; }
.toolbar { min-width: 0; }
.toolbar-space { display: flex; flex-wrap: wrap; }
.viewer-range { margin: 12px 0 0; overflow-wrap: anywhere; }
.wrap-control { display: inline-flex; align-items: center; gap: 8px; }
.viewer-status { margin: 12px 0 0; }
.log-viewport { flex: 1; min-height: 0; min-width: 0; overflow: auto; padding: 16px; }
.log-viewport:focus-visible, textarea:focus-visible { outline: 2px solid currentColor; outline-offset: -2px; }
pre { margin: 0; white-space: pre; font-family: var(--font-mono, monospace); tab-size: 4; }
pre.wrap-lines { white-space: pre-wrap; overflow-wrap: anywhere; }
code { font-family: inherit; }
.loading-placeholder { min-height: 80px; }
.manual-copy { margin-top: 12px; }
.manual-copy label { display: block; margin-top: 8px; }
textarea { box-sizing: border-box; width: 100%; height: 96px; white-space: pre; font-family: var(--font-mono, monospace); }
@media (max-width: 480px) {
  .viewer-header, .log-viewport { padding: 12px; }
}
</style>
