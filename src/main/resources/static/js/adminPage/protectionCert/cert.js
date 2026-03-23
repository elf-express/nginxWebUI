var certNS = {};
(function(ns) {

	// === Init event handlers ===
	$(function() {
		form.on('switch(autoRenew)', function(data) {
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/setAutoRenew',
				data: {
					id: data.value,
					autoRenew: data.elem.checked ? 1 : 0
				},
				dataType: 'json',
				success: function(data) {
					if (data.success) {
						//location.reload();
					} else {
						layer.msg(data.msg);
					}
				},
				error: function() {
					layer.alert(commonStr.errorInfo);
				}
			});
		});

		layui.use('upload', function() {
			var upload = layui.upload;
			upload.render({
				elem: '#pemBtn',
				url: ctx + '/adminPage/main/upload',
				accept: 'file',
				done: function(res) {
					if (res.success) {
						$("#pem").val(res.obj);
						var path = res.obj.split('/');
						$("#pemPath").html(path[path.length - 1]);
					}
				},
				error: function() {
				}
			});

			upload.render({
				elem: '#keyBtn',
				url: ctx + '/adminPage/main/upload',
				accept: 'file',
				done: function(res) {
					if (res.success) {
						$("#key").val(res.obj);
						var path = res.obj.split('/');
						$("#keyPath").html(path[path.length - 1]);
					}
				},
				error: function() {
				}
			});
		});

		layui.use('laydate', function() {
			layui.laydate.render({
				elem: '#makeTime, #endTime',
				type: 'datetime',
				format: 'yyyy-MM-dd HH:mm:ss'
			});
		});

		form.on('select(dnsType)', function(data) {
			checkDnsType(data.value);
		});

		form.on('select(type)', function(data) {
			checkType(data.value);
		});

		form.on('checkbox(certCheckAll)', function(data) {
			if (data.elem.checked) {
				$("input[name='certIds']").prop("checked", true);
			} else {
				$("input[name='certIds']").prop("checked", false);
			}
			form.render();
		});
	});

	function search() {
		$("input[name='curr']").val(1);
		$("#certSearchForm").submit();
	}

	function checkDnsType(value) {
		$("#ali").hide();
		$("#dp").hide();
		$("#tencent").hide();
		$("#aws").hide();
		$("#cf").hide();
		$("#cfToken").hide();
		$("#gd").hide();
		$("#hw").hide();
		$("#ipv64").hide();

		$("#" + value).show();
	}

	function checkType(value) {
		$("#type0").hide();
		$("#type1").hide();
		$("#encryptionDiv").hide();

		if (value == 0) {
			$("#type0").show();
			$("#encryptionDiv").show();
		}
		if (value == 1) {
			$("#type1").show();
		}
		if (value == 2) {
			$("#encryptionDiv").show();
		}
	}

	function add() {
		$("#certHidId").val("");
		$("#domain").val("");
		$("#type option:first").prop("selected", true);
		$("#dnsType option:first").prop("selected", true);
		$("#encryption option:first").prop("selected", true);
		$("#aliKey").val("");
		$("#aliSecret").val("");
		$("#dpId").val("");
		$("#dpKey").val("");
		$("#tencentSecretId").val("");
		$("#tencentSecretKey").val("");
		$("#awsAccessKeyId").val("");
		$("#awsSecretAccessKey").val("");
		$("#cfEmail").val("");
		$("#cfKey").val("");

		$("#cft").val("");
		$("#cfAccountId").val("");
		$("#cfZoneId").val("");

		$("#gdKey").val("");
		$("#gdSecret").val("");
		$("#ipv64Token").val("");

		$("#hwUsername").val("");
		$("#hwPassword").val("");
		$("#hwDomainName").val("");

		$("#pem").val("");
		$("#key").val("");
		$("#pemPath").html("");
		$("#keyPath").html("");

		$("#domain").attr("disabled", false);
		$("#domain").removeClass("disabled");
		$("#type").attr("disabled", false);
		$("#encryption").attr("disabled", false);
		$("#encryption").removeClass("disabled");

		$("#makeTime").val("");
		$("#endTime").val("");

		checkType(2);
		checkDnsType('ali');

		form.render();
		showWindow(certStr.add);
	}

	function edit(id, clone) {
		$("#certHidId").val(id);

		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/cert/detail',
			dataType: 'json',
			data: {
				id: id
			},
			success: function(data) {
				if (data.success) {

					var cert = data.obj;

					$("#domain").val(cert.domain);
					$("#type").val(cert.type);
					$("#dnsType").val(cert.dnsType != null ? cert.dnsType : 'ali');
					$("#encryption").val(cert.encryption != null ? cert.encryption : 'RSA');
					$("#aliKey").val(cert.aliKey);
					$("#aliSecret").val(cert.aliSecret);
					$("#dpId").val(cert.dpId);
					$("#dpKey").val(cert.dpKey);
					$("#tencentSecretId").val(cert.tencentSecretId);
					$("#tencentSecretKey").val(cert.tencentSecretKey);
					$("#awsAccessKeyId").val(cert.awsAccessKeyId);
					$("#awsSecretAccessKey").val(cert.awsSecretAccessKey);
					$("#cfEmail").val(cert.cfEmail);
					$("#cfKey").val(cert.cfKey);

					$("#cft").val(cert.cfToken);
					$("#cfAccountId").val(cert.cfAccountId);
					$("#cfZoneId").val(cert.cfZoneId);

					$("#gdKey").val(cert.gdKey);
					$("#gdSecret").val(cert.gdSecret);
					$("#ipv64Token").val(cert.ipv64Token);

					$("#hwUsername").val(cert.hwUsername);
					$("#hwPassword").val(cert.hwPassword);
					$("#hwDomainName").val(cert.hwDomainName);

					if (!clone) {
						$("#domain").attr("disabled", true);
						$("#domain").addClass("disabled");

						if (cert.pem != null && cert.pem != '' && cert.key != null && cert.key != '') {
							$("#type").attr("disabled", true);
							$("#encryption").attr("disabled", true);
							$("#encryption").addClass("disabled");
						} else {
							$("#type").attr("disabled", false);
							$("#encryption").attr("disabled", false);
							$("#encryption").removeClass("disabled");
						}

						$("#certHidId").val(cert.id);
						$("#pem").val(cert.pem);
						$("#key").val(cert.key);
						var path = cert.pem.split('/');
						$("#pemPath").html(path[path.length - 1]);
						path = cert.key.split('/');
						$("#keyPath").html(path[path.length - 1]);

						if (cert.makeTime != null) {
							$("#makeTime").val(layui.util.toDateString(cert.makeTime, 'yyyy-MM-dd HH:mm:ss'));
						}
						if (cert.endTime != null) {
							$("#endTime").val(layui.util.toDateString(cert.endTime, 'yyyy-MM-dd HH:mm:ss'));
						}
					} else {
						$("#domain").attr("disabled", false);
						$("#domain").removeClass("disabled");
						$("#encryption").attr("disabled", false);
						$("#encryption").removeClass("disabled");
						$("#type").attr("disabled", false);

						$("#certHidId").val("");
						$("#pem").val("");
						$("#key").val("");
						$("#pemPath").html("");
						$("#keyPath").html("");
						$("#makeTime").val("");
						$("#endTime").val("");
					}

					checkType(cert.type);
					checkDnsType(cert.dnsType != null ? cert.dnsType : 'ali');

					form.render();
					showWindow(certStr.edit);

				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	function showWindow(title) {
		layer.open({
			type: 1,
			title: title,
			area: ['1000px', '630px'],
			content: $('#certWindowDiv')
		});
	}

	function addOver() {
		if ($("#domain").val() == "") {
			layer.msg(certStr.error1);
			return;
		}

		if ($("#type").val() == 0) {
			if ($("#dnsType").val() == 'ali') {
				if ($("#aliKey").val() == '' || $("#aliSecret").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
			if ($("#dnsType").val() == 'dp') {
				if ($("#dpId").val() == '' || $("#dpKey").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
			if ($("#dnsType").val() == 'cf') {
				if ($("#cfEmail").val() == '' || $("#cfKey").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}

			if ($("#dnsType").val() == 'cfToken') {
				if ($("#cft").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}

			if ($("#dnsType").val() == 'gd') {
				if ($("#gdKey").val() == '' || $("#gdSecret").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
			if ($("#dnsType").val() == 'hw') {
				if ($("#hwUsername").val() == '' || $("#hwPassword").val() == '' || $("#hwDomainName").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
			if ($("#dnsType").val() == 'aws') {
				if ($("#awsAccessKeyId").val() == '' || $("#awsSecretAccessKey").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
			if ($("#dnsType").val() == 'ipv64') {
				if ($("#ipv64Token").val() == '') {
					layer.msg(commonStr.IncompleteEntry);
					return;
				}
			}
		}

		if ($("#type").val() == 1 && $("#pem").val() == $("#key").val()) {
			layer.msg(certStr.error5);
			return;
		}

		// Convert time fields to timestamps
		if ($("#makeTime").val() !== '') {
			$("#makeTime").val(new Date($("#makeTime").val()).getTime());
		}
		if ($("#endTime").val() !== '') {
			$("#endTime").val(new Date($("#endTime").val()).getTime());
		}

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/cert/addOver',
			data: $('#certAddForm').serialize(),
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					location.reload();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	function del(id) {
		if (confirm(commonStr.confirmDel)) {
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/del',
				data: {
					id: id
				},
				dataType: 'json',
				success: function(data) {
					if (data.success) {
						location.reload();
					} else {
						layer.msg(data.msg);
					}
				},
				error: function() {
					layer.alert(commonStr.errorInfo);
				}
			});
		}
	}

	function delMany() {
		if (confirm(commonStr.confirmDel)) {
			var ids = [];

			$("input[name='certIds']").each(function() {
				if ($(this).prop("checked")) {
					ids.push($(this).val());
				}
			});

			if (ids.length == 0) {
				layer.msg(commonStr.unselected);
				return;
			}

			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/del',
				data: {
					id: ids.join(",")
				},
				dataType: 'json',
				success: function(data) {
					if (data.success) {
						location.reload();
					} else {
						layer.msg(data.msg);
					}
				},
				error: function() {
					layer.alert(commonStr.errorInfo);
				}
			});
		}
	}

	function issue(id) {
		if (confirm(certStr.confirm1)) {
			showLoad();
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/apply',
				data: {
					id: id,
					type: "issue"
				},
				dataType: 'json',
				success: function(data) {
					closeLoad();
					if (data.success) {
						layer.alert(certStr.applySuccess, function(index) {
							layer.close(index);
							location.reload();
						});
					} else {
						layer.open({
							type: 0,
							area: ['810px', '400px'],
							content: data.msg
						});
					}
				},
				error: function() {
					closeLoad();
					layer.alert(commonStr.errorInfo);
				}
			});
		}
	}

	function renew(id) {
		if (confirm(certStr.confirm2)) {
			showLoad();
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/apply',
				data: {
					id: id,
					type: "renew"
				},
				dataType: 'json',
				success: function(data) {
					closeLoad();
					if (data.success) {
						layer.alert(certStr.renewSuccess, function(index) {
							layer.close(index);
							location.reload();
						});
					} else {
						layer.open({
							type: 0,
							area: ['810px', '400px'],
							content: data.msg
						});
					}
				},
				error: function() {
					closeLoad();
					layer.alert(commonStr.errorInfo);
				}
			});
		}
	}

	function selectPem() {
		rootSelect.selectOne(function(rs) {
			$("#pem").val(rs);
			$("#pemPath").html(rs);
		});
	}

	function selectKey() {
		rootSelect.selectOne(function(rs) {
			$("#key").val(rs);
			$("#keyPath").html(rs);
		});
	}

	function download(id) {
		window.open(ctx + "/adminPage/cert/download?id=" + id);
	}

	function clone(id) {
		if (confirm(serverStr.confirmClone)) {
			edit(id, true);
		}
	}

	function getTxtValue(id) {
		if (confirm(certStr.hostRecords)) {
			showLoad();
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/cert/getTxtValue',
				data: {
					id: id
				},
				dataType: 'json',
				success: function(data) {
					closeLoad();
					if (data.success) {
						var html = '';

						for (var i = 0; i < data.obj.length; i++) {
							var map = data.obj[i];
							html += '<tr>'
								+ '<td>' + map.domain + '</td>'
								+ '<td>' + map.type + '</td>'
								+ '<td>' + map.value + '</td>'
								+ '</tr>';
						}

						$("#certNotice").html(html);

						layer.open({
							type: 1,
							title: certStr.hostRecords,
							area: ['900px', '400px'],
							content: $('#certTxtDiv')
						});
					} else {
						layer.alert(data.msg);
					}
				},
				error: function() {
					layer.closeAll();
					layer.alert(commonStr.errorInfo);
				}
			});
		}
	}

	// === Expose public API ===
	ns.search = search;
	ns.checkDnsType = checkDnsType;
	ns.checkType = checkType;
	ns.add = add;
	ns.edit = edit;
	ns.showWindow = showWindow;
	ns.addOver = addOver;
	ns.del = del;
	ns.delMany = delMany;
	ns.issue = issue;
	ns.renew = renew;
	ns.selectPem = selectPem;
	ns.selectKey = selectKey;
	ns.download = download;
	ns.clone = clone;
	ns.getTxtValue = getTxtValue;

})(certNS);
