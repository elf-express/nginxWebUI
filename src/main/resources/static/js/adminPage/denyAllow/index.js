// === Tag editor state ===
var ipTags = [];
var searchFilter = '';
var batchLayerIndex = 0;

// IPv4: 1.2.3.4 or 1.2.3.4/24
// IPv6: ::1, fe80::1, 2001:db8::1 or with /prefix
// Also allow "all" keyword used in nginx
var ipv4Re = /^(\d{1,3}\.){3}\d{1,3}(\/\d{1,2})?$/;
var ipv6Re = /^([0-9a-fA-F]{0,4}:){1,7}[0-9a-fA-F]{0,4}(\/\d{1,3})?$/;

function isValidIp(str) {
	str = str.trim();
	if (str === 'all') return true;
	if (ipv4Re.test(str)) {
		var parts = str.split('/')[0].split('.');
		for (var i = 0; i < parts.length; i++) {
			if (parseInt(parts[i], 10) > 255) return false;
		}
		var cidr = str.split('/')[1];
		if (cidr !== undefined && (parseInt(cidr, 10) > 32 || parseInt(cidr, 10) < 0)) return false;
		return true;
	}
	if (ipv6Re.test(str)) return true;
	return false;
}

function addIp(raw) {
	var val = raw.trim();
	if (!val) return;

	// Check duplicate
	for (var i = 0; i < ipTags.length; i++) {
		if (ipTags[i].ip === val) {
			// Flash the existing tag
			var el = document.getElementById('tag-' + i);
			if (el) {
				el.style.transition = 'none';
				el.style.transform = 'scale(1.15)';
				setTimeout(function() {
					el.style.transition = 'transform 0.2s';
					el.style.transform = 'scale(1)';
				}, 150);
			}
			return;
		}
	}

	ipTags.push({ ip: val, valid: isValidIp(val) });
	renderTags();
}

function removeTag(idx) {
	ipTags.splice(idx, 1);
	renderTags();
}

function renderTags() {
	var container = document.getElementById('tagContainer');
	if (!container) return;
	var html = '';
	var totalCount = ipTags.length;
	var invalidCount = 0;
	var visibleCount = 0;
	var filter = searchFilter.toLowerCase();

	for (var i = 0; i < ipTags.length; i++) {
		var tag = ipTags[i];
		if (!tag.valid) invalidCount++;

		var hidden = filter && tag.ip.toLowerCase().indexOf(filter) === -1;
		if (hidden) continue;
		visibleCount++;

		var cls = tag.valid ? 'ip-tag ip-tag-valid' : 'ip-tag ip-tag-invalid';
		html += '<span id="tag-' + i + '" class="' + cls + '">'
			+ escapeHtml(tag.ip)
			+ ' <i class="layui-icon layui-icon-close" onclick="removeTag(' + i + ')"></i>'
			+ '</span>';
	}

	if (totalCount === 0) {
		html = '<div class="ip-empty-state"><i class="layui-icon layui-icon-note"></i>' + denyAllowStr.inputPlaceholder + '</div>';
	} else if (filter && visibleCount === 0) {
		html = '<div class="ip-empty-state">--</div>';
	}

	container.innerHTML = html;

	// Update stats
	var statsEl = document.getElementById('tagStats');
	if (statsEl) {
		var text = denyAllowStr.totalIps + ': ' + totalCount;
		if (invalidCount > 0) {
			text += '  <span style="color:#FF5722;">' + denyAllowStr.invalidIps + ': ' + invalidCount + '</span>';
		}
		if (filter) {
			text += '  (' + visibleCount + ' matched)';
		}
		statsEl.innerHTML = text;
	}

	syncToTextarea();
}

function syncToTextarea() {
	var arr = [];
	for (var i = 0; i < ipTags.length; i++) {
		arr.push(ipTags[i].ip);
	}
	$('#ip').val(arr.join('\n'));
}

function loadFromTextarea(text) {
	ipTags = [];
	if (!text) { renderTags(); return; }
	var lines = text.split(/[\n,;\s]+/);
	for (var i = 0; i < lines.length; i++) {
		var v = lines[i].trim();
		if (v) {
			ipTags.push({ ip: v, valid: isValidIp(v) });
		}
	}
	renderTags();
}

