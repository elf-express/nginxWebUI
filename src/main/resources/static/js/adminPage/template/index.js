$(function() {
	// Per-group checkbox
	form.on('checkbox(checkGroup)', function(data) {
		var group = $(data.elem).data('group');
		$("input[name='ids'][data-group='" + group + "']").prop("checked", data.elem.checked);
		form.render();
	});

	// 分組下拉：選「自訂」時顯示手輸 groupName
	form.on('select(templateGroup)', function(data) {
		toggleGroupCustom(data.value === '_custom');
	});

	// def tag：勾選時加 .on；禁用項不可勾
	$(document).on('change', "input[name='defTag']", function() {
		if (this.disabled) {
			this.checked = false;
		}
		syncDefTagClass();
	});

	// 參數 name 變動 → 後端 allowedDefs 重算（debounced）
	var _defRefreshTimer = null;
	$(document).on('input change', "#paramList textarea[name='name']", function() {
		if (_defRefreshTimer) {
			clearTimeout(_defRefreshTimer);
		}
		_defRefreshTimer = setTimeout(function() {
			refreshDefTagAvailability();
		}, 200);
	});

	// Init collapse
	element.init();
})

function syncDefTagClass() {
	$("input[name='defTag']").each(function() {
		var $lab = $(this).closest('.def-tag');
		$lab.toggleClass('on', this.checked && !this.disabled);
		$lab.toggleClass('disabled', !!this.disabled);
	});
}

function toggleGroupCustom(show) {
	if (show) {
		$("#groupNameCustomWrap").show();
	} else {
		$("#groupNameCustomWrap").hide();
		$("#groupNameCustom").val("");
	}
}

/** 解析實際要送出的 groupName */
function resolveGroupName() {
	var sel = $("#groupName").val() || "";
	if (sel === "_custom") {
		return ($("#groupNameCustom").val() || "").trim();
	}
	return sel;
}

/** 固定順序（與 TemplateDefUtils.ALL 一致；僅作顯示序） */
var DEF_TAG_ORDER = ["http", "server", "server1", "server2", "stream", "location", "upstream"];

function collectParamDirectiveNames() {
	var names = [];
	$("#paramList textarea[name='name']").each(function() {
		var n = ($(this).val() || "").trim();
		if (n) {
			names.push(n);
		}
	});
	return names;
}

/**
 * 套用允許清單到 tag UI
 * @param {string[]} list 後端回傳的 allowed context 陣列
 */
function applyAllowedDefList(list) {
	var allowed = {};
	if (list && list.length) {
		for (var i = 0; i < list.length; i++) {
			allowed[String(list[i]).toLowerCase()] = true;
		}
	}
	// 空陣列 = 全部禁用（衝突或只手動）
	var disableTip = (templateStr.defDisabledTip || "此參數組合不適用此層級");
	$("input[name='defTag']").each(function() {
		var v = ($(this).val() || "").toLowerCase();
		var ok = !!allowed[v];
		this.disabled = !ok;
		if (!ok) {
			this.checked = false;
			$(this).closest('.def-tag').attr('title', disableTip);
		} else {
			var $lab = $(this).closest('.def-tag');
			var defTitle = $lab.attr('data-default-title');
			if (defTitle) {
				$lab.attr('title', defTitle);
			}
		}
	});
	syncDefTagClass();
}

/**
 * 呼叫後端 /adminPage/template/allowedDefs（唯一真相，避免 JS 複製清單漂移）
 * 失敗時保守：全部禁用自動套用
 */
function refreshDefTagAvailability(done) {
	var names = collectParamDirectiveNames();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/template/allowedDefs',
		data: {
			paramJson: JSON.stringify(names.map(function(n) {
				return { name: n, value: '' };
			}))
		},
		dataType: 'json',
		success: function(data) {
			if (data && data.success && Array.isArray(data.obj)) {
				applyAllowedDefList(data.obj);
			} else if (data && data.success && data.obj == null) {
				// 無參數時後端回全部
				applyAllowedDefList(DEF_TAG_ORDER);
			} else {
				applyAllowedDefList(names.length === 0 ? DEF_TAG_ORDER : []);
			}
			if (typeof done === 'function') {
				done();
			}
		},
		error: function() {
			// 離線／錯誤：無參數全開，有參數全關（不冒險）
			applyAllowedDefList(names.length === 0 ? DEF_TAG_ORDER : []);
			if (typeof done === 'function') {
				done();
			}
		}
	});
}

/** 讀取多選 tag → 逗號分隔小寫字串（跳過 disabled） */
function getDefValue() {
	var picked = {};
	$("input[name='defTag']:checked:not(:disabled)").each(function() {
		var v = ($(this).val() || "").toLowerCase().trim();
		if (v) {
			picked[v] = true;
		}
	});
	var out = [];
	for (var i = 0; i < DEF_TAG_ORDER.length; i++) {
		if (picked[DEF_TAG_ORDER[i]]) {
			out.push(DEF_TAG_ORDER[i]);
		}
	}
	return out.join(",");
}

