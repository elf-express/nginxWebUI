var pwdNS = {};
(function(ns) {
	$(function() {
		form.on('checkbox(pwdCheckAll)', function(data) {
			$("input[name='pwdIds']").prop("checked", data.elem.checked);
			form.render();
		});
	});

	ns.search = function() {
		$("#pwdSearchForm input[name='curr']").val(1);
		$("#pwdSearchForm").submit();
	};

	ns.add = function() {
		$("#pwdId").val("");
		$("#pwdName").val("");
		$("#pwdPass").val("");
		$("#pwdDescr").val("");
		ns.showWindow(commonStr.add);
	};

	ns.showWindow = function(title) {
		layer.open({
			type: 1, title: title,
			area: ['400px', '300px'],
			content: $('#pwdWindowDiv')
		});
	};

	ns.addOver = function() {
		if ($("#pwdName").val() == '' || $("#pwdPass").val() == '') {
			layer.msg(passwordStr.notFill);
			return;
		}
		$.ajax({
			type: 'POST', url: ctx + '/adminPage/password/addOver',
			data: $('#pwdAddForm').serialize(), dataType: 'json',
			success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
			error: function() { layer.alert(commonStr.errorInfo); }
		});
	};

	ns.edit = function(id) {
		$("#pwdId").val(id);
		$.ajax({
			type: 'GET', url: ctx + '/adminPage/password/detail',
			dataType: 'json', data: { id: id },
			success: function(data) {
				if (data.success) {
					var password = data.obj;
					$("#pwdId").val(password.id);
					$("#pwdPass").val(password.pass);
					$("#pwdName").val(password.name);
					$("#pwdDescr").val(password.descr);
					form.render();
					ns.showWindow(commonStr.edit);
				} else { layer.msg(data.msg); }
			},
			error: function() { layer.alert(commonStr.errorInfo); }
		});
	};

	ns.del = function(id) {
		if (confirm(commonStr.confirmDel)) {
			$.ajax({
				type: 'POST', url: ctx + '/adminPage/password/del',
				data: { id: id }, dataType: 'json',
				success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	};

	ns.delMany = function() {
		if (confirm(commonStr.confirmDel)) {
			var ids = [];
			$("input[name='pwdIds']").each(function() {
				if ($(this).prop("checked")) ids.push($(this).val());
			});
			if (ids.length == 0) { layer.msg(commonStr.unselected); return; }
			$.ajax({
				type: 'POST', url: ctx + '/adminPage/password/del',
				data: { id: ids.join(",") }, dataType: 'json',
				success: function(data) { if (data.success) { location.reload(); } else { layer.msg(data.msg); } },
				error: function() { layer.alert(commonStr.errorInfo); }
			});
		}
	};
})(pwdNS);
