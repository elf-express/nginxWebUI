// src/types.ts
var SCHEMA_VERSION = "0.0.6";

// src/viewport.ts
function captureViewport(win = window) {
  return {
    width: win.innerWidth,
    height: win.innerHeight,
    devicePixelRatio: win.devicePixelRatio ?? 1
  };
}
function captureScroll(win = window) {
  return { x: win.scrollX, y: win.scrollY };
}

// src/lexicon.ts
var DEFAULT_LEXICON = Object.freeze({
  // Box model
  "padding": "\u5167\u908A\u8DDD",
  "padding-top": "\u4E0A\u5167\u908A\u8DDD",
  "padding-right": "\u53F3\u5167\u908A\u8DDD",
  "padding-bottom": "\u4E0B\u5167\u908A\u8DDD",
  "padding-left": "\u5DE6\u5167\u908A\u8DDD",
  "margin": "\u5916\u908A\u8DDD",
  "margin-top": "\u4E0A\u5916\u908A\u8DDD",
  "margin-right": "\u53F3\u5916\u908A\u8DDD",
  "margin-bottom": "\u4E0B\u5916\u908A\u8DDD",
  "margin-left": "\u5DE6\u5916\u908A\u8DDD",
  "border": "\u908A\u6846",
  "border-width": "\u908A\u6846\u5BEC\u5EA6",
  "border-style": "\u908A\u6846\u6A23\u5F0F",
  "border-color": "\u908A\u6846\u984F\u8272",
  "border-radius": "\u5713\u89D2",
  // Sizing
  "width": "\u5BEC\u5EA6",
  "height": "\u9AD8\u5EA6",
  "min-width": "\u6700\u5C0F\u5BEC\u5EA6",
  "min-height": "\u6700\u5C0F\u9AD8\u5EA6",
  "max-width": "\u6700\u5927\u5BEC\u5EA6",
  "max-height": "\u6700\u5927\u9AD8\u5EA6",
  // Typography
  "color": "\u6587\u5B57\u984F\u8272",
  "font-family": "\u5B57\u9AD4",
  "font-size": "\u5B57\u9AD4\u5927\u5C0F",
  "font-weight": "\u5B57\u91CD",
  "font-style": "\u5B57\u578B\u6A23\u5F0F",
  "line-height": "\u884C\u9AD8",
  "letter-spacing": "\u5B57\u8DDD",
  "text-align": "\u5C0D\u9F50\u65B9\u5F0F",
  "text-decoration": "\u6587\u5B57\u88DD\u98FE",
  "text-transform": "\u6587\u5B57\u8F49\u63DB",
  // Background
  "background": "\u80CC\u666F",
  "background-color": "\u80CC\u666F\u8272",
  "background-image": "\u80CC\u666F\u5716",
  // Layout
  "display": "\u986F\u793A\u6A21\u5F0F",
  "position": "\u5B9A\u4F4D\u65B9\u5F0F",
  "top": "\u4E0A\u504F\u79FB",
  "right": "\u53F3\u504F\u79FB",
  "bottom": "\u4E0B\u504F\u79FB",
  "left": "\u5DE6\u504F\u79FB",
  "z-index": "\u5C64\u7D1A",
  "overflow": "\u6EA2\u51FA\u8655\u7406",
  "visibility": "\u53EF\u898B\u6027",
  // Flex / Grid
  "flex": "\u5F48\u6027\u6392\u7248",
  "flex-direction": "\u4E3B\u8EF8\u65B9\u5411",
  "flex-wrap": "\u63DB\u884C",
  "gap": "\u9593\u8DDD",
  "justify-content": "\u4E3B\u8EF8\u5C0D\u9F4A",
  "align-items": "\u4EA4\u53C9\u8EF8\u5C0D\u9F4A",
  // Visual
  "box-shadow": "\u9670\u5F71",
  "opacity": "\u4E0D\u900F\u660E\u5EA6",
  "transform": "\u8B8A\u5F62",
  "transition": "\u904E\u6E21\u6548\u679C",
  "cursor": "\u6E38\u6A19\u6A23\u5F0F",
  // Interaction
  "pointer-events": "\u6307\u6A19\u4E8B\u4EF6",
  "user-select": "\u6587\u5B57\u9078\u53D6"
});
function annotate(property, override) {
  const key = property.toLowerCase();
  if (override && key in override) return override[key];
  return DEFAULT_LEXICON[key] ?? "";
}

