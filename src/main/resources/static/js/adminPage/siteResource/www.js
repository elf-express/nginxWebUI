var wwwNS = {};
(function(ns) {
	$(function() {
		layui.use('upload', function() {
			var upload = layui.upload;
			upload.render({
				elem: '#wwwUpload',
				url: ctx + '/adminPage/main/upload',
				accept: 'file',
				before: function(res) { showLoad(); },
				done: function(res) {
					closeLoad();
					if (res.success) {
						var path = res.obj.split('/');
						if (path[path.length - 1].indexOf('.zip') == -1) {
							layer.alert("只能上传zip文件");
							return;
						}
						$("#wwwFileName").html(path[path.length - 1]);
						$("#wwwDirTemp").val(res.obj);
					}
				},
				error: function() { closeLoad(); }
			});
		});

		form.on('checkbox(wwwCheckAll)', function(data) {
			$("input[name='wwwIds']").prop("checked", data.elem.checked);
			form.render();
		});
	});

	ns.add = function() {
		$("#wwwId").val("");
		$("#wwwDir").val("");
		$("#wwwDirTemp").html("");
		$("#wwwFileName").html("");
		ns.showWindow(wwwStr.add);
	};

	ns.showWindow = function(title) {
		layer.open({
			type: 1, title: title,
			area: ['560px', '360px'],
			content: $('#wwwWindowDiv')
		});
	};

	ns.addOver = function() {
		if ($("#wwwDir").val() == '') { layer.alert(wwwStr.noFill); return; }
		if ($("#wwwDirTemp").val() == '') { layer.alert(wwwStr.noUpload); return; }
		showLoad();
		$.ajax({
			type: 'POST', url: ctx + '/adminPage/www/addOver',
			data: $('#wwwAddForm').serialize(), dataType: 'json',
			success: function(data) { closeLoad(); if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
			error: function() { closeLoad(); layer.alert(commonStr.errorInfo); }
		});
	};

	ns.edit = function(id) {
		$.ajax({
			type: 'POST', url: ctx + '/adminPage/www/detail',
			data: { id: id }, dataType: 'json',
			success: function(data) {
				if (data.success) {
					var www = data.obj;
					$("#wwwId").val(www.id);
					$("#wwwDir").val(www.dir);
					$("#wwwDirTemp").html("");
					$("#wwwFileName").html("");
					ns.showWindow(wwwStr.editOrUpdate);
				} else { layer.msg(data.msg); }
			},
			error: function() { layer.alert(commonStr.errorInfo); }
		});
	};

	ns.del = function(id) {
		if (confirm(commonStr.confirmDel)) {
			$.ajax({
				type: 'POST', url: ctx + '/adminPage/www/del',
				data: { id: id }, dataType: 'json',
				success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	};

	ns.delMany = function() {
		if (confirm(commonStr.confirmDel)) {
			var ids = [];
			$("input[name='wwwIds']").each(function() {
				if ($(this).prop("checked")) ids.push($(this).val());
			});
			if (ids.length == 0) { layer.msg(commonStr.unselected); return; }
			$.ajax({
				type: 'POST', url: ctx + '/adminPage/www/del',
				data: { id: ids.join(",") }, dataType: 'json',
				success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	};

	ns.copy = function(str) {
		var oInput = document.createElement('input');
		oInput.value = str;
		document.body.appendChild(oInput);
		oInput.select();
		document.execCommand("Copy");
		oInput.style.display = 'none';
		layer.msg(wwwStr.cpoySuccess);
	};

	ns.clean = function(id) {
		if (confirm(wwwStr.clean + "?")) {
			$.ajax({
				type: 'POST', url: ctx + '/adminPage/www/clean',
				data: { id: id }, dataType: 'json',
				success: function(data) { if (data.success) { layer.msg(wwwStr.cleanSuccess); } else { layer.msg(data.msg); } },
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	};

	ns.editDescr = function(id) {
		$.ajax({
			type: 'POST', url: ctx + '/adminPage/www/getDescr',
			data: { id: id }, dataType: 'json',
			success: function(data) {
				if (data.success) {
					$("#wwwItemId").val(id);
					$("#wwwDescr").val(data.obj != null ? data.obj : '');
					layer.open({ type: 1, title: commonStr.descr, area: ['500px', '360px'], content: $('#wwwDescrDiv') });
				} else { layer.msg(data.msg); }
			},
			error: function() { layer.alert(commonStr.errorInfo); }
		});
	};

	ns.editDescrOver = function() {
		$.ajax({
			type: 'POST', url: ctx + '/adminPage/www/editDescr',
			data: { id: $("#wwwItemId").val(), descr: $("#wwwDescr").val() },
			dataType: 'json',
			success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
			error: function() { layer.alert(commonStr.errorInfo); }
		});
	};

	ns.selectRootCustom = function() {
		rootSelect.selectOne(function callBack(val) {
			$("#wwwDir").val(val);
		});
	};
})(wwwNS);
