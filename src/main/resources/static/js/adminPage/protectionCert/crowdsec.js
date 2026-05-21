var crowdsecNS = {};
(function(ns) {

	var alertPage = 1;
	var decisionPage = 1;

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
						textEl.text(crowdsecStr.connected).css('color', '#5FB878');
					} else if (status === 'disconnected') {
						el.css('background', '#FF5722');
						textEl.text(crowdsecStr.disconnected).css('color', '#FF5722');
					} else {
						el.css('background', '#999');
						textEl.text(crowdsecStr.notConfigured).css('color', '#999');
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
							body.append('<tr><td colspan="5" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
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
								+ '<td title="' + message + '">' + (message.length > 50 ? message.substring(0, 50) + '...' : message) + '</td>'
								+ '<td>' + time + '</td>'
								+ '<td>' + country + '</td>'
								+ '</tr>');
						}
					} catch (e) {
						body.append('<tr><td colspan="5" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
					}
				} else {
					$('#csAlertsBody').html('<tr><td colspan="5" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
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
							body.append('<tr><td colspan="6" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
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
							body.append('<tr>'
								+ '<td>' + ip + '</td>'
								+ '<td>' + type + '</td>'
								+ '<td>' + duration + '</td>'
								+ '<td>' + scenario + '</td>'
								+ '<td>' + reason + '</td>'
								+ '<td><button type="button" class="layui-btn layui-btn-xs layui-btn-danger" onclick="crowdsecNS.deleteDecision(\'' + id + '\')">' + crowdsecStr.delete + '</button></td>'
								+ '</tr>');
						}
					} catch (e) {
						body.append('<tr><td colspan="6" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
					}
				} else {
					$('#csDecisionsBody').html('<tr><td colspan="6" style="text-align:center;color:#999;">' + crowdsecStr.noData + '</td></tr>');
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
			title: crowdsecStr.addBan,
			area: ['450px', '320px'],
			content: '<div style="padding:20px;" class="layui-form">'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + crowdsecStr.ip + '</label><div class="layui-input-block"><input type="text" id="csAddIp" class="layui-input" placeholder="' + crowdsecStr.ipPlaceholder + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + crowdsecStr.duration + '</label><div class="layui-input-block"><input type="text" id="csAddDuration" class="layui-input" value="4h" placeholder="' + crowdsecStr.durationPlaceholder + '"></div></div>'
				+ '<div class="layui-form-item"><label class="layui-form-label">' + crowdsecStr.reason + '</label><div class="layui-input-block"><input type="text" id="csAddReason" class="layui-input" placeholder="' + crowdsecStr.reasonPlaceholder + '"></div></div>'
				+ '<div class="layui-form-item center"><button type="button" class="layui-btn layui-btn-normal" onclick="crowdsecNS.doAddDecision()"><i class="layui-icon layui-icon-ok"></i> ' + commonStr.submit + '</button></div>'
				+ '</div>'
		});
	}

	function doAddDecision() {
		var ip = $('#csAddIp').val();
		var duration = $('#csAddDuration').val() || '4h';
		var reason = $('#csAddReason').val() || '';
		if (!ip) {
			layer.msg(crowdsecStr.ipPlaceholder);
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

	function deleteDecision(id) {
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
	ns.deleteDecision = deleteDecision;
	ns.refreshAll = refreshAll;

})(crowdsecNS);