// src/gap.ts
function computeGap(fromIndex, toIndex, a, b) {
  const aRight = a.x + a.width;
  const aBottom = a.y + a.height;
  const bRight = b.x + b.width;
  const bBottom = b.y + b.height;
  const overlapsY = a.y < bBottom && b.y < aBottom;
  const overlapsX = a.x < bRight && b.x < aRight;
  if (overlapsX && overlapsY) return null;
  if (overlapsY && !overlapsX) {
    const gap = a.x >= bRight ? a.x - bRight : b.x - aRight;
    if (gap <= 0) return null;
    return { from: fromIndex, to: toIndex, axis: "horizontal", px: round(gap) };
  }
  if (overlapsX && !overlapsY) {
    const gap = a.y >= bBottom ? a.y - bBottom : b.y - aBottom;
    if (gap <= 0) return null;
    return { from: fromIndex, to: toIndex, axis: "vertical", px: round(gap) };
  }
  return null;
}
function round(n) {
  return Math.round(n * 100) / 100;
}

// src/capture.ts
function captureElement(el2, index) {
  if (!el2.isConnected) {
    throw new Error("SpecSnap: element is not attached to the document");
  }
  const rect = domRect(el2);
  const style = getComputedStyle(el2);
  return {
    index,
    identity: identify(el2),
    rect,
    viewportRelative: {
      xPct: round2(rect.x / window.innerWidth * 100, 2),
      yPct: round2(rect.y / window.innerHeight * 100, 2)
    },
    boxModel: readBoxModel(style, rect),
    typography: readTypography(style),
    background: readBackground(style)
  };
}
function captureSession(elements) {
  const frames = elements.map((el2, i) => captureElement(el2, i + 1));
  const gaps = [];
  for (let i = 1; i < frames.length; i++) {
    const g = computeGap(
      frames[i - 1].index,
      frames[i].index,
      frames[i - 1].rect,
      frames[i].rect
    );
    if (g) gaps.push(g);
  }
  return {
    schemaVersion: SCHEMA_VERSION,
    id: makeSessionId(),
    capturedAt: (/* @__PURE__ */ new Date()).toISOString(),
    url: typeof location === "undefined" ? "" : location.href,
    pageTitle: typeof document === "undefined" ? "" : document.title,
    viewport: captureViewport(),
    scroll: captureScroll(),
    frames,
    gaps
  };
}
function domRect(el2) {
  const r = el2.getBoundingClientRect();
  return {
    x: round2(r.left + window.scrollX, 2),
    y: round2(r.top + window.scrollY, 2),
    width: round2(r.width, 2),
    height: round2(r.height, 2)
  };
}
function readBoxModel(style, rect) {
  const padding = fourSides(style, "padding");
  const border = fourSides(style, "border", "-width");
  const margin = fourSides(style, "margin");
  const contentWidth = rect.width - padding[1] - padding[3] - border[1] - border[3];
  const contentHeight = rect.height - padding[0] - padding[2] - border[0] - border[2];
  return {
    content: {
      width: round2(Math.max(0, contentWidth), 2),
      height: round2(Math.max(0, contentHeight), 2)
    },
    padding,
    border,
    margin
  };
}
function fourSides(style, prop, suffix = "") {
  const read = (side) => parsePx(style.getPropertyValue(`${prop}-${side}${suffix}`));
  return [read("top"), read("right"), read("bottom"), read("left")];
}
function parsePx(v) {
  const n = parseFloat(v);
  return Number.isFinite(n) ? round2(n, 2) : 0;
}
function readTypography(style) {
  return {
    fontFamily: style.fontFamily,
    fontSize: parsePx(style.fontSize),
    fontWeight: style.fontWeight,
    lineHeight: style.lineHeight,
    letterSpacing: style.letterSpacing,
    color: style.color,
    textAlign: style.textAlign
  };
}
function readBackground(style) {
  return {
    color: style.backgroundColor,
    image: style.backgroundImage,
    borderRadius: [
      parsePx(style.borderTopLeftRadius),
      parsePx(style.borderTopRightRadius),
      parsePx(style.borderBottomRightRadius),
      parsePx(style.borderBottomLeftRadius)
    ]
  };
}
function identify(el2) {
  const tagName = el2.tagName.toLowerCase();
  const id = el2.id || null;
  const classList = [...el2.classList];
  const name = formatName(el2, tagName, id, classList);
  const domPath = buildDomPath(el2);
  const identity = { tagName, id, classList, name, domPath };
  const i18nKey = el2.getAttribute("data-i18n-key");
  if (i18nKey) identity.i18nKey = i18nKey;
  const source = el2.getAttribute("data-v-source");
  if (source) identity.source = source;
  return identity;
}
function formatName(el2, tag, id, classes) {
  if (id) return `${tag}#${id}`;
  const ariaLabel = el2.getAttribute("aria-label");
  if (ariaLabel && ariaLabel.trim()) {
    return `${tag}[aria-label="${ariaLabel.trim().slice(0, 40)}"]`;
  }
  const heading = el2.querySelector("h1, h2, h3, h4, h5, h6");
  if (heading && heading.textContent) {
    const headingText = heading.textContent.trim().slice(0, 40);
    if (headingText) return `${tag}[heading="${headingText}"]`;
  }
  const rawText = el2.innerText ?? el2.textContent ?? "";
  const normalized = rawText.replace(/\s+/g, " ").trim();
  if (normalized) {
    const snippet = normalized.length <= 24 ? normalized : normalized.slice(0, 24).replace(/\s+\S*$/, "");
    if (snippet) return `${tag}[text="${snippet}"]`;
  }
  return classes[0] ? `${tag}.${classes[0]}` : tag;
}
function buildDomPath(el2) {
  const parts = [];
  let node = el2;
  while (node && node.nodeType === Node.ELEMENT_NODE) {
    let segment = node.tagName.toLowerCase();
    if (node.id) {
      parts.unshift(`${segment}#${CSS.escape(node.id)}`);
      break;
    }
    const parent = node.parentElement;
    if (parent) {
      const siblings = [...parent.children].filter(
        (c) => c.tagName === node.tagName
      );
      if (siblings.length > 1) {
        segment += `:nth-of-type(${siblings.indexOf(node) + 1})`;
      }
    }
    parts.unshift(segment);
    node = parent;
  }
  return parts.join(" > ");
}
function round2(n, decimals) {
  const p = 10 ** decimals;
  return Math.round(n * p) / p;
}
function makeSessionId() {
  return "s-" + Math.random().toString(36).slice(2, 8);
}

