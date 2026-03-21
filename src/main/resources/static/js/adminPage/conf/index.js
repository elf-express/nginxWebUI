// CodeMirror 編輯器實例
var cmLeft = null;
var cmRight = null;

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
					area: ['810px', '400px'],
					content: data.obj
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
					area: ['810px', '400px'],
					content: data.obj
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
					area: ['810px', '400px'],
					content: data.obj
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
