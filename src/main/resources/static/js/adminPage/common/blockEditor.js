/* Shared raw editor. Text is always assigned through value/textContent, never HTML. */
(function(global) {
    'use strict';
    var state = {}, modal, root, originalBody = '', normalizedBody = '', generation = 0;
    function el(tag, attrs, text) {
        var node = document.createElement(tag);
        Object.keys(attrs || {}).forEach(function(key) { node.setAttribute(key, attrs[key]); });
        if (text != null) node.textContent = text;
        return node;
    }
    function field(id, label, tag) {
        root.appendChild(el('label', {for: id, style: 'display:block;margin:10px 0 5px'}, label));
        var node = el(tag || 'input', {id: id, class: 'layui-input'});
        root.appendChild(node);
        return node;
    }
    function button(id, label, action) {
        var node = el('button', {id: id, type: 'button', class: 'layui-btn layui-btn-sm'}, label);
        node.addEventListener('click', action);
        return node;
    }
    function init() {
        if (root) return;
        root = el('div', {id: 'blockEditor', style: 'display:none;padding:16px;box-sizing:border-box'});
        document.body.appendChild(root);
        var context = field('blockContext', blockEditorStr.context, 'select');
        context.className = '';
        context.style.cssText = 'display:block;width:100%;height:36px';
        [['http-upstream', 'HTTP'], ['stream-upstream', 'TCP/UDP'], ['stream-child', 'Stream']].forEach(function(item) {
            context.appendChild(el('option', {value: item[0]}, item[1]));
        });
        context.addEventListener('change', function() { state.context = context.value; });
        var header = field('blockHeader', blockEditorStr.header);
        var headerRow = el('div', {style:'display:flex;align-items:center;gap:8px;font-family:monospace'});
        root.insertBefore(headerRow, header);
        headerRow.appendChild(el('span', {id:'blockPrefix'}, 'upstream'));
        headerRow.appendChild(header);
        root.appendChild(el('div', {}, '{'));
        root.appendChild(el('label', {for:'blockBody'}, blockEditorStr.body));
        var wrap = el('div', {style:'display:flex;border:1px solid #ccc;margin-top:5px'});
        var gutter = el('pre', {id:'blockLines', 'aria-hidden':'true', style:'margin:0;padding:8px;background:#f2f2f2;color:#666;overflow:hidden;text-align:right;font:14px/21px monospace;height:32vh;box-sizing:border-box;min-width:40px'});
        var body = el('textarea', {id:'blockBody', spellcheck:'false', wrap:'off', style:'flex:1;min-width:0;height:32vh;resize:vertical;border:0;padding:8px;box-sizing:border-box;font:14px/21px monospace;tab-size:4'});
        wrap.appendChild(gutter); wrap.appendChild(body); root.appendChild(wrap);
        body.addEventListener('input', refresh);
        body.addEventListener('scroll', function() { gutter.scrollTop = body.scrollTop; });
        root.appendChild(el('div', {}, '}'));
        root.appendChild(el('p', {id:'blockHelp'}, blockEditorStr.pasteHelp));
        root.appendChild(el('p', {id:'blockStreamHelp'}, blockEditorStr.streamHelp));
        root.appendChild(el('p', {id:'blockSnapshot'}, blockEditorStr.snapshot));
        root.appendChild(el('p', {id:'blockMonitorNote'}, blockEditorStr.monitor));
        root.appendChild(el('p', {id:'blockError', role:'alert', style:'color:#b42318;white-space:pre-wrap'}));
        var actions = el('div', {style:'position:sticky;bottom:0;background:white;padding:10px 0;display:flex;gap:8px;flex-wrap:wrap'});
        actions.appendChild(button('blockParse', blockEditorStr.parse, parse));
        actions.appendChild(button('blockPreview', commonStr.preview, function() {
            $('#blockOutput').text(render()).toggle();
        }));
        actions.appendChild(button('blockSave', commonStr.submit, save));
        actions.appendChild(button('blockClose', commonStr.close, function() { layer.close(modal); }));
        root.appendChild(actions);
        root.appendChild(el('pre', {id:'blockOutput', style:'display:none;white-space:pre-wrap;overflow-wrap:anywhere;padding:10px;background:#f2f2f2'}));
    }
    function refresh() {
        var count = $('#blockBody').val().split('\n').length;
        $('#blockLines').text(Array.from({length: count}, function(_, i) { return i + 1; }).join('\n'));
        $('#blockOutput').hide();
    }
    function getDraft() {
        var body = $('#blockBody').val();
        return {header: (state.kind === 'upstream' ? 'upstream ' : '') + $('#blockHeader').val(), body: body === normalizedBody ? originalBody : body, context: $('#blockContext').val()};
    }
    function setDraft(draft) {
        $('#blockHeader').val(state.kind === 'upstream' ? draft.header.replace(/^upstream\s+/, '') : draft.header);
        $('#blockBody').val(draft.body || '');
        originalBody = draft.body || '';
        normalizedBody = $('#blockBody').val();
        $('#blockContext').val(draft.context || state.context);
        refresh();
    }
    function render() { var d = getDraft(); return d.header + ' {' + d.body + (d.body.endsWith('\n') ? '' : '\n') + '}'; }
    function error(data) {
        $('#blockError').text(data.msg || commonStr.errorInfo);
        if (data.line) {
            var body = $('#blockBody')[0], lines = body.value.split('\n');
            var offset = lines.slice(0, Math.max(0, data.line - 1)).join('\n').length;
            if (data.line > 1) offset++;
            offset += Math.max(0, (data.column || 1) - 1);
            body.focus(); body.setSelectionRange(offset, offset);
        }
    }
    function post(action, data, done) {
        var requestGeneration = generation;
        $('#blockError').text('');
        $.ajax({type:'POST', url:ctx + '/adminPage/' + state.kind + '/' + action, data:data, dataType:'json',
            success:function(result) { if (requestGeneration !== generation) return; if (result.success) done(result.obj); else error(result); },
            error:function() { if (requestGeneration === generation) error({msg:commonStr.errorInfo}); }
        });
    }
    function parse() {
        var draft = getDraft();
        post('parseBlock', {text:draft.body, context:draft.context}, function(parsed) {
            var current = getDraft();
            if (current.header !== draft.header || current.body !== draft.body || current.context !== draft.context) return;
            if ($('#blockHeader').val() && parsed.header !== draft.header && !confirm(blockEditorStr.titleConflict)) return;
            parsed.context = draft.context;
            setDraft(parsed);
        });
    }
    function save() {
        if (state.convertLegacy && !confirm(blockEditorStr.confirmConvert)) return;
        var draft = getDraft();
        var data = {id:state.id || '', rawContent:draft.body, convertLegacy:!!state.convertLegacy};
        if (state.kind === 'upstream') {
            data.name = $('#blockHeader').val();
            data.proxyType = draft.context === 'http-upstream' ? 0 : 1;
        } else data.header = draft.header;
        post('saveBlock', data, function() { location.reload(); });
    }
    function open(options) {
        init(); state = options; generation++;
        $('#blockError').text(''); $('#blockOutput').hide();
        $('#blockContext').prop('disabled', options.kind === 'stream');
        $('#blockContext option').each(function() { this.hidden = options.kind === 'stream' ? this.value !== 'stream-child' : this.value === 'stream-child'; });
        $('#blockPrefix').toggle(options.kind === 'upstream');
        $('#blockSnapshot').toggle(!!options.convertLegacy);
        $('#blockMonitorNote').toggle(options.kind === 'upstream');
        $('#blockStreamHelp').toggle(options.kind === 'stream');
        setDraft({header:options.header || '', body:options.body || '', context:options.context});
        var area = getMainArea();
        var openedGeneration = generation;
        modal = layer.open({type:1, anim:-1, isOutAnim:false, title:blockEditorStr.title, offset:area.offset, area:[area.w, area.h], content:$(root), end:function() { if (generation === openedGeneration) generation++; }});
        $('#blockHeader').trigger('focus');
    }
    global.blockEditor = {open:open, setDraft:setDraft, getDraft:getDraft};
})(window);
