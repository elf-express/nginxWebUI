import { bootstrap } from '../../shared/bootstrap'
import CrowdSecTab from './CrowdSecTab.vue'

// 掛在「防護與憑證」頁（Layui 舊頁）的 CrowdSec 分頁內；整頁不是 SPA，失敗時不轉 ?legacy=1，
// 改在容器顯示 data-load-failed 的文字
void bootstrap(CrowdSecTab, '#crowdsecApp', () => {
  const el = document.getElementById('crowdsecApp')
  if (el) {
    el.textContent = el.getAttribute('data-load-failed') ?? ''
  }
})