// src/serialize-md.ts
function toMarkdown(session, options = {}) {
  return session.frames.map((frame, i) => {
    const imageFilename = options.imageFilenames?.[i];
    return renderFrame(session, frame, options, imageFilename);
  });
}
function renderFrame(session, frame, options, imageFilename) {
  const a = (prop) => {
    const cn = annotate(prop, options.lexiconOverride);
    return cn ? ` (${cn})` : "";
  };
  const total = session.frames.length;
  const { identity, rect, viewportRelative, boxModel, typography, background } = frame;
  const pad = boxModel.padding;
  const bd = boxModel.border;
  const mg = boxModel.margin;
  return [
    "---",
    `frame: ${frame.index} of ${total}`,
    `captured_at: ${session.capturedAt}`,
    `viewport: { width: ${session.viewport.width}, height: ${session.viewport.height}, dpr: ${session.viewport.devicePixelRatio} }`,
    `scroll: { x: ${session.scroll.x}, y: ${session.scroll.y} }`,
    `url: ${session.url}`,
    `page_title: ${session.pageTitle}`,
    `session_id: ${session.id}`,
    "---",
    "",
    ...imageFilename ? [`![Frame ${frame.index}](./${imageFilename})`, ""] : [],
    `# Frame ${frame.index} \xB7 ${identity.name}`,
    "",
    "## \u57FA\u672C (Basics)",
    `- **name**: \`${identity.name}\``,
    ...identity.i18nKey ? [`- **i18n_key**: \`${identity.i18nKey}\``] : [],
    ...identity.source ? [`- **source**: \`${identity.source}\``] : [],
    `- **dom_path**: \`${identity.domPath}\``,
    `- **position**: (${rect.x}, ${rect.y}) \xB7 viewport-relative (${viewportRelative.xPct}%, ${viewportRelative.yPct}%)`,
    `- **size**: ${rect.width}${a("width")} \xD7 ${rect.height}${a("height")} px`,
    "",
    "## \u76D2\u6A21\u578B (Box Model)",
    `- content: ${boxModel.content.width} \xD7 ${boxModel.content.height} px`,
    `- padding: ${pad[0]} / ${pad[1]} / ${pad[2]} / ${pad[3]} (\u4E0A/\u53F3/\u4E0B/\u5DE6)${a("padding")}`,
    `- border: ${displayPx(bd[0])} / ${displayPx(bd[1])} / ${displayPx(bd[2])} / ${displayPx(bd[3])}${a("border")}`,
    `- margin: ${mg[0]} / ${mg[1]} / ${mg[2]} / ${mg[3]}${a("margin")}`,
    "",
    "## \u5B57\u9AD4 (Typography)",
    `- font-family: ${typography.fontFamily}${a("font-family")}`,
    `- font-size: ${typography.fontSize}px${a("font-size")}`,
    `- font-weight: ${typography.fontWeight}${a("font-weight")}`,
    `- line-height: ${typography.lineHeight}${a("line-height")}`,
    `- color: ${typography.color}${a("color")}`,
    "",
    "## \u80CC\u666F (Background)",
    `- background-color: ${background.color}${a("background-color")}`,
    `- border-radius: ${background.borderRadius.join(" / ")}${a("border-radius")}`,
    "",
    ...frame.index === 1 && session.gaps.length > 0 ? [
      "## \u9593\u8DDD (Gaps)",
      ...session.gaps.map(
        (g) => `- **Frame ${g.from} \u2192 Frame ${g.to}**: ${g.px}px ${g.axis} (${g.axis === "horizontal" ? "\u6C34\u5E73\u9593\u8DDD" : "\u5782\u76F4\u9593\u8DDD"})`
      ),
      ""
    ] : []
  ].join("\n");
}
function displayPx(v) {
  const rounded = Math.round(v);
  if (Math.abs(v - rounded) < 0.5) return String(rounded);
  return String(v);
}