/** 依 def 字串勾選 tag（兼容舊單值與新多值） */
function setDefTags(def) {
	$("input[name='defTag']").prop("checked", false).prop("disabled", false);
	if (def) {
		var parts = String(def).split(/[,;\s]+/);
		for (var i = 0; i < parts.length; i++) {
			var p = (parts[i] || "").toLowerCase().trim();
			if (!p) {
				continue;
			}
			$("input[name='defTag'][value='" + p + "']").prop("checked", true);
		}
	}
	$(".def-tag").each(function() {
		if (!$(this).attr('data-default-title')) {
			$(this).attr('data-default-title', $(this).attr('title') || '');
		}
	});
	refreshDefTagAvailability();
}

function add() {
	$("#id").val("");
	$("#name").val("");
	setDefTags("");
	$("#groupName option:first").prop("selected", true);
	toggleGroupCustom(false);
	$("#paramList").html("");

	form.render();
	showWindow(templateStr.add);
	refreshDefTagAvailability();
}


function showWindow(title) {
	$(".def-tag").each(function() {
		if (!$(this).attr('data-default-title')) {
			$(this).attr('data-default-title', $(this).attr('title') || '');
		}
	});
	layer.open({
		type: 1,
		title: title,
		area: ['min(850px, 92vw)', 'min(680px, 92vh)'],
		content: $('#windowDiv')
	});
}

function addOver() {
	if ($("#name").val() == "") {
		layer.msg(templateStr.noname);
		return;
	}

	var groupName = resolveGroupName();
	if (!groupName) {
		layer.msg(templateStr.groupRequired || templateStr.noname);
		return;
	}

	// 存檔前再向後端對齊允許層級，再取值
	refreshDefTagAvailability(function() {
		var templateParams = [];
		$("#paramList").children().each(function() {
			var templateParam = {};
			templateParam.name = $(this).find("textarea[name='name']").val();
			templateParam.value = $(this).find("textarea[name='value']").val();
			templateParams.push(templateParam);
		});

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/template/addOver',
			data: {
				id: $("#id").val(),
				name: $("#name").val(),
				def: getDefValue(),
				groupName: groupName,
				paramJson: JSON.stringify(templateParams),
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
	});
}

function edit(id) {

	$.ajax({
		type: 'GET',
		url: ctx + '/adminPage/template/detail',
		dataType: 'json',
		data: {
			id: id
		},
		success: function(data) {
			if (data.success) {
				var ext = data.obj;
				var list = ext.paramList;

				$("#id").val(ext.template.id);
				$("#name").val(ext.template.name);

				var gn = ext.template.groupName || "";
				var known = false;
				$("#groupName option").each(function() {
					if ($(this).val() === gn) {
						known = true;
					}
				});
				if (known && gn) {
					$("#groupName").val(gn);
					toggleGroupCustom(false);
				} else if (gn) {
					$("#groupName").val("_custom");
					toggleGroupCustom(true);
					$("#groupNameCustom").val(gn);
				} else {
					$("#groupName option:first").prop("selected", true);
					toggleGroupCustom(false);
				}

				var html = ``;
				for (let i = 0; i < list.length; i++) {
					var param = list[i];
					var uuid = guid();
					html += `<tr name="param" id=${uuid}>
								<td>
									<textarea  name="name" class="layui-textarea">${param.name || ''}</textarea>
								</td>
								<td  style="width: 50%;">
									<textarea  name="value" class="layui-textarea">${param.value || ''}</textarea>
								</td>
								<td>
									<button type="button" class="layui-btn layui-btn-sm layui-btn-danger" onclick="delTr('${uuid}')">${commonStr.del}</button>
									
									<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', -1)">${commonStr.up}</button>
									<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', 1)">${commonStr.down}</button>
								</td>
							</tr>`
				}
				$("#paramList").html(html);

				setDefTags(ext.template.def || "");

				form.render();
				showWindow(templateStr.edit);
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
			url: ctx + '/adminPage/template/del',
			data: {
				id: id
			},
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					location.reload();
				} else {
					layer.msg(data.msg)
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

		$("input[name='ids']").each(function() {
			if ($(this).prop("checked")) {
				ids.push($(this).val());
			}
		})

		if (ids.length == 0) {
			layer.msg(commonStr.unselected);
			return;
		}

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/template/del',
			data: {
				id: ids.join(",")
			},
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					location.reload();
				} else {
					layer.msg(data.msg)
				}
			},
			error: function() {
				layer.alert("请求失败，请刷新重试");
			}
		});
	}
}


function addParam() {
	var uuid = guid();
	var html = `<tr name="param" id=${uuid}>
				<td>
					<textarea  name="name" class="layui-textarea"></textarea>
				</td>
				<td  style="width: 50%;">
					<textarea  name="value" class="layui-textarea"></textarea>
				</td>
				<td>
					<button type="button" class="layui-btn layui-btn-sm layui-btn-danger" onclick="delTr('${uuid}')">${commonStr.del}</button>
					
					<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', -1)">${commonStr.up}</button>
					<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', 1)">${commonStr.down}</button>
				</td>
			</tr>`
	$("#paramList").append(html);
	refreshDefTagAvailability();
}

function delTr(id) {
	$("#" + id).remove();
	refreshDefTagAvailability();
}
