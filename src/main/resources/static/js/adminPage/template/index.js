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

	// 參數 name 變動 → 重算可選層級
	$(document).on('input change', "#paramList textarea[name='name']", function() {
		refreshDefTagAvailability();
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

/** 固定順序的小寫 tag 值（與 TemplateDefUtils.ALL 一致） */
var DEF_TAG_ORDER = ["http", "server", "server1", "server2", "stream", "location", "upstream"];
var DEF_HTTP_STACK = ["http", "server", "location", "upstream"];
var DEF_STREAM_STACK = ["stream", "server1", "server2"];

/** 與後端 TemplateDefUtils 對齊（精準子集；存檔以後端 filter 為準） */
var SERVER_LOCATION_ONLY = { "if": 1, "try_files": 1, "internal": 1, "alias": 1 };
var HTTP_TOP_ONLY = {
	"limit_req_zone": 1, "proxy_cache_path": 1, "lua_shared_dict": 1,
	"types": 1, "charset_map": 1, "vhost_traffic_status_zone": 1, "acme_issuer": 1
};
var HTTP_ONLY_NAMES = {
	"if": 1, "add_header": 1, "more_set_headers": 1, "more_clear_headers": 1,
	"more_set_input_headers": 1, "auth_request": 1, "auth_request_set": 1,
	"auth_basic": 1, "auth_basic_user_file": 1, "auth_jwt": 1, "auth_jwt_key_file": 1,
	"root": 1, "alias": 1, "index": 1, "try_files": 1, "rewrite": 1, "return": 1,
	"error_page": 1, "proxy_set_header": 1, "proxy_hide_header": 1, "proxy_pass_header": 1,
	"fastcgi_pass": 1, "fastcgi_param": 1, "uwsgi_pass": 1, "scgi_pass": 1, "grpc_pass": 1,
	"limit_req": 1, "limit_req_zone": 1, "limit_req_status": 1, "limit_req_dry_run": 1,
	"limit_req_log_level": 1, "client_max_body_size": 1, "client_body_buffer_size": 1,
	"expires": 1, "etag": 1, "gzip": 1, "brotli": 1, "ssi": 1, "charset": 1,
	"types": 1, "default_type": 1, "sendfile": 1, "tcp_nopush": 1, "keepalive_timeout": 1,
	"lingering_close": 1, "open_file_cache": 1, "stub_status": 1, "sub_filter": 1,
	"addition_types": 1, "image_filter": 1, "mp4": 1, "flv": 1, "hls": 1,
	"dav_methods": 1, "create_full_put_path": 1, "min_delete_depth": 1, "internal": 1,
	"mirror": 1, "slice": 1, "http2": 1, "http3": 1, "quic": 1
};
var STREAM_ONLY_NAMES = {
	"ssl_preread": 1, "proxy_protocol_timeout": 1, "proxy_responses": 1, "proxy_requests": 1,
	"proxy_socket_keepalive": 1, "js_access": 1, "js_preread": 1, "js_filter": 1,
	"preread_buffer_size": 1, "preread_timeout": 1, "udp_requests": 1, "udp_responses": 1
};

function normalizeDirectiveNameJs(raw) {
	var n = (raw || "").trim().toLowerCase();
	if (!n) {
		return "";
	}
	if (n === "if" || n.indexOf("if ") === 0 || n.indexOf("if(") === 0 || n.indexOf("if\t") === 0) {
		return "if";
	}
	var sp = n.indexOf(" ");
	if (sp > 0) {
		n = n.substring(0, sp);
	}
	var paren = n.indexOf("(");
	if (paren > 0) {
		n = n.substring(0, paren);
	}
	return n;
}

function collectParamDirectiveNames() {
	var names = [];
	$("#paramList textarea[name='name']").each(function() {
		var n = normalizeDirectiveNameJs($(this).val());
		if (n) {
			names.push(n);
		}
	});
	return names;
}

/**
 * 依參數推算允許層級（與 TemplateDefUtils.allowedContexts 對齊）
 * @returns {Object} map of context -> true
 */
function computeAllowedDefMap() {
	var names = collectParamDirectiveNames();
	var hasHttpOnly = false;
	var hasStreamOnly = false;
	var hasServerLocationOnly = false;
	var hasHttpTopOnly = false;
	for (var i = 0; i < names.length; i++) {
		var n = names[i];
		if (SERVER_LOCATION_ONLY[n]) {
			hasServerLocationOnly = true;
			hasHttpOnly = true;
		} else if (HTTP_TOP_ONLY[n]) {
			hasHttpTopOnly = true;
			hasHttpOnly = true;
		} else if (HTTP_ONLY_NAMES[n]) {
			hasHttpOnly = true;
		}
		if (STREAM_ONLY_NAMES[n]) {
			hasStreamOnly = true;
		}
	}
	var list;
	if (hasHttpOnly && hasStreamOnly) {
		list = [];
	} else if (hasStreamOnly) {
		list = DEF_STREAM_STACK;
	} else if (hasHttpTopOnly && !hasServerLocationOnly) {
		var onlyTop = true;
		for (var t = 0; t < names.length; t++) {
			var nt = names[t];
			if (HTTP_TOP_ONLY[nt] || STREAM_ONLY_NAMES[nt]) {
				continue;
			}
			if (HTTP_ONLY_NAMES[nt] || SERVER_LOCATION_ONLY[nt]) {
				onlyTop = false;
				break;
			}
		}
		list = onlyTop ? ["http"] : DEF_HTTP_STACK;
	} else if (hasServerLocationOnly && !hasHttpTopOnly) {
		var onlySl = true;
		for (var s = 0; s < names.length; s++) {
			var ns = names[s];
			if (SERVER_LOCATION_ONLY[ns]) {
				continue;
			}
			if (HTTP_ONLY_NAMES[ns] || HTTP_TOP_ONLY[ns]) {
				onlySl = false;
				break;
			}
		}
		list = onlySl ? ["server", "location"] : DEF_HTTP_STACK;
	} else if (hasHttpOnly) {
		list = DEF_HTTP_STACK;
	} else {
		list = DEF_TAG_ORDER;
	}
	var allowed = {};
	for (var j = 0; j < list.length; j++) {
		allowed[list[j]] = true;
	}
	return allowed;
}

/**
 * 禁用非法 tag（唯讀灰掉）、取消已勾但不合法的選項
 */
function refreshDefTagAvailability() {
	var allowed = computeAllowedDefMap();
	var disableTip = (templateStr.defDisabledTip || "此參數組合不適用此層級");
	$("input[name='defTag']").each(function() {
		var v = ($(this).val() || "").toLowerCase();
		var ok = !!allowed[v];
		this.disabled = !ok;
		if (!ok) {
			this.checked = false;
			$(this).closest('.def-tag').attr('title', disableTip);
		} else {
			// 還原預設 title（若有 data-default-title）
			var $lab = $(this).closest('.def-tag');
			var defTitle = $lab.attr('data-default-title');
			if (defTitle) {
				$lab.attr('title', defTitle);
			}
		}
	});
	syncDefTagClass();
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
	// 記住預設 title
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
	// 空參數：全部可選
	refreshDefTagAvailability();
}


function showWindow(title) {
	// 記住 title
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

	// 存檔前再濾一次
	refreshDefTagAvailability();

	var templateParams = [];
	$("#paramList").children().each(function() {

		var templateParam = {};
		templateParam.name = $(this).find("textarea[name='name']").val();
		templateParam.value = $(this).find("textarea[name='value']").val();

		templateParams.push(templateParam);
	})


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

				// 分組：已知 key 選中；否則走自訂
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

				// 參數渲染後再套 def（會依參數禁用非法層）
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
