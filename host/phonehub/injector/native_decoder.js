/*
 * native_decoder.js — a self-contained H.264 video decoder built on the NDK AMediaCodec C API
 * (libmediandk.so) rendering into our injected SurfaceView's ANativeWindow (libandroid.so).
 *
 * WHY NATIVE: ambient now runs with the moving/compacting GC ON (gc_guard restores it — disabling
 * it forever OOM'd in ~11 min). Resident Java hooks and heavy Java calls from frida crash the moving
 * GC (tombstone_28). A Java MediaCodec driven from frida would reintroduce that. The NDK path is pure
 * C: no Java calls on the hot path, no retained Java refs, no GC roots → GC-safe, like the native
 * touch hooks. It also lets us render each frame immediately (releaseOutputBuffer render=true) and
 * skip stale frames — our shot at the ~1s latency — AND it decodes with NO dependency on jys / the
 * device-link bridge, so a reopened window just re-inits (fixes the app-switch cycle).
 *
 * Exposes globalThis.GB_NDEC = { init, feed, drain, destroy, ready }.
 *   init(surfaceJavaObj, w, h, spsU8, ppsU8)  — on the MAIN thread (needs JNIEnv for the Surface)
 *   feed(u8nal, ptsUs, isKey)                  — pure native, call from the pump
 *   drain()                                    — pure native, call from the pump (renders newest)
 *   destroy()
 */
