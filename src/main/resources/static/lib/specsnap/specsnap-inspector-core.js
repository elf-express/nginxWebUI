// src/create-inspector.ts
import { captureSession, toMarkdown, toSpecSnapBundle } from "@tw199501/specsnap-core";

// src/store.ts
function createStore(opts) {
  let state = {
    frames: [],
    session: null,
    visible: false,
    picking: false,
    nextCaptureId: opts.nextCaptureId,
    lastSave: null
  };
  let cachedSnapshot = null;
  const listeners = /* @__PURE__ */ new Set();
  function snapshot() {
    if (cachedSnapshot) return cachedSnapshot;
    cachedSnapshot = {
      frames: state.frames,
      session: state.session,
      visible: state.visible,
      picking: state.picking,
      nextCaptureId: state.nextCaptureId,
      lastSave: state.lastSave
    };
    return cachedSnapshot;
  }
  function invalidateAndEmit() {
    cachedSnapshot = null;
    for (const l of listeners) l();
  }
  function setState(partial) {
    let changed = false;
    const next = { ...state };
    for (const k of Object.keys(partial)) {
      const nextVal = partial[k];
      if (nextVal === void 0) continue;
      if (next[k] !== nextVal) {
        next[k] = nextVal;
        changed = true;
      }
    }
    if (!changed) return;
    state = next;
    invalidateAndEmit();
  }
  return {
    getSnapshot: snapshot,
    setState,
    subscribe(listener) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    appendFrame(el) {
      state = { ...state, frames: [...state.frames, el] };
      invalidateAndEmit();
    },
    clearFrames() {
      if (state.frames.length === 0 && state.session === null) return;
      state = { ...state, frames: [], session: null };
      invalidateAndEmit();
    }
  };
}

// src/picker.ts
function resolveScope(scope) {
  if (!scope) return document.body;
  if (typeof scope === "function") return scope();
  return scope;
}
function matchesAny(el, selectors) {
  for (const sel of selectors) {
    if (el.closest(sel)) return true;
  }
  return false;
}
function createPicker(opts) {
  let active = false;
  function onClick(e) {
    if (!active) return;
    if (!(e.target instanceof HTMLElement)) return;
    const exclude = opts.excludeSelectors ?? [];
    if (exclude.length > 0 && matchesAny(e.target, exclude)) return;
    const scopeEl = resolveScope(opts.scope);
    if (!scopeEl.contains(e.target)) return;
    e.preventDefault();
    e.stopPropagation();
    opts.onPick(e.target);
  }
  function onKeyDown(e) {
    if (!active) return;
    if (e.key === "Escape") {
      e.preventDefault();
      opts.onCancel?.();
    }
  }
  function onMouseMove(e) {
    void e;
  }
  return {
    start() {
      if (active) return;
      active = true;
      document.addEventListener("mousemove", onMouseMove, true);
      document.addEventListener("click", onClick, true);
      document.addEventListener("keydown", onKeyDown, true);
    },
    stop() {
      if (!active) return;
      active = false;
      document.removeEventListener("mousemove", onMouseMove, true);
      document.removeEventListener("click", onClick, true);
      document.removeEventListener("keydown", onKeyDown, true);
    },
    isActive() {
      return active;
    }
  };
}

