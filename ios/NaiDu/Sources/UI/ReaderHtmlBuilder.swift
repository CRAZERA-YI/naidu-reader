import Foundation

enum ReaderHtmlBuilder {
    static func buildDocument(
        chapterBodyHtml: String,
        settings: AppSettings,
        dueWords: [String],
        learningWords: [String],
        initialPageRatio: Double
    ) -> String {
        let theme = settings.readerTheme
        let dueJson = encodeStringArray(Array(dueWords.prefix(2000)))
        let learningJson = encodeStringArray(Array(learningWords.prefix(2000)))
        let tapEnabled = settings.tapToTranslate ? "true" : "false"
        let highlightEnabled = settings.highlightVocab ? "true" : "false"
        let turnMode = settings.pageTurnMode.rawValue

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  :root {
    --bg: \(theme.bgHex);
    --fg: \(theme.textHex);
    --muted: \(theme.secondaryHex);
    --accent: \(theme.accentHex);
    --quote-bg: \(theme.quoteBgHex);
    --font-size: \(settings.fontSizeSp)px;
    --line-height: \(settings.lineHeight);
    --font-family: \(settings.fontFamily.cssFamily);
    --h-margin: \(settings.horizontalMarginDp)px;
  }
  * {
    box-sizing: border-box;
    -webkit-tap-highlight-color: transparent;
  }
  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    overflow: hidden !important;
    overscroll-behavior: none;
    background-color: var(--bg);
    color: var(--fg);
    font-family: var(--font-family);
    font-size: var(--font-size);
    line-height: var(--line-height);
    -webkit-font-smoothing: antialiased;
    touch-action: manipulation;
  }
  #lingo-viewport {
    position: fixed;
    inset: 0;
    overflow: hidden !important;
    background-color: var(--bg);
  }
  .lingo-page-clip {
    position: fixed;
    inset: 0;
    overflow: hidden !important;
    background-color: var(--bg);
    will-change: transform, opacity;
    backface-visibility: hidden;
    -webkit-backface-visibility: hidden;
  }
  #lingo-under-clip {
    z-index: 1;
    visibility: hidden;
    pointer-events: none;
    user-select: none;
    -webkit-user-select: none;
  }
  #lingo-under-dim {
    position: fixed;
    inset: 0;
    z-index: 2;
    background-color: #000;
    opacity: 0;
    pointer-events: none;
    display: none;
  }
  #lingo-top-clip {
    z-index: 3;
  }
  #lingo-edge-shadow {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: 34px;
    z-index: 4;
    pointer-events: none;
    display: none;
    background: linear-gradient(to right, rgba(0,0,0,0.24) 0%, rgba(0,0,0,0.10) 38%, rgba(0,0,0,0) 100%);
    will-change: transform, opacity;
  }
  #lingo-curl-back {
    position: fixed;
    top: 0;
    bottom: 0;
    left: 0;
    width: 48px;
    z-index: 5;
    pointer-events: none;
    display: none;
    background: linear-gradient(
      to right,
      rgba(0,0,0,0.12) 0%,
      var(--quote-bg) 18%,
      var(--bg) 56%,
      rgba(0,0,0,0.16) 94%,
      rgba(0,0,0,0.28) 100%
    );
    box-shadow: -8px 0 18px rgba(0,0,0,0.16), 12px 0 26px rgba(0,0,0,0.22);
    will-change: transform, width, opacity;
  }
  .lingo-content-layer {
    position: absolute;
    top: 16px;
    left: 0;
    width: 100vw;
    height: calc(100vh - 50px);
    padding: 0 var(--h-margin);
    column-width: calc(100vw - 2 * var(--h-margin));
    column-gap: calc(2 * var(--h-margin));
    column-fill: auto;
    word-wrap: break-word;
    overflow-wrap: break-word;
    will-change: transform;
    backface-visibility: hidden;
    -webkit-backface-visibility: hidden;
  }
  .lingo-content-layer > * { max-width: 100%; }
  h1, h2, h3, h4, h5, h6 {
    line-height: 1.35;
    margin: 0.45em 0 0.65em 0;
    color: var(--fg);
    font-weight: 700;
    break-after: avoid;
  }
  h1 { font-size: 1.42em; }
  h2 { font-size: 1.24em; }
  h3 { font-size: 1.1em; }
  p { margin: 0 0 0.85em 0; text-align: left; orphans: 2; widows: 2; }
  blockquote {
    margin: 0.8em 0;
    padding: 0.65em 0.95em;
    border-left: 3.5px solid var(--accent);
    background-color: var(--quote-bg);
    border-radius: 0 8px 8px 0;
    break-inside: avoid;
  }
  code {
    font-family: 'SF Mono', Menlo, monospace;
    font-size: 0.85em;
    background-color: var(--quote-bg);
    padding: 1px 5px;
    border-radius: 4px;
  }
  pre {
    background-color: var(--quote-bg);
    padding: 10px 12px;
    border-radius: 10px;
    white-space: pre-wrap;
    word-break: break-word;
    break-inside: avoid;
  }
  img {
    max-width: 100% !important;
    max-height: calc(100vh - 110px) !important;
    width: auto !important;
    height: auto !important;
    object-fit: contain;
    display: block;
    margin: 0.8em auto;
    border-radius: 8px;
    break-inside: avoid;
  }
  .lingo-vocab {
    border-bottom: 1.4px dotted #6A8C7E;
    background-color: rgba(106, 140, 126, 0.06);
    border-radius: 2px;
    padding: 0 1px;
  }
  .lingo-vocab-due {
    border-bottom: 1.8px solid #D9822B;
    background-color: rgba(217, 130, 43, 0.14);
    border-radius: 2px;
    padding: 0 1px;
  }
  #__lingo_end_sentinel {
    display: block;
    width: 1px;
    height: 0;
    opacity: 0;
    pointer-events: none;
  }
  #__lingo_tap_hl {
    position: fixed;
    pointer-events: none;
    background-color: rgba(217, 130, 43, 0.30);
    border-bottom: 2px solid #D9822B;
    border-radius: 4px;
    z-index: 9999;
    display: none;
  }
