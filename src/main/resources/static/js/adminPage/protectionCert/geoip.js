// GeoIP 資料庫手動下載（防護與憑證頁 Tab 1 的 GeoIP 資訊表格）
// 依賴全域：$ / layer / ctx / geoipStr（i18n，由 common.html 自動產生）
var geoipNS = {};
(function(ns) {

	function download(db) {
		layer.confirm(geoipStr.download + ' (' + db + ') ?', { icon: 3 }, function(idx) {
			layer.close(idx);
			var loading = layer.msg(geoipStr.downloading, { icon: 16, time: 0, shade: 0.1 });
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/geoip/download',
				data: { db: db },
				dataType: 'json',
				success: function(data) {
					layer.close(loading);
					if (data.success) {
						layer.msg(geoipStr.downloadSuccess, { icon: 1 });
						setTimeout(function() { location.reload(); }, 1000);
					} else {
						layer.msg(data.msg || geoipStr.downloadFail, { icon: 2 });
					}
				},
				error: function() {
					layer.close(loading);
					layer.msg(geoipStr.downloadFail, { icon: 2 });
				}
			});
		});
	}

	function downloadCloudflare() {
		layer.confirm(geoipStr.download + ' (Cloudflare) ?', { icon: 3 }, function(idx) {
			layer.close(idx);
			var loading = layer.msg(geoipStr.downloading, { icon: 16, time: 0, shade: 0.1 });
			$.ajax({
				type: 'POST',
				url: ctx + '/adminPage/geoip/downloadCloudflare',
				dataType: 'json',
				success: function(data) {
					layer.close(loading);
					if (data.success) {
						layer.msg(geoipStr.downloadSuccess, { icon: 1 });
						setTimeout(function() { location.reload(); }, 1000);
					} else {
						layer.msg(data.msg || geoipStr.downloadFail, { icon: 2 });
					}
				},
				error: function() { layer.close(loading); layer.msg(geoipStr.downloadFail, { icon: 2 }); }
			});
		});
	}

	// 重新驗證全部：重拉 versions JSON 後 reload，即時反映最新 stat/status（不需重下載）
	function reverify() {
		var loading = layer.msg(geoipStr.reverifying, { icon: 16, time: 0, shade: 0.1 });
		$.ajax({
			type: 'GET',
			url: ctx + '/adminPage/geoip/versions',
			dataType: 'json',
			success: function() {
				layer.close(loading);
				location.reload();
			},
			error: function() { layer.close(loading); layer.msg(commonStr.errorInfo, { icon: 2 }); }
		});
	}

	ns.download = download;
	ns.downloadCloudflare = downloadCloudflare;
	ns.reverify = reverify;
})(geoipNS);
