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

	ns.download = download;
})(geoipNS);
