// nginxWebUI Modern Template Picker
// Vue 3 + shadcn-vue 風格 Combobox，取代 layui select 的「選擇參數模板」
// dropdown。透過 esm.sh CDN 載入 vue@3.5，與 SpecSnap inspector 共用同一
// instance（esm.sh edge cache 自動 dedup）。
//
// 公開 API：
//   window.openTemplatePicker(callback)
//     - callback(templateId: String) 在使用者點「確定」後被呼叫
//     - 點「取消」/ ESC / 點 overlay 則 callback 不會被呼叫

(function() {
	var pickerInitPromise = null;   // 第一次 mount 的 Promise，避免重複 mount
	var pickerState = null;         // Vue setup 內暴露的 { open, close }
	var pickerCallback = null;      // 當前 open 的 confirm callback

	function ensurePickerMounted() {
		if (pickerInitPromise) return pickerInitPromise;

		pickerInitPromise = (async function() {
			var Vue = await import('https://esm.sh/vue@3.5');

			var TemplatePicker = {
				setup: function() {
					var isOpen = Vue.ref(false);
					var templates = Vue.ref([]);
					var search = Vue.ref('');
					var selectedId = Vue.ref('');
					var loading = Vue.ref(false);
					var loadError = Vue.ref('');

					function loadList() {
						loading.value = true;
						loadError.value = '';
						return fetch(ctx + '/adminPage/template/getTemplate', {
							method: 'POST',
							headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
							credentials: 'same-origin'
						})
							.then(function(res) { return res.json(); })
							.then(function(data) {
								if (data.success) {
									templates.value = data.obj || [];
								} else {
									loadError.value = data.msg || 'Load failed';
								}
							})
							.catch(function(err) { loadError.value = err.message; })
							.finally(function() { loading.value = false; });
					}

					var grouped = Vue.computed(function() {
						var q = search.value.trim().toLowerCase();
						var filtered = !q ? templates.value : templates.value.filter(function(t) {
							var name = (t.name || '').toLowerCase();
							var group = (t.groupName || '').toLowerCase();
							return name.indexOf(q) !== -1 || group.indexOf(q) !== -1;
						});

						var groupsMap = {};
						var groupsOrder = [];
						filtered.forEach(function(t) {
							var g = t.groupName || 'other';
							if (!groupsMap[g]) {
								groupsMap[g] = [];
								groupsOrder.push(g);
							}
							groupsMap[g].push(t);
						});
						return groupsOrder.map(function(g) {
							return { name: g, items: groupsMap[g] };
						});
					});

					function open() {
						isOpen.value = true;
						search.value = '';
						selectedId.value = '';
						if (templates.value.length === 0) {
							loadList();
						}
					}

					function close() {
						isOpen.value = false;
						pickerCallback = null;
					}

					function confirm() {
						if (!selectedId.value) return;
						var cb = pickerCallback;
						pickerCallback = null;
						isOpen.value = false;
						if (cb) cb(selectedId.value);
					}

					function onKeydown(e) {
						if (!isOpen.value) return;
						if (e.key === 'Escape') { close(); }
						else if (e.key === 'Enter' && selectedId.value) { confirm(); }
					}
					document.addEventListener('keydown', onKeydown);

					// 對外暴露給 wrapper
					pickerState = { open: open, close: close };

					return {
						isOpen: isOpen,
						search: search,
						selectedId: selectedId,
						loading: loading,
						loadError: loadError,
						grouped: grouped,
						close: close,
						confirm: confirm
					};
				},
				template: `
					<div v-if="isOpen" class="tp-overlay" @click.self="close">
						<div class="tp-modal" role="dialog" aria-modal="true" aria-labelledby="tp-title">
							<header class="tp-header">
								<h3 id="tp-title">選擇參數模板</h3>
								<button class="tp-close" @click="close" aria-label="Close">×</button>
							</header>
							<div class="tp-search-bar">
								<svg class="tp-search-icon" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
									<circle cx="7" cy="7" r="5" fill="none" stroke="currentColor" stroke-width="1.5"/>
									<line x1="11" y1="11" x2="14" y2="14" stroke="currentColor" stroke-width="1.5"/>
								</svg>
								<input v-model="search" placeholder="搜尋模板名稱或分組..." class="tp-search-input" autofocus/>
							</div>
							<div class="tp-body">
								<div v-if="loading" class="tp-empty">載入中...</div>
								<div v-else-if="loadError" class="tp-empty tp-error">{{ loadError }}</div>
								<div v-else-if="grouped.length === 0" class="tp-empty">找不到符合的模板</div>
								<div v-else v-for="group in grouped" :key="group.name" class="tp-group">
									<div class="tp-group-title">{{ group.name }}</div>
									<div
										v-for="t in group.items"
										:key="t.id"
										class="tp-item"
										:class="{ 'tp-item-selected': selectedId === t.id }"
										@click="selectedId = t.id"
										@dblclick="confirm()">
										<span class="tp-item-name">{{ t.name }}</span>
										<svg v-if="selectedId === t.id" class="tp-check" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
											<polyline points="3,8 7,12 13,4" fill="none" stroke="currentColor" stroke-width="2"/>
										</svg>
									</div>
								</div>
							</div>
							<footer class="tp-footer">
								<button class="tp-btn tp-btn-secondary" @click="close">取消</button>
								<button class="tp-btn tp-btn-primary" :disabled="!selectedId" @click="confirm">確定</button>
							</footer>
						</div>
					</div>
				`
			};

			// 注入 styles（一次性）
			var style = document.createElement('style');
			style.id = 'tp-styles';
			style.textContent = [
				'.tp-overlay { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.5); z-index: 19999; display: flex; align-items: center; justify-content: center; backdrop-filter: blur(2px); animation: tp-fade-in 0.15s ease-out; }',
				'@keyframes tp-fade-in { from { opacity: 0; } to { opacity: 1; } }',
				'.tp-modal { background: #fff; border-radius: 12px; width: 480px; max-height: 75vh; box-shadow: 0 20px 60px rgba(0,0,0,0.25); display: flex; flex-direction: column; overflow: hidden; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang TC", "Microsoft JhengHei", sans-serif; animation: tp-slide-up 0.18s ease-out; }',
				'@keyframes tp-slide-up { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: translateY(0); } }',
				'.tp-header { display: flex; align-items: center; justify-content: space-between; padding: 16px 20px; border-bottom: 1px solid #e5e7eb; }',
				'.tp-header h3 { margin: 0; font-size: 16px; font-weight: 600; color: #111827; }',
				'.tp-close { background: none; border: none; font-size: 22px; cursor: pointer; color: #6b7280; padding: 0; width: 28px; height: 28px; border-radius: 6px; line-height: 1; display: flex; align-items: center; justify-content: center; }',
				'.tp-close:hover { background: #f3f4f6; color: #111827; }',
				'.tp-search-bar { display: flex; align-items: center; gap: 10px; padding: 12px 20px; border-bottom: 1px solid #e5e7eb; }',
				'.tp-search-icon { color: #9ca3af; flex-shrink: 0; }',
				'.tp-search-input { flex: 1; border: none; outline: none; font-size: 14px; color: #111827; background: transparent; font-family: inherit; }',
				'.tp-body { flex: 1; overflow-y: auto; padding: 8px 12px; min-height: 200px; }',
				'.tp-empty { padding: 40px; text-align: center; color: #9ca3af; font-size: 14px; }',
				'.tp-empty.tp-error { color: #dc2626; }',
				'.tp-group { margin-bottom: 10px; }',
				'.tp-group-title { font-size: 11px; font-weight: 600; color: #6b7280; text-transform: uppercase; letter-spacing: 0.6px; padding: 8px 12px 4px; }',
				'.tp-item { display: flex; align-items: center; justify-content: space-between; padding: 9px 12px; border-radius: 6px; cursor: pointer; transition: background 0.12s; font-size: 14px; color: #111827; user-select: none; }',
				'.tp-item:hover { background: #f3f4f6; }',
				'.tp-item-selected { background: #ecfdf5; color: #047857; font-weight: 500; }',
				'.tp-item-selected:hover { background: #d1fae5; }',
				'.tp-item-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; margin-right: 8px; }',
				'.tp-check { flex-shrink: 0; color: #047857; }',
				'.tp-footer { display: flex; gap: 8px; justify-content: flex-end; padding: 12px 20px; border-top: 1px solid #e5e7eb; background: #fafafa; }',
				'.tp-btn { padding: 7px 18px; border-radius: 6px; font-size: 13px; font-weight: 500; cursor: pointer; border: 1px solid transparent; transition: all 0.12s; font-family: inherit; }',
				'.tp-btn-secondary { background: #fff; border-color: #d1d5db; color: #374151; }',
				'.tp-btn-secondary:hover { background: #f9fafb; border-color: #9ca3af; }',
				'.tp-btn-primary { background: #16baaa; border-color: #16baaa; color: #fff; }',
				'.tp-btn-primary:hover:not(:disabled) { background: #14a698; }',
				'.tp-btn-primary:disabled { opacity: 0.45; cursor: not-allowed; }'
			].join('\n');
			document.head.appendChild(style);

			// Mount
			var mount = document.createElement('div');
			mount.id = 'template-picker-mount';
			document.body.appendChild(mount);
			var app = Vue.createApp(TemplatePicker);
			app.mount('#template-picker-mount');
		})();

		return pickerInitPromise;
	}

	window.openTemplatePicker = function(callback) {
		pickerCallback = callback;
		ensurePickerMounted().then(function() {
			// 等 Vue mount 完成才 open
			if (pickerState) pickerState.open();
		}).catch(function(err) {
			console.error('[TemplatePicker] init failed:', err);
			// fallback: 用 prompt 讓使用者貼 templateId（極端後備）
			var id = prompt('Template picker 載入失敗，請貼 template ID（或取消）:');
			if (id && callback) callback(id);
		});
	};
})();
