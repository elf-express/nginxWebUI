$(function() {
	form.on('checkbox(checkAll)', function(data) {
		if (data.elem.checked) {
			$("input[name='ids']").prop("checked", true)
		} else {
			$("input[name='ids']").prop("checked", false)
		}

		form.render();
	});

	// ── 模組依賴 ──
	// key 依賴 value（value 必須先載入）
	var MODULE_DEPS = {
		'ngx_stream_geoip2_module.so': 'ngx_stream_module.so',
		'ngx_http_lua_module.so': 'ndk_http_module.so'
	};

	// 反向對應：value → [依賴它的 key]
	var MODULE_DEPENDENTS = {};
	for (var dep in MODULE_DEPS) {
		var parent = MODULE_DEPS[dep];
		if (!MODULE_DEPENDENTS[parent]) {
			MODULE_DEPENDENTS[parent] = [];
		}
		MODULE_DEPENDENTS[parent].push(dep);
	}

	function sendModuleToggle(id, enable) {
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/basic/setModuleEnable',
			data: { id: id, enable: enable ? 1 : 0 },
			dataType: 'json',
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	}

	form.on('switch(moduleEnable)', function(data) {
		var id = data.elem.value;
		var moduleName = $(data.elem).data('name');
		var enabling = data.elem.checked;

		// 送出本身的切換
		sendModuleToggle(id, enabling);

		if (enabling) {
			// 啟用時：自動啟用依賴模組
			var depName = MODULE_DEPS[moduleName];
			if (depName) {
				var depCheckbox = $("input[name='moduleEnable'][data-name='" + depName + "']");
				if (depCheckbox.length > 0 && !depCheckbox[0].checked) {
					depCheckbox[0].checked = true;
					form.render('checkbox');
					sendModuleToggle(depCheckbox.val(), true);
					layer.msg(moduleStr.depAutoEnabled.replace('{0}', depName));
				}
			}
		} else {
			// 停用時：自動停用相依模組
			var dependents = MODULE_DEPENDENTS[moduleName];
			if (dependents) {
				for (var i = 0; i < dependents.length; i++) {
					var childName = dependents[i];
					var childCheckbox = $("input[name='moduleEnable'][data-name='" + childName + "']");
					if (childCheckbox.length > 0 && childCheckbox[0].checked) {
						childCheckbox[0].checked = false;
						form.render('checkbox');
						sendModuleToggle(childCheckbox.val(), false);
						layer.msg(moduleStr.depAutoDisabled.replace('{0}', childName));
					}
				}
			}
		}
	});
})

function search() {
	$("input[name='curr']").val(1);
	$("#searchForm").submit();
}

function add() {
	$("#id").val(""); 
	$("#name").val(""); 
	$("#value").val(""); 
	
	showWindow(basicStr.add);
}


function showWindow(title){
	layer.open({
		type : 1,
		title : title,
		area : [ '600px', '400px' ], // 宽高
		content : $('#windowDiv')
	});
}

function addOver() {
	if ($("#name").val() == "") {
		layer.msg(basicStr.nameNotice);
		return;
	}
	if ($("#value").val() == "") {
		layer.msg(basicStr.valueNotice);
		return;
	}
	
	
	$.ajax({
		type : 'POST',
		url : ctx + '/adminPage/basic/addOver',
		data : $('#addForm').serialize(),
		dataType : 'json',
		success : function(data) {
			if (data.success) {
				location.reload();
			} else {
				layer.msg(data.msg);
			}
		},
		error : function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}

function edit(id) {
	$("#id").val(id); 
	
	$.ajax({
		type : 'GET',
		url : ctx + '/adminPage/basic/detail',
		dataType : 'json',
		data : {
			id : id
		},
		success : function(data) {
			if (data.success) {
				var http = data.obj;
				$("#id").val(http.id); 
				$("#value").val(http.value); 
				$("#name").val(http.name);
				
				form.render();
				showWindow(basicStr.edit);
			}else{
				layer.msg(data.msg);
			}
		},
		error : function() {
			layer.alert(commonStr.errorInfo);
		}
	});
}

function del(id){
	if(confirm(commonStr.confirmDel)){
		$.ajax({
			type : 'POST',
			url : ctx + '/adminPage/basic/del',
			data : {
				id : id
			},
			dataType : 'json',
			success : function(data) {
				if (data.success) {
					location.reload();
				}else{
					layer.msg(data.msg)
				}
			},
			error : function() {
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
			url : ctx + '/adminPage/basic/del',
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


function setOrder(id, count){
	showLoad();
	$.ajax({
		type : 'POST',
		url : ctx + '/adminPage/basic/setOrder',
		data : {
			id : id,
			count : count
		},
		dataType : 'json',
		success : function(data) {
			closeLoad();
			if (data.success) {
				location.reload();
			}else{
				layer.msg(data.msg)
			}
		},
		error : function() {
			closeLoad();
			layer.alert(commonStr.errorInfo);
		}
	});
}
