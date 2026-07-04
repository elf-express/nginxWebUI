// SpecSnap inspector launcher — loaded as ES module via common.html's import map.
//
// Mounts a floating action button (bottom-right, fixed) instead of injecting into
// the existing header (which has no spare horizontal room — adding to it overlapped
// neighboring items per 2026-06-30 review).
//
// Plan: docs/superpowers/plans/2026-06-30-specsnap-inspector-integration.md
// Source: @tw199501/specsnap-inspector-core (vendored under static/lib/specsnap/)

import { createInspector } from '@tw199501/specsnap-inspector-core';

const inspector = createInspector({
	scope: document.body
	// No onSave => default storage ladder: File System Access -> ZIP (fflate) -> individual downloads
});

const label = (window.commonStr && window.commonStr.specSnap) || 'Measure';

const btn = document.createElement('button');
btn.type = 'button';
btn.id = 'specsnap-fab';
btn.setAttribute('aria-label', label);
btn.title = label;
const icon = document.createElement('i');
icon.className = 'layui-icon layui-icon-screen-full';
icon.setAttribute('aria-hidden', 'true');
btn.appendChild(icon);

Object.assign(btn.style, {
	position: 'fixed',
	top: '14px',
	left: '212px',
	width: '32px',
	height: '32px',
	borderRadius: '50%',
	border: '0',
	background: '#1E9FFF',
	color: '#fff',
	fontSize: '16px',
	cursor: 'pointer',
	zIndex: '9000',
	boxShadow: '0 2px 6px rgba(0,0,0,0.3)',
	display: 'flex',
	alignItems: 'center',
	justifyContent: 'center'
});

btn.addEventListener('click', () => inspector.toggle());

// Robust mount: layui rebuilds body asynchronously, so an early appendChild gets
// wiped out. Mount after window.load (when layui init is settled) and self-repair
// via MutationObserver in case any later script removes it.
function mountFab() {
	if (document.getElementById('specsnap-fab')) return;
	document.body.appendChild(btn);
}
if (document.readyState === 'complete') {
	mountFab();
} else {
	window.addEventListener('load', mountFab);
}
new MutationObserver(mountFab).observe(document.body, { childList: true });

// Keep public global for backward-compat / external triggers.
window.launchSpecSnap = () => inspector.toggle();
