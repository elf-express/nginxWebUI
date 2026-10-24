import { bootstrap } from '../../shared/bootstrap'
import GuardPage from './GuardPage.vue'

// 新頁沒有舊版 Freemarker 頁可回落：失敗時在容器顯示 data-load-failed 的文字
void bootstrap(GuardPage, '#crowdsecGuardApp', () => {
  const el = document.getElementById('crowdsecGuardApp')
  if (el) {
    el.textContent = el.getAttribute('data-load-failed') ?? ''
  }
})
