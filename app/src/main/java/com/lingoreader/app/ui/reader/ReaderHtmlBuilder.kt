package com.lingoreader.app.ui.reader

import com.google.gson.Gson
import com.lingoreader.app.data.model.AppSettings

object ReaderHtmlBuilder {

    private val gson = Gson()

    /**
     * Builds a silky 60/120fps horizontal paginated novel-style reading document
     * supporting 5 page-turn modes (cover, simulation, slide, fade, none)
     * and double-tap word lookup + single-tap page-turn/menu zones.
     */
    fun buildDocument(
        chapterBodyHtml: String,
        settings: AppSettings,
        dueWords: Set<String>,
        learningWords: Set<String>,
        initialPageRatio: Float
    ): String {
        val theme = settings.readerTheme
        val dueJson = gson.toJson(dueWords.take(2000))
        val learningJson = gson.toJson(learningWords.take(2000))
        val tapEnabled = settings.tapToTranslate
        val highlightEnabled = settings.highlightVocab
        val turnMode = settings.pageTurnMode.id

        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<style>
  :root {
    --bg: ${theme.bgHex};
    --fg: ${theme.textHex};
    --muted: ${theme.secondaryHex};
    --accent: ${theme.accentHex};
    --quote-bg: ${theme.quoteBgHex};
    --font-size: ${settings.fontSizeSp}px;
    --line-height: ${settings.lineHeight};
    --font-family: ${settings.fontFamily.cssFamily};
    --h-margin: ${settings.horizontalMarginDp}px;
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
    top: 22px;
    left: 0;
    width: 100vw;
    height: calc(100vh - 58px);
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
  .lingo-content-layer > * {
    max-width: 100%;
  }
  h1, h2, h3, h4, h5, h6 {
    line-height: 1.35;
    margin: 0.45em 0 0.65em 0;
    color: var(--fg);
    font-weight: 700;
    break-after: avoid;
    page-break-after: avoid;
  }
  h1 { font-size: 1.42em; }
  h2 { font-size: 1.24em; }
  h3 { font-size: 1.1em; }
  p {
    margin: 0 0 0.85em 0;
    text-align: left;
    orphans: 2;
    widows: 2;
  }
  blockquote {
    margin: 0.8em 0;
    padding: 0.65em 0.95em;
    border-left: 3.5px solid var(--accent);
    background-color: var(--quote-bg);
    border-radius: 0 8px 8px 0;
    color: var(--fg);
    break-inside: avoid;
  }
  blockquote p:last-child {
    margin-bottom: 0;
  }
  code {
    font-family: 'JetBrains Mono', 'Roboto Mono', monospace;
    font-size: 0.85em;
    background-color: var(--quote-bg);
    padding: 1px 5px;
    border-radius: 4px;
    word-break: break-word;
  }
  pre {
    background-color: var(--quote-bg);
    padding: 10px 12px;
    border-radius: 10px;
    line-height: 1.45;
    margin: 0.8em 0;
    white-space: pre-wrap;
    word-break: break-word;
    overflow: hidden;
    break-inside: avoid;
  }
  pre code {
    background: transparent;
    padding: 0;
  }
  ul, ol {
    padding-left: 1.35em;
    margin: 0.5em 0 0.85em 0;
  }
  li {
    margin-bottom: 0.38em;
  }
  hr {
    border: none;
    border-top: 1px solid var(--muted);
    opacity: 0.3;
    margin: 1.2em 0;
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
  a {
    color: var(--accent);
    text-decoration: underline;
    text-underline-offset: 3px;
  }
  .table-wrap {
    max-width: 100%;
    overflow: hidden;
    margin: 0.8em 0;
    break-inside: avoid;
  }
  table {
    width: 100%;
    border-collapse: collapse;
    font-size: 0.86em;
    table-layout: fixed;
    word-break: break-word;
  }
  th, td {
    border: 1px solid rgba(128,128,128,0.28);
    padding: 6px 8px;
    text-align: left;
  }
  th {
    background-color: var(--quote-bg);
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
    line-height: 0;
    font-size: 0;
    margin: 0;
    padding: 0;
    overflow: hidden;
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
$chapterBodyHtml
      <span id="__lingo_end_sentinel"></span>
    </article>
  </div>
  <div id="lingo-edge-shadow"></div>
  <div id="lingo-curl-back"></div>
</div>

<script>
(function() {
  window.__lingoTapEnabled = $tapEnabled;
  window.__lingoHighlightEnabled = $highlightEnabled;
  window.__lingoDueWords = new Set($dueJson);
  window.__lingoLearningWords = new Set($learningJson);
  window.__lingoPopupOpen = false;
  window.__lingoPageTurnMode = '$turnMode';

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
  var currentMarginPx = ${settings.horizontalMarginDp};

  // Animation state (pure rAF for silky 60/120fps, zero stutter)
  var animRafId = 0;
  var dragRafId = 0;
  var isAnimating = false;
  var pendingDragDx = 0;
  var activeDragDir = 0; // -1 = dragging left (to next page), +1 = dragging right (to prev page)
  var underLayerDirty = true;

  function syncUnderContent() {
    underLayerDirty = true;
  }

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

  function isWordChar(ch) {
    return /[A-Za-z'\-\u2019]/.test(ch);
  }

  function hasActiveSelection() {
    var sel = window.getSelection();
    return !!(sel && !sel.isCollapsed && sel.toString().trim().length > 0);
  }

  function clearTapHighlight() {
    if (tapHlEl) tapHlEl.style.display = 'none';
  }
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

  // ---- Column Geometry & Exact Page Counting ----

  function applyColumnGeometryTo(el, vw, vh, colW, margin) {
    el.style.width = vw + 'px';
    el.style.height = (vh - 58) + 'px';
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

  function notifyPageChanged() {
    if (window.LingoBridge && window.LingoBridge.onPageChanged) {
      window.LingoBridge.onPageChanged(currentPage, totalPages);
    }
  }

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
    if (animRafId) {
      cancelAnimationFrame(animRafId);
      animRafId = 0;
    }
    if (dragRafId) {
      cancelAnimationFrame(dragRafId);
      dragRafId = 0;
    }
    isAnimating = false;
  }

  // Render a single frame of page turn progress for ALL 5 modes!
  // dir = -1 (going forward to currentPage+1), dir = +1 (going backward to currentPage-1)
  // offsetPx:
  //   for dir = -1: 0 (rest) -> -pageWidth (completed next page)
  //   for dir = +1: 0 (rest) -> +pageWidth (completed prev page)
  function renderTurnFrame(mode, dir, offsetPx) {
    var W = pageWidth;
    var clamped = dir < 0
      ? Math.max(-W, Math.min(0, offsetPx))
      : Math.max(0, Math.min(W, offsetPx));
    var progress = Math.abs(clamped) / W; // 0 .. 1

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
      // Top clip slides from 0 -> -W (next) OR from -W -> 0 (prev)
      var topX = dir < 0 ? clamped : (-W + clamped);
      topClipEl.style.transform = 'translate3d(' + topX + 'px,0,0)';
      topClipEl.style.opacity = '1';

      var edgeX = topX + W; // Right edge of the moving top page (0 .. W)

      if (mode === 'cover') {
        curlBackEl.style.display = 'none';
        underDimEl.style.display = 'block';
        // Subtle ambient shadow on underneath page that lifts cleanly as top page moves away
        var dimFactor = dir < 0 ? (1 - progress) : progress;
        underDimEl.style.opacity = String((dimFactor * 0.14).toFixed(3));

        edgeShadowEl.style.display = 'block';
        edgeShadowEl.style.transform = 'translate3d(' + edgeX + 'px,0,0)';
        edgeShadowEl.style.opacity = String(Math.sin(progress * Math.PI) * 0.9 + 0.1);
      } else {
        // SIMULATION: realistic paper curl lip + dynamic shadow + ambient depth
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
      return;
    }
  }

  function prepareDualLayer(dir) {
    var mode = window.__lingoPageTurnMode;
    if (mode === 'slide' || mode === 'none') return;
    var targetPage = currentPage + (dir < 0 ? 1 : -1);
    if (targetPage < 0 || targetPage >= totalPages) return;

    flushUnderContentIfNeeded();
    underClipEl.style.visibility = 'visible';
    if (dir < 0) {
      // Forward: currentPage on top, nextPage underneath
      contentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
      underContentEl.style.transform = 'translate3d(' + (-targetPage * pageWidth) + 'px,0,0)';
    } else {
      if (mode === 'fade') {
        contentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
        underContentEl.style.transform = 'translate3d(' + (-targetPage * pageWidth) + 'px,0,0)';
      } else {
        // Backward cover/simulation: prevPage slides IN on top over stationary currentPage underneath
        contentEl.style.transform = 'translate3d(' + (-targetPage * pageWidth) + 'px,0,0)';
        underContentEl.style.transform = 'translate3d(' + (-currentPage * pageWidth) + 'px,0,0)';
      }
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
      var elapsed = now - startTime;
      var t = Math.min(1, elapsed / durationMs);
      // Smooth quartic/cubic deceleration curve (silk-smooth, zero bounce stutter)
      var eased = 1 - Math.pow(1 - t, 3.2);
      var currentOffset = startOffset + (endOffset - startOffset) * eased;
      renderTurnFrame(mode, dir, currentOffset);

      if (t < 1) {
        animRafId = requestAnimationFrame(step);
      } else {
        animRafId = 0;
        if (commitTargetPage !== null) {
          currentPage = commitTargetPage;
        }
        resetVisualLayers();
        isAnimating = false;
        // Notify Android AFTER animation completes so Compose/SQLite never causes frame jank!
        if (commitTargetPage !== null) {
          setTimeout(notifyPageChanged, 16);
        }
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
    if (currentPage < totalPages - 1) {
      goToPage(currentPage + 1, true);
    } else {
      resetVisualLayers();
      if (window.LingoBridge && window.LingoBridge.onRequestNextChapter) {
        window.LingoBridge.onRequestNextChapter();
      }
    }
  }
  window.nextPage = nextPage;

  function prevPage() {
    if (isAnimating) return;
    if (currentPage > 0) {
      goToPage(currentPage - 1, true);
    } else {
      resetVisualLayers();
      if (window.LingoBridge && window.LingoBridge.onRequestPrevChapter) {
        window.LingoBridge.onRequestPrevChapter();
      }
    }
  }
  window.prevPage = prevPage;

  function relayoutPages(preserveRatio) {
    stopAnimations();
    var ratio = typeof preserveRatio === 'number'
      ? preserveRatio
      : (totalPages <= 1 ? 0 : currentPage / (totalPages - 1));
    computeTotalPages();
    if (ratio < 0) {
      currentPage = totalPages - 1;
    } else {
      currentPage = Math.max(0, Math.min(totalPages - 1, Math.round(ratio * (totalPages - 1))));
    }
    resetVisualLayers();
    notifyPageChanged();
  }
  window.relayoutPages = relayoutPages;

  // ---- Word Lookup & Sentence Extraction ----

  function extractSentence(node, wordStartText) {
    var block = node;
    while (block && block !== contentEl) {
      if (block.nodeType === 1) {
        var tag = block.tagName;
        if (tag === 'P' || tag === 'LI' || tag === 'BLOCKQUOTE' || tag === 'TD' || tag === 'H1' || tag === 'H2' || tag === 'H3' || tag === 'DIV') {
          break;
        }
      }
      block = block.parentNode;
    }
    var full = (block && block.innerText ? block.innerText : (node.textContent || '')).replace(/\s+/g, ' ').trim();
    if (!full) return '';
    if (full.length <= 220) return full;

    var needle = (wordStartText || '').trim();
    var pos = needle ? full.toLowerCase().indexOf(needle.toLowerCase()) : 0;
    if (pos < 0) pos = 0;

    var start = pos;
    while (start > 0 && !/[.!?。！？\n]/.test(full.charAt(start - 1))) start--;
    var end = pos + needle.length;
    while (end < full.length && !/[.!?。！？\n]/.test(full.charAt(end))) end++;
    if (end < full.length) end++;
    var sentence = full.slice(start, end).trim();
    if (sentence.length > 280) sentence = sentence.slice(0, 277) + '...';
    return sentence;
  }

  function getCaretRange(x, y) {
    if (document.caretRangeFromPoint) {
      return document.caretRangeFromPoint(x, y);
    }
    if (document.caretPositionFromPoint) {
      var pos = document.caretPositionFromPoint(x, y);
      if (pos) {
        var r = document.createRange();
        r.setStart(pos.offsetNode, pos.offset);
        r.collapse(true);
        return r;
      }
    }
    return null;
  }

  function findWordAtPoint(x, y) {
    var range = getCaretRange(x, y);
    if (!range || !range.startContainer || range.startContainer.nodeType !== 3) {
      return null;
    }
    var textNode = range.startContainer;
    var text = textNode.nodeValue || '';
    var offset = range.startOffset;
    if (offset >= text.length && offset > 0) offset = text.length - 1;
    if (!isWordChar(text.charAt(offset))) {
      if (offset > 0 && isWordChar(text.charAt(offset - 1))) {
        offset = offset - 1;
      } else {
        return null;
      }
    }

    var start = offset;
    var end = offset;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    while (end < text.length && isWordChar(text.charAt(end))) end++;

    var rawWord = text.slice(start, end).replace(/^['\-\u2019]+|['\-\u2019]+$/g, '');
    if (!rawWord || !/[A-Za-z]/.test(rawWord)) return null;

    var wordRange = document.createRange();
    wordRange.setStart(textNode, start);
    wordRange.setEnd(textNode, end);

    var rect = wordRange.getBoundingClientRect();
    var padX = 16, padY = 14;
    if (x < rect.left - padX || x > rect.right + padX || y < rect.top - padY || y > rect.bottom + padY) {
      return null;
    }

    return {
      word: rawWord,
      range: wordRange,
      sentence: extractSentence(textNode, rawWord)
    };
  }

  // ---- Gesture Engine: Smooth Swipe + Single-Tap Page/Menu + Double-Tap Word Lookup ----

  var touchStartX = 0, touchStartY = 0, touchStartTime = 0;
  var touchLastX = 0, touchLastY = 0;
  var touchMoved = false;
  var isDraggingPage = false;

  // Double-tap disambiguation state
  var lastTapTime = 0, lastTapX = 0, lastTapY = 0;
  var singleTapTimer = null;

  function cancelPendingSingleTap() {
    if (singleTapTimer) {
      clearTimeout(singleTapTimer);
      singleTapTimer = null;
    }
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

      // Boundary check: soft elastic bounce at chapter ends
      var atStart = (currentPage === 0 && desiredDir > 0);
      var atEnd = (currentPage === totalPages - 1 && desiredDir < 0);

      if (atStart || atEnd) {
        if (activeDragDir !== 0) {
          resetVisualLayers();
        }
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
    if (e.touches.length !== 1) return;
    if (hasActiveSelection() || isAnimating) {
      isDraggingPage = false;
      return;
    }
    touchStartX = e.touches[0].clientX;
    touchStartY = e.touches[0].clientY;
    touchLastX = touchStartX;
    touchLastY = touchStartY;
    touchStartTime = Date.now();
    touchMoved = false;
    isDraggingPage = false;
    pendingDragDx = 0;
    activeDragDir = 0;
  }, { passive: true });

  viewportEl.addEventListener('touchmove', function(e) {
    if (e.touches.length !== 1) return;
    if (hasActiveSelection() || isAnimating) {
      isDraggingPage = false;
      return;
    }
    var x = e.touches[0].clientX;
    var y = e.touches[0].clientY;
    var dx = x - touchStartX;
    var dy = y - touchStartY;
    touchLastX = x;
    touchLastY = y;

    if (Math.abs(dx) > 9 || Math.abs(dy) > 9) {
      touchMoved = true;
      cancelPendingSingleTap();
    }

    if (!isDraggingPage && Math.abs(dx) > 10 && Math.abs(dx) > Math.abs(dy) * 1.05) {
      isDraggingPage = true;
      clearTapHighlight();
      if (window.__lingoPopupOpen && window.LingoBridge) {
        window.LingoBridge.onBlankTapped();
      }
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
    if (window.__lingoPopupOpen || y < 34) {
      if (window.LingoBridge) window.LingoBridge.onBlankTapped();
      return;
    }
    var w = Math.max(1, window.innerWidth);
    if (x < w * 0.30) {
      prevPage();
    } else if (x > w * 0.70) {
      nextPage();
    } else {
      if (window.LingoBridge) window.LingoBridge.onBlankTapped();
    }
  }

  viewportEl.addEventListener('touchend', function(e) {
    if (isDraggingPage) {
      isDraggingPage = false;
      if (dragRafId) {
        cancelAnimationFrame(dragRafId);
        dragRafId = 0;
      }

      var dx = touchLastX - touchStartX;
      var dt = Math.max(1, Date.now() - touchStartTime);
      var velocity = Math.abs(dx) / dt; // px/ms
      var mode = window.__lingoPageTurnMode;
      var shouldTurn = Math.abs(dx) > pageWidth * 0.16 || (Math.abs(dx) > 22 && velocity > 0.28);

      var dir = dx < 0 ? -1 : 1;
      var atStart = (currentPage === 0 && dir > 0);
      var atEnd = (currentPage === totalPages - 1 && dir < 0);

      if (shouldTurn && (atStart || atEnd)) {
        resetVisualLayers();
        if (atEnd) {
          if (window.LingoBridge && window.LingoBridge.onRequestNextChapter) {
            window.LingoBridge.onRequestNextChapter();
          }
        } else {
          if (window.LingoBridge && window.LingoBridge.onRequestPrevChapter) {
            window.LingoBridge.onRequestPrevChapter();
          }
        }
        return;
      }

      if (atStart || atEnd) {
        // Spring back elastic boundary cleanly
        animateTurn('slide', dir, dx * 0.28, 0, null, 180);
        return;
      }

      if (mode === 'none') {
        if (shouldTurn) {
          currentPage = currentPage + (dir < 0 ? 1 : -1);
          resetVisualLayers();
          notifyPageChanged();
        } else {
          resetVisualLayers();
        }
        return;
      }

      if (activeDragDir !== dir) {
        activeDragDir = dir;
        prepareDualLayer(dir);
      }

      if (shouldTurn) {
        var targetPage = currentPage + (dir < 0 ? 1 : -1);
        var remainingRatio = Math.max(0.25, 1 - Math.abs(dx) / pageWidth);
        var baseDur = mode === 'simulation' ? 280 : (mode === 'fade' ? 200 : 240);
        var dur = Math.max(110, Math.round(baseDur * remainingRatio));
        animateTurn(mode, dir, dx, dir < 0 ? -pageWidth : pageWidth, targetPage, dur);
      } else {
        var cancelDur = Math.max(110, Math.round(200 * (Math.abs(dx) / pageWidth)));
        animateTurn(mode, dir, dx, 0, null, cancelDur);
      }
      return;
    }

    // Tap / Double-tap handling (only for quick touches without drag or active selection)
    var touchDuration = Date.now() - touchStartTime;
    if (touchMoved || touchDuration > 320 || hasActiveSelection() || isAnimating) {
      return;
    }

    var now = Date.now();
    var tapX = touchLastX;
    var tapY = touchLastY;
    var distSq = (tapX - lastTapX) * (tapX - lastTapX) + (tapY - lastTapY) * (tapY - lastTapY);

    if (now - lastTapTime < 280 && distSq < 44 * 44) {
      // ---- DOUBLE TAP DETECTED: Look up word! ----
      cancelPendingSingleTap();
      lastTapTime = 0;

      if (window.__lingoTapEnabled) {
        var hit = findWordAtPoint(tapX, tapY);
        if (hit) {
          e.preventDefault();
          showTapHighlight(hit.range);
          // Clear any accidental native browser selection triggered by double-tap
          setTimeout(function() {
            var s = window.getSelection();
            if (s && !s.isCollapsed) s.removeAllRanges();
          }, 25);
          if (window.LingoBridge) {
            window.LingoBridge.onWordTapped(hit.word, hit.sentence);
          }
          return;
        }
      }
      // Double-tap on blank space toggles menu
      if (window.LingoBridge) window.LingoBridge.onBlankTapped();
      return;
    }

    // First tap: if blank/margin area (no word under finger), act IMMEDIATELY in 0ms!
    // Only wait briefly (175ms) when finger directly hit an English word (for possible double-tap lookup).
    lastTapTime = now;
    lastTapX = tapX;
    lastTapY = tapY;

    if (window.__lingoPopupOpen || tapY < 34 || !window.__lingoTapEnabled) {
      handleSingleTapAction(tapX, tapY);
      lastTapTime = 0;
    } else {
      var probeWord = findWordAtPoint(tapX, tapY);
      if (!probeWord) {
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

  viewportEl.addEventListener('touchcancel', function() {
    cancelPendingSingleTap();
    if (isDraggingPage) {
      isDraggingPage = false;
      resetVisualLayers();
    }
  }, { passive: true });

  // Prevent browser default dblclick zoom/text-highlight flicker when double-tap word lookup is enabled
  document.addEventListener('dblclick', function(e) {
    if (window.__lingoTapEnabled) {
      e.preventDefault();
    }
  }, false);

  // ---- Selection Change Reporting (长按划词 / 划句翻译) ----

  var selTimer = null;
  document.addEventListener('selectionchange', function() {
    clearTimeout(selTimer);
    selTimer = setTimeout(function() {
      var sel = window.getSelection();
      var text = sel ? sel.toString().trim() : '';
      if (text.length >= 2) {
        clearTapHighlight();
        cancelPendingSingleTap();
        var anchor = sel.anchorNode || contentEl;
        var sentence = extractSentence(anchor, text);
        if (window.LingoBridge) {
          window.LingoBridge.onSelectionChanged(text, sentence);
        }
      } else {
        if (window.LingoBridge) {
          window.LingoBridge.onSelectionCleared();
        }
      }
    }, 180);
  });

  window.getSelectedWithContext = function() {
    var sel = window.getSelection();
    var text = sel ? sel.toString().trim() : '';
    if (!text) return JSON.stringify({ text: '', sentence: '' });
    var sentence = extractSentence(sel.anchorNode || contentEl, text);
    return JSON.stringify({ text: text, sentence: sentence });
  };

  window.clearSelection = function() {
    var sel = window.getSelection();
    if (sel) sel.removeAllRanges();
    clearTapHighlight();
  };

  // ---- Live Style / Mode Updates without Reload ----

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
    requestAnimationFrame(function() {
      relayoutPages(prevRatio);
    });
  };

  // ---- Graded Vocabulary Highlighting (due/stubborn vs actively learning; mastered omitted!) ----

  function clearVocabHighlightsOn(rootEl) {
    var marks = rootEl.querySelectorAll('span.lingo-vocab, span.lingo-vocab-due');
    for (var i = 0; i < marks.length; i++) {
      var m = marks[i];
      var parent = m.parentNode;
      if (parent) {
        parent.replaceChild(document.createTextNode(m.textContent), m);
        parent.normalize();
      }
    }
  }

  function applyVocabHighlights(dueArray, learningArray, enabled) {
    clearVocabHighlightsOn(contentEl);
    window.__lingoHighlightEnabled = enabled;
    if (Array.isArray(dueArray)) {
      window.__lingoDueWords = new Set(dueArray);
    }
    if (Array.isArray(learningArray)) {
      window.__lingoLearningWords = new Set(learningArray);
    }
    if (!enabled || (window.__lingoDueWords.size === 0 && window.__lingoLearningWords.size === 0)) {
      syncUnderContent();
      return;
    }

    var walker = document.createTreeWalker(contentEl, NodeFilter.SHOW_TEXT, {
      acceptNode: function(node) {
        if (!node.nodeValue || !/[A-Za-z]/.test(node.nodeValue)) return NodeFilter.FILTER_REJECT;
        var p = node.parentNode;
        if (!p) return NodeFilter.FILTER_REJECT;
        var tag = p.tagName;
        if (tag === 'SCRIPT' || tag === 'STYLE' || tag === 'CODE' || tag === 'PRE') return NodeFilter.FILTER_REJECT;
        if (p.classList && (p.classList.contains('lingo-vocab') || p.classList.contains('lingo-vocab-due'))) return NodeFilter.FILTER_REJECT;
        return NodeFilter.FILTER_ACCEPT;
      }
    });

    var nodes = [];
    var n;
    while ((n = walker.nextNode())) nodes.push(n);

    var wordRegex = /\b[A-Za-z]{2,}(?:['\-][A-Za-z]+)?\b/g;
    var dueSet = window.__lingoDueWords;
    var learnSet = window.__lingoLearningWords;

    for (var i = 0; i < nodes.length; i++) {
      var textNode = nodes[i];
      var text = textNode.nodeValue;
      wordRegex.lastIndex = 0;
      var match;
      var lastIdx = 0;
      var frag = null;

      while ((match = wordRegex.exec(text)) !== null) {
        var w = match[0];
        var wl = w.toLowerCase();
        var cls = dueSet.has(wl) ? 'lingo-vocab-due' : (learnSet.has(wl) ? 'lingo-vocab' : null);
        if (cls) {
          if (!frag) frag = document.createDocumentFragment();
          if (match.index > lastIdx) {
            frag.appendChild(document.createTextNode(text.slice(lastIdx, match.index)));
          }
          var sp = document.createElement('span');
          sp.className = cls;
          sp.textContent = w;
          frag.appendChild(sp);
          lastIdx = match.index + w.length;
        }
      }
      if (frag) {
        if (lastIdx < text.length) {
          frag.appendChild(document.createTextNode(text.slice(lastIdx)));
        }
        textNode.parentNode.replaceChild(frag, textNode);
      }
    }
    syncUnderContent();
  }
  window.applyVocabHighlights = applyVocabHighlights;

  // ---- Initial Layout & Resize / Image Load Re-pagination ----

  var initRatio = $initialPageRatio;

  function initPagination() {
    if (window.__lingoHighlightEnabled && (window.__lingoDueWords.size > 0 || window.__lingoLearningWords.size > 0)) {
      applyVocabHighlights(Array.from(window.__lingoDueWords), Array.from(window.__lingoLearningWords), true);
    } else {
      syncUnderContent();
    }
    relayoutPages(initRatio);
    setTimeout(function() { relayoutPages(initRatio); }, 180);
  }

  window.addEventListener('DOMContentLoaded', initPagination);
  window.addEventListener('load', function() {
    var imgs = contentEl.querySelectorAll('img');
    if (imgs.length > 0) {
      syncUnderContent();
      relayoutPages(initRatio);
    }
  });
  window.addEventListener('resize', function() {
    relayoutPages();
  });
})();
</script>
</body>
</html>
        """.trimIndent()
    }
}
