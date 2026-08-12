/**
 * ASN catalog + protection profile + block intents (protectionCert ASN tab).
 * APIs: /adminPage/asn/{profile,setProfile,catalog,syncMeta,intents,addIntent,pushIntent,revokeIntent,suggestCandidates}
 */
var asnCatalogNS = (function () {
	var profile = 'light';
	var crowdsecConfigured = false;
	var catalogCurr = 1;
	var catalogLimit = 10;
	var catalogCount = 0;
	var profileForm = null;
	var applyingProfile = false;

	var PROFILE_TIPS = {
		light: function () { return asnStr.profileLightTip || ''; },
		manual: function () { return asnStr.profileManualTip || ''; },
		strict: function () { return asnStr.profileStrictTip || ''; }
	};

	function esc(str) {
		if (str == null || str === '') return '';
		return String(str)
			.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;');
	}

	function fmtTime(ms) {
		if (ms == null || ms === '' || ms === 0) return '--';
		var d = new Date(typeof ms === 'number' ? ms : parseInt(ms, 10));
		if (isNaN(d.getTime())) return '--';
		function p(n) { return n < 10 ? '0' + n : '' + n; }
		return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate())
			+ ' ' + p(d.getHours()) + ':' + p(d.getMinutes());
	}

	function canCreateIntent() {
		return profile === 'manual' || profile === 'strict';
	}

	function canPush() {
		return canCreateIntent() && crowdsecConfigured;
	}

	function canSuggest() {
		return profile === 'strict';
	}

	function applyUiGates() {
		var intentDisabled = !canCreateIntent();
		var pushDisabled = !canPush();

		$('#btnAsnAddIntent').prop('disabled', intentDisabled);
		if (intentDisabled) {
			$('#btnAsnAddIntent').addClass('layui-btn-disabled');
		} else {
			$('#btnAsnAddIntent').removeClass('layui-btn-disabled');
		}

		// per-row push/add in tables re-rendered after load
		if (canSuggest()) {
			$('#btnAsnSuggestHosting').show();
		} else {
			$('#btnAsnSuggestHosting').hide();
		}

		if (!crowdsecConfigured) {
			$('#asnCrowdsecBanner').show();
		} else {
			$('#asnCrowdsecBanner').hide();
		}

		// Manual/Strict: warn bulk range push is not production-hardened (phase-1)
		if (profile === 'manual' || profile === 'strict') {
			$('#asnBulkWarnBanner').show();
		} else {
			$('#asnBulkWarnBanner').hide();
		}

		var tipFn = PROFILE_TIPS[profile];
		$('#asnProfileTip').text(tipFn ? tipFn() : '');

		// disable catalog row actions when light
		$('#asnCatalogBody button.asn-add-intent-btn').each(function () {
			$(this).prop('disabled', intentDisabled);
			if (intentDisabled) $(this).addClass('layui-btn-disabled');
			else $(this).removeClass('layui-btn-disabled');
		});
		$('#asnIntentBody button.asn-push-btn').each(function () {
			$(this).prop('disabled', pushDisabled);
			if (pushDisabled) $(this).addClass('layui-btn-disabled');
			else $(this).removeClass('layui-btn-disabled');
		});
		// revoke needs CS
		$('#asnIntentBody button.asn-revoke-btn').each(function () {
			$(this).prop('disabled', !crowdsecConfigured);
			if (!crowdsecConfigured) $(this).addClass('layui-btn-disabled');
			else $(this).removeClass('layui-btn-disabled');
		});
	}

	function setRadioChecked(val) {
		applyingProfile = true;
		$('input[name="protectionProfile"]').prop('checked', false);
		$('input[name="protectionProfile"][value="' + val + '"]').prop('checked', true);
		if (profileForm) profileForm.render('radio');
		applyingProfile = false;
	}

	function loadProfile() {
		$.get(ctx + '/adminPage/asn/profile', function (res) {
			if (!res.success || !res.obj) return;
			profile = res.obj.profile || 'light';
			crowdsecConfigured = !!res.obj.crowdsecConfigured;
			setRadioChecked(profile);
			applyUiGates();
			loadCatalog();
			loadIntents();
		});
	}

	function postSetProfile(next, revokeMode) {
		var data = { profile: next };
		if (revokeMode) data.revokeMode = revokeMode;
		$.post(ctx + '/adminPage/asn/setProfile', data, function (res) {
			if (!res.success) {
				layer.msg(res.msg || 'error');
			}
			// Always resync radio + gates from server (covers revoke fail after profile already light)
			loadProfile();
		}).fail(function () {
			loadProfile();
		});
	}

	function onProfileChange(next) {
		if (applyingProfile) return;
		if (next === profile) return;

		// Z: manual/strict → light
		if (next === 'light' && profile !== 'light') {
			// Without CrowdSec, Revoke cannot run — only offer Keep + Close
			if (!crowdsecConfigured) {
				layer.confirm(asnStr.switchLightMsg || '', {
					title: asnStr.switchLightTitle || '',
					icon: 3,
					btn: [
						asnStr.switchLightKeep || 'Keep',
						commonStr.close || 'Close'
					],
					cancel: function () {
						setRadioChecked(profile);
					}
				}, function (index) {
					// keep
					layer.close(index);
					postSetProfile('light', 'keep');
				}, function (index) {
					// close — revert radio
					setRadioChecked(profile);
					layer.close(index);
				});
				return;
			}

			layer.confirm(asnStr.switchLightMsg || '', {
				title: asnStr.switchLightTitle || '',
				icon: 3,
				btn: [
					asnStr.switchLightKeep || 'Keep',
					asnStr.switchLightRevoke || 'Revoke',
					commonStr.close || 'Close'
				],
				btn3: function (index) {
					// cancel — revert radio
					setRadioChecked(profile);
					layer.close(index);
				},
				cancel: function () {
					setRadioChecked(profile);
				}
			}, function (index) {
				// keep
				layer.close(index);
				postSetProfile('light', 'keep');
			}, function (index) {
				// revoke
				layer.close(index);
				postSetProfile('light', 'revoke');
			});
			return;
		}

		postSetProfile(next, null);
	}

	function loadCatalog() {
		var q = ($('#asnCatalogQ').val() || '').trim();
		var category = ($('#asnCatalogCategory').val() || '').trim();
		var countryCode = ($('#asnCatalogCountry').val() || '').trim().toUpperCase();
		$.get(ctx + '/adminPage/asn/catalog', {
			curr: catalogCurr,
			limit: catalogLimit,
			q: q,
			category: category,
			countryCode: countryCode
		}, function (res) {
			if (!res.success) {
				layer.msg(res.msg || 'error');
				return;
			}
			var page = res.obj || {};
			var list = page.records || [];
			catalogCount = page.count != null ? parseInt(page.count, 10) : list.length;
			catalogCurr = page.curr != null ? parseInt(page.curr, 10) : catalogCurr;
			catalogLimit = page.limit != null ? parseInt(page.limit, 10) : catalogLimit;

			var html = '';
			if (!list.length) {
				html = '<tr><td colspan="6" style="text-align:center;color:#999;">--</td></tr>';
			} else {
				for (var i = 0; i < list.length; i++) {
					var item = list[i];
					var asn = item.asn || '';
					html += '<tr>';
					html += '<td>' + esc(asn) + '</td>';
					html += '<td>' + esc(item.handle || '') + '</td>';
					html += '<td>' + esc(item.description || '') + '</td>';
					html += '<td>' + esc(item.countryCode || '') + '</td>';
					html += '<td>' + esc(item.category || '') + '</td>';
					html += '<td>';
					html += '<button type="button" class="layui-btn layui-btn-xs layui-btn-normal asn-add-intent-btn" data-asn="'
						+ esc(asn) + '" onclick="asnCatalogNS.addIntentFromCatalog(this)">'
						+ esc(asnStr.addIntent || '') + '</button>';
					html += '</td>';
					html += '</tr>';
				}
			}
			$('#asnCatalogBody').html(html);

			var totalPages = catalogLimit > 0 ? Math.max(1, Math.ceil(catalogCount / catalogLimit)) : 1;
			var infoTpl = asnStr.pageInfo || 'Page {0} / {1} (total {2})';
			$('#asnCatalogPageInfo').text(
				infoTpl.replace('{0}', catalogCurr).replace('{1}', totalPages).replace('{2}', catalogCount)
			);
			$('#btnAsnCatalogPrev').prop('disabled', catalogCurr <= 1);
			$('#btnAsnCatalogNext').prop('disabled', catalogCurr >= totalPages);
			applyUiGates();
		});
	}

	function loadIntents() {
		$.get(ctx + '/adminPage/asn/intents', function (res) {
			if (!res.success) return;
			var list = res.obj || [];
			var html = '';
			if (!list.length) {
				html = '<tr><td colspan="6" style="text-align:center;color:#999;">--</td></tr>';
			} else {
				for (var i = 0; i < list.length; i++) {
					var item = list[i];
					var id = item.id || '';
					html += '<tr>';
					html += '<td>' + esc(item.asn || '') + '</td>';
					html += '<td>' + esc(item.status || '') + '</td>';
					html += '<td>' + esc(item.duration || '') + '</td>';
					html += '<td>' + esc(fmtTime(item.lastPushAt)) + '</td>';
					html += '<td style="max-width:240px;word-break:break-all;">' + esc(item.lastError || '') + '</td>';
					html += '<td>';
					html += '<button type="button" class="layui-btn layui-btn-xs asn-push-btn" data-id="'
						+ esc(id) + '" onclick="asnCatalogNS.pushIntent(this)">'
						+ esc(asnStr.push || 'Push') + '</button> ';
					html += '<button type="button" class="layui-btn layui-btn-xs layui-btn-danger asn-revoke-btn" data-id="'
						+ esc(id) + '" onclick="asnCatalogNS.revokeIntent(this)">'
						+ esc(asnStr.revoke || 'Revoke') + '</button>';
					html += '</td>';
					html += '</tr>';
				}
			}
			$('#asnIntentBody').html(html);
			applyUiGates();
		});
	}

	function syncMeta() {
		$.post(ctx + '/adminPage/asn/syncMeta', function (res) {
			if (res.success) {
				layer.msg(asnStr.syncStarted || 'ok');
			} else {
				layer.msg(res.msg || asnStr.syncInProgress || 'error');
			}
		});
	}

	function search() {
		catalogCurr = 1;
		loadCatalog();
	}

	function prevPage() {
		if (catalogCurr <= 1) return;
		catalogCurr--;
		loadCatalog();
	}

	function nextPage() {
		var totalPages = catalogLimit > 0 ? Math.ceil(catalogCount / catalogLimit) : 1;
		if (catalogCurr >= totalPages) return;
		catalogCurr++;
		loadCatalog();
	}

	function addIntentFromCatalog(btn) {
		if (!canCreateIntent()) {
			layer.msg(asnStr.profileDisallowsIntent || 'profile_disallows_intent');
			return;
		}
		var asn = $(btn).data('asn');
		if (!asn) return;
		var duration = ($('#asnIntentDuration').val() || '24h').trim() || '24h';
		$.post(ctx + '/adminPage/asn/addIntent', {
			asn: String(asn),
			duration: duration,
			note: ''
		}, function (res) {
			if (res.success) {
				layer.msg(commonStr.success || 'ok');
				loadIntents();
			} else {
				layer.msg(res.msg || 'error');
			}
		});
	}

	function promptAddIntent() {
		if (!canCreateIntent()) {
			layer.msg(asnStr.profileDisallowsIntent || 'profile_disallows_intent');
			return;
		}
		layer.prompt({
			title: asnStr.addIntent || 'Add intent',
			formType: 0,
			value: ''
		}, function (value, index) {
			var asn = (value || '').trim();
			if (!asn || !/^\d+$/.test(asn)) {
				layer.msg(asnStr.invalidAsn || 'invalid');
				return;
			}
			var duration = ($('#asnIntentDuration').val() || '24h').trim() || '24h';
			$.post(ctx + '/adminPage/asn/addIntent', {
				asn: asn,
				duration: duration,
				note: ''
			}, function (res) {
				if (res.success) {
					layer.close(index);
					loadIntents();
				} else {
					layer.msg(res.msg || 'error');
				}
			});
		});
	}

	function pushIntent(btn) {
		if (!canPush()) {
			layer.msg(
				!crowdsecConfigured
					? (asnStr.crowdsecRequired || 'crowdsec_not_configured')
					: (asnStr.profileDisallowsPush || 'profile_disallows_push')
			);
			return;
		}
		var id = $(btn).data('id');
		if (!id) return;
		$.post(ctx + '/adminPage/asn/pushIntent', { id: id }, function (res) {
			if (res.success) {
				layer.msg(commonStr.success || 'ok');
				loadIntents();
			} else {
				layer.msg(res.msg || 'error');
				loadIntents();
			}
		});
	}

	function revokeIntent(btn) {
		if (!crowdsecConfigured) {
			layer.msg(asnStr.crowdsecRequired || 'crowdsec_not_configured');
			return;
		}
		var id = $(btn).data('id');
		if (!id) return;
		layer.confirm(commonStr.confirmDel || 'Confirm?', function (idx) {
			layer.close(idx);
			$.post(ctx + '/adminPage/asn/revokeIntent', { id: id }, function (res) {
				if (res.success) {
					layer.msg(commonStr.success || 'ok');
					loadIntents();
				} else {
					layer.msg(res.msg || 'error');
					loadIntents();
				}
			});
		});
	}

	function suggestHosting() {
		if (!canSuggest()) {
			layer.msg(asnStr.profileDisallowsSuggest || 'profile_disallows_suggest');
			return;
		}
		layer.confirm(asnStr.suggestConfirm || 'Suggest?', { icon: 3 }, function (idx) {
			layer.close(idx);
			$.post(ctx + '/adminPage/asn/suggestCandidates', { category: 'hosting' }, function (res) {
				if (res.success) {
					var obj = res.obj;
					var n = (obj != null && typeof obj === 'object' && obj.added != null) ? obj.added : (obj != null ? obj : 0);
					var msg = String(asnStr.suggestAdded || 'Added {0} candidates').replace('{0}', n);
					if (obj && obj.capped) {
						msg += ' ' + (asnStr.suggestCapped || '(capped for safety)');
					}
					layer.msg(msg);
					loadIntents();
				} else {
					layer.msg(res.msg || 'error');
				}
			});
		});
	}

	function init() {
		layui.use(['form'], function () {
			profileForm = layui.form;
			profileForm.on('radio(asnProfile)', function (data) {
				onProfileChange(data.value);
			});
			// category select render
			profileForm.render('select');
			loadProfile();
		});
	}

	// auto-init
	$(function () {
		// only when ASN tab markup is present
		if ($('#asnProfileLight').length) {
			init();
		}
	});

	return {
		syncMeta: syncMeta,
		search: search,
		prevPage: prevPage,
		nextPage: nextPage,
		addIntentFromCatalog: addIntentFromCatalog,
		promptAddIntent: promptAddIntent,
		pushIntent: pushIntent,
		revokeIntent: revokeIntent,
		suggestHosting: suggestHosting,
		reload: function () {
			loadProfile();
		}
	};
})();
