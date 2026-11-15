/*
 * gc_guard.js — RETIRED 2026-10-30. It was the cause of the ~11-minute AppCast OOM, not a fix.
 *
 * WHAT IT USED TO DO: bump Heap::disable_moving_gc_count_ and never restore it, on the theory that
 * ART would fall back to a non-moving mark-sweep and merely "not compact".
 *
 * WHAT ACTUALLY HAPPENS ON THIS BUILD (Android 16 / ART 17): ART does not fall back — it SKIPS the
 * GC. The app's own log shows it, hundreds of times a second under allocation pressure:
 *
 *     W bient.streaming: Skipping GC due to disable moving GC count 2
 *
 * Consequences, all measured on 2026-10-30 against pid 214467 with the counter at 2:
 *   - heap climbed to the 256 MB ceiling and the process was OOM-killed at ~11 min of streaming;
 *   - System.gc() never returned (it spun logging "Skipping GC");
 *   - heap dumps showed ~20% of all instances unreferenced (jyq frame payloads 100% unreferenced,
 *     381/381 of them surviving from dump A into dump B) — garbage nobody collected, not retention.
 * Clearing the counter to 0 and forcing one SIGUSR1 GC then produced:
 *
 *     Explicit concurrent mark compact GC freed 164MB AllocSpace bytes, 97% free, 5416KB/256MB
 *
 * ...one collection, 164 MB back, 5.4 MB live. There is no leak in the frame pipeline.
 *
 * WORSE: the old code incremented on every session and never restored, so the counter STACKED
 * (0 -> 1 -> 2 ...) across frida runs. A long-lived process accumulated guards from past sessions
 * and ended up with collection fully disabled.
 *
 * WHAT IT DOES NOW: reads the counter and, if a previous session left it above zero, writes it back
 * to 0. It never raises it. Non-compacting behaviour is no longer wanted anywhere: the crash it was
 * introduced for ("GC tried to mark invalid reference" / SIGSEGV in HeapTaskDaemon, caused by ART
 * relocating objects while frida held raw pointers) was driven by the dynamically registered input
 * listener classes, which feed_live.js no longer creates — it hooks SurfaceView's dispatch methods
 * instead, so there are no GC-tainted roots to choke on.
 *
 * To deliberately re-enable the old behaviour for a bisect (and accept that you will OOM in ~11
 * minutes), run frida with --eval 'global.__FORCE_MOVING_GC_OFF = true' before loading this file.
 */
'use strict';
function log(t,m){console.log('['+new Date().toISOString().slice(11,23)+']['+t+'] '+m);}

var HEAP_OFF  = 0x200;   // Runtime::heap_
var COUNT_OFF = 0x428;   // Heap::disable_moving_gc_count_ (size_t)

function resolveArt(name){
  try{ var e=Module.getGlobalExportByName(name); if(e) return e; }catch(_){}
  var mod=null;
  try{ mod=Process.getModuleByName('libart.so'); }catch(_){}
  if(!mod){ try{ Process.enumerateModules().forEach(function(m){ if(/libart\.so$/.test(m.path)) mod=m; }); }catch(_){} }
  if(!mod) return null;
  try{ var ex=mod.findExportByName(name); if(ex) return ex; }catch(_){}
  var found=null;
  try{ mod.enumerateSymbols().forEach(function(s){ if(!found && s.name===name && !s.address.isNull()) found=s.address; }); }catch(_){}
  return found;
}

(function(){
  try{
    var instSym = resolveArt('_ZN3art7Runtime9instance_E');
    if(!instSym){ log('GCGUARD','ERROR: Runtime::instance_ not found — GC left untouched'); return; }
    var runtime = instSym.readPointer();
    if(runtime.isNull()){ log('GCGUARD','ERROR: Runtime::instance_ is null'); return; }
    var heap = runtime.add(HEAP_OFF).readPointer();
    var countPtr = heap.add(COUNT_OFF);
    var before = countPtr.readU64();
    log('GCGUARD','Heap*='+heap+' disable_moving_gc_count_='+before);
    if(before.compare(0x1000) > 0){
      log('GCGUARD','ERROR: implausible count '+before+' — offset likely wrong, NOT writing');
      return;
    }
    if(typeof __FORCE_MOVING_GC_OFF !== 'undefined' && __FORCE_MOVING_GC_OFF){
      countPtr.writeU64(before.add(1));
      log('GCGUARD','__FORCE_MOVING_GC_OFF set: count '+before+' -> '+countPtr.readU64()+'  (ART will SKIP GC: expect OOM in ~11 min)');
      return;
    }
    if(before.equals(0)){
      log('GCGUARD','count already 0 — moving/compacting GC ENABLED, nothing to undo');
      return;
    }
    countPtr.writeU64(0);
    log('GCGUARD','RESTORED count '+before+' -> 0 — collection re-enabled (previous session had disabled it; that is what caused the frame-payload "leak")');
  }catch(e){ log('GCGUARD','ERROR '+e+'\n'+(e&&e.stack)); }
})();

// Nothing installed here is removable, and nothing here is a hook: the supervisor calls this
// during teardown so every stage answers with the same shape. Registered on globalThis rather
// than rpc.exports because all four files are loaded into ONE script context (as the CLI does),
// and each file's rpc.exports assignment would otherwise clobber the previous one.
globalThis.__GB_GC_SHUTDOWN = function(){
  log('SHUTDOWN','gc_guard: nothing installed by this script (counter is written once, never pinned)');
  return {reverted: 0};
};
