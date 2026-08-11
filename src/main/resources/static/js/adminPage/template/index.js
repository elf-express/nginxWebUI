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

	// Init collapse
	element.init();
})

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

function add() {
	$("#id").val("");
	$("#name").val("");
	$("#def option:first").prop("selected", true);
	$("#groupName option:first").prop("selected", true);
	toggleGroupCustom(false);
	$("#paramList").html("");

	form.render();
	showWindow(templateStr.add);
}


function showWindow(title) {
	layer.open({
		type: 1,
		title: title,
		// 較寬以完整顯示長模板名與「預設配置到」下拉選項（+50px）
		area: ['min(850px, 92vw)', 'min(620px, 90vh)'],
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
			def: $("#def").val(),
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
				$("#def").val(ext.template.def || "");

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
									<textarea  name="name" class="layui-textarea">${param.name}</textarea>
								</td>
								<td  style="width: 50%;">
									<textarea  name="value" class="layui-textarea">${param.value}</textarea>
								</td>
								<td>
									<button type="button" class="layui-btn layui-btn-sm layui-btn-danger" onclick="delTr('${uuid}')">${commonStr.del}</button>
									
									<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', -1)">${commonStr.up}</button>
									<button class="layui-btn layui-btn-normal layui-btn-sm" onclick="setParamOrder('${uuid}', 1)">${commonStr.down}</button>
								</td>
							</tr>`
				}
				$("#paramList").html(html);


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
}

function delTr(id) {
	$("#" + id).remove();
}
