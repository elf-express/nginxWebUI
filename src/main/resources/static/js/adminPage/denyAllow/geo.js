// 國家存取控制
var geoCountries = [];
var selectedCodes = new Set ? new Set() : {};
var currentGeoRuleId = null;

// 大洲名稱 i18n
var continentNames = {
    'asia': geoStr.asia,
    'europe': geoStr.europe,
    'northAmerica': geoStr.northAmerica,
    'southAmerica': geoStr.southAmerica,
    'oceania': geoStr.oceania,
    'africa': geoStr.africa
};

// 初始化
layui.use(['element', 'form'], function() {
    var element = layui.element;
    var form = layui.form;

    // 檢查 GeoIP2 模組
    $.get(ctx + '/adminPage/geo/hasGeoIp2', function(data) {
        if (data.success) {
            if (!data.obj) {
                $('#geoNoGeoIp').show();
                $('#geoForm').hide();
            }
        }
    });

    loadGeoData(form, element);
});

function loadGeoData(formObj, elementObj) {
    // 載入國家清單
    $.get(ctx + '/adminPage/geo/countries', function(data) {
        if (data.success) {
            geoCountries = data.obj;
            renderCountries(formObj, elementObj);
        }
    });

    // 載入現有規則（全域）
    $.get(ctx + '/adminPage/geo/detail', function(data) {
        if (data.success && data.obj) {
            var rule = data.obj;
            currentGeoRuleId = rule.id;
            $("input[name='geoMode'][value='" + rule.mode + "']").prop('checked', true);
            if (rule.countries) {
                rule.countries.split(',').forEach(function(code) {
                    if (typeof selectedCodes.add === 'function') {
                        selectedCodes.add(code.trim());
                    }
                });
            }
            if (formObj) formObj.render('radio');
            updateSelectedDisplay();
            updateCheckboxes(formObj);
        }
    });
}

function renderCountries(formObj, elementObj) {
    var html = '';
    geoCountries.forEach(function(continent) {
        var name = continentNames[continent.key] || continent.key;
        html += '<div class="layui-colla-item">';
        html += '<h2 class="layui-colla-title">' + name +
                ' <button type="button" class="layui-btn layui-btn-xs layui-btn-normal" onclick="selectContinent(\'' +
                continent.key + '\')" style="margin-left:10px;">' + geoStr.selectAll + '</button></h2>';
        html += '<div class="layui-colla-content">';
        html += '<div style="display:flex;flex-wrap:wrap;gap:8px;padding:10px;">';

        continent.countries.forEach(function(c) {
            var checked = (typeof selectedCodes.has === 'function' && selectedCodes.has(c.code)) ? ' checked' : '';
            html += '<label style="width:170px;cursor:pointer;display:inline-flex;align-items:center;" class="geo-country-item" data-code="' + c.code + '" data-name="' + c.nameZh + ' ' + c.nameEn + '">';
            html += '<input type="checkbox" lay-skin="primary" lay-filter="geoCountry" value="' + c.code + '"' + checked + '>';
            html += ' ' + c.code + ' ' + c.nameZh;
            html += '</label>';
        });

        html += '</div></div></div>';
    });

    $('#geoCountries').html(html);
    if (elementObj) elementObj.render('collapse');
    if (formObj) formObj.render('checkbox');

    // 監聽 checkbox 變化
    if (formObj) {
        formObj.on('checkbox(geoCountry)', function(data) {
            if (data.elem.checked) {
                selectedCodes.add(data.value);
            } else {
                selectedCodes['delete'](data.value);
            }
            updateSelectedDisplay();
        });
    }

    // 搜尋功能
    $('#geoSearch').off('input').on('input', function() {
        var keyword = $(this).val().toLowerCase();
        $('.geo-country-item').each(function() {
            var itemName = ($(this).data('name') || '').toString().toLowerCase();
            var itemCode = ($(this).data('code') || '').toString().toLowerCase();
            if (keyword === '' || itemName.indexOf(keyword) > -1 || itemCode.indexOf(keyword) > -1) {
                $(this).show();
            } else {
                $(this).hide();
            }
        });
    });
}

function selectContinent(key) {
    var continent = null;
    for (var i = 0; i < geoCountries.length; i++) {
        if (geoCountries[i].key === key) {
            continent = geoCountries[i];
            break;
        }
    }
    if (!continent) return;
    continent.countries.forEach(function(c) {
        selectedCodes.add(c.code);
    });
    updateCheckboxes();
    updateSelectedDisplay();
}

function updateCheckboxes(formObj) {
    $('input[lay-filter="geoCountry"]').each(function() {
        $(this).prop('checked', typeof selectedCodes.has === 'function' && selectedCodes.has($(this).val()));
    });
    var f = formObj || (typeof form !== 'undefined' ? form : null);
    if (f) f.render('checkbox');
}

function updateSelectedDisplay() {
    var html = '';
    var codes = typeof selectedCodes.forEach === 'function' ? selectedCodes : [];
    codes.forEach(function(code) {
        html += '<span class="layui-badge layui-bg-blue" style="margin:3px;cursor:pointer;" onclick="removeCountry(\'' + code + '\')">' +
                code + ' &times;</span>';
    });
    if (html === '') {
        html = '<span style="color:#999;">--</span>';
    }
    $('#selectedCountries').html(html);
}

function removeCountry(code) {
    selectedCodes['delete'](code);
    updateCheckboxes();
    updateSelectedDisplay();
}

function saveGeoRule() {
    var mode = $("input[name='geoMode']:checked").val();
    var countries = [];
    if (typeof selectedCodes.forEach === 'function') {
        selectedCodes.forEach(function(code) {
            countries.push(code);
        });
    }

    $.post(ctx + '/adminPage/geo/addOver', {
        id: currentGeoRuleId || '',
        mode: mode,
        countries: countries.join(','),
        enable: true
    }, function(data) {
        if (data.success) {
            layer.msg(geoStr.saved);
            loadGeoData();
        } else {
            layer.msg(data.msg);
        }
    });
}

function clearGeoRule() {
    if (typeof selectedCodes.clear === 'function') {
        selectedCodes.clear();
    }
    updateCheckboxes();
    updateSelectedDisplay();
}
