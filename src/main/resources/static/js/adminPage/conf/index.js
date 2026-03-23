// CodeMirror 編輯器實例
var cmLeft = null;
var cmRight = null;
var isEditMode = false;

// CodeMirror 通用配置
var cmOptions = {
	mode: 'nginx',
	theme: 'monokai',
	lineNumbers: true,
	lineWrapping: false,
	tabSize: 4,
	indentUnit: 4,
	matchBrackets: true,
};

$(function() {
	// 初始化左側 CodeMirror（可編輯）
	cmLeft = CodeMirror.fromTextArea(document.getElementById('nginxContent'), Object.assign({}, cmOptions, {
		readOnly: false
	}));

	// 初始化右側 CodeMirror（唯讀）
	cmRight = CodeMirror.fromTextArea(document.getElementById('org'), Object.assign({}, cmOptions, {
		readOnly: true
	}));

	loadOrg();
	loadConf();

	form.on('switch(decompose)', function(data) {
		if (isEditMode) {
			layer.msg(confStr.exitEditConfirm);
			data.elem.checked = !data.elem.checked;
			form.render();
			return;
		}

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/conf/decompose',
			data: {
				decompose: data.elem.checked
			},
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					loadConf();
					loadOrg();
				} else {
					layer.msg(data.msg);
				}
			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	});

	nginxStatus();
})

function nginxStatus() {

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/nginxStatus',
		dataType: 'json',
		success: function(data) {
			if (data.success) {
				$("#nginxStatus").html(data.obj);
			}
		},
		error: function() {

		}
	});
}

function buildJson() {
	var json = {};
	json.nginxPath = $("#nginxPath").val();
	json.nginxContent = Base64.encode(cmLeft.getValue());
	json.subContent = [];
	json.subName = [];
	$("textarea[name='subContent']").each(function() {
		json.subContent.push(Base64.encode($(this).val()));
	})
	$("input[name='subName']").each(function() {
		json.subName.push($(this).val());
	})
	return json;
}

function replace() {
	if ($("#nginxPath").val() == '') {
		layer.msg(confStr.jserror2);
		return;
	}

	var json = buildJson();

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/replace',
		data: {
			json: JSON.stringify(json)
		},
		dataType: 'json',
		success: function(data) {
			if (data.success) {
				layer.msg(data.obj);
				loadOrg();

				if (isEditMode) {
					isEditMode = false;
					$("#editModeBtn").show();
					$("#exitEditBtn").hide();
					$("#editModeBanner").hide();
					$(".CodeMirror").first().css("border", "1px solid #444");
					loadConf();
				}
			} else {
				layer.alert(data.msg);
			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}


function loadConf() {
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/loadConf',
		data: {

		},
		dataType: 'json',
		success: function(data) {
			if (data.success) {
				var confExt = data.obj
				cmLeft.setValue(confExt.conf);

				var html = "";
				for (var i = 0; i < confExt.fileList.length; i++) {
					var confFile = confExt.fileList[i];
					var uuid = confFile.name.replace(/\./g, "-");
					html += '<div class="title" onclick="showHide(\'' + uuid + '\')">' + confFile.name + ' ▼</div>'
							+ '<textarea lang="' + uuid + '" class="layui-textarea conf sub" name="subContent" style="height: 200px; resize: none;" spellcheck="false">' + confFile.conf + '</textarea>'
							+ '<input type="hidden" name="subName" value="' + confFile.name + '">';
				}

				$("#nginxContentOther").html(html);

				$(".sub").each(function() {
					$(this).parent().hide();
				});
			} else {
				layer.alert(data.msg);
			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}

function loadOrg() {

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/loadOrg',
		data: {
			nginxPath: $("#nginxPath").val()
		},
		dataType: 'json',
		success: function(data) {
			if (data.success) {
				var confExt = data.obj
				cmRight.setValue(confExt.conf);

				var html = "";
				for (var i = 0; i < confExt.fileList.length; i++) {
					var confFile = confExt.fileList[i];
					var uuid = confFile.name.replace(/\./g, "-");
					html += '<div class="title" onclick="showHide(\'' + uuid + '\')">' + confFile.name + ' ▼</div>'
					+ '<textarea lang="' + uuid + '" class="layui-textarea org sub" style="height: 200px; resize: none; background-color: #ededed;" readonly="readonly" spellcheck="false">' + confFile.conf + '</textarea>';
				}
				$("#orgOther").html(html);

				$(".sub").each(function() {
					$(this).parent().hide();
				});
			} else {
				layer.alert(data.msg);
			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}

function showHide(id) {

	if ($('textarea[lang="' + id + '"]').parent().is(':hidden')) {
		$('textarea[lang="' + id + '"]').parent().show();
	} else {
		$('textarea[lang="' + id + '"]').parent().hide();
	}

}

function check() {
	if ($("#nginxPath").val() == '') {
		layer.msg(confStr.jserror2);
		return;
	}

	if ($("#nginxExe").val() == '') {
		layer.msg(confStr.jserror3);
		return;
	}

	if ($("#nginxExe").val().indexOf('/') > -1 || $("#nginxExe").val().indexOf('\\') > -1) {
		if ($("#nginxDir").val() == '') {
			layer.msg(confStr.jserror4);
			return;
		}
	}

	var json = buildJson();

	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/check',
		data: {
			nginxPath: $("#nginxPath").val(),
			nginxExe: $("#nginxExe").val(),
			nginxDir: $("#nginxDir").val(),
			json: JSON.stringify(json)
		},
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				layer.open({
					type: 0,
					area: ['810px', '500px'],
					content: parseNginxErrors(data.obj)
				});
			}
		},
		error: function() {
			closeLoad();
			layer.alert(commonStr.errorInfo);
		}
	});
}

function reload() {
	if ($("#nginxPath").val() == '') {
		layer.msg(confStr.jserror2);
		return;
	}

	if ($("#nginxExe").val() == '') {
		layer.msg(confStr.jserror3);
		return;
	}

	if ($("#nginxExe").val().indexOf('/') > -1 || $("#nginxExe").val().indexOf('\\') > -1) {
		if ($("#nginxDir").val() == '') {
			layer.msg(confStr.jserror4);
			return;
		}
	}

	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/reload',
		data: {
			nginxPath: $("#nginxPath").val(),
			nginxExe: $("#nginxExe").val(),
			nginxDir: $("#nginxDir").val()
		},
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				layer.open({
					type: 0,
					area: ['810px', '500px'],
					content: parseNginxErrors(data.obj)
				});
			}
		},
		error: function() {
			closeLoad();
			layer.alert(commonStr.errorInfo);
		}
	});

}