// src/serialize-json.ts
function toJSON(session, options = {}) {
  const pretty = options.pretty !== false;
  return JSON.stringify(session, null, pretty ? 2 : 0);
}

// src/annotate.ts
var SVG_NS = "http://www.w3.org/2000/svg";
var STROKE_SELECTED = "#2563eb";
var FILL_SELECTED = "#2563eb";
var STROKE_GAP = "#ff5000";
var FILL_GAP = "#ff5000";
function buildAnnotationSvg(input, options = {}) {
  const showBadges = options.badges !== false;
  const showSizeLabels = options.sizeLabels !== false;
  const showGaps = options.gaps !== false;
  const focusFrame = options.focusFrame;
  const svg = el("svg", {
    width: input.canvas.width,
    height: input.canvas.height,
    xmlns: SVG_NS
  });
  for (const frame of input.frames) {
    if (focusFrame !== void 0 && frame.index !== focusFrame) continue;
    svg.appendChild(outlineRect(frame.bounds));
    if (showSizeLabels) appendSizeLabel(svg, frame.bounds);
    if (showBadges) appendBadge(svg, frame.index, frame.bounds);
  }
  if (showGaps && input.gaps.length > 0) {
    const byIndex = /* @__PURE__ */ new Map();
    for (const f of input.frames) byIndex.set(f.index, f.bounds);
    for (const gap of input.gaps) {
      const a = byIndex.get(gap.from);
      const b = byIndex.get(gap.to);
      if (!a || !b) continue;
      if (gap.axis === "horizontal") drawHorizontalGap(svg, a, b, gap.px);
      else drawVerticalGap(svg, a, b, gap.px);
    }
  }
  return svg;
}
function outlineRect(b) {
  return el("rect", {
    "data-role": "frame-outline",
    x: b.x,
    y: b.y,
    width: b.width,
    height: b.height,
    fill: "none",
    stroke: STROKE_SELECTED,
    "stroke-width": 2
  });
}
function appendBadge(parent, n, b) {
  const cx = b.x - 10;
  const cy = b.y - 10;
  parent.appendChild(el("circle", {
    "data-role": "badge",
    cx,
    cy,
    r: 10,
    fill: FILL_SELECTED,
    stroke: "#fff",
    "stroke-width": 2
  }));
  const t = el("text", {
    "data-role": "badge-text",
    x: cx,
    y: cy + 3,
    fill: "#fff",
    "font-family": "system-ui, sans-serif",
    "font-size": 11,
    "font-weight": 700,
    "text-anchor": "middle"
  });
  t.textContent = String(n);
  parent.appendChild(t);
}
function appendSizeLabel(parent, b) {
  const text = `${Math.round(b.width)} \xD7 ${Math.round(b.height)} px`;
  const padX = 5;
  const approxW = text.length * 7;
  const bgX = b.x + b.width - approxW - padX * 2;
  const bgY = b.y - 14;
  parent.appendChild(el("rect", {
    "data-role": "size-label-bg",
    x: bgX,
    y: bgY,
    width: approxW + padX * 2,
    height: 16,
    rx: 3,
    ry: 3,
    fill: FILL_SELECTED
  }));
  const t = el("text", {
    "data-role": "size-label",
    x: b.x + b.width - padX,
    y: bgY + 12,
    fill: "#fff",
    "font-family": "system-ui, sans-serif",
    "font-size": 11,
    "font-weight": 600,
    "text-anchor": "end"
  });
  t.textContent = text;
  parent.appendChild(t);
}
function drawHorizontalGap(svg, a, b, px) {
  const aRight = a.x + a.width;
  const bRight = b.x + b.width;
  const left = aRight <= b.x ? { right: aRight } : { right: bRight };
  const right = aRight <= b.x ? { left: b.x } : { left: a.x };
  const y = (Math.max(a.y, b.y) + Math.min(a.y + a.height, b.y + b.height)) / 2;
  svg.appendChild(el("line", {
    "data-role": "gap-main",
    x1: left.right,
    y1: y,
    x2: right.left,
    y2: y,
    stroke: STROKE_GAP,
    "stroke-width": 1.5,
    "stroke-dasharray": "4 3"
  }));
  svg.appendChild(el("line", {
    "data-role": "gap-cap",
    x1: left.right,
    y1: y - 5,
    x2: left.right,
    y2: y + 5,
    stroke: STROKE_GAP,
    "stroke-width": 1.5
  }));
  svg.appendChild(el("line", {
    "data-role": "gap-cap",
    x1: right.left,
    y1: y - 5,
    x2: right.left,
    y2: y + 5,
    stroke: STROKE_GAP,
    "stroke-width": 1.5
  }));
  const midX = (left.right + right.left) / 2;
  appendGapLabel(svg, `${px}px`, midX, y - 6, "middle");
}
function drawVerticalGap(svg, a, b, px) {
  const aBottom = a.y + a.height;
  const bBottom = b.y + b.height;
  const top = aBottom <= b.y ? { bottom: aBottom } : { bottom: bBottom };
  const bottom = aBottom <= b.y ? { top: b.y } : { top: a.y };
  const x = (Math.max(a.x, b.x) + Math.min(a.x + a.width, b.x + b.width)) / 2;
  svg.appendChild(el("line", {
    "data-role": "gap-main",
    x1: x,
    y1: top.bottom,
    x2: x,
    y2: bottom.top,
    stroke: STROKE_GAP,
    "stroke-width": 1.5,
    "stroke-dasharray": "4 3"
  }));
  svg.appendChild(el("line", {
    "data-role": "gap-cap",
    x1: x - 5,
    y1: top.bottom,
    x2: x + 5,
    y2: top.bottom,
    stroke: STROKE_GAP,
    "stroke-width": 1.5
  }));
  svg.appendChild(el("line", {
    "data-role": "gap-cap",
    x1: x - 5,
    y1: bottom.top,
    x2: x + 5,
    y2: bottom.top,
    stroke: STROKE_GAP,
    "stroke-width": 1.5
  }));
  const midY = (top.bottom + bottom.top) / 2;
  appendGapLabel(svg, `${px}px`, x + 4, midY + 4, "start");
}
function appendGapLabel(svg, text, x, y, anchor) {
  const padX = 5;
  const approxW = text.length * 7;
  let bgX = x;
  if (anchor === "middle") bgX = x - approxW / 2 - padX;
  else if (anchor === "end") bgX = x - approxW - padX * 2;
  svg.appendChild(el("rect", {
    "data-role": "gap-label-bg",
    x: bgX,
    y: y - 10,
    width: approxW + padX * 2,
    height: 16,
    rx: 3,
    ry: 3,
    fill: FILL_GAP
  }));
  const t = el("text", {
    "data-role": "gap-label",
    x: anchor === "middle" ? x : anchor === "end" ? x - padX : x + padX,
    y: y + 2,
    fill: "#fff",
    "font-family": "system-ui, sans-serif",
    "font-size": 11,
    "font-weight": 600,
    "text-anchor": anchor
  });
  t.textContent = text;
  svg.appendChild(t);
}
function el(name, attrs) {
  const node = document.createElementNS(SVG_NS, name);
  for (const [k, v] of Object.entries(attrs)) node.setAttribute(k, String(v));
  return node;
}

