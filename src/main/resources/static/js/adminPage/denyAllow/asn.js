// ASN 封鎖管理
var asnForm = null;

layui.use(['form'], function() {
	asnForm = layui.form;

	// 全選
	asnForm.on('checkbox(asnCheckAll)', function(data) {
		$('#asnTableBody input[name="asnIds"]').prop('checked', data.elem.checked);
		asnForm.render('checkbox');
	});

	// Switch 切換啟用/停用
	asnForm.on('switch(asnEnable)', function(data) {
		var id = $(data.elem).data('id');
		var enable = data.elem.checked;
		$.post(ctx + '/adminPage/asn/setEnable', { id: id, enable: enable }, function(res) {
			if (!res.success) {
				layer.msg(res.msg);
				loadAsnList();
			}
		});
	});

	loadAsnList();
});

function loadAsnList() {
	$.get(ctx + '/adminPage/asn/list', function(res) {
		if (!res.success) return;
		var list = res.obj || [];
		var html = '';

		if (list.length === 0) {
			html = '<tr><td colspan="5" style="text-align:center;color:#999;">--</td></tr>';
		} else {
			for (var i = 0; i < list.length; i++) {
				var item = list[i];
				var checked = (item.enable === true || item.enable === 'true') ? ' checked' : '';
				html += '<tr>';
				html += '<td><input type="checkbox" name="asnIds" lay-skin="primary" value="' + item.id + '"></td>';
				html += '<td>' + escapeHtmlAsn(item.asn) + '</td>';
				html += '<td>' + escapeHtmlAsn(item.orgName || '') + '</td>';
				html += '<td><input type="checkbox" lay-skin="switch" lay-text="ON|OFF" lay-filter="asnEnable" data-id="' + item.id + '"' + checked + '></td>';
				html += '<td>';
				html += '<button type="button" class="layui-btn layui-btn-sm" onclick="editAsn(\'' + item.id + '\')">' + commonStr.edit + '</button> ';
				html += '<button type="button" class="layui-btn layui-btn-sm layui-btn-danger" onclick="delAsn(\'' + item.id + '\')">' + commonStr.del + '</button>';
				html += '</td>';
				html += '</tr>';
			}
		}

		$('#asnTableBody').html(html);
		if (asnForm) asnForm.render(null, 'asnForm');
	});
}

function addAsn() {
	$('#asnId').val('');
	$('#asnNumber').val('');
	$('#asnOrgName').val('');

	layer.open({
		type: 1,
		title: asnStr.addTitle,
		area: ['450px'],
		content: $('#asnWindowDiv')
	});
}

function editAsn(id) {
	$.get(ctx + '/adminPage/asn/list', function(res) {
		if (!res.success) return;
		var list = res.obj || [];
		for (var i = 0; i < list.length; i++) {
			if (list[i].id === id) {
				$('#asnId').val(list[i].id);
				$('#asnNumber').val(list[i].asn);
				$('#asnOrgName').val(list[i].orgName || '');

				layer.open({
					type: 1,
					title: asnStr.editTitle,
					area: ['450px'],
					content: $('#asnWindowDiv')
				});
				break;
			}
		}
	});
}

function saveAsn() {
	var asn = $('#asnNumber').val().trim();
	if (!asn || !/^\d+$/.test(asn)) {
		layer.msg(asnStr.invalidAsn);
		return;
	}

	$.post(ctx + '/adminPage/asn/addOver', {
		id: $('#asnId').val(),
		asn: asn,
		orgName: $('#asnOrgName').val().trim(),
		enable: true
	}, function(res) {
		if (res.success) {
			layer.closeAll();
			loadAsnList();
		} else {
			layer.msg(res.msg);
		}
	});
}

function delAsn(id) {
	layer.confirm(commonStr.confirmDel, function() {
		$.post(ctx + '/adminPage/asn/del', { id: id }, function(res) {
			if (res.success) {
				layer.closeAll();
				loadAsnList();
			} else {
				layer.msg(res.msg);
			}
		});
	});
}

function delManyAsn() {
	var ids = [];
	$('#asnTableBody input[name="asnIds"]:checked').each(function() {
		ids.push($(this).val());
	});

	if (ids.length === 0) {
		layer.msg(commonStr.selectOne);
		return;
	}

	layer.confirm(commonStr.confirmDel, function() {
		$.post(ctx + '/adminPage/asn/del', { id: ids.join(',') }, function(res) {
			if (res.success) {
				layer.closeAll();
				loadAsnList();
			} else {
				layer.msg(res.msg);
			}
		});
	});
}

function escapeHtmlAsn(str) {
	if (!str) return '';
	return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}