function saveCmd() {

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/saveCmd',
		data: {
			nginxExe: $("#nginxExe").val(),
			nginxDir: $("#nginxDir").val(),
			nginxPath: $("#nginxPath").val()
		},
		dataType: 'json',
		success: function(data) {
			if (data.success) {

			}
		},
		error: function() {
			layer.alert(commonStr.errorInfo);
		}
	});

}



function selectRootCustom(inputId) {
	rootSelect.selectOne(function callBack(val) {
		$("#" + inputId).val(val);
		saveCmd();

		if (inputId == 'nginxPath') {
			loadOrg();
		}
	});
}


function diffUsingJS() {
	// get the baseText and newText values from the two CodeMirror editors
	var base = difflib.stringAsLines(cmRight.getValue());
	var newtxt = difflib.stringAsLines(cmLeft.getValue());

	var sm = new difflib.SequenceMatcher(base, newtxt);
	var opcodes = sm.get_opcodes();
	var diffoutputdiv = $("#diffoutput");
	while (diffoutputdiv.firstChild) {
		diffoutputdiv.removeChild(diffoutputdiv.firstChild);
	}

	diffoutputdiv.html("");
	diffoutputdiv.append(diffview.buildView({
		baseTextLines: base,
		newTextLines: newtxt,
		opcodes: opcodes,
		baseTextName: confStr.build,
		newTextName: confStr.target,
		viewType: 1
	}));

	layer.open({
		type: 1,
		title: false,
		area: ['1000px', '90%'],
		content: $('#diffoutput')
	});
}

