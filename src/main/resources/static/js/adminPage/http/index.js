$(function() {
	form.on('switch(enable)', function(data) {

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/http/setEnable',
			data: {
				enable: data.elem.checked ? 1 : 0,
				id: data.elem.value
			},
			dataType: 'json',
			success: function(data) {

			},
			error: function() {
				layer.alert(commonStr.errorInfo);
			}
		});
	});

	// 分組 checkAll
	form.on('checkbox(checkGroup)', function(data) {
		var group = $(data.elem).data('group');
		$("input[name='ids'][data-group='" + group + "']").prop("checked", data.elem.checked);
		form.render();
	});

	// 初始化 collapse
	layui.element.init();

})


function search() {
	$("input[name='curr']").val(1);
	$("#searchForm").submit();
}

function add() {
	$("#id").val("");
	$("#name").val("");
	$("#value").val("");

	showWindow(httpStr.add);
}


function showWindow(title) {
	layer.open({
		type: 1,
		title: title,
		area: ['600px', '400px'], // 宽高
		content: $('#windowDiv')
	});
}

function addOver() {
	if ($("#name").val() == "") {
		layer.msg(httpStr.noname);
		return;
	}

	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/http/addOver',
		data: $('#addForm').serialize(),
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

var batchInputIndex;
function showBatchInput() {
	$("#batchInputText").val("");
	batchInputIndex = layer.open({
		type: 1,
		title: serverStr.batchInputTitle,
		area: ['700px', '500px'],
		content: $('#batchInputDiv')
	});
}

function closeBatchInput() {
	layer.close(batchInputIndex);
}

function parseBatchInput() {
	var text = $("#batchInputText").val();
	if (!text || text.trim() === "") {
		closeBatchInput();
		return;
	}

	var lines = text.split("\n");
	var items = [];
	for (var i = 0; i < lines.length; i++) {
		var line = lines[i].trim();
		if (line === "") continue;

		if (line.endsWith(";")) {
			line = line.substring(0, line.length - 1).trim();
		}

		var name = "";
		var value = "";
		var spaceIndex = line.indexOf(" ");
		if (spaceIndex > 0) {
			name = line.substring(0, spaceIndex);
			value = line.substring(spaceIndex + 1).trim();
		} else {
			name = line;
			value = "";
		}
		items.push({name: name, value: value});
	}

	if (items.length === 0) {
		closeBatchInput();
		return;
	}

	closeBatchInput();

	var completed = 0;
	for (var j = 0; j < items.length; j++) {
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/http/addOver',
			data: {name: items[j].name, value: items[j].value},
			dataType: 'json',
			success: function(data) {
				completed++;
				if (completed >= items.length) {
					location.reload();
				}
			},
			error: function() {
				completed++;
				if (completed >= items.length) {
					location.reload();
				}
			}
		});
	}
}

function edit(id) {
	$("#id").val(id);

	$.ajax({
		type: 'GET',
		url: ctx + '/adminPage/http/detail',
		dataType: 'json',
		data: {
			id: id
		},
		success: function(data) {
			if (data.success) {
				var http = data.obj;
				$("#id").val(http.id);
				$("#value").val(http.value);
				$("#name").val(http.name);

				form.render();
				showWindow(httpStr.edit);
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
			url: ctx + '/adminPage/http/del',
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
			url: ctx + '/adminPage/http/del',
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


function guide() {

	layer.open({
		type: 1,
		title: httpStr.guide,
		area: ['min(800px, 90vw)', '90%'], // 宽高
		content: $('#guideDiv')
	});

}

function addGiudeOver() {

	var params = [];
	$("input[name='param']").each(function() {

		var http = {};
		http.name = $(this).attr("id");
		http.value = $(this).val();
		http.unit = $(this).attr("lang");

		if (http.name == 'gzip') {
			if ($(this).prop("checked")) {
				http.value = "on";
			} else {
				http.value = "off";
			}
		}

		params.push(http);
	})

	var http = {
		name: "gzip_types",
		value: "",
		unit: ""
	};

	$("input[name='type']").each(function() {

		if ($(this).val() == 'js' && $(this).prop("checked")) {
			http.value += "application/javascript application/x-javascript text/javascript ";
		}
		if ($(this).val() == 'css' && $(this).prop("checked")) {
			http.value += "text/css ";
		}
		if ($(this).val() == 'json' && $(this).prop("checked")) {
			http.value += "application/json ";
		}
		if ($(this).val() == 'xml' && $(this).prop("checked")) {
			http.value += "application/xml ";
		}
	})

	if (http.value != "") {
		params.push(http);
	}


	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/http/addGiudeOver',
		data: {
			json: JSON.stringify(params),
			mimeTypes: $("#mimeTypes").prop("checked"),
			logStatus: $("#logStatus").prop("checked"),
			webSocket: $("#webSocket").prop("checked")
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


function setOrder(id, count) {
	showLoad();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/http/setOrder',
		data: {
			id: id,
			count: count
		},
		dataType: 'json',
		success: function(data) {
			closeLoad();
			if (data.success) {
				location.reload();
			} else {
				layer.msg(data.msg)
			}
		},
		error: function() {
			closeLoad();
			layer.alert(commonStr.errorInfo);
		}
	});
}


// === 全域 http 參數啟用面板(自 server/index.js 移入:全域設定歸全域頁) ===

function openHttpParamPanel() {
  layer.open({
    type: 1,
    title: serverStr.httpParm,
    area: ['85vw', '78vh'],
    content: $('#httpParamPanelDiv'),
    success: function() {
      updateHttpParamCount();
    }
  });
}

function updateHttpParamCount() {
  var n = $('input[name="httpParamItem"]:checked').length;
  $('#httpParamCountNum').text(n);
}

function saveHttpParamPanel() {
  // mutex 檢查:任一 data-mutex group 勾選 >1 → warn confirm(不強制)
  var perGroup = {};
  $('#httpParamPanelDiv input[name="httpParamItem"][data-mutex="1"]:checked').each(function () {
    var g = $(this).attr('data-group');
    perGroup[g] = (perGroup[g] || 0) + 1;
  });
  var over = Object.keys(perGroup).some(function (g) { return perGroup[g] > 1; });
  if (over) {
    layer.confirm(serverStr.httpParamMutexWarn, function (idx) {
      layer.close(idx);
      doSaveHttpParam();
    });
    return;
  }
  doSaveHttpParam();
}

function doSaveHttpParam() {
  var ids = $('input[name="httpParamItem"]:checked').map(function(){ return this.value; }).get();
  var loadIndex = layer.load(2);
  $.ajax({
    type: 'POST',
    url: ctx + '/adminPage/http/saveEnable',
    data: { checkedIds: ids.join(",") },
    dataType: 'json',
    success: function(data) {
      layer.close(loadIndex);
      if (data.success) {
        layer.msg(data.obj);  // renderSuccess(String) 把訊息放 obj（見 conf/index.js 慣例）
      } else {
        layer.alert(data.msg);  // renderError(String) 放 msg
      }
    },
    error: function() {
      layer.close(loadIndex);
      layer.alert(commonStr.errorInfo);
    }
  });
}

