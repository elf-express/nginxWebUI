var crowdsecNS = {};
(function(ns) {

	var alertPage = 1;
	var decisionPage = 1;

	/** i18n with English fallbacks (crowdsecStr.* from messages*.properties). */
	function t(key, fallback) {
		if (typeof crowdsecStr !== 'undefined' && crowdsecStr[key]) {
			return crowdsecStr[key];
		}
		return fallback;
	}

	function escHtml(s) {
		if (s == null) return '';
		return String(s)
			.replace(/&/g, '&amp;')
			.replace(/"/g, '&quot;')
			.replace(/'/g, '&#39;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;');
	}

	/** Escape for single-quoted JS string in inline onclick. */
	function escJs(s) {
		if (s == null) return '';
		return String(s).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
	}

	function loadConfig() {
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/crowdsec/getConfig',
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					var config = data.obj;
					$('#csUrl').val(config.url || '');
					$('#csApiKey').val(config.apiKey || '');
					checkStatus();
				}
			}
		});
	}

	function saveConfig() {
		var url = $('#csUrl').val();
		var apiKey = $('#csApiKey').val();
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/crowdsec/saveConfig',
			data: { url: url, apiKey: apiKey },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					layer.msg(commonStr.successMsg || 'OK');
					loadConfig();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	function checkStatus() {
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/crowdsec/status',
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					var status = data.obj;
					var el = $('#csStatusDot');
					var textEl = $('#csStatusText');
					if (status === 'connected') {
						el.css('background', '#5FB878');
						textEl.text(t('connected', 'Connected')).css('color', '#5FB878');
					} else if (status === 'disconnected') {
						el.css('background', '#FF5722');
						textEl.text(t('disconnected', 'Disconnected')).css('color', '#FF5722');
					} else {
						el.css('background', '#999');
						textEl.text(t('notConfigured', 'Not configured')).css('color', '#999');
					}
				}
			}
		});
	}

	function loadAlerts() {
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/crowdsec/alerts',
			data: { limit: 20, page: alertPage },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					var body = $('#csAlertsBody');
					body.empty();
					try {
						var alerts = typeof data.obj === 'string' ? JSON.parse(data.obj) : data.obj;
						if (!alerts || alerts.length === 0) {
							body.append('<tr><td colspan="5" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
							return;
						}
						for (var i = 0; i < alerts.length; i++) {
							var a = alerts[i];
							var source = a.source || {};
							var ip = source.ip || source.value || '--';
							var scenario = a.scenario || '--';
							var message = a.message || '--';
							var time = a.created_at || '--';
							var country = source.country || '--';
							body.append('<tr>'
								+ '<td>' + ip + '</td>'
								+ '<td>' + scenario + '</td>'
								+ '<td title="' + escHtml(message) + '">' + escHtml(message.length > 50 ? message.substring(0, 50) + '...' : message) + '</td>'
								+ '<td>' + time + '</td>'
								+ '<td>' + country + '</td>'
								+ '</tr>');
						}
					} catch (e) {
						body.append('<tr><td colspan="5" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
					}
				} else {
					$('#csAlertsBody').html('<tr><td colspan="5" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
				}
			},
			error: function() {
				$('#csAlertsBody').html('<tr><td colspan="5" style="text-align:center;color:#FF5722;">Error</td></tr>');
			}
		});
	}

	function loadDecisions() {
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/crowdsec/decisions',
			data: { limit: 50, page: decisionPage },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					var body = $('#csDecisionsBody');
					body.empty();
					try {
						var decisions = typeof data.obj === 'string' ? JSON.parse(data.obj) : data.obj;
						if (!decisions || decisions.length === 0) {
							body.append('<tr><td colspan="6" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
							return;
						}
						for (var i = 0; i < decisions.length; i++) {
							var d = decisions[i];
							var ip = d.value || '--';
							var type = d.type || '--';
							var duration = d.duration || '--';
							var scenario = d.scenario || '--';
							var reason = d.reason || '--';
							var id = d.id || '';
							var scope = d.scope || 'ip';
							// Whitelist applies to single-IP false positives (not range big-blocks)
							var canWhitelist = (scope === 'ip' || !scope || scope === 'Ip')
								&& type !== 'whitelist'
								&& ip !== '--';
							var actions = '';
							if (canWhitelist) {
								actions += '<button type="button" class="layui-btn layui-btn-xs layui-btn-normal" onclick="crowdsecNS.whitelistIpDialog(\''
									+ escJs(ip) + '\')">' + t('whitelistIp', 'Whitelist IP') + '</button> ';
							}
							actions += '<button type="button" class="layui-btn layui-btn-xs layui-btn-danger" onclick="crowdsecNS.deleteDecision(\''
								+ escJs(String(id)) + '\')">' + t('delete', 'Delete') + '</button>';
							body.append('<tr>'
								+ '<td>' + escHtml(ip) + '</td>'
								+ '<td>' + escHtml(type) + '</td>'
								+ '<td>' + escHtml(duration) + '</td>'
								+ '<td>' + escHtml(scenario) + '</td>'
								+ '<td>' + escHtml(reason) + '</td>'
								+ '<td style="white-space:nowrap;">' + actions + '</td>'
								+ '</tr>');
						}
					} catch (e) {
						body.append('<tr><td colspan="6" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
					}
				} else {
					$('#csDecisionsBody').html('<tr><td colspan="6" style="text-align:center;color:#999;">' + t('noData', 'No data') + '</td></tr>');
				}
			},
			error: function() {
				$('#csDecisionsBody').html('<tr><td colspan="6" style="text-align:center;color:#FF5722;">Error</td></tr>');
			}
		});
	}

	function addDecision() {
		layer.open({
			type: 1,
			title: t('addBan', 'Add ban'),
			area: ['450px', '320px'],
			content: '<div style="padding:20px;" class="layui-form">'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('ip', 'IP') + '</label><div class="layui-input-block"><input type="text" id="csAddIp" class="layui-input" placeholder="' + t('ipPlaceholder', 'IP address') + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('duration', 'Duration') + '</label><div class="layui-input-block"><input type="text" id="csAddDuration" class="layui-input" value="4h" placeholder="' + t('durationPlaceholder', 'e.g. 4h') + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('reason', 'Reason') + '</label><div class="layui-input-block"><input type="text" id="csAddReason" class="layui-input" placeholder="' + t('reasonPlaceholder', 'Reason') + '"></div></div>'
				+ '<div class="layui-form-item center"><button type="button" class="layui-btn layui-btn-normal" onclick="crowdsecNS.doAddDecision()"><i class="layui-icon layui-icon-ok"></i> ' + commonStr.submit + '</button></div>'
				+ '</div>'
		});
	}

	function doAddDecision() {
		var ip = $('#csAddIp').val();
		var duration = $('#csAddDuration').val() || '4h';
		var reason = $('#csAddReason').val() || '';
		if (!ip) {
			layer.msg(t('ipPlaceholder', 'IP address'));
			return;
		}
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/crowdsec/addDecision',
			data: { ip: ip, duration: duration, reason: reason },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					layer.closeAll();
					layer.msg(commonStr.successMsg || 'OK');
					loadDecisions();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	/**
	 * False-positive path: open whitelist dialog for an IP (from decisions row or blank).
	 * Optional checkbox syncs a site-wide DenyAllow type=allow entry.
	 */
	function whitelistIpDialog(prefillIp) {
		var ipVal = prefillIp || '';
		layer.open({
			type: 1,
			title: t('whitelistIp', 'Whitelist IP'),
			area: ['480px', '360px'],
			content: '<div style="padding:20px;" class="layui-form" lay-filter="csWhitelistForm">'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('ip', 'IP') + '</label><div class="layui-input-block"><input type="text" id="csWlIp" class="layui-input" value="' + escHtml(ipVal) + '" placeholder="' + t('ipPlaceholder', 'IP address') + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('duration', 'Duration') + '</label><div class="layui-input-block"><input type="text" id="csWlDuration" class="layui-input" value="24h" placeholder="' + t('durationPlaceholder', 'e.g. 24h') + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + t('reason', 'Reason') + '</label><div class="layui-input-block"><input type="text" id="csWlReason" class="layui-input" value="nginxwebui:fp-whitelist" placeholder="' + t('reasonPlaceholder', 'Reason') + '"></div></div>'
				+ '<div class="layui-form-item"><div class="layui-input-block"><input type="checkbox" id="csWlSyncDenyAllow" lay-skin="primary" title="' + t('syncDenyAllow', 'Also add to site whitelist (DenyAllow)') + '"> <span style="margin-left:6px;">' + t('syncDenyAllow', 'Also add to site whitelist (DenyAllow)') + '</span></div></div>'
				+ '<div class="layui-form-item center"><button type="button" class="layui-btn layui-btn-normal" onclick="crowdsecNS.doWhitelistIp()"><i class="layui-icon layui-icon-ok"></i> ' + commonStr.submit + '</button></div>'
				+ '</div>'
		});
	}

	function doWhitelistIp() {
		var ip = ($('#csWlIp').val() || '').trim();
		var duration = $('#csWlDuration').val() || '24h';
		var reason = $('#csWlReason').val() || 'nginxwebui:fp-whitelist';
		var syncDenyAllow = $('#csWlSyncDenyAllow').is(':checked') ? 'true' : 'false';
		if (!ip) {
			layer.msg(t('ipPlaceholder', 'IP address'));
			return;
		}
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/crowdsec/whitelistIp',
			data: { ip: ip, duration: duration, reason: reason, syncDenyAllow: syncDenyAllow },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					layer.closeAll();
					if (data.obj === 'whitelist_ok_denyallow_failed') {
						layer.msg(t('whitelistOkDenyAllowFailed', 'Whitelisted in CrowdSec; site whitelist sync failed'));
					} else {
						layer.msg(commonStr.successMsg || 'OK');
					}
					loadDecisions();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	function deleteDecision(id) {
		if (!id) {
			return;
		}
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/crowdsec/deleteDecision',
			data: { decisionId: id },
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					layer.msg(commonStr.successMsg || 'OK');
					loadDecisions();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	function refreshAll() {
		loadAlerts();
		loadDecisions();
	}

	$(function() {
		loadConfig();
	});

	ns.loadConfig = loadConfig;
	ns.saveConfig = saveConfig;
	ns.checkStatus = checkStatus;
	ns.loadAlerts = loadAlerts;
	ns.loadDecisions = loadDecisions;
	ns.addDecision = addDecision;
	ns.doAddDecision = doAddDecision;
	ns.whitelistIpDialog = whitelistIpDialog;
	ns.doWhitelistIp = doWhitelistIp;
	ns.deleteDecision = deleteDecision;
	ns.refreshAll = refreshAll;

})(crowdsecNS);
