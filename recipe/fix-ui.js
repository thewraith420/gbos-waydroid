(() => {
  const logs = [];
  // WebView 158 here does not repaint a focused <input> (text and caret vanish until it is selected or blurred).
  // Giving inputs their own compositing layer fixes it. Idempotent; runs on every injection tick.
  try {
    if (!document.getElementById('gb-paint-fix')) {
      const st = document.createElement('style');
      st.id = 'gb-paint-fix';
      st.textContent = 'input,textarea{will-change:transform;transform:translateZ(0)}';
      (document.head || document.documentElement).appendChild(st);
    }
  } catch (e) {}
  try {
    if (!window._errHooked) {
      window._errHooked = true;
      window._jsErrors = [];
      window.addEventListener('error', e => {
        window._jsErrors.push('ERR: ' + (e.message || e) + ' @ ' + (e.filename || '') + ':' + (e.lineno || ''));
      });
      window.addEventListener('unhandledrejection', e => {
        window._jsErrors.push('REJ: ' + (e.reason && (e.reason.stack || e.reason.message || e.reason)));
      });
    }

    if (!window._xhrHooked) {
      window._xhrHooked = true;
      window._xhrs = [];
      const origOpen = XMLHttpRequest.prototype.open;
      const origSend = XMLHttpRequest.prototype.send;
      XMLHttpRequest.prototype.open = function(method, url) {
        this._xhrRec = { method: method, url: String(url).slice(0, 200), status: 'open', t: Date.now() };
        window._xhrs.push(this._xhrRec);
        return origOpen.apply(this, arguments);
      };
      XMLHttpRequest.prototype.send = function(body) {
        if (this._xhrRec) {
          this._xhrRec.status = 'sent';
          this._xhrRec.bodyLen = body ? String(body).length : 0;
          this.addEventListener('readystatechange', () => {
            this._xhrRec.rs = this.readyState;
            if (this.readyState === 4) {
              this._xhrRec.httpStatus = this.status;
              this._xhrRec.respLen = (this.responseText || '').length;
              this._xhrRec.respHead = (this.responseText || '').slice(0, 160);
            }
          });
          this.addEventListener('error', () => {
            this._xhrRec.status = 'error';
          });
        }
        return origSend.apply(this, arguments);
      };
    }

    // Dismiss Checking Info overlay via showView()
    if (window.mm && window.mm.showView && !window._mmShown) {
      window._mmShown = true;
      window.mm.showView();
      logs.push('Called window.mm.showView()');
    }

    // Script monitoring: queue scripts ONLY if they fail to load
    window._scriptQueue = window._scriptQueue || [];
    if (!window._scriptHooked) {
      window._scriptHooked = true;
      const origInsert = Element.prototype.insertBefore;
      const origAppend = Element.prototype.appendChild;
      function monitorScript(node) {
        if (node && node.tagName === 'SCRIPT' && node.src) {
          if (!window._injectedUrls || !window._injectedUrls[node.src]) {
            if (!window._scriptQueue.includes(node.src)) {
              window._scriptQueue.push(node.src);
              (window._scriptSeen = window._scriptSeen || {})[node.src] = Date.now();
            }
          }
        }
        return node;
      }
      Element.prototype.insertBefore = function(newNode, refNode) {
        monitorScript(newNode);
        return origInsert.call(this, newNode, refNode);
      };
      Element.prototype.appendChild = function(newNode) {
        monitorScript(newNode);
        return origAppend.call(this, newNode);
      };
    }

    const emailInput = document.getElementById('identifierId');
    if (emailInput && !window._enterHooked) {
      window._enterHooked = true;
      emailInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' || e.keyCode === 13) {
          e.preventDefault();
          if (typeof window.nativePrimaryActionHit === 'function') {
            window.nativePrimaryActionHit();
          }
        }
      });
      emailInput.addEventListener('input', () => {
        if (emailInput.value && emailInput.value.length > 2) {
          if (window.mm && window.mm.setAllActionsEnabled) {
            window.mm.setAllActionsEnabled(true);
          }
        }
      });
    }

    if (emailInput && !window._uiCleaned) {
      window._uiCleaned = true;
      const sub = document.getElementById('headingSubtext');
      if (sub) sub.style.marginBottom = '16px';
      logs.push('UI cleaned');
    }

    window._injectedUrls = window._injectedUrls || {};
    const baseEl = document.getElementById('base-js');

    const loadedSet = new Set(
      performance.getEntriesByType('resource').map(r => r.name)
    );

    function getScriptPriority(u) {
      if (!u) return 99;
      if (u.includes('_b,_tp') || (baseEl && u === baseEl.src)) return 1;
      if (u.includes('byfTOb') || u.includes('LEikZe') || u.includes('lsjVmc')) return 2;
      if (u.includes('SwTloc')) return 3;
      return 10;
    }

    // Nothing is injected unless a script has been missing for GRACE ms. WebView 158 loads and runs all of Google's scripts
    // itself; injecting a second copy of any of them (above all the 1.3 MB base-js) runs it twice and breaks the page's
    // module system (TypeError "reading 'Ea'", Next never reaches the password step). On a slow machine the first poll
    // arrives long before the page has finished parsing and running its own copy, so the old "inject at once" rule
    // fired every time. A script that is still loading is not in the finished-resources list, hence the long grace.
    const GRACE = window._graceMs || 45000;
    window._scriptSeen = window._scriptSeen || {};
    const nowT = Date.now();
    let needScript = null;
    if (baseEl && baseEl.src && !window._scriptSeen[baseEl.src]) window._scriptSeen[baseEl.src] = nowT;
    if (baseEl && baseEl.src && !window.BOQ_loadedInitialJS && !window._injectedUrls[baseEl.src] &&
        !loadedSet.has(baseEl.src) && nowT - window._scriptSeen[baseEl.src] > GRACE) {
      baseEl.onerror = null;
      needScript = baseEl.src;
    } else {
      const pending = new Set();
      for (const u of window._scriptQueue) {
        const seen = window._scriptSeen[u] || 0;
        if (u && !window._injectedUrls[u] && !loadedSet.has(u) && nowT - seen > GRACE) pending.add(u);
      }
      const scripts = document.querySelectorAll('script[src]');
      for (const s of scripts) {
        if (s.id === 'base-js') continue;
        const src = s.src;
        if (!src || window._injectedUrls[src] || loadedSet.has(src)) continue;
        if (!window._scriptSeen[src]) window._scriptSeen[src] = nowT;
        if (nowT - window._scriptSeen[src] > GRACE) pending.add(src);
      }

      const hasByf = Object.keys(window._injectedUrls || {}).some(u => u.includes('byfTOb') || u.includes('LEikZe'));
      const sorted = Array.from(pending).sort((a, b) => getScriptPriority(a) - getScriptPriority(b));

      for (const cand of sorted) {
        if (cand.includes('SwTloc') && !hasByf) {
          continue;
        }
        needScript = cand;
        break;
      }
    }

    const passInput = document.querySelector('input[type="password"]');

    return JSON.stringify({
      needScript: needScript,
      hasView: !!(window.mm && window.mm.showView && !window._mmShown),
      hasPass: !!passInput,
      injectedCount: Object.keys(window._injectedUrls).length,
      queueLen: window._scriptQueue.length,
      emailVal: (emailInput || {}).value || '',
      xhrs: (window._xhrs || []).slice(-4),
      errors: (window._jsErrors || []).slice(-4),
      logs: logs,
      applied: true
    });
  } catch (err) {
    return JSON.stringify({ error: String(err), stack: err && err.stack, applied: false });
  }
})()