// src/sequence.ts
function formatDateYYYYMMDD(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}${m}${d}`;
}
function formatCaptureId(day, sequence) {
  const nn = sequence < 100 ? String(sequence).padStart(2, "0") : String(sequence);
  return `${day}-${nn}`;
}
function readEntry(storage, key) {
  if (!storage) return null;
  const raw = storage.getItem(key);
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw);
    if (typeof parsed === "object" && parsed !== null && "day" in parsed && "lastCommitted" in parsed && typeof parsed.day === "string" && typeof parsed.lastCommitted === "number") {
      return parsed;
    }
    return null;
  } catch {
    return null;
  }
}
function writeEntry(storage, key, entry) {
  if (!storage) return;
  storage.setItem(key, JSON.stringify(entry));
}
function getNextCaptureId(opts) {
  const today = formatDateYYYYMMDD(opts.date);
  const entry = readEntry(opts.storage, opts.key);
  const lastCommittedToday = entry && entry.day === today ? entry.lastCommitted : 0;
  const sequence = lastCommittedToday + 1;
  return { sequence, captureId: formatCaptureId(today, sequence), today };
}
function commitSequence(opts) {
  const today = formatDateYYYYMMDD(opts.date);
  writeEntry(opts.storage, opts.key, { day: today, lastCommitted: opts.sequence });
}

// src/clipboard.ts
async function copyTextToClipboard(text) {
  if (typeof navigator === "undefined" || !navigator.clipboard?.writeText) {
    throw new Error("Clipboard API unavailable (non-secure context or unsupported browser)");
  }
  await navigator.clipboard.writeText(text);
}

// src/storage/fs-access.ts
function isFileSystemAccessSupported(win) {
  return typeof win.showDirectoryPicker === "function";
}
var DB_VERSION = 1;
var STORE_NAME = "handles";
function openDb(dbName) {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(dbName, DB_VERSION);
    req.onupgradeneeded = () => {
      req.result.createObjectStore(STORE_NAME);
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("IndexedDB open failed"));
  });
}
async function saveCachedRootHandle(dbName, handle) {
  const db = await openDb(dbName);
  try {
    await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, "readwrite");
      tx.objectStore(STORE_NAME).put(handle, "root");
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error ?? new Error("IndexedDB put failed"));
    });
  } finally {
    db.close();
  }
}
async function loadCachedRootHandle(dbName) {
  const db = await openDb(dbName);
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction(STORE_NAME, "readonly");
      const req = tx.objectStore(STORE_NAME).get("root");
      req.onsuccess = () => resolve(req.result ?? null);
      req.onerror = () => reject(req.error ?? new Error("IndexedDB get failed"));
    });
  } finally {
    db.close();
  }
}
var DB_NAME = "specsnap-inspector-fs";
async function ensureRootHandle() {
  if (!isFileSystemAccessSupported(window)) return null;
  const cached = await loadCachedRootHandle(DB_NAME);
  if (cached && cached.queryPermission) {
    const permission = await cached.queryPermission({ mode: "readwrite" });
    if (permission === "granted") return cached;
    if (cached.requestPermission) {
      const next = await cached.requestPermission({ mode: "readwrite" });
      if (next === "granted") return cached;
    }
  }
  try {
    const winFSA = window;
    if (!winFSA.showDirectoryPicker) return null;
    const picked = await winFSA.showDirectoryPicker({ mode: "readwrite" });
    await saveCachedRootHandle(DB_NAME, picked);
    return picked;
  } catch {
    return null;
  }
}
async function writeFile(dir, filename, data) {
  const fileHandle = await dir.getFileHandle(filename, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(data);
  await writable.close();
}
async function writeBundleToFilesystem(root, bundle) {
  const subdir = await root.getDirectoryHandle(bundle.dirName, { create: true });
  await writeFile(subdir, bundle.markdownFilename, bundle.markdownContent);
  for (const img of bundle.images) {
    await writeFile(subdir, img.filename, img.blob);
  }
  return {
    mode: "filesystem",
    where: `${root.name}/${bundle.dirName}/`,
    fileCount: bundle.images.length + 1
  };
}
function triggerDownload(filename, blob) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}
function writeBundleViaDownloads(bundle) {
  triggerDownload(
    bundle.markdownFilename,
    new Blob([bundle.markdownContent], { type: "text/markdown" })
  );
  for (const img of bundle.images) {
    triggerDownload(img.filename, img.blob);
  }
  return {
    mode: "downloads",
    where: `Downloads/ (drag into ${bundle.dirName}/ manually)`,
    fileCount: bundle.images.length + 1
  };
}
async function writeBundle(bundle) {
  const root = await ensureRootHandle();
  if (!root) return writeBundleViaDownloads(bundle);
  try {
    return await writeBundleToFilesystem(root, bundle);
  } catch (err) {
    console.warn("[specsnap] FSA write failed, falling back to downloads:", err);
    return writeBundleViaDownloads(bundle);
  }
}

// src/storage/zip-fallback.ts
async function loadFflate() {
  if (globalThis.__importOverride) {
    return await globalThis.__importOverride();
  }
  return await import("fflate");
}
async function blobToUint8(blob) {
  const ab = await blob.arrayBuffer();
  return new Uint8Array(ab);
}
async function saveBundleAsZip(bundle) {
  try {
    const fflate = await loadFflate();
    const mdBytes = new TextEncoder().encode(bundle.markdownContent);
    const entries = {};
    entries[`${bundle.dirName}/${bundle.markdownFilename}`] = mdBytes;
    for (const img of bundle.images) {
      entries[`${bundle.dirName}/${img.filename}`] = await blobToUint8(img.blob);
    }
    const zipped = fflate.zipSync(entries, { level: 6 });
    const zipBlob = new Blob([zipped], { type: "application/zip" });
    const url = URL.createObjectURL(zipBlob);
    const zipName = `${bundle.captureId}.zip`;
    const a = document.createElement("a");
    a.href = url;
    a.download = zipName;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    return {
      strategy: "zip",
      fileCount: 1 + bundle.images.length,
      location: zipName,
      error: null
    };
  } catch (err) {
    return {
      strategy: "zip",
      fileCount: 0,
      location: null,
      error: err instanceof Error ? err.message : "unknown zip-fallback error"
    };
  }
}

// src/storage/individual-fallback.ts
function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}
async function saveBundleAsIndividualFiles(bundle) {
  try {
    const mdBlob = new Blob([bundle.markdownContent], { type: "text/markdown" });
    downloadBlob(mdBlob, bundle.markdownFilename);
    for (const img of bundle.images) {
      downloadBlob(img.blob, img.filename);
    }
    return {
      strategy: "individual",
      fileCount: 1 + bundle.images.length,
      location: "browser downloads folder",
      error: null
    };
  } catch (err) {
    return {
      strategy: "individual",
      fileCount: 0,
      location: null,
      error: err instanceof Error ? err.message : "unknown individual-fallback error"
    };
  }
}

// src/storage/save-bundle.ts
async function saveBundleWithLadder(bundle, opts) {
  if (opts.onSave) {
    try {
      await opts.onSave(bundle);
      return {
        strategy: "callback",
        fileCount: 1 + bundle.images.length,
        location: "handled by host app",
        error: null
      };
    } catch (err) {
      return {
        strategy: "callback",
        fileCount: 0,
        location: null,
        error: err instanceof Error ? err.message : "onSave rejected"
      };
    }
  }
  const viaFs = await opts.strategies.fsAccess(bundle);
  if (viaFs && viaFs.error === null) return viaFs;
  const viaZip = await opts.strategies.zip(bundle);
  if (viaZip.error === null) return viaZip;
  return opts.strategies.individual(bundle);
}

// src/overlay.ts
import { buildAnnotationSvg, computeGap } from "@tw199501/specsnap-core";
var OVERLAY_ID = "specsnap-inspector-overlay";
function rectOf(el) {
  const r = el.getBoundingClientRect();
  return { x: r.x, y: r.y, width: r.width, height: r.height };
}
function createOverlay() {
  let host = document.createElement("div");
  host.id = OVERLAY_ID;
  host.style.cssText = [
    "position:fixed",
    "inset:0",
    "pointer-events:none",
    "z-index:2147482999"
  ].join(";");
  document.body.appendChild(host);
  let currentFrames = [];
  function render() {
    if (!host) return;
    while (host.firstChild) host.removeChild(host.firstChild);
    if (currentFrames.length === 0) return;
    const annotateFrames = currentFrames.map((el, i) => ({
      index: i + 1,
      bounds: rectOf(el)
    }));
    const gaps = [];
    for (let i = 1; i < currentFrames.length; i++) {
      const prev = rectOf(currentFrames[i - 1]);
      const curr = rectOf(currentFrames[i]);
      const g = computeGap(i, i + 1, prev, curr);
      if (g) gaps.push(g);
    }
    const svg = buildAnnotationSvg(
      {
        frames: annotateFrames,
        gaps,
        canvas: { width: window.innerWidth, height: window.innerHeight }
      },
      { badges: true, sizeLabels: true, gaps: true }
    );
    host.appendChild(svg);
  }
  let rafScheduled = false;
  function schedule() {
    if (rafScheduled) return;
    rafScheduled = true;
    requestAnimationFrame(() => {
      rafScheduled = false;
      render();
    });
  }
  window.addEventListener("scroll", schedule, true);
  window.addEventListener("resize", schedule);
  return {
    update(frames) {
      currentFrames = frames;
      render();
    },
    destroy() {
      window.removeEventListener("scroll", schedule, true);
      window.removeEventListener("resize", schedule);
      if (host && host.parentNode) host.parentNode.removeChild(host);
      host = null;
      currentFrames = [];
    }
  };
}

// src/create-inspector.ts
var DEFAULT_SEQUENCE_KEY = "specsnap:sequence";
function getLocalStorageSafely() {
  try {
    return typeof localStorage === "undefined" ? null : localStorage;
  } catch {
    return null;
  }
}
function toBundleToWrite(b) {
  return {
    dirName: b.dirName,
    markdownFilename: b.markdownFilename,
    markdownContent: b.markdownContent,
    images: b.images
  };
}
function extractSequenceFromCaptureId(captureId) {
  const parts = captureId.split("-");
  const tail = parts[parts.length - 1] ?? "1";
  const n = Number(tail);
  return Number.isFinite(n) && n > 0 ? n : 1;
}
function createInspector(options = {}) {
  if (typeof window === "undefined") {
    throw new Error('SpecSnap Inspector requires a browser environment (typeof window === "undefined")');
  }
  const storage = getLocalStorageSafely();
  const sequenceKey = options.sequenceStorageKey ?? DEFAULT_SEQUENCE_KEY;
  function computeNextId() {
    const now = /* @__PURE__ */ new Date();
    const { captureId } = getNextCaptureId({ date: now, storage, key: sequenceKey });
    return captureId;
  }
  const store = createStore({ nextCaptureId: computeNextId() });
  const overlay = createOverlay();
  const unsubscribeOverlay = store.subscribe(() => {
    overlay.update(store.getSnapshot().frames);
  });
  const excludeSelectors = [
    ".specsnap-inspector-panel",
    ".specsnap-inspector-trigger",
    "#specsnap-inspector-overlay",
    // Internal download anchors (created transiently by the storage layer
    // to trigger file downloads). These click() calls bubble to document
    // and were being captured as picks — ate-our-own-tail bug.
    "a[download]"
  ];
  const picker = createPicker({
    scope: options.scope ?? null,
    excludeSelectors,
    onPick(el) {
      store.appendFrame(el);
      const frames = store.getSnapshot().frames;
      const session = captureSession(frames);
      store.setState({ session });
      options.onCapture?.({ frameIndex: frames.length, session });
    },
    onCancel() {
      picker.stop();
      store.setState({ picking: false });
    }
  });
  function open() {
    if (store.getSnapshot().visible) return;
    store.setState({ visible: true });
    options.onOpen?.();
  }
  function close() {
    if (!store.getSnapshot().visible) return;
    if (picker.isActive()) {
      picker.stop();
      store.setState({ picking: false });
    }
    store.setState({ visible: false });
    options.onClose?.();
  }
  function toggle() {
    if (store.getSnapshot().visible) close();
    else open();
  }
  function startPicker() {
    if (picker.isActive()) return;
    picker.start();
    store.setState({ picking: true });
  }
  function stopPicker() {
    if (!picker.isActive()) return;
    picker.stop();
    store.setState({ picking: false });
  }
  function clearFrames() {
    store.clearFrames();
    options.onClear?.();
  }
  async function copyMarkdown() {
    const session = store.getSnapshot().session;
    if (!session) return;
    const md = toMarkdown(session);
    const joined = md.join("\n\n\u2501\u2501\u2501\u2501\u2501\n\n");
    await copyTextToClipboard(joined);
    options.onCopy?.(joined);
  }
  async function saveBundle() {
    const session = store.getSnapshot().session;
    if (!session) {
      const empty = { strategy: "callback", fileCount: 0, location: null, error: "No frames to save" };
      store.setState({ lastSave: empty });
      return empty;
    }
    const now = /* @__PURE__ */ new Date();
    const bundle = await toSpecSnapBundle(session, { date: now });
    const result = await saveBundleWithLadder(bundle, {
      ...options.onSave ? { onSave: options.onSave } : {},
      strategies: {
        fsAccess: async (b) => {
          try {
            const r = await writeBundle(toBundleToWrite(b));
            if (!r) return null;
            return {
              strategy: "fs-access",
              fileCount: r.fileCount,
              location: r.where,
              error: null
            };
          } catch {
            return null;
          }
        },
        zip: saveBundleAsZip,
        individual: saveBundleAsIndividualFiles
      }
    });
    if (result.error === null) {
      commitSequence({
        sequence: extractSequenceFromCaptureId(bundle.captureId),
        date: now,
        storage,
        key: sequenceKey
      });
      store.setState({ nextCaptureId: computeNextId() });
    }
    store.setState({ lastSave: result });
    return result;
  }
  function destroy() {
    if (picker.isActive()) picker.stop();
    unsubscribeOverlay();
    overlay.destroy();
  }
  return {
    open,
    close,
    toggle,
    startPicker,
    stopPicker,
    clearFrames,
    copyMarkdown,
    saveBundle,
    getSnapshot: () => store.getSnapshot(),
    subscribe: (listener) => store.subscribe(listener),
    destroy
  };
}

// src/mount.ts
function mount(container, options = {}) {
  void container;
  const handle = createInspector(options);
  options.onReady?.(handle);
  return handle;
}
export {
  commitSequence,
  createInspector,
  formatDateYYYYMMDD,
  getNextCaptureId,
  mount,
  saveBundleWithLadder
};
//# sourceMappingURL=index.mjs.map