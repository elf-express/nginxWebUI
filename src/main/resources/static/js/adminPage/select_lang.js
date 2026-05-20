var changeLangIndex;
function changeLang() {
	changeLangIndex = layer.open({
		type: 1,
		title: "Language",
		area: ['380px', '360px'],
		content: $('#changeLangDiv'),
		success: function () {
			pickLang($("#lang").val());
		}
	});
}

function pickLang(code) {
	$("#lang").val(code);
	$(".lang-option").removeClass("selected");
	$(".lang-option[data-lang='" + code + "']").addClass("selected");
}

function changeLangOver() {
	$.ajax({
		type: 'POST',
		url: ctx + '/adminPage/login/changeLang',
		data: {
			lang: $("#lang").val()
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