'use strict';
(function(){
  function ts(){return new Date().toISOString().slice(11,23);}
  function log(m){ console.log('['+ts()+'][NDEC] '+m); }

  // ---- resolve native symbols (libmediandk / libandroid) ----
  // frida 17 removed Module.getExportByName(lib,name). Use the global export table first (finds any
  // symbol in an ALREADY-loaded lib — this gets all the AMediaCodec_* from libmediandk), then fall
  // back to loading the named module and querying it (ANativeWindow_fromSurface lives in
  // libandroid.so, which may not be mapped yet at script load — Module.load maps it).
  function modByName(n){
    try{ if(Process.findModuleByName) return Process.findModuleByName(n); }catch(e){}
    try{ if(Module.findBaseAddress && Module.findBaseAddress(n)) return Process.getModuleByName(n); }catch(e){}
    return null;
  }
  function expFrom(m, name){
    if(!m) return null;
    try{ if(m.findExportByName){ var p=m.findExportByName(name); if(p && !p.isNull()) return p; } }catch(e){}
    try{ if(m.getExportByName){ var q=m.getExportByName(name); if(q && !q.isNull()) return q; } }catch(e){}
    return null;
  }
  function sym(mods, name){
    try{ var g=Module.getGlobalExportByName(name); if(g && !g.isNull()) return g; }catch(e){}
    for(var i=0;i<mods.length;i++){
      var p=expFrom(modByName(mods[i]), name);
      if(p) return p;
      try{ var m=Module.load(mods[i]); p=expFrom(m, name); if(p) return p; }catch(e){}
    }
    return null;
  }
  var MDK=['libmediandk.so'], ANW=['libandroid.so','libnativewindow.so'];

  var p_createDecByType = sym(MDK,'AMediaCodec_createDecoderByType');
  var p_createByName    = sym(MDK,'AMediaCodec_createCodecByName');
  var p_fmtNew          = sym(MDK,'AMediaFormat_new');
  var p_fmtSetStr       = sym(MDK,'AMediaFormat_setString');
  var p_fmtSetI32       = sym(MDK,'AMediaFormat_setInt32');
  var p_fmtSetBuf       = sym(MDK,'AMediaFormat_setBuffer');
  var p_fmtDelete       = sym(MDK,'AMediaFormat_delete');
  var p_configure       = sym(MDK,'AMediaCodec_configure');
  var p_start           = sym(MDK,'AMediaCodec_start');
  var p_stop            = sym(MDK,'AMediaCodec_stop');
  var p_flush           = sym(MDK,'AMediaCodec_flush');
  var p_delete          = sym(MDK,'AMediaCodec_delete');
  var p_deqIn           = sym(MDK,'AMediaCodec_dequeueInputBuffer');
  var p_getIn           = sym(MDK,'AMediaCodec_getInputBuffer');
  var p_queueIn         = sym(MDK,'AMediaCodec_queueInputBuffer');
  var p_deqOut          = sym(MDK,'AMediaCodec_dequeueOutputBuffer');
  var p_relOut          = sym(MDK,'AMediaCodec_releaseOutputBuffer');
  var p_anwFromSurface  = sym(ANW,'ANativeWindow_fromSurface');
  var p_anwRelease      = sym(ANW,'ANativeWindow_release');

  var MISSING=[];
  [['createDecoderByType',p_createDecByType],['AMediaFormat_new',p_fmtNew],['setString',p_fmtSetStr],
   ['setInt32',p_fmtSetI32],['setBuffer',p_fmtSetBuf],['configure',p_configure],['start',p_start],
   ['dequeueInputBuffer',p_deqIn],['getInputBuffer',p_getIn],['queueInputBuffer',p_queueIn],
   ['dequeueOutputBuffer',p_deqOut],['releaseOutputBuffer',p_relOut],['ANativeWindow_fromSurface',p_anwFromSurface]
  ].forEach(function(e){ if(!e[1]) MISSING.push(e[0]); });
  if(MISSING.length){ log('MISSING symbols: '+MISSING.join(', ')+' — native decoder unavailable'); globalThis.GB_NDEC={ready:false}; return; }

  // NativeFunction wrappers (media_status_t = int; ssize_t = long/ptr-sized)
  var createDecByType = new NativeFunction(p_createDecByType,'pointer',['pointer']);
  var createByName    = p_createByName ? new NativeFunction(p_createByName,'pointer',['pointer']) : null;
  var fmtNew          = new NativeFunction(p_fmtNew,'pointer',[]);
  var fmtSetStr       = new NativeFunction(p_fmtSetStr,'void',['pointer','pointer','pointer']);
  var fmtSetI32       = new NativeFunction(p_fmtSetI32,'void',['pointer','pointer','int']);
  var fmtSetBuf       = new NativeFunction(p_fmtSetBuf,'void',['pointer','pointer','pointer','size_t']);
  var fmtDelete       = p_fmtDelete ? new NativeFunction(p_fmtDelete,'int',['pointer']) : null;
  var configure       = new NativeFunction(p_configure,'int',['pointer','pointer','pointer','pointer','uint32']);
  var start           = new NativeFunction(p_start,'int',['pointer']);
  var stopf           = p_stop ? new NativeFunction(p_stop,'int',['pointer']) : null;
  var flushf          = p_flush ? new NativeFunction(p_flush,'int',['pointer']) : null;
  var deletef         = p_delete ? new NativeFunction(p_delete,'int',['pointer']) : null;
  var deqIn           = new NativeFunction(p_deqIn,'long',['pointer','int64']);
  var getIn           = new NativeFunction(p_getIn,'pointer',['pointer','size_t','pointer']);
  var queueIn         = new NativeFunction(p_queueIn,'int',['pointer','size_t','int64','size_t','uint64','uint32']);
  var deqOut          = new NativeFunction(p_deqOut,'long',['pointer','pointer','int64']);
  var relOut          = new NativeFunction(p_relOut,'int',['pointer','size_t','int']);
  var anwFromSurface  = new NativeFunction(p_anwFromSurface,'pointer',['pointer','pointer']);
  var anwRelease      = p_anwRelease ? new NativeFunction(p_anwRelease,'void',['pointer']) : null;

  function cstr(s){ return Memory.allocUtf8String(s); }
  var K_MIME=cstr('mime'), K_W=cstr('width'), K_H=cstr('height'),
      K_LL=cstr('low-latency'), K_CSD0=cstr('csd-0'), K_CSD1=cstr('csd-1'), V_AVC=cstr('video/avc');
  // Waydroid's MediaCodecList enumeration is empty, so createDecoderByType("video/avc") returns null.
  // The component DOES resolve by name (mediacodec_inject_info.js builds c2.android.avc.decoder and
  // stubs the ResourceManager proxy), so create by name and fall back to by-type only if that fails.
  var DEC_NAME=cstr('c2.android.avc.decoder');

  // AMediaCodecBufferInfo { int32 offset; int32 size; int64 pts; uint32 flags; } -> 24 bytes aligned
  var BINFO=Memory.alloc(32);
  var OUTSZ=Memory.alloc(8);           // size_t* out for getInputBuffer

  // writeByteArray wants an ArrayBuffer/array; a pending.slice() NAL owns its whole buffer, so hand
  // over the backing ArrayBuffer directly (no per-frame copy to a plain JS array).
  function toArr(u8){
    if(u8 instanceof Uint8Array){
      if(u8.byteOffset===0 && u8.byteLength===u8.buffer.byteLength) return u8.buffer;
      return u8.buffer.slice(u8.byteOffset, u8.byteOffset+u8.byteLength);
    }
    return u8;
  }

  var CODEC=null, WINDOW=null, READY=false, framesIn=0, framesOut=0, lastErr='';

  function destroy(){
    try{ if(CODEC){ if(stopf)stopf(CODEC); if(deletef)deletef(CODEC); } }catch(e){}
    try{ if(WINDOW && anwRelease) anwRelease(WINDOW); }catch(e){}
    CODEC=null; WINDOW=null; READY=false;
    log('destroyed');
  }

  // init MUST run where a JNIEnv is valid (main thread). surfaceObj = a frida Java Surface wrapper.
  function asPtr(x){                                 // coerce frida handle / number / string -> NativePointer or null
    try{
      if(x===null||x===undefined) return null;
      if(x instanceof NativePointer) return x;
      return ptr(x);                                  // accepts NativePointer-likes, numbers, "0x.." strings
    }catch(e){ return null; }
  }
  // STEP 1 (MAIN THREAD, fresh Surface wrapper): turn our SurfaceView's Surface into an ANativeWindow*.
  // This is the ONLY part that needs a JNIEnv + a live jobject, so it must run on the UI thread with
  // the ORIGINAL surface wrapper (not a Java.retain'd cross-thread copy, whose $handle is unreliable).
  function attachSurface(surfaceObj){
    try{
      var envObj = Java.vm.getEnv();
      var env = asPtr(envObj && envObj.handle !== undefined ? envObj.handle : envObj);
      // This frida's Java bridge stores the jobject global-ref as `.$h` (not `.$handle`); keep the
      // others as fallbacks across bridge versions.
      var sh;
      if(surfaceObj){
        if(surfaceObj.$h !== undefined) sh=surfaceObj.$h;
        else if(surfaceObj.$handle !== undefined) sh=surfaceObj.$handle;
        else if(typeof surfaceObj.$getHandle==='function'){ try{ sh=surfaceObj.$getHandle(); }catch(e){} }
      }
      var jsurface = asPtr(sh);
      if(!env || !jsurface){
        lastErr='attachSurface bad handle: env='+(env||'(bad)')+' jsurface='+(jsurface||'(none; $h='+(typeof sh)+')');
        log(lastErr); return false;
      }
      try{ if(WINDOW && anwRelease) anwRelease(WINDOW); }catch(e){}
      WINDOW = anwFromSurface(env, jsurface);
      if(!WINDOW || WINDOW.isNull()){ WINDOW=null; lastErr='ANativeWindow_fromSurface null'; log(lastErr); return false; }
      log('attachSurface OK — ANativeWindow='+WINDOW);
      return true;
    }catch(e){ lastErr='attachSurface: '+e; log(lastErr); return false; }
  }
  // STEP 2 (any thread, PURE NATIVE): create/configure/start the codec against the stored ANativeWindow.
  function init(w, h, spsU8, ppsU8){
    try{
      if(!WINDOW){ lastErr='init: no ANativeWindow (attachSurface first)'; log(lastErr); return false; }
      if(CODEC){ try{ if(stopf)stopf(CODEC); if(deletef)deletef(CODEC); }catch(e){} CODEC=null; }

      // Create by component name first (Waydroid's by-type enumeration is empty); by-type as fallback.
      var prefStr = globalThis.__GB_PREF_AVC_DEC || 'c2.android.avc.decoder';
      var prefCStr = cstr(prefStr);
      CODEC=null;
      if(createByName){ try{ CODEC=createByName(prefCStr); }catch(e){ log('createCodecByName('+prefStr+') threw: '+e); } }
      if((!CODEC || CODEC.isNull()) && prefStr !== 'c2.android.avc.decoder' && createByName){
        try{ CODEC=createByName(DEC_NAME); prefStr='c2.android.avc.decoder'; }catch(e){}
      }
      if(!CODEC || CODEC.isNull()){
        try{ CODEC=createDecByType(V_AVC); }catch(e){ log('createDecoderByType threw: '+e); }
      }
      if(!CODEC || CODEC.isNull()){ lastErr='createDecoder null (byName+byType both failed)'; log(lastErr); return false; }
      log('decoder created ('+(createByName?prefStr:'by-type')+') @'+CODEC);

      var fmt = fmtNew();
      fmtSetStr(fmt, K_MIME, V_AVC);
      fmtSetI32(fmt, K_W, w|0);
      fmtSetI32(fmt, K_H, h|0);
      fmtSetI32(fmt, K_LL, 1);                        // low-latency decode (emit ASAP, no reorder)
      if(spsU8 && spsU8.length){ var sp=Memory.alloc(spsU8.length); sp.writeByteArray(toArr(spsU8)); fmtSetBuf(fmt, K_CSD0, sp, spsU8.length); }
      if(ppsU8 && ppsU8.length){ var pp=Memory.alloc(ppsU8.length); pp.writeByteArray(toArr(ppsU8)); fmtSetBuf(fmt, K_CSD1, pp, ppsU8.length); }

      var rc = configure(CODEC, fmt, WINDOW, ptr(0), 0);
      if(fmtDelete) fmtDelete(fmt);
      // On failure, drop ONLY the codec (keep WINDOW) so the next keyframe can retry init cheaply.
      if(rc!==0){ lastErr='configure rc='+rc; log(lastErr); dropCodec(); return false; }
      rc = start(CODEC);
      if(rc!==0){ lastErr='start rc='+rc; log(lastErr); dropCodec(); return false; }
      READY=true; framesIn=0; framesOut=0;
      log('native decoder READY ('+w+'x'+h+', low-latency) rendering into our ANativeWindow');
      return true;
    }catch(e){ lastErr='init: '+e; log(lastErr); dropCodec(); return false; }
  }
  function dropCodec(){ try{ if(CODEC){ if(stopf)stopf(CODEC); if(deletef)deletef(CODEC); } }catch(e){} CODEC=null; READY=false; }

  // feed one NAL (with start code) as an input access unit. Pure native; call from the pump.
  // Returns true if queued, false if no input buffer was free this moment (caller may retry/drop).
  function feed(u8nal, ptsUs, isKey, timeoutUs){
    if(!READY) return false;
    try{
      var idx = deqIn(CODEC, timeoutUs||0).toNumber();  // non-blocking unless the caller grants a short wait
      if(idx < 0) return false;                        // no free input buffer right now
      var n = u8nal.length;
      OUTSZ.writeU64(0);
      var buf = getIn(CODEC, idx, OUTSZ);
      var cap = OUTSZ.readU64().toNumber();
      if(!buf || buf.isNull() || cap < n){ lastErr='getInputBuffer cap='+cap+' need='+n; return false; }
      buf.writeByteArray(toArr(u8nal));                 // straight into the codec's input buffer
      var flags = isKey ? 1 : 0;                        // BUFFER_FLAG_KEY_FRAME (advisory)
      var rc = queueIn(CODEC, idx, 0, n, uint64(ptsUs), flags);  // pts in us (< 2^53, safe)
      if(rc!==0){ lastErr='queueInputBuffer rc='+rc; return false; }
      framesIn++;
      return true;
    }catch(e){ lastErr='feed: '+e; return false; }
  }

  // drain all currently-decoded frames and render the NEWEST immediately (skip-to-latest), so a
  // backlog never adds latency. Pure native; call from the pump every tick.
  function drain(){
    if(!READY) return 0;
    var rendered=0, pending=-1, guard=0;
    try{
      while(guard++ < 64){
        var idx = deqOut(CODEC, BINFO, 0).toNumber();  // non-blocking
        if(idx === -1) break;                          // AMEDIACODEC_INFO_TRY_AGAIN_LATER
        if(idx === -3 || idx === -2) continue;         // buffers/format changed: ignore
        if(idx < 0) break;
        if(pending >= 0){ relOut(CODEC, pending, 0); } // drop the older ready frame (no render)
        pending = idx;                                  // keep the newest
      }
      if(pending >= 0){ relOut(CODEC, pending, 1); rendered=1; framesOut++; } // render newest
    }catch(e){ lastErr='drain: '+e; }
    return rendered;
  }

  globalThis.GB_NDEC = {
    ready:true,            // module loaded & symbols resolved
    isLive:function(){ return READY; },
    hasWindow:function(){ return !!WINDOW; },
    attachSurface:attachSurface, init:init, feed:feed, drain:drain, destroy:destroy,
    stats:function(){ return {in:framesIn, out:framesOut, err:lastErr, ready:READY, win:!!WINDOW}; }
  };
  log('native decoder module loaded (symbols resolved) — attachSurface(surface) then init(w,h,sps,pps)');
})();
