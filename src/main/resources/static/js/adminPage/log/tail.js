var run = true;
var guid = guid();

$(function() {
	setInterval(startTail, 1000);
});
// 保留上一次数据
var temp = "";

function startTail() {
	if (run) {
		$.ajax({
			type: 'POST',
			url: ctx + '/adminPage/log/tailCmd',
			data: {
				id: $("#id").val(),
				guid: guid
			},
			dataType: 'json',
			success: function(data) {
				if (data.success) {
					// 接收服务端的实时日志并以纯文本添加到HTML页面中
					renderLines(data.obj);
					// 滚动条滚动到最低部
					if (data.obj != "" && data.obj != temp) {
						window.scrollTo(0, document.body.scrollHeight);
					}
					temp = data.obj;
				}
			},
			error: function() {
				//layer.alert(commonStr.errorInfo);
			}
		});
	}
}

// tailCmd 回传 <div>行</div> 片段；在不执行脚本、不载入资源的独立文档里解析，
// 只把每行的文字写进页面，旧版或远端节点回传未转义内容时也不会产生元素
function renderLines(html) {
	var container = document.getElementById("log-container");
	var body = new DOMParser().parseFromString(html || "", "text/html").body;
	var fragment = document.createDocumentFragment();
	for (var i = 0; i < body.childNodes.length; i++) {
		var text = body.childNodes[i].textContent;
		if (body.childNodes[i].nodeType === Node.TEXT_NODE && text.trim() === "") {
			continue;
		}
		var line = document.createElement("div");
		line.textContent = text;
		fragment.appendChild(line);
	}
	container.replaceChildren(fragment);
}

function stopOrStart() {
	if (run) {
		run = false;
		$("#stopOrStart").html(loginStr.continue);
	} else {
		run = true;
		$("#stopOrStart").html(loginStr.pause);
	}
}