function runCmd(type) {
	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/getLastCmd',
		data: {
			type: type
		},
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				$("#nginxStop").hide();
				$("#nginxStart").hide();

				var dir = "";
				if ($("#nginxDir").val() != '') {
					dir = " -p " + $("#nginxDir").val();
				}

				$("#startNormal").attr("title", $("#nginxExe").val() + " -c " + $("#nginxPath").val() + dir);
				$("#stopNormal").attr("title", $("#nginxExe").val() + " -s stop" + dir);

				var cmd = data.obj;
				if (type == 'cmdStop') {
					$("#nginxStop").show();
					$("#stopNormal").prop("checked", true);

					$("#nginxStop input[name='cmd']").each(function() {
						if ($(this).attr("title") == cmd || $(this).attr("id") == cmd) {
							$(this).prop("checked", true);
						}
					})
				} else {
					$("#nginxStart").show();
					$("#startNormal").prop("checked", true);

					$("#nginxStart input[name='cmd']").each(function() {
						if ($(this).attr("title") == cmd || $(this).attr("id") == cmd) {
							$(this).prop("checked", true);
						}
					})
				}

				form.render();


				layer.open({
					type: 1,
					title: confStr.runCmd,
					area: ['750px', '400px'],
					content: $('#cmdForm')
				});
			}
		},
		error: function() {
			closeLoad();
		}
	});



}

function runCmdOver() {
	var cmd = "";
	var type = "";
	$("input[name='cmd']").each(function() {
		if ($(this).prop("checked")) {
			if ($(this).attr("id") == 'stopNormal') {
				cmd = "stopNormal";
			} else if ($(this).attr("id") == 'startNormal') {
				cmd = "startNormal";
			} else {
				cmd = $(this).attr("title");
			}

			type = $(this).attr("lang");
		}
	})

	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/runCmd',
		data: {
			cmd: cmd,
			type: type
		},
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				layer.open({
					type: 0,
					area: ['810px', '500px'],
					content: parseNginxErrors(data.obj)
				});
			}

			setTimeout(function() {
				nginxStatus();
			}, 3000);
		},
		error: function() {
			closeLoad();
		}
	});
}


function showBak() {

	layer.open({
		type: 2,
		title: bakStr.bakFile,
		area: ['900px', '90%'],
		content: ctx + "/adminPage/bak"
	});
}

// ── A1: 編輯模式 ──

function enterEditMode() {
	layer.confirm(confStr.editModeConfirm, {
		btn: [commonStr.submit, commonStr.close]
	}, function(index) {
		layer.close(index);
		isEditMode = true;

		// 複製右側（實際 conf）到左側
		cmLeft.setValue(cmRight.getValue());

		// 同步子檔案（decompose 模式下）
		$("textarea.org.sub").each(function(i) {
			var leftSub = $("textarea.conf.sub").eq(i);
			if (leftSub.length) {
				leftSub.val($(this).val());
			}
		});

		// UI 切換
		$("#editModeBtn").hide();
		$("#exitEditBtn").show();
		$("#editModeBanner").show();
		$(".CodeMirror").first().css("border", "2px solid #FF5722");
	});
}

function exitEditMode() {
	layer.confirm(confStr.exitEditConfirm, {
		btn: [commonStr.submit, commonStr.close]
	}, function(index) {
		layer.close(index);
		isEditMode = false;

		// 恢復生成的 conf
		loadConf();

		// UI 切換
		$("#editModeBtn").show();
		$("#exitEditBtn").hide();
		$("#editModeBanner").hide();
		$(".CodeMirror").first().css("border", "1px solid #444");
	});
}

// ── A2: Nginx 錯誤診斷 ──