// src/to-annotated-png.ts
var OVERLAY_ID = "specsnap-capture-overlay";
async function toAnnotatedPNG(session, options = {}) {
  if (session.frames.length === 0) {
    throw new Error("SpecSnap: cannot screenshot an empty session (no frames)");
  }
  const padding = options.padding ?? 16;
  const bbox = computeBbox(session, padding);
  const pixelRatio = options.pixelRatio ?? session.viewport.devicePixelRatio ?? 1;
  const bgcolor = options.background ?? "#ffffff";
  const quality = options.quality ?? 0.92;
  const dtim = await import('dom-to-image-more');
  const toBlob = dtim.default?.toBlob ?? dtim.toBlob;
  const blobs = [];
  for (const frame of session.frames) {
    const overlay = mountOverlay(session, bbox, frame.index, options);
    try {
      const blobOptions = {
        width: bbox.width,
        height: bbox.height,
        pixelRatio,
        bgcolor,
        quality,
        style: {
          transform: `translate(${-bbox.x}px, ${-bbox.y}px)`,
          transformOrigin: "0 0"
        }
      };
      const userFilter = options.filter;
      blobOptions.filter = (node) => {
        if (node === overlay || overlay.contains(node)) return true;
        return userFilter ? userFilter(node) : true;
      };
      const blob = await toBlob(document.body, blobOptions);
      blobs.push(blob);
    } finally {
      overlay.remove();
    }
  }
  return blobs;
}
function computeBbox(session, padding) {
  const first = session.frames[0].rect;
  let minX = first.x;
  let minY = first.y;
  let maxX = first.x + first.width;
  let maxY = first.y + first.height;
  for (const f of session.frames) {
    minX = Math.min(minX, f.rect.x);
    minY = Math.min(minY, f.rect.y);
    maxX = Math.max(maxX, f.rect.x + f.rect.width);
    maxY = Math.max(maxY, f.rect.y + f.rect.height);
  }
  return {
    x: minX - padding,
    y: minY - padding,
    width: maxX - minX + padding * 2,
    height: maxY - minY + padding * 2
  };
}
function mountOverlay(session, bbox, focusFrame, options) {
  const host = document.createElement("div");
  host.id = OVERLAY_ID;
  host.style.cssText = [
    "position:absolute",
    `left:${bbox.x}px`,
    `top:${bbox.y}px`,
    `width:${bbox.width}px`,
    `height:${bbox.height}px`,
    "pointer-events:none",
    "z-index:2147483647"
  ].join(";");
  const annotateOptions = { focusFrame };
  if (options.badges !== void 0) annotateOptions.badges = options.badges;
  if (options.gaps !== void 0) annotateOptions.gaps = options.gaps;
  if (options.sizeLabels !== void 0) annotateOptions.sizeLabels = options.sizeLabels;
  const svg = buildAnnotationSvg(
    {
      frames: session.frames.map((f) => ({
        index: f.index,
        bounds: {
          x: f.rect.x - bbox.x,
          y: f.rect.y - bbox.y,
          width: f.rect.width,
          height: f.rect.height
        }
      })),
      gaps: session.gaps,
      canvas: { width: bbox.width, height: bbox.height }
    },
    annotateOptions
  );
  host.appendChild(svg);
  document.body.appendChild(host);
  return host;
}