</style>
</head>
<body>
<div id="__lingo_tap_hl"></div>
<div id="lingo-viewport">
  <div id="lingo-under-clip" class="lingo-page-clip">
    <article id="lingo-under-content" class="lingo-content-layer"></article>
  </div>
  <div id="lingo-under-dim"></div>
  <div id="lingo-top-clip" class="lingo-page-clip">
    <article id="lingo-content" class="lingo-content-layer">
\(chapterBodyHtml)
      <span id="__lingo_end_sentinel"></span>
    </article>
  </div>
  <div id="lingo-edge-shadow"></div>
  <div id="lingo-curl-back"></div>
</div>

<script>
(function() {
  function postBridge(payload) {
    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.lingoBridge) {
      window.webkit.messageHandlers.lingoBridge.postMessage(payload);
    }
  }

  window.LingoBridge = {
    onPageChanged: function(cur, total) { postBridge({ type: 'pageChanged', current: cur, total: total }); },
    onWordTapped: function(word, sentence) { postBridge({ type: 'wordTapped', word: word, sentence: sentence }); },
    onSelectionChanged: function(text, sentence) { postBridge({ type: 'selectionChanged', text: text, sentence: sentence }); },
    onSelectionCleared: function() { postBridge({ type: 'selectionCleared' }); },
    onBlankTapped: function() { postBridge({ type: 'blankTapped' }); },
    onRequestNextChapter: function() { postBridge({ type: 'nextChapter' }); },
    onRequestPrevChapter: function() { postBridge({ type: 'prevChapter' }); }
  };

  window.__lingoTapEnabled = \(tapEnabled);
  window.__lingoHighlightEnabled = \(highlightEnabled);
  window.__lingoDueWords = new Set(\(dueJson));
  window.__lingoLearningWords = new Set(\(learningJson));
  window.__lingoPopupOpen = false;
  window.__lingoPageTurnMode = '\(turnMode)';

  var viewportEl = document.getElementById('lingo-viewport');
  var underClipEl = document.getElementById('lingo-under-clip');
  var underContentEl = document.getElementById('lingo-under-content');
  var underDimEl = document.getElementById('lingo-under-dim');
  var topClipEl = document.getElementById('lingo-top-clip');
  var contentEl = document.getElementById('lingo-content');
  var edgeShadowEl = document.getElementById('lingo-edge-shadow');
  var curlBackEl = document.getElementById('lingo-curl-back');
  var endSentinel = document.getElementById('__lingo_end_sentinel');
  var tapHlEl = document.getElementById('__lingo_tap_hl');

  var currentPage = 0;
  var totalPages = 1;
  var pageWidth = Math.max(1, window.innerWidth);
  var currentMarginPx = \(settings.horizontalMarginDp);

  var animRafId = 0;
  var dragRafId = 0;
  var isAnimating = false;
  var pendingDragDx = 0;
  var activeDragDir = 0;
  var underLayerDirty = true;

  function syncUnderContent() { underLayerDirty = true; }

  function flushUnderContentIfNeeded() {
    if (!underLayerDirty || !underContentEl || !contentEl) return;
    underLayerDirty = false;
    var vh = Math.max(200, window.innerHeight);
    var colW = Math.max(120, pageWidth - 2 * currentMarginPx);
    applyColumnGeometryTo(underContentEl, pageWidth, vh, colW, currentMarginPx);
    underContentEl.innerHTML = contentEl.innerHTML;
    var dup = underContentEl.querySelector('#__lingo_end_sentinel');
    if (dup) dup.removeAttribute('id');
  }

  function isWordChar(ch) { return /[A-Za-z'\\-\\u2019]/.test(ch); }

  function hasActiveSelection() {
    var sel = window.getSelection();
    return !!(sel && !sel.isCollapsed && sel.toString().trim().length > 0);
  }

  function clearTapHighlight() { if (tapHlEl) tapHlEl.style.display = 'none'; }
  window.clearTapHighlight = clearTapHighlight;

  function showTapHighlight(range) {
    if (!tapHlEl || !range) return;
    var rects = range.getClientRects();
    if (!rects || rects.length === 0) return;
    var r = rects[0];
    tapHlEl.style.left = (r.left - 2) + 'px';
    tapHlEl.style.top = (r.top - 1) + 'px';
    tapHlEl.style.width = (r.width + 4) + 'px';
    tapHlEl.style.height = (r.height + 2) + 'px';
    tapHlEl.style.display = 'block';
  }

  function applyColumnGeometryTo(el, vw, vh, colW, margin) {
    el.style.width = vw + 'px';
    el.style.height = (vh - 50) + 'px';
    el.style.paddingLeft = margin + 'px';
    el.style.paddingRight = margin + 'px';
    el.style.columnWidth = colW + 'px';
    el.style.columnGap = (2 * margin) + 'px';
  }

  function applyColumnGeometry() {
    pageWidth = Math.max(1, window.innerWidth);
    var vh = Math.max(200, window.innerHeight);
    var colW = Math.max(120, pageWidth - 2 * currentMarginPx);
    applyColumnGeometryTo(contentEl, pageWidth, vh, colW, currentMarginPx);
    if (!underLayerDirty) {
      applyColumnGeometryTo(underContentEl, pageWidth, vh, colW, currentMarginPx);
    }
  }

  function computeTotalPages() {
    applyColumnGeometry();
    var contentRect = contentEl.getBoundingClientRect();
    var pages = 1;
    if (endSentinel) {
      var endRect = endSentinel.getBoundingClientRect();
      var relX = endRect.left - contentRect.left;
      if (relX >= 0) {
        pages = Math.floor((relX + 1) / pageWidth) + 1;
      }
    }
    var byScroll = Math.max(1, Math.round((contentEl.scrollWidth - 2) / pageWidth));
    if (byScroll > pages && (contentEl.scrollWidth - pages * pageWidth) > pageWidth * 0.25) {
      pages = byScroll;
    }
    totalPages = Math.max(1, pages);
    if (currentPage >= totalPages) currentPage = totalPages - 1;
    if (currentPage < 0) currentPage = 0;
  }

  function notifyPageChanged() { window.LingoBridge.onPageChanged(currentPage, totalPages); }

  function resetVisualLayers() {
    topClipEl.style.transform = 'translate3d(0,0,0)';
    topClipEl.style.opacity = '1';
    contentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
    underClipEl.style.visibility = 'hidden';
    underDimEl.style.display = 'none';
    underDimEl.style.opacity = '0';
    edgeShadowEl.style.display = 'none';
    curlBackEl.style.display = 'none';
    activeDragDir = 0;
  }

  function stopAnimations() {
    if (animRafId) { cancelAnimationFrame(animRafId); animRafId = 0; }
    if (dragRafId) { cancelAnimationFrame(dragRafId); dragRafId = 0; }
    isAnimating = false;
  }

  function renderTurnFrame(mode, dir, offsetPx) {
    var W = pageWidth;
    var clamped = dir < 0 ? Math.max(-W, Math.min(0, offsetPx)) : Math.max(0, Math.min(W, offsetPx));
    var progress = Math.abs(clamped) / W;

    if (mode === 'slide') {
      underClipEl.style.visibility = 'hidden';
      underDimEl.style.display = 'none';
      edgeShadowEl.style.display = 'none';
      curlBackEl.style.display = 'none';
      topClipEl.style.transform = 'translate3d(0,0,0)';
      topClipEl.style.opacity = '1';
      contentEl.style.transform = 'translate3d(' + (-currentPage * W + clamped) + 'px,0,0)';
      return;
    }
    if (mode === 'fade') {
      edgeShadowEl.style.display = 'none';
      curlBackEl.style.display = 'none';
      underDimEl.style.display = 'none';
      topClipEl.style.transform = 'translate3d(0,0,0)';
      topClipEl.style.opacity = String(Math.max(0, 1 - progress));
      return;
    }
    if (mode === 'cover' || mode === 'simulation') {
      var topX = dir < 0 ? clamped : (-W + clamped);
      topClipEl.style.transform = 'translate3d(' + topX + 'px,0,0)';
      topClipEl.style.opacity = '1';
      var edgeX = topX + W;
      if (mode === 'cover') {
        curlBackEl.style.display = 'none';
        underDimEl.style.display = 'block';
        var dimFactor = dir < 0 ? (1 - progress) : progress;
        underDimEl.style.opacity = String((dimFactor * 0.14).toFixed(3));
        edgeShadowEl.style.display = 'block';
        edgeShadowEl.style.transform = 'translate3d(' + edgeX + 'px,0,0)';
        edgeShadowEl.style.opacity = String(Math.sin(progress * Math.PI) * 0.9 + 0.1);
      } else {
        edgeShadowEl.style.display = 'none';
        underDimEl.style.display = 'block';
        var simDim = dir < 0 ? (1 - progress) : progress;
        underDimEl.style.opacity = String((simDim * 0.22).toFixed(3));
        var curlW = Math.max(8, Math.round(Math.sin(progress * Math.PI) * 58));
        curlBackEl.style.display = 'block';
        curlBackEl.style.width = curlW + 'px';
        curlBackEl.style.transform = 'translate3d(' + (edgeX - Math.round(curlW * 0.35)) + 'px,0,0)';
        curlBackEl.style.opacity = String(Math.min(1, Math.sin(progress * Math.PI) * 1.45 + 0.12));
      }
    }
  }

  function prepareDualLayer(dir) {
    var mode = window.__lingoPageTurnMode;
    if (mode === 'slide' || mode === 'none') return;
    var targetPage = currentPage + (dir < 0 ? 1 : -1);
    if (targetPage < 0 || targetPage >= totalPages) return;
    flushUnderContentIfNeeded();
    underClipEl.style.visibility = 'visible';
    if (dir < 0 || mode === 'fade') {
      contentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
      underContentEl.style.transform = 'translate3d(' + (-targetPage * pageWidth) + 'px,0,0)';
    } else {
      contentEl.style.transform = 'translate3d(' + (-targetPage * pageWidth) + 'px,0,0)';
      underContentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
    }
  }

  function animateTurn(mode, dir, startOffset, endOffset, commitTargetPage, durationMs) {
    stopAnimations();
    isAnimating = true;
    if (mode === 'none' || durationMs <= 0) {
      if (commitTargetPage !== null) currentPage = commitTargetPage;
      resetVisualLayers();
      isAnimating = false;
      if (commitTargetPage !== null) notifyPageChanged();
      return;
    }
    var startTime = performance.now();
    function step(now) {
      var t = Math.min(1, (now - startTime) / durationMs);
      var eased = 1 - Math.pow(1 - t, 3.2);
      renderTurnFrame(mode, dir, startOffset + (endOffset - startOffset) * eased);
      if (t < 1) {
        animRafId = requestAnimationFrame(step);
      } else {
        animRafId = 0;
        if (commitTargetPage !== null) currentPage = commitTargetPage;
        resetVisualLayers();
        isAnimating = false;
        if (commitTargetPage !== null) setTimeout(notifyPageChanged, 16);
      }
    }
    animRafId = requestAnimationFrame(step);
  }

  function goToPage(pageIdx, animated) {
    stopAnimations();
    clearTapHighlight();
    var target = Math.max(0, Math.min(totalPages - 1, pageIdx));
    if (!animated || target === currentPage || window.__lingoPageTurnMode === 'none') {
      currentPage = target;
      resetVisualLayers();
      notifyPageChanged();
      return;
    }
    var dir = target > currentPage ? -1 : 1;
    var mode = window.__lingoPageTurnMode;
    if (Math.abs(target - currentPage) === 1 && mode !== 'slide') {
      prepareDualLayer(dir);
      var dur = mode === 'simulation' ? 300 : (mode === 'fade' ? 220 : 260);
      animateTurn(mode, dir, 0, dir < 0 ? -pageWidth : pageWidth, target, dur);
    } else if (Math.abs(target - currentPage) === 1) {
      animateTurn('slide', dir, 0, dir < 0 ? -pageWidth : pageWidth, target, 250);
    } else {
      currentPage = target;
      resetVisualLayers();
      notifyPageChanged();
    }
  }
  window.goToPage = goToPage;

  function nextPage() {
    if (isAnimating) return;
    if (currentPage < totalPages - 1) goToPage(currentPage + 1, true);
    else { resetVisualLayers(); window.LingoBridge.onRequestNextChapter(); }
  }
  window.nextPage = nextPage;

  function prevPage() {
    if (isAnimating) return;
    if (currentPage > 0) goToPage(currentPage - 1, true);
    else { resetVisualLayers(); window.LingoBridge.onRequestPrevChapter(); }
  }
  window.prevPage = prevPage;

  function relayoutPages(preserveRatio) {
    stopAnimations();
    var ratio = typeof preserveRatio === 'number' ? preserveRatio : (totalPages <= 1 ? 0 : currentPage / (totalPages - 1));
    computeTotalPages();
    currentPage = ratio < 0 ? (totalPages - 1) : Math.max(0, Math.min(totalPages - 1, Math.round(ratio * (totalPages - 1))));
    resetVisualLayers();
    notifyPageChanged();
  }
  window.relayoutPages = relayoutPages;

  function extractSentence(node, wordStartText) {
    var block = node;
    while (block && block !== contentEl) {
      if (block.nodeType === 1 && /^(P|LI|BLOCKQUOTE|TD|H1|H2|H3|DIV)$/.test(block.tagName)) break;
      block = block.parentNode;
    }
    var full = (block && block.innerText ? block.innerText : (node.textContent || '')).replace(/\\s+/g, ' ').trim();
    if (!full) return '';
    if (full.length <= 220) return full;
    var needle = (wordStartText || '').trim();
    var pos = needle ? full.toLowerCase().indexOf(needle.toLowerCase()) : 0;
    if (pos < 0) pos = 0;
    var start = pos, end = pos + needle.length;
    while (start > 0 && !/[.!?。！？\\n]/.test(full.charAt(start - 1))) start--;
    while (end < full.length && !/[.!?。！？\\n]/.test(full.charAt(end))) end++;
    if (end < full.length) end++;
    var s = full.slice(start, end).trim();
    return s.length > 280 ? (s.slice(0, 277) + '...') : s;
  }

  function findWordAtPoint(x, y) {
    var range = document.caretRangeFromPoint ? document.caretRangeFromPoint(x, y) : null;
    if (!range || !range.startContainer || range.startContainer.nodeType !== 3) return null;
    var textNode = range.startContainer;
    var text = textNode.nodeValue || '';
    var offset = range.startOffset;
    if (offset >= text.length && offset > 0) offset = text.length - 1;
    if (!isWordChar(text.charAt(offset))) {
      if (offset > 0 && isWordChar(text.charAt(offset - 1))) offset--;
      else return null;
    }
    var start = offset, end = offset;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    while (end < text.length && isWordChar(text.charAt(end))) end++;
    var rawWord = text.slice(start, end).replace(/^['\\-\\u2019]+|['\\-\\u2019]+$/g, '');
    if (!rawWord || !/[A-Za-z]/.test(rawWord)) return null;
    var wordRange = document.createRange();
    wordRange.setStart(textNode, start);
    wordRange.setEnd(textNode, end);
    var rect = wordRange.getBoundingClientRect();
    if (x < rect.left - 16 || x > rect.right + 16 || y < rect.top - 14 || y > rect.bottom + 14) return null;
    return { word: rawWord, range: wordRange, sentence: extractSentence(textNode, rawWord) };
  }

  var touchStartX = 0, touchStartY = 0, touchStartTime = 0, touchLastX = 0, touchLastY = 0;
  var touchMoved = false, isDraggingPage = false;
  var lastTapTime = 0, lastTapX = 0, lastTapY = 0, singleTapTimer = null;

  function cancelPendingSingleTap() {
    if (singleTapTimer) { clearTimeout(singleTapTimer); singleTapTimer = null; }
  }

  function scheduleDragRender() {
    if (dragRafId) return;
    dragRafId = requestAnimationFrame(function() {
      dragRafId = 0;
      if (!isDraggingPage) return;
      var mode = window.__lingoPageTurnMode;
      var dx = pendingDragDx;
      var desiredDir = dx < 0 ? -1 : 1;
      if (mode === 'none') return;
      var atStart = (currentPage === 0 && desiredDir > 0);
      var atEnd = (currentPage === totalPages - 1 && desiredDir < 0);
      if (atStart || atEnd) {
        if (activeDragDir !== 0) resetVisualLayers();
        contentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth + dx * 0.28) + 'px,0,0)';
        return;
      }
      if (activeDragDir !== desiredDir) {
        activeDragDir = desiredDir;
        prepareDualLayer(desiredDir);
      }
      renderTurnFrame(mode, desiredDir, dx);
    });
  }

  viewportEl.addEventListener('touchstart', function(e) {
    if (e.touches.length !== 1 || hasActiveSelection() || isAnimating) { isDraggingPage = false; return; }
    touchStartX = touchLastX = e.touches[0].clientX;
    touchStartY = touchLastY = e.touches[0].clientY;
    touchStartTime = Date.now();
    touchMoved = false;
    isDraggingPage = false;
    pendingDragDx = 0;
    activeDragDir = 0;
  }, { passive: true });

  viewportEl.addEventListener('touchmove', function(e) {
    if (e.touches.length !== 1 || hasActiveSelection() || isAnimating) { isDraggingPage = false; return; }
    var x = e.touches[0].clientX, y = e.touches[0].clientY;
    var dx = x - touchStartX, dy = y - touchStartY;
    touchLastX = x; touchLastY = y;
    if (Math.abs(dx) > 9 || Math.abs(dy) > 9) { touchMoved = true; cancelPendingSingleTap(); }
    if (!isDraggingPage && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.05) {
      isDraggingPage = true;
      clearTapHighlight();
    }
    if (isDraggingPage) {
      e.preventDefault();
      pendingDragDx = dx;
      scheduleDragRender();
    } else if (Math.abs(dy) > 10) {
      e.preventDefault();
    }
  }, { passive: false });

  function handleSingleTapAction(x, y) {
    clearTapHighlight();
    if (window.__lingoPopupOpen || y < 34) { window.LingoBridge.onBlankTapped(); return; }
    var w = Math.max(1, window.innerWidth);
    if (x < w * 0.30) prevPage();
    else if (x > w * 0.70) nextPage();
    else window.LingoBridge.onBlankTapped();
  }

  viewportEl.addEventListener('touchend', function(e) {
    if (isDraggingPage) {
      isDraggingPage = false;
      if (dragRafId) { cancelAnimationFrame(dragRafId); dragRafId = 0; }
      var dx = touchLastX - touchStartX;
      var dt = Math.max(1, Date.now() - touchStartTime);
      var velocity = Math.abs(dx) / dt;
      var mode = window.__lingoPageTurnMode;
      var shouldTurn = Math.abs(dx) > pageWidth * 0.16 || (Math.abs(dx) > 22 && velocity > 0.28);
      var dir = dx < 0 ? -1 : 1;
      var atStart = (currentPage === 0 && dir > 0);
      var atEnd = (currentPage === totalPages - 1 && dir < 0);

      if (shouldTurn && (atStart || atEnd)) {
        resetVisualLayers();
        if (atEnd) window.LingoBridge.onRequestNextChapter();
        else window.LingoBridge.onRequestPrevChapter();
        return;
      }
      if (atStart || atEnd) { animateTurn('slide', dir, dx * 0.28, 0, null, 180); return; }
      if (mode === 'none') {
        if (shouldTurn) { currentPage += (dir < 0 ? 1 : -1); resetVisualLayers(); notifyPageChanged(); }
        else resetVisualLayers();
        return;
      }
      if (activeDragDir !== dir) { activeDragDir = dir; prepareDualLayer(dir); }
      if (shouldTurn) {
        var targetPage = currentPage + (dir < 0 ? 1 : -1);
        var rem = Math.max(0.25, 1 - Math.abs(dx) / pageWidth);
        var baseDur = mode === 'simulation' ? 280 : (mode === 'fade' ? 200 : 240);
        animateTurn(mode, dir, dx, dir < 0 ? -pageWidth : pageWidth, targetPage, Math.max(110, Math.round(baseDur * rem)));
      } else {
        animateTurn(mode, dir, dx, 0, null, Math.max(110, Math.round(200 * (Math.abs(dx) / pageWidth))));
      }
      return;
    }

    var touchDuration = Date.now() - touchStartTime;
    if (touchMoved || touchDuration > 320 || hasActiveSelection() || isAnimating) return;

    var now = Date.now(), tapX = touchLastX, tapY = touchLastY;
    var distSq = (tapX - lastTapX) * (tapX - lastTapX) + (tapY - lastTapY) * (tapY - lastTapY);

    if (now - lastTapTime < 280 && distSq < 44 * 44) {
      cancelPendingSingleTap();
      lastTapTime = 0;
      if (window.__lingoTapEnabled) {
        var hit = findWordAtPoint(tapX, tapY);
        if (hit) {
          e.preventDefault();
          showTapHighlight(hit.range);
          setTimeout(function() {
            var s = window.getSelection();
            if (s && !s.isCollapsed) s.removeAllRanges();
          }, 25);
          window.LingoBridge.onWordTapped(hit.word, hit.sentence);
          return;
        }
      }
      window.LingoBridge.onBlankTapped();
      return;
    }

    lastTapTime = now; lastTapX = tapX; lastTapY = tapY;
    if (window.__lingoPopupOpen || tapY < 34 || !window.__lingoTapEnabled) {
      handleSingleTapAction(tapX, tapY);
      lastTapTime = 0;
    } else {
      var probe = findWordAtPoint(tapX, tapY);
      if (!probe) {
        handleSingleTapAction(tapX, tapY);
        lastTapTime = 0;
      } else {
        cancelPendingSingleTap();
        singleTapTimer = setTimeout(function() {
          singleTapTimer = null;
          handleSingleTapAction(tapX, tapY);
        }, 175);
      }
    }
  }, { passive: false });

  var selTimer = null;
  document.addEventListener('selectionchange', function() {
    clearTimeout(selTimer);
    selTimer = setTimeout(function() {
      var sel = window.getSelection();
      var text = sel ? sel.toString().trim() : '';
      if (text.length >= 2) {
        clearTapHighlight();
        cancelPendingSingleTap();
        window.LingoBridge.onSelectionChanged(text, extractSentence(sel.anchorNode || contentEl, text));
      } else {
        window.LingoBridge.onSelectionCleared();
      }
    }, 220);
  });

  window.applyStyle = function(bg, fg, muted, accent, quoteBg, fontSize, lineHeight, fontFamily, hMargin, tapEnabled, pageTurnMode) {
    var prevRatio = totalPages <= 1 ? 0 : currentPage / (totalPages - 1);
    var root = document.documentElement.style;
    root.setProperty('--bg', bg);
    root.setProperty('--fg', fg);
    root.setProperty('--muted', muted);
    root.setProperty('--accent', accent);
    root.setProperty('--quote-bg', quoteBg);
    root.setProperty('--font-size', fontSize + 'px');
    root.setProperty('--line-height', String(lineHeight));
    root.setProperty('--font-family', fontFamily);
    root.setProperty('--h-margin', hMargin + 'px');
    currentMarginPx = hMargin;
    window.__lingoTapEnabled = tapEnabled;
    if (pageTurnMode) window.__lingoPageTurnMode = pageTurnMode;
    requestAnimationFrame(function() { relayoutPages(prevRatio); });
  };

  function clearVocabHighlightsOn(rootEl) {
    var marks = rootEl.querySelectorAll('span.lingo-vocab, span.lingo-vocab-due');
    for (var i = 0; i < marks.length; i++) {
      var m = marks[i], p = m.parentNode;
      if (p) { p.replaceChild(document.createTextNode(m.textContent), m); p.normalize(); }
    }
  }

  function applyVocabHighlights(dueArray, learningArray, enabled) {
    clearVocabHighlightsOn(contentEl);
    window.__lingoHighlightEnabled = enabled;
    if (Array.isArray(dueArray)) window.__lingoDueWords = new Set(dueArray);
    if (Array.isArray(learningArray)) window.__lingoLearningWords = new Set(learningArray);
    if (!enabled || (window.__lingoDueWords.size === 0 && window.__lingoLearningWords.size === 0)) {
      syncUnderContent();
      return;
    }
    var walker = document.createTreeWalker(contentEl, NodeFilter.SHOW_TEXT, null);
    var nodes = [], n;
    while ((n = walker.nextNode())) {
      if (n.nodeValue && /[A-Za-z]/.test(n.nodeValue)) nodes.push(n);
    }
    var wordRegex = /\\b[A-Za-z]{2,}(?:['\\-][A-Za-z]+)?\\b/g;
    var dueSet = window.__lingoDueWords, learnSet = window.__lingoLearningWords;
    for (var i = 0; i < nodes.length; i++) {
      var textNode = nodes[i], text = textNode.nodeValue;
      var p = textNode.parentNode;
      if (!p || /^(SCRIPT|STYLE|CODE|PRE)$/.test(p.tagName)) continue;
      wordRegex.lastIndex = 0;
      var match, lastIdx = 0, frag = null;
      while ((match = wordRegex.exec(text)) !== null) {
        var w = match[0], wl = w.toLowerCase();
        var cls = dueSet.has(wl) ? 'lingo-vocab-due' : (learnSet.has(wl) ? 'lingo-vocab' : null);
        if (cls) {
          if (!frag) frag = document.createDocumentFragment();
          if (match.index > lastIdx) frag.appendChild(document.createTextNode(text.slice(lastIdx, match.index)));
          var sp = document.createElement('span');
          sp.className = cls;
          sp.textContent = w;
          frag.appendChild(sp);
          lastIdx = match.index + w.length;
        }
      }
      if (frag) {
        if (lastIdx < text.length) frag.appendChild(document.createTextNode(text.slice(lastIdx)));
        p.replaceChild(frag, textNode);
      }
    }
    syncUnderContent();
  }
  window.applyVocabHighlights = applyVocabHighlights;

  var initRatio = \(initialPageRatio);
  window.addEventListener('DOMContentLoaded', function() {
    if (window.__lingoHighlightEnabled && (window.__lingoDueWords.size > 0 || window.__lingoLearningWords.size > 0)) {
      applyVocabHighlights(Array.from(window.__lingoDueWords), Array.from(window.__lingoLearningWords), true);
    } else {
      syncUnderContent();
    }
    relayoutPages(initRatio);
    setTimeout(function() { relayoutPages(initRatio); }, 160);
  });
  window.addEventListener('resize', function() { relayoutPages(); });
})();
</script>
</body>
</html>
"""
    }

    private static func encodeStringArray(_ arr: [String]) -> String {
        guard let data = try? JSONEncoder().encode(arr),
              let s = String(data: data, encoding: .utf8) else {
            return "[]"
        }
        return s
    }
}