function parseNginxErrors(html) {
	var patterns = [
		[/unknown directive "([^"]+)"/i,                  'diagUnknownDir',    'diagUnknownDirTip'],
		[/host not found in upstream "([^"]+)"/i,         'diagUpstream',      'diagUpstreamTip'],
		[/bind\(\) to [^:]+:(\d+) failed/i,              'diagBindFail',      'diagBindFailTip'],
		[/open\(\) "[^"]+" failed \(2: No such file/i,   'diagFileNotFound',  'diagFileNotFoundTip'],
		[/SSL: error:/i,                                  'diagSslError',      'diagSslErrorTip'],
		[/ssl_certificate.*No such file/i,                'diagSslError',      'diagSslErrorTip'],
		[/zero size shared memory zone/i,                 'diagZeroZone',      'diagZeroZoneTip'],
		[/duplicate location "([^"]+)"/i,                 'diagDupLocation',   'diagDupLocationTip'],
		[/conflicting server name "([^"]+)"/i,            'diagDupServer',     'diagDupServerTip'],
		[/nginx\.pid|open\(\).*\.pid.*failed|PID|cannot find the file/i, 'diagPidError', 'diagPidErrorTip'],
		[/the "ssl" directive is deprecated/i,            'diagSslDeprecated', 'diagSslDeprecatedTip'],
		[/\(13: Permission denied\)/i,                    'diagPermission',    'diagPermissionTip'],
		[/\(1: Operation not permitted\)/i,               'diagPermission',    'diagPermissionTip'],
		[/unexpected end of file|unexpected "}"|invalid number of arguments|invalid parameter/i, 'diagSyntaxError', 'diagSyntaxErrorTip']
	];

	// 去除 HTML 標籤後做正則匹配
	var plainText = html.replace(/<[^>]+>/g, ' ');

	var matches = [];
	for (var i = 0; i < patterns.length; i++) {
		var m = plainText.match(patterns[i][0]);
		if (m) {
			matches.push({
				detail: m[1] || '',
				desc: confStr[patterns[i][1]],
				tip: confStr[patterns[i][2]]
			});
		}
	}

	if (matches.length === 0) {
		return html;
	}

	var diagHtml = '<hr style="margin:10px 0; border-color:#eee;">'
		+ '<div style="background:#fff3e0; border-left:4px solid #FF9800; padding:10px 15px; margin-top:5px; border-radius:0 4px 4px 0;">'
		+ '<div style="font-weight:bold; font-size:14px; color:#E65100; margin-bottom:8px;">'
		+ '\u26A0 ' + confStr.diagTitle
		+ '</div>';

	for (var j = 0; j < matches.length; j++) {
		var item = matches[j];
		diagHtml += '<div style="margin-bottom:8px; padding:6px 10px; background:#fff8e1; border-radius:3px;">'
			+ '<div style="font-weight:bold; color:#E65100;">'
			+ item.desc
			+ (item.detail ? ' <code style="background:#ffecb3; padding:1px 4px; border-radius:2px;">' + item.detail + '</code>' : '')
			+ '</div>'
			+ '<div style="color:#666; margin-top:3px;">'
			+ item.tip
			+ '</div>'
			+ '</div>';
	}

	diagHtml += '</div>';
	return html + diagHtml;
}

// ── Test Connectivity ──

function testConnectivity() {
	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/conf/testConnectivity',
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				showConnectivityResults(data.obj);
			} else {
				layer.alert(data.msg || commonStr.errorInfo);
			}
		},
		error: function() {
			closeLoad();
			layer.alert(commonStr.errorInfo);
		}
	});
}

function showConnectivityResults(results) {
	if (!results || results.length === 0) {
		layer.msg(confStr.testConnNoTarget);
		return;
	}

	var okCount = 0;
	var failCount = 0;

	var html = '<div style="padding: 15px;">';
	html += '<table class="layui-table" lay-size="sm">';
	html += '<thead><tr>';
	html += '<th>' + confStr.testConnServer + '</th>';
	html += '<th>' + confStr.testConnLocation + '</th>';
	html += '<th>' + confStr.testConnDest + '</th>';
	html += '<th style="width:80px; text-align:center;">' + confStr.testConnStatus + '</th>';
	html += '</tr></thead>';
	html += '<tbody>';

	for (var i = 0; i < results.length; i++) {
		var r = results[i];
		var isOk = (r.status === 'OK');
		if (isOk) okCount++; else failCount++;

		var statusHtml = isOk
			? '<span style="color:#5FB878;font-weight:bold;">&#10004; OK</span>'
			: '<span style="color:#FF5722;font-weight:bold;">&#10008; FAIL</span>';

		html += '<tr' + (isOk ? '' : ' style="background-color:#FFF3E0;"') + '>';
		html += '<td>' + escapeHtml(r.server) + '</td>';
		html += '<td>' + escapeHtml(r.location) + '</td>';
		html += '<td><code>' + escapeHtml(r.destination) + '</code></td>';
		html += '<td style="text-align:center;">' + statusHtml + '</td>';
		html += '</tr>';
	}

	html += '</tbody></table>';

	html += '<div style="margin-top:10px; padding:8px 12px; background:#f7f7f7; border-radius:4px;">';
	html += confStr.testConnSummary + ': ';
	html += '<span style="color:#5FB878; font-weight:bold;">' + okCount + ' OK</span>';
	html += ' / ';
	html += '<span style="color:' + (failCount > 0 ? '#FF5722' : '#5FB878') + '; font-weight:bold;">' + failCount + ' FAIL</span>';
	html += '</div>';
	html += '</div>';

	layer.open({
		type: 1,
		title: confStr.testConn,
		area: ['800px', '500px'],
		content: html
	});
}

function escapeHtml(str) {
	if (!str) return '';
	return str.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;');
}