function escapeHtml(str) {
	return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

// === Batch import ===
function showBatchImport() {
	$('#batchText').val('');
	$('#batchFile').val('');
	batchLayerIndex = layer.open({
		type: 1,
		title: denyAllowStr.batchImport,
		area: ['600px', '400px'],
		content: $('#batchImportDiv')
	});
}

function doBatchImport() {
	var fileInput = document.getElementById('batchFile');
	if (fileInput.files && fileInput.files.length > 0) {
		var reader = new FileReader();
		reader.onload = function(e) {
			processImportText(e.target.result);
			layer.close(batchLayerIndex);
		};
		reader.readAsText(fileInput.files[0]);
	} else {
		var text = $('#batchText').val();
		if (text.trim()) {
			processImportText(text);
		}
		layer.close(batchLayerIndex);
	}
}

function processImportText(text) {
	var lines = text.split(/[\n\r,;\s]+/);
	var count = 0;
	for (var i = 0; i < lines.length; i++) {
		var v = lines[i].trim();
		if (!v) continue;
		// Skip duplicate
		var dup = false;
		for (var j = 0; j < ipTags.length; j++) {
			if (ipTags[j].ip === v) { dup = true; break; }
		}
		if (!dup) {
			ipTags.push({ ip: v, valid: isValidIp(v) });
			count++;
		}
	}
	renderTags();
	if (count > 0) {
		layer.msg(denyAllowStr.importedCount.replace('{0}', count));
	}
}

// === Main page logic ===
$(function() {
	form.on('checkbox(checkAll)', function(data) {
		if (data.elem.checked) {
			$("input[name='ids']").prop("checked", true);
		} else {
			$("input[name='ids']").prop("checked", false);
		}
		form.render();
	});

	// Tag input: Enter / comma / space to add
	$('#tagInput').on('keydown', function(e) {
		if (e.keyCode === 13 || e.keyCode === 188) { // Enter or comma
			e.preventDefault();
			var val = $(this).val().trim().replace(/,$/, '');
			if (val) {
				// May contain multiple IPs separated by space/comma
				var parts = val.split(/[\s,;]+/);
				for (var i = 0; i < parts.length; i++) {
					if (parts[i].trim()) addIp(parts[i].trim());
				}
			}
			$(this).val('');
		}
	});

	// Paste handler: split multiple IPs
	$('#tagInput').on('paste', function(e) {
		var self = this;
		setTimeout(function() {
			var val = $(self).val();
			if (val.indexOf('\n') !== -1 || val.indexOf(',') !== -1 || val.indexOf(' ') !== -1) {
				var parts = val.split(/[\n\r,;\s]+/);
				for (var i = 0; i < parts.length; i++) {
					if (parts[i].trim()) addIp(parts[i].trim());
				}
				$(self).val('');
			}
		}, 50);
	});

	// Search filter
	$('#tagSearch').on('input', function() {
		searchFilter = $(this).val();
		renderTags();
	});
});

function search() {
	$("input[name='curr']").val(1);
	$("#searchForm").submit();
}

function add() {
	$("#id").val("");
	$("#name").val("");
	$("#sourceUrl").val("");
	$("#fetchTime").val("");
	ipTags = [];
	searchFilter = '';
	$('#tagSearch').val('');
	renderTags();
	showWindow(commonStr.add);
}

function showWindow(title) {
	layer.open({
		type: 1,
		title: title,
		area: ['min(750px, 90vw)', 'min(600px, 90vh)'],
		content: $('#windowDiv')
	});
}

function doSave() {
	syncToTextarea();
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/denyAllow/addOver',
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

function addOver() {
	if ($("#name").val() == '') {
		layer.msg(serverStr.noFill);
		return;
	}

	// Validate: empty list — 但允許「IP 空 + 有 sourceUrl」，讓 backend 即時抓
	if (ipTags.length === 0 && !$("#sourceUrl").val().trim()) {
		layer.msg(denyAllowStr.emptyList);
		return;
	}

	// Validate: invalid IPs
	var invalidCount = 0;
	for (var i = 0; i < ipTags.length; i++) {
		if (!ipTags[i].valid) invalidCount++;
	}
	if (invalidCount > 0) {
		layer.confirm(denyAllowStr.invalidWarn.replace('{0}', invalidCount), {
			btn: [commonStr.submit, commonStr.close]
		}, function(index) {
			layer.close(index);
			doSave();
		});
		return;
	}

	doSave();
}

function edit(id) {
	$("#id").val(id);
	searchFilter = '';
	$('#tagSearch').val('');

	$.ajax({
		type: 'GET',
		url: ctx + '/adminPage/denyAllow/detail',
		dataType: 'json',
		data: { id: id },
		success: function(data) {
			if (data.success) {
				var denyAllow = data.obj;
				$("#id").val(denyAllow.id);
				$("#name").val(denyAllow.name);
				$("#sourceUrl").val(denyAllow.sourceUrl || "");
				$("#fetchTime").val(denyAllow.fetchTime || "");
				loadFromTextarea(denyAllow.ip);
				form.render();
				showWindow(commonStr.edit);
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
			url: ctx + '/adminPage/denyAllow/del',
			data: { id: id },
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
}

function delMany() {
	if (confirm(commonStr.confirmDel)) {
		var ids = [];
		$("input[name='ids']").each(function() {
			if ($(this).prop("checked")) {
				ids.push($(this).val());
			}
		});

		if (ids.length == 0) {
			layer.msg(commonStr.unselected);
			return;
		}

		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/denyAllow/del',
			data: { id: ids.join(",") },
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
}