// src/to-specsnap-bundle.ts
function formatDateYYYYMMDD(date) {
  const y = date.getFullYear().toString().padStart(4, "0");
  const m = (date.getMonth() + 1).toString().padStart(2, "0");
  const d = date.getDate().toString().padStart(2, "0");
  return `${y}${m}${d}`;
}
function formatCaptureId(date, sequence) {
  const dateStr = formatDateYYYYMMDD(date);
  const seqStr = Math.max(1, Math.min(99, Math.floor(sequence))).toString().padStart(2, "0");
  return `${dateStr}-${seqStr}`;
}
async function toSpecSnapBundle(session, options = {}) {
  if (session.frames.length === 0) {
    throw new Error("SpecSnap: cannot bundle an empty session (no frames)");
  }
  const date = options.date ?? /* @__PURE__ */ new Date();
  const sequence = options.sequence ?? 1;
  const dirName = options.dirName ?? formatDateYYYYMMDD(date);
  const captureId = options.captureId ?? formatCaptureId(date, sequence);
  const imageFilenames = session.frames.map((_, i) => `${captureId}-${i + 1}.png`);
  const pngOptions = {};
  if (options.badges !== void 0) pngOptions.badges = options.badges;
  if (options.gaps !== void 0) pngOptions.gaps = options.gaps;
  if (options.sizeLabels !== void 0) pngOptions.sizeLabels = options.sizeLabels;
  if (options.format !== void 0) pngOptions.format = options.format;
  if (options.quality !== void 0) pngOptions.quality = options.quality;
  if (options.pixelRatio !== void 0) pngOptions.pixelRatio = options.pixelRatio;
  if (options.padding !== void 0) pngOptions.padding = options.padding;
  if (options.background !== void 0) pngOptions.background = options.background;
  if (options.filter !== void 0) pngOptions.filter = options.filter;
  const blobs = await toAnnotatedPNG(session, pngOptions);
  const mdTexts = toMarkdown(session, { imageFilenames });
  const markdownContent = mdTexts.join("\n\n---\n\n");
  return {
    dirName,
    captureId,
    markdownFilename: `${captureId}.md`,
    markdownContent,
    images: blobs.map((blob, i) => ({
      filename: imageFilenames[i],
      blob
    }))
  };
}

// src/index.ts
var VERSION = "0.0.1";

export { DEFAULT_LEXICON, SCHEMA_VERSION, VERSION, annotate, buildAnnotationSvg, captureElement, captureScroll, captureSession, captureViewport, computeGap, formatCaptureId, formatDateYYYYMMDD, toAnnotatedPNG, toJSON, toMarkdown, toSpecSnapBundle };
//# sourceMappingURL=index.mjs.map
//# sourceMappingURL=index.mjs.map