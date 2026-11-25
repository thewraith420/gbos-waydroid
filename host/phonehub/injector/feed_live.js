/*
 * feed_live.js — feed the Pixel's LIVE screen into GBOS's AppCast window.
 *
 * Source = host_relay.py streaming the Pixel's screenrecord H.264 over `adb reverse tcp:8099`.
 * We connect (inside the GBOS container) to 127.0.0.1:8099, parse Annex-B INCREMENTALLY as bytes
 * arrive, and feed each access unit into JniOpenScreenReceiver.onNativeFrameReceived — the exact
 * winning wrapper + injected SurfaceView + mime override from feed_receiver.js.
 *
 * KEY vs feed_receiver: screenrecord emits SPS/PPS ONCE at stream start (not before later IDRs),
 * so we CACHE SPS+PPS and prepend them as CSD to every keyframe.
 *
 * Run (gc_guard FIRST so the moving GC stays disabled):
 *   adb -s <container> reverse tcp:8099 tcp:8099
 *   python3 host_relay.py <pixel> &
 *   frida --runtime=v8 -U -p <ambient> -l gc_guard.js -l mediacodec_inject_info.js -l appcast_loopback.js -l feed_live.js
 *   then launch the grid + tap Settings (1024,360) to bring up the receiver.
 */
'use strict';
var HOST_IP=(globalThis.FEED_OPTS&&FEED_OPTS.videoHost)||'127.0.0.1', PORT=8099;
// The container normally reaches the relays through `adb reverse` (127.0.0.1). MEASURED 2026-10-30:
// that tunnel can silently break in ONE direction — it accepts the container's connection and then
// delivers zero bytes (recv() == b''), so video keeps flowing host->container while every gesture
// vanishes. The container can reach the laptop on the LAN (verified direct), so use that
// when configured and fall back to loopback when it is not.
var W=1376, H=736, FPS=60;
var RECV_CLS='com.google.ambient.streaming.multidevicesync.appcast.core.openscreen.JniOpenScreenReceiver';
var ACT_CLS='com.google.ambient.streaming.multidevicesync.appcast.receiver.AppCastActivity';
var PKG='';
// R8-obfuscated Cross-Device Services class names; resolved automatically from the APK by resolve_cds_names.py
// into globalThis.GB_CDS_NAMES, with built-in defaults matching 1.0.1392 (versionCode 100144420).
// History: pre-2026-11-15: xil, kkp, kjq, jys | 1.0.1387 (100143140): zlf, [mbo,lyy], map, jys |
//          1.0.1392 (100144420): xlo, [mbl,mec], mdd, lmn.
var _CN_FL = globalThis.GB_CDS_NAMES || {};
var N_UNIT=_CN_FL.unit||'xlo', N_STALL=_CN_FL.stall||['mbl','mec'], N_CONN=_CN_FL.conn||'mdd', N_JYS=_CN_FL.jys||'lmn';
var POLL_MS=600, POLL_TRIES=400;

function ts(){return new Date().toISOString().slice(11,23);}
function log(t,m){console.log('['+ts()+']['+t+'] '+m);}
function gx(n){return Module.getGlobalExportByName(n);}

// ---- test switches -------------------------------------------------------
// A PRE script (loaded before this one) can set globalThis.FEED_OPTS to drop individual
// resident hooks, so which one trips ART's mark-compact GC can be MEASURED rather than
// guessed. Empty/unset = full feature set.
var FO=Object.assign({ownDecoder:1}, globalThis.FEED_OPTS||{});
// Set by stopStream(): ends the reader, the receiver poller and the surface watcher. Every
// timer loop checks it, so a teardown cannot be re-armed by an in-flight setTimeout.
var STOP=false;

// ---- MediaCodec.configure: force the real mime (jys uses an obfuscated/hashed mime) ----
if(FO.noConfig){ log('LIVE','[FO] MediaCodec.configure hook DISABLED'); } else
Java.perform(function(){
  try{
    var MC=Java.use('android.media.MediaCodec');
    MC.configure.overloads.forEach(function(ov){
      ov.implementation=function(){
        try{
          var fmt=arguments[0];
          if(fmt){
            var m=''+fmt.getString('mime');
            if(m && m!=='video/avc' && (m.length>12 || m.indexOf('/')===-1)){
              fmt.setString('mime','video/avc');
              log('CFG','overrode mime '+m.slice(0,16)+'… -> video/avc');
            }
          }
        }catch(e){}
        try{ return ov.apply(this,arguments); }catch(e){ log('CFG','configure THREW: '+e); throw e; }
      };
    });
    log('CFG','hooked MediaCodec.configure');
  }catch(e){ log('WARN','configure hook: '+e); }
});

// ---- native non-blocking socket ----
var socketf=new NativeFunction(gx('socket'),'int',['int','int','int']);
var connectf=new NativeFunction(gx('connect'),'int',['int','pointer','int']);
var recvf=new NativeFunction(gx('recv'),'long',['int','pointer','long','int']);
// errno separates "no data yet" (EAGAIN) from a dead socket, which recv reports identically as -1.
var errnoPtr=null;
try{ errnoPtr=new NativeFunction(Module.getExportByName(null,'__errno'),'pointer',[])(); }catch(e){ log('WARN','__errno: '+e); }
var closef=new NativeFunction(gx('close'),'int',['int']);
var RBUF=Memory.alloc(65536);
var FD=-1;
function sockaddr(host,port){
  var sa=Memory.alloc(16);
  sa.writeU16(2);                    // sin_family = AF_INET (host order)
  sa.add(2).writeU8((port>>8)&0xff); sa.add(3).writeU8(port&0xff);  // sin_port BE
  host.split('.').forEach(function(o,i){ sa.add(4+i).writeU8(+o); });
  return sa;
}
function connectHost(){
  var hosts=[HOST_IP]; if(HOST_IP!=='127.0.0.1') hosts.push('127.0.0.1');
  for(var i=0;i<hosts.length;i++){
    FD=socketf(2,1,0);                 // AF_INET, SOCK_STREAM
    if(FD<0){ log('ERR','socket() failed'); return false; }
    if(connectf(FD,sockaddr(hosts[i],PORT),16)>=0){
      log('LIVE','connected to '+hosts[i]+':'+PORT);
      return true;
    }
    closef(FD); FD=-1;
  }
  log('ERR','connect '+HOST_IP+':'+PORT+' failed (relay up? LAN reachable? adb reverse set?)');
  return false;
}

// ---- INPUT FORWARDING: clicks in the AppCast window -> taps on the real Pixel ----
// feed_live sends "tap <px> <py>" over 127.0.0.1:INPUT_PORT (adb reverse) to input_relay.py,
// which runs `adb -s <pixel> shell input tap`. The Pixel's screen changes; the mirror follows.
var sendf=new NativeFunction(gx('send'),'long',['int','pointer','long','int']);
var INPUT_FD=-1, INPUT_PORT=8100;
var INPUT_HOST=(globalThis.FEED_OPTS&&FEED_OPTS.inputHost)||'127.0.0.1';
var PIXEL_W=1080, PIXEL_H=2400, VW=1376, VH=736;   // Pixel res + screenrecord frame size
function connectInput(){
  if(INPUT_FD>=0){ try{ closef(INPUT_FD); }catch(e){} INPUT_FD=-1; }
  var hosts=[INPUT_HOST]; if(INPUT_HOST!=='127.0.0.1') hosts.push('127.0.0.1');
  for(var i=0;i<hosts.length;i++){
    INPUT_FD=socketf(2,1,0);
    if(INPUT_FD<0){ log('WARN','input socket() failed'); return false; }
    if(connectf(INPUT_FD,sockaddr(hosts[i],INPUT_PORT),16)>=0){
      log('LIVE','input channel connected to '+hosts[i]+':'+INPUT_PORT); return true;
    }
    closef(INPUT_FD); INPUT_FD=-1;
  }
  log('WARN','input connect :'+INPUT_PORT+' failed (input_relay up? LAN/reverse?)'); return false;
}
// Persistent connection across gestures with automatic reconnect on send failure.
// MEASURED BUG (2026-10-31): closing INPUT_FD immediately after send() races with adb reverse
// / TCP teardown, causing the host input_relay to read 0 bytes (empty disconnect).
// We keep INPUT_FD open, use MSG_NOSIGNAL (0x4000) so a dropped connection returns <= 0 instead
// of raising SIGPIPE, and reconnect immediately when ret <= 0.
function sendLine(s){
  if(INPUT_FD < 0 && !connectInput()) return;
  try{
    var b = Memory.allocUtf8String(s);
    var len = s.length;
    var ret = sendf(INPUT_FD, b, len, 0x4000).toNumber();
    if(ret <= 0){
      log('WARN', 'sendLine: send failed (' + ret + '), reconnecting...');
      try{ closef(INPUT_FD); }catch(e){}
      INPUT_FD = -1;
      if(connectInput()){
        sendf(INPUT_FD, b, len, 0x4000);
      }
    }
  }catch(e){
    log('WARN', 'sendLine error: ' + e);
    try{ closef(INPUT_FD); }catch(e2){}
    INPUT_FD = -1;
  }
}
// map a touch within the SurfaceView to Pixel screen coords. The Pixel (portrait) is letterboxed
// inside the VWxVH screenrecord frame, which fills the SurfaceView; invert both transforms.
function mapToPixel(tx,ty,svW,svH){
  if(svW<=0||svH<=0) return null;
  var scale=Math.min(VW/PIXEL_W, VH/PIXEL_H);
  var cW=PIXEL_W*scale, cH=PIXEL_H*scale, left=(VW-cW)/2, top=(VH-cH)/2;
  var fx=tx/svW*VW, fy=ty/svH*VH;
  if(fx<left||fx>left+cW||fy<top||fy>top+cH) return null;   // black bar
  return [Math.round((fx-left)/cW*PIXEL_W), Math.round((fy-top)/cH*PIXEL_H)];
}
function mapToPixelRaw(tx,ty,svW,svH){
  if(svW<=0||svH<=0) return [Math.round(PIXEL_W/2), Math.round(PIXEL_H/2)];
  var scale=Math.min(VW/PIXEL_W, VH/PIXEL_H);
  var cW=PIXEL_W*scale, cH=PIXEL_H*scale, left=(VW-cW)/2, top=(VH-cH)/2;
  var fx=tx/svW*VW, fy=ty/svH*VH;
  var px = Math.round((fx-left)/cW*PIXEL_W);
  var py = Math.round((fy-top)/cH*PIXEL_H);
  return [px, py];
}
// NO JAVA VIEW HOOKS. Hooking SurfaceView.dispatchTouchEvent / dispatchGenericMotionEvent actually
// hooks android.view.View (since SurfaceView does not override them), firing 500+ times/sec on
// mouse hover (ACTION_HOVER_MOVE) across every View in the hierarchy and calling
// JNI::CallNonvirtualBooleanMethod, which crashes ART's MarkCompact stack walk (tombstone_28).
//
// The input anchor is android::MotionEvent::initialize() in libinput.so. MEASURED 2026-10-31: one
// call per delivered event (a tap gives exactly 2: action 0 then 1; a swipe gives DOWN, MOVEs, UP),
// while the libandroid_runtime creators (android_view_MotionEvent_obtainFromNative / obtainAsCopy)
// never fire for events delivered to this window on this build, which is why the hook "didn't fire"
// after the Java hooks were removed. Fields read on leave (all verified against injected taps):
//   +0x38  int32  action (0=DOWN 1=UP 2=MOVE 5=PTR_DOWN 6=PTR_UP 8=SCROLL)
//   coordinates via the exported getters, so no pointer-array offsets are guessed:
//            raw = getRawAxisValue(ev, axis, pointer);  local = raw + getRawOffset()
// Zero Java hooks, zero GC stack-walk hazard.
// Kotlin's Unit singleton, retained once. Every suppressed coroutine (kkp.b / kjq.b) must return
// something compatible with java.lang.Object; a wrapper captured later can be invalidated by the
// moving GC that resumes once media is flowing, which then throws on the next call.
var UNIT=null, KJQ_HOOKED=false;
function unitSentinel(){
  if(!UNIT){ try{ UNIT=Java.retain(Java.use(PKG+N_UNIT).a.value); }catch(e){ log('WARN',N_UNIT+'.a retain: '+e); } }
  return UNIT;   // null is still Object-compatible if the lookup ever fails
}
try{ unitSentinel(); }catch(e){}
var DOWN_P_RAW=null, DOWN_IN_PHONE=false, DOWN_T=0, RAW_DOWN_X=0, RAW_DOWN_Y=0, IS_MULTI=false;
// LIVE TOUCH (2026-11-02). Until now a gesture was only acted on at RELEASE: a drag became one swipe of
// 160-350 ms replayed after the finger lifted, so the phone never followed the finger (and long-press,
// hold-and-drag and pinch were impossible). Now DOWN / MOVE / UP are streamed as they happen
// ("touch D|M|U <id> <x> <y>") to the persistent injector on the phone (touch_injector/) via input_relay,
// which falls back to the old tap/swipe replay if the injector is unreachable.
// Gestures that START in the phone's real system-gesture strips (status bar, nav bar, back edges) are NOT
// streamed: they keep the release-time Back / Home / Recents / notification-pulldown mapping below.
// FEED_OPTS {noLiveTouch:1} turns the whole thing off.
var LIVE_ON=false, LIVE_NPTR=0, LIVE_LAST_T=0;
function liveXY(tx,ty,svW,svH){
  var p=mapToPixelRaw(tx,ty,svW,svH);
  return [Math.max(0,Math.min(PIXEL_W-1,p[0])), Math.max(0,Math.min(PIXEL_H-1,p[1]))];
}
function liveSend(verb,id,xy){ sendLine('touch '+verb+' '+id+' '+xy[0]+' '+xy[1]+'\n'); }
function inSystemStrip(px,py){ return px<=90 || px>=PIXEL_W-90 || py<=100 || py>=PIXEL_H-110; }
var lastScrollT=0, SCROLL_DEBOUNCE=180, BASE_SCROLL_DIST=550;
var SV_HOOKED=false, INPUT_LISTENERS=[];
var SV_W=376, SV_H=835, SV_TOP=45, SV_FOCUSED=true, OUR_RECEIVER_PTR=null;
var H_SCALE=1376.0/331.2;

function handleNativeTouch(action, ptrCount, winX0, winY0, winX1, winY1, evTime){
  var svW=SV_W, svH=SV_H;
  var tx0=(winX0 - svW*0.5)/H_SCALE + svW*0.5, ty0=winY0 - SV_TOP;
  var tx1=(winX1 - svW*0.5)/H_SCALE + svW*0.5, ty1=winY1 - SV_TOP;
  if(action===0){            // DOWN — single finger start
    if(ty0 < 0){             // Click in the freeform window title/caption bar: let WM drag/close
      DOWN_P_RAW = null;
      return;
    }
    RAW_DOWN_X = winX0;
    RAW_DOWN_Y = ty0;
    DOWN_P_RAW = mapToPixelRaw(tx0, ty0, svW, svH);
    DOWN_IN_PHONE = (mapToPixel(tx0, ty0, svW, svH)!==null);
    DOWN_T = evTime;
    IS_MULTI = false;
    LIVE_ON = false; LIVE_NPTR = 0;
    if(!FO.noLiveTouch && DOWN_IN_PHONE && !inSystemStrip(DOWN_P_RAW[0], DOWN_P_RAW[1])){
      LIVE_ON = true; LIVE_NPTR = 1; LIVE_LAST_T = evTime;
      liveSend('D', 0, liveXY(tx0, ty0, svW, svH));
    }
  } else if(action===2){     // MOVE — only ever delivered here while a live gesture is active
    if(LIVE_ON && evTime - LIVE_LAST_T >= 7){          // <= ~140 updates/s; UP carries the final position
      LIVE_LAST_T = evTime;
      liveSend('M', 0, liveXY(tx0, ty0, svW, svH));
      if(LIVE_NPTR >= 2) liveSend('M', 1, liveXY(tx1, ty1, svW, svH));
    }
  } else if(action===3){     // CANCEL
    if(LIVE_ON){ sendLine('touch C\n'); LIVE_ON=false; LIVE_NPTR=0; }
    DOWN_P_RAW = null; IS_MULTI = false;
  } else if(action===5){     // POINTER_DOWN — second finger touches
    if(LIVE_ON){
      LIVE_NPTR++;
      IS_MULTI = true;
      if(LIVE_NPTR === 2 && ptrCount>=2) liveSend('D', 1, liveXY(tx1, ty1, svW, svH));   // 3rd+ fingers ignored
      return;
    }
    IS_MULTI = true;
    if(ptrCount>=2){
      RAW_DOWN_X = (winX0 + winX1) * 0.5;
      RAW_DOWN_Y = (ty0 + ty1) * 0.5;
      var mtx = (tx0 + tx1) * 0.5, mty = (ty0 + ty1) * 0.5;
      DOWN_P_RAW = mapToPixelRaw(mtx, mty, svW, svH);
      DOWN_IN_PHONE = (mapToPixel(mtx, mty, svW, svH)!==null);
    }
    DOWN_T = evTime;
  } else if(action===6 || action===1){     // POINTER_UP (6) or UP (1)
    if(LIVE_ON){
      if(action===6){
        if(LIVE_NPTR === 2) liveSend('U', 1, liveXY(tx1, ty1, svW, svH));
        if(LIVE_NPTR > 0) LIVE_NPTR--;
        return;
      }
      if(LIVE_NPTR >= 2) liveSend('U', 1, liveXY(tx1, ty1, svW, svH));   // safety: never leave finger 1 down
      liveSend('U', 0, liveXY(tx0, ty0, svW, svH));
      LIVE_ON = false; LIVE_NPTR = 0; IS_MULTI = false; DOWN_P_RAW = null;
      return;
    }
    if(DOWN_P_RAW){
      var rawUpX = winX0, rawUpY = ty0;
      var upTx = tx0, upTy = ty0;
      if(action===6 && ptrCount>=2){
        rawUpX = (winX0 + winX1) * 0.5;
        rawUpY = (ty0 + ty1) * 0.5;
        upTx = (tx0 + tx1) * 0.5;
        upTy = (ty0 + ty1) * 0.5;
      }
      var rawDx = rawUpX - RAW_DOWN_X;
      var rawDy = rawUpY - RAW_DOWN_Y;
      var rawDist = Math.sqrt(rawDx*rawDx + rawDy*rawDy);
      var rawDur = Math.max(50, evTime - DOWN_T);

      // 1. Edge Back Gesture:
      // Swiping right from left edge OR swiping left from right edge
      var isLeftEdge = (DOWN_P_RAW[0] <= 140) || (RAW_DOWN_X <= svW * 0.14);
      var isRightEdge = (DOWN_P_RAW[0] >= PIXEL_W - 140) || (RAW_DOWN_X >= svW * 0.86);
      if(isLeftEdge && rawDx > 35 && Math.abs(rawDx) > Math.abs(rawDy) * 0.8){
        sendLine('key KEYCODE_BACK\n');
        log('TOUCH', 'Edge Back (left swipe right) -> KEYCODE_BACK');
        DOWN_P_RAW = null; return;
      }
      if(isRightEdge && rawDx < -35 && Math.abs(rawDx) > Math.abs(rawDy) * 0.8){
        sendLine('key KEYCODE_BACK\n');
        log('TOUCH', 'Edge Back (right swipe left) -> KEYCODE_BACK');
        DOWN_P_RAW = null; return;
      }

      // 2. Bottom Home & Recents Gesture:
      // Swiping up from the navigation bar (bottom 10% or phone y >= 2220)
      var isBottomEdge = (DOWN_P_RAW[1] >= PIXEL_H - 180) || (RAW_DOWN_Y >= svH * 0.88);
      if(isBottomEdge && rawDy < -45 && Math.abs(rawDy) > Math.abs(rawDx) * 0.8){
        if(rawDur > 400){
          sendLine('key KEYCODE_APP_SWITCH\n');
          log('TOUCH', 'Bottom Swipe & Hold -> KEYCODE_APP_SWITCH');
        } else {
          sendLine('key KEYCODE_HOME\n');
          log('TOUCH', 'Bottom Swipe Up -> KEYCODE_HOME');
        }
        DOWN_P_RAW = null; return;
      }

      // 3. Top Notification Pulldown:
      // Swiping down from the status bar (top 10% or phone y <= 180)
      var isTopEdge = (DOWN_P_RAW[1] <= 180) || (RAW_DOWN_Y <= svH * 0.12);
      if(isTopEdge && rawDy > 45 && Math.abs(rawDy) > Math.abs(rawDx) * 0.8){
        sendLine('swipe 540 10 540 1400 220\n');
        log('TOUCH', 'Top Swipe Down -> Notification Pulldown');
        DOWN_P_RAW = null; return;
      }

      // 4. In-App Drag / Scroll:
      if(rawDist > 18){
        var dur = Math.min(350, Math.max(160, Math.round(rawDur * 0.6)));
        var UP_P_RAW = mapToPixelRaw(upTx, upTy, svW, svH);
        var sx = DOWN_P_RAW[0], sy = DOWN_P_RAW[1];
        var ex = UP_P_RAW[0], ey = UP_P_RAW[1];
        if(!DOWN_IN_PHONE){
          sx = Math.round(PIXEL_W / 2);
          ex = sx + Math.round(rawDx * 2.5);
          sy = Math.round(PIXEL_H / 2);
          ey = sy + Math.round(rawDy * 2.5);
        }
        sx = Math.max(20, Math.min(PIXEL_W - 20, sx));
        ex = Math.max(20, Math.min(PIXEL_W - 20, ex));
        sy = Math.max(40, Math.min(PIXEL_H - 40, sy));
        ey = Math.max(40, Math.min(PIXEL_H - 40, ey));
        sendLine('swipe ' + sx + ' ' + sy + ' ' + ex + ' ' + ey + ' ' + dur + '\n');
        log('TOUCH', 'swipe (' + sx + ',' + sy + ')->(' + ex + ',' + ey + ') ' + dur + 'ms (raw=' + rawDur + 'ms)');
      } else if(action===1 && !IS_MULTI && DOWN_IN_PHONE){
        var downTx = (RAW_DOWN_X - svW*0.5)/H_SCALE + svW*0.5;
        var tapP = mapToPixel(downTx, RAW_DOWN_Y, svW, svH);
        if(tapP){
          sendLine('tap ' + tapP[0] + ' ' + tapP[1] + '\n');
          log('TOUCH', 'tap (' + tapP[0] + ',' + tapP[1] + ')');
        }
      }
      DOWN_P_RAW = null;
    }
    if(action===1){
      DOWN_P_RAW = null;
      IS_MULTI = false;
    }
  }
}

// trackpad two-finger swipes / mouse wheel arrive as ACTION_SCROLL (8) events.
function handleNativeScroll(winX, winY, vs, hs){
  if(vs===0 && hs===0) return;
  var now=Date.now();
  if(now-lastScrollT<SCROLL_DEBOUNCE) return;

  var tx=(winX - SV_W*0.5)/H_SCALE + SV_W*0.5, ty=winY - SV_TOP;
  var p=mapToPixel(tx, ty, SV_W, SV_H);
  var sx = p ? p[0] : Math.round(PIXEL_W/2);
  var sy = p ? p[1] : Math.round(PIXEL_H/2);

  sx = Math.max(250, Math.min(PIXEL_W-250, sx));
  sy = Math.max(450, Math.min(PIXEL_H-450, sy));

  if(Math.abs(vs)>=Math.abs(hs)){
    var dist = Math.min(850, Math.max(350, Math.round(BASE_SCROLL_DIST * Math.abs(vs))));
    var ey = vs>0 ? (sy - dist) : (sy + dist);
    ey = Math.max(250, Math.min(PIXEL_H-250, ey));
    sendLine('swipe '+sx+' '+sy+' '+sx+' '+ey+' 120\n');
    log('TOUCH','scroll vs='+vs+' -> vertical swipe ('+sx+','+sy+')->('+sx+','+ey+') 120ms');
  } else {
    var hsy = Math.max(600, Math.min(1800, sy));
    if(hs < 0){
      sendLine('swipe 880 '+hsy+' 180 '+hsy+' 140\n');
      log('TOUCH','scroll hs='+hs+' -> page left (880->180) 140ms');
    } else {
      sendLine('swipe 180 '+hsy+' 880 '+hsy+' 140\n');
      log('TOUCH','scroll hs='+hs+' -> page right (180->880) 140ms');
    }
  }
  lastScrollT = now;
}

function findExportSub(modName, exact, sub){
  // (used for symbols that move with every AOSP signature change — see INIT_SUB below)
  var m=null;
  try{ m=Process.getModuleByName(modName); }catch(e){ return null; }
  try{ var a=m.findExportByName(exact); if(a && !a.isNull()) return a; }catch(e){}
  var found=null;
  try{
    m.enumerateExports().forEach(function(s){
      if(!found && s.name.indexOf(sub)!==-1 && s.address && !s.address.isNull()) found=s.address;
    });
  }catch(e){}
  if(!found){
    try{
      m.enumerateSymbols().forEach(function(s){
        if(!found && s.name.indexOf(sub)!==-1 && s.address && !s.address.isNull()) found=s.address;
      });
    }catch(e){}
  }
  return found;
}

// The mangled name carries every parameter type, so it changes with any AOSP signature edit.
// Match on the stable part instead and fall back to the exact string if that ever fails.
var INIT_SUB = 'MotionEvent10initialize';
var INIT_EXACT = '_ZN7android11MotionEvent10initializeEiijNS_2ui16LogicalDisplayIdENSt3__15arrayIhLm32EEEiiNS_3ftl5FlagsINS_10MotionFlagEEEiiiNS_20MotionClassificationERKNS1_9TransformEffffSD_llmPKNS_17PointerPropertiesEPKNS_13PointerCoordsE';
var ACTION_OFF = 0x38;   // verified: tap -> [0, 1], swipe -> [0, 2, 2, …, 1]
// Our window's screen origin & size, initialized to the `am task resize` defaults and updated dynamically
// every 500ms by watchSurface() as the user drags or resizes the freeform window.
var WIN_X = 772, WIN_Y = 80, WIN_W = 376, WIN_H = 880;

function installDispatchHooks(){
  if(SV_HOOKED) return;
  if(FO.noDispatch){ log('LIVE','[FO] dispatch hooks DISABLED'); return; }
  try{
    var initSym = findExportSub('libinput.so', INIT_EXACT, INIT_SUB);
    var axisSym = findExportSub('libinput.so', '_ZNK7android11MotionEvent12getAxisValueEim', 'MotionEvent12getAxisValueEim');
    var offXSym = findExportSub('libinput.so', '_ZNK7android11MotionEvent13getRawXOffsetEv', 'MotionEvent13getRawXOffsetEv');
    var offYSym = findExportSub('libinput.so', '_ZNK7android11MotionEvent13getRawYOffsetEv', 'MotionEvent13getRawYOffsetEv');
    if(!initSym || !axisSym || !offXSym || !offYSym){
      log('WARN','native input symbols missing: init='+initSym+' axis='+axisSym+
          ' offX='+offXSym+' offY='+offYSym);
      return;
    }
    var getAxisFn = new NativeFunction(axisSym, 'float', ['pointer','int','ulong']);
    var getOffXFn = new NativeFunction(offXSym, 'float', ['pointer']);
    var getOffYFn = new NativeFunction(offYSym, 'float', ['pointer']);
    var seenEv = 0, dbgN = 0, dbgN2 = 0, dbgErr = 0;
    INPUT_LISTENERS.push(Interceptor.attach(initSym, {
      onEnter: function(args){
        this.ev = args[0];
        if(dbgN < 3){ dbgN++; log('IN','initialize fired ev='+args[0]+' svref='+(SVREF?'yes':'no')); }
      },
      // onLeave: initialize() has populated the event by now. Everything below is native
      // getters + one int read — no JNI, no allocation, safe with the moving GC running.
      onLeave: function(){
        var ev = this.ev;
        if(!ev || ev.isNull()) return;
        try{
          var action = ev.add(ACTION_OFF).readS32() & 0xff;
          // Ignore MOVE (2) and all HOVER_* (7/9/10): only a press, release, pointer
          // change or wheel notch is forwarded, so a hovering mouse costs one int read.
          // MOVE (2) and CANCEL (3) are only wanted while a live gesture is streaming.
          if(action===2 || action===3){ if(!LIVE_ON) return; }
          else if(action!==0 && action!==1 && action!==5 && action!==6 && action!==8) return;

          // Window identity: the raw offset is -windowOrigin, so an event carrying (-WIN_X,-WIN_Y)
          // belongs to our window as watchSurface() tracks drags/resizes. Fullscreen fallback keeps (0,0) when focused.
          var ox = getOffXFn(ev), oy = getOffYFn(ev);
          var isFreeformWin = ((WIN_X !== 0 || WIN_Y !== 0) && Math.abs(ox + WIN_X) <= 2 && Math.abs(oy + WIN_Y) <= 2);
          var isFullscreenWin = (SV_FOCUSED && Math.abs(ox) <= 2 && Math.abs(oy) <= 2);
          var mine = isFreeformWin || isFullscreenWin;
          // cooked = window-local (VERIFIED against an injected tap).
          var x0 = getAxisFn(ev, 0, 0), y0 = getAxisFn(ev, 1, 0);
          if(dbgN2 < 20){
            dbgN2++;
            log('IN','action='+action+' cooked=('+x0.toFixed(0)+','+y0.toFixed(0)+')'+
                ' off=('+ox.toFixed(0)+','+oy.toFixed(0)+') want=(-'+WIN_X+',-'+WIN_Y+')'+
                ' mine='+mine+' sv='+SV_W+'x'+SV_H);
          }
          if(!FO.winLoose && !mine) return;
          // Ambient has other windows/activities: keep only events that land in ours.
          // A live gesture's MOVE/UP may legitimately end outside the window (finger dragged off the edge):
          // dropping those would leave the phone's finger down, so they are let through (and clamped).
          var liveTail = LIVE_ON && (action===1 || action===2 || action===3 || action===6);
          var boundW = Math.max(WIN_W, SV_W);
          var boundH = Math.max(WIN_H, SV_H + SV_TOP);
          if(!liveTail && (x0 < -2 || y0 < -2 || x0 > boundW + 2 || y0 > boundH + 2)) return;

          // Pointer index 1 may ONLY be read when the action proves a second pointer exists:
          // MEASURED 2026-10-31, getRawAxisValue(ev, axis, 1) on a single-pointer event calls
          // abort() in libinput (the "Error: abort was called" that silently killed forwarding).
          // POINTER_DOWN (5) / POINTER_UP (6) always carry both pointers, so those are safe.
          // (MOVE with two live fingers also proves pointer 1 exists — LIVE_NPTR counts the pointers we saw
          // go down, so index 1 is never read on a single-pointer event.)
          var two = (action === 5 || action === 6 || (action === 2 && LIVE_NPTR >= 2));
          var x1 = two ? getAxisFn(ev, 0, 1) : x0, y1 = two ? getAxisFn(ev, 1, 1) : y0;

          if(action === 8){
            handleNativeScroll(x0, y0, getAxisFn(ev, 9, 0), getAxisFn(ev, 10, 0));
          } else {
            seenEv++;
            handleNativeTouch(action, two ? 2 : 1, x0, y0, x1, y1, Date.now());
          }
        }catch(e){
          if(dbgErr < 10){ dbgErr++; log('WARN','native input initialize: '+e); }
        }
      }
    }));
    SV_HOOKED=true;
    log('LIVE','native C++ input hook installed on MotionEvent::initialize (libinput.so, zero Java hooks)');
  }catch(e){ log('WARN','installDispatchHooks: '+e); }
}
function installTouchForward(sv){
  installDispatchHooks();
  try{ SVREF=Java.retain(sv); }catch(e){ SVREF=sv; }
  log('LIVE','touch + scroll forwarding active on our SurfaceView');
}

// ---- reusable direct ByteBuffer wrapper builder ----
var RBB=null, RADDR=null, RCAP=0;
function ensureBB(cap){
  if(RBB && RCAP>=cap) return;
  RBB=Java.use('java.nio.ByteBuffer').allocateDirect(cap);
  var f=Java.use('java.nio.Buffer').class.getDeclaredField('address'); f.setAccessible(true);
  RADDR=ptr(f.getLong(RBB)); RCAP=cap;
}
function wU32(off,v){ RADDR.add(off).writeU8((v>>>24)&0xff); RADDR.add(off+1).writeU8((v>>>16)&0xff); RADDR.add(off+2).writeU8((v>>>8)&0xff); RADDR.add(off+3).writeU8(v&0xff); }
function wU64(off,v){ for(var i=0;i<8;i++) RADDR.add(off+i).writeU8(Math.floor(v/Math.pow(2,8*(7-i)))&0xff); }
function u8buf(u){ return u.buffer.byteLength===u.length ? u.buffer : u.slice().buffer; }

// ---- incremental Annex-B parse + feed ----
var pending=new Uint8Array(0);
var SPS=null, PPS=null;          // cached NAL bytes (incl. start code)
var RECV=null, ptsUs=0, fed=0, gotFirstKey=false;
var JYS=null, SVREF=null;   // retained for the surface-keeper
var KEEPER_ON=false;        // keeper churns jys.d() on unreliable field reads -> test with it OFF

// jys.u() (releaseSurface) nulls jys.k on session teardown (the synthetic session's ~15s
// app-launch timeout fires it) -> every frame after is rejected "surface is null or invalid"
// and the display freezes. Block it so our surface survives; the keeper re-asserts as a backstop.
var _uBlocked=0, _kDiag=0;
// OFF BY DEFAULT (measured 2026-10-30). Run with frames flowing and a stimulus on the Pixel:
// u-hook resident -> SIGSEGV t=10s (MarkCompact::MarkRoots -> ReloadScanObjClass, klass pointer 0);
// u-hook absent   -> 45s clean, 3120 frames. It was belt-and-braces for the synthetic receiver
// session; with kkp.b suppressed the 15s launch-timeout teardown that nulled jys.k never fires,
// so the freeze it guarded against does not occur on the loopback path. Set
// globalThis.FEED_OPTS={keepU:1} only to debug a session where something else tears down.
function suppressReleaseSurface(){
  if(!FO.keepU){ log('LIVE','jys.u suppression NOT installed (would be a resident Java replacement across GC)'); return; }
  try{
    Java.use(PKG+N_JYS).u.implementation=function(){
      if(_uBlocked++<3){
        var st=''; try{ st=Java.use('android.util.Log').getStackTraceString(Java.use('java.lang.Exception').$new()); }catch(e){}
        log('DIAG','jys.u() releaseSurface CALLED (blocked). caller:\n'+st.split('\n').slice(1,6).join('\n'));
      }
    };
    log('LIVE','hooked jys.u() -> no-op');
  }catch(e){ log('WARN','jys.u hook: '+e); }
}
// THE Phase-A freeze fix: suppress kkp.b() — the 500ms-poll coroutine that fires
// "App launch timed out after 15s" / "Stream stalled >20s" -> connection lost -> teardown ->
// surface invalid -> FREEZE. Short-circuit to xil.a (kotlin.Unit = coroutine completed).
var T_SUPP=false;
function suppressLaunchTimeout(){
  if(T_SUPP) return;
  if(FO.noKkp){ log('LIVE','[FO] kkp.b suppression DISABLED'); return; }
  try{
    N_STALL.forEach(function(c){ Java.use(PKG+c).b.implementation=function(obj){ return unitSentinel(); }; });
    T_SUPP=true; log('LIVE',N_STALL.join('/')+'.b launch/stall timeout SUPPRESSED (unit='+unitSentinel()+')');
  }catch(e){ log('WARN','suppress kkp.b: '+e); }
}
function surfaceKeeper(){
  Java.scheduleOnMainThread(function(){
    try{
      if(JYS&&SVREF){
        var sur=null; try{ sur=SVREF.getHolder().getSurface(); }catch(e){}
        if(sur && sur.isValid()){
          var k=null; try{ k=JYS.k.value; }catch(e){}
          var kNull=(k===null); var kInv=false; if(!kNull){ try{ kInv=!k.isValid(); }catch(e){ kInv=true; } }
          var kBad=kNull||kInv;
          if(kBad){
            if(_kDiag++<5) log('DIAG','keeper: k null='+kNull+' invalid='+kInv);
            try{ JYS.d(sur); }catch(e){ log('WARN','keeper jys.d: '+e); }
          }
        }
      }
    }catch(e){}
    if(!STOP) setTimeout(surfaceKeeper, 800);
  });
}

function appendBytes(ab){
  var nw=new Uint8Array(ab);
  var m=new Uint8Array(pending.length+nw.length);
  m.set(pending,0); m.set(nw,pending.length); pending=m;
}
function findStarts(u8){
  var s=[];
  for(var i=0;i+3<u8.length;i++){
    if(u8[i]===0&&u8[i+1]===0&&u8[i+2]===1) s.push([i,3]);
    else if(i+4<u8.length&&u8[i]===0&&u8[i+1]===0&&u8[i+2]===0&&u8[i+3]===1){ s.push([i,4]); i+=3; }
  }
  return s;
}
function processNALs(){
  var st=findStarts(pending);
  if(st.length<2) return;
  var consumed=0;
  for(var k=0;k+1<st.length;k++){
    var from=st[k][0], hdr=st[k][0]+st[k][1], to=st[k+1][0];
    var t=pending[hdr]&0x1f;
    handleNAL(t, pending.slice(from,to));   // copy incl. start code (offset 0)
    consumed=to;
  }
  pending=pending.slice(consumed);           // keep trailing incomplete NAL
}
function handleNAL(t, nal){
  if(t===7){ SPS=nal; return; }
  if(t===8){ PPS=nal; return; }
  if(t===6||t===9) return;                   // SEI/AUD: skip
  if(t===5){ if(SPS&&PPS){ gotFirstKey=true; feedKey(SPS,PPS,nal); } return; }
  if(t===1){ if(gotFirstKey) feedDelta(nal); }   // drop pre-first-keyframe deltas
}
// ---- OWN-DECODER PATH (FO.ownDecoder) -----------------------------------------------------
// GC-safe native NDK AMediaCodec decoder (native_decoder.js / GB_NDEC) rendering straight into
// our injected SurfaceView's ANativeWindow. Bypasses the app's jys receiver entirely: no
// RECV.onNativeFrameReceived, no jys.t queue, no device-link decode dependency. This is the
// app-switch fix (a reopened window just re-inits) and our latency lever (render-newest ASAP).
var NDEC_LIVE=false, ndecInitTried=0;
function ndecAvailable(){ return FO.ownDecoder && globalThis.GB_NDEC && GB_NDEC.ready; }
function ndecFeedKey(sps,pps,idr){
  if(!NDEC_LIVE){
    if(!GB_NDEC.hasWindow()){ if(fed<3) log('NDEC','keyframe before ANativeWindow ready — waiting'); return; }
    var ok=false;
    try{ ok=GB_NDEC.init(W, H, sps, pps); }catch(e){ log('NDEC','init threw: '+e); }
    ndecInitTried++;
    if(!ok){ if(ndecInitTried<4||ndecInitTried%120===0) log('NDEC','init not ready yet: '+JSON.stringify(GB_NDEC.stats())); return; }
    NDEC_LIVE=true;
    log('NDEC','native decoder initialised on first keyframe');
  }
  ndecSubmit(idr, true);
}
function ndecFeedDelta(slice){
  if(!NDEC_LIVE) return;                 // wait for a keyframe to init first
  if(NDEC_NEEDKEY) return;               // a frame was lost: deltas would smear until the next keyframe
  ndecSubmit(slice, false);
}
// MEASURED 2026-11-03: fed=1920 but decoder in=1841 — ~4% of frames were silently dropped when the codec had
// no free input buffer even after one retry. A dropped P-frame leaves every later frame referencing a
// picture the decoder never saw: the smeared/torn blocks until the next keyframe (up to 2 s). So wait a
// few ms for a buffer instead of giving up, and if a frame is STILL lost, hold the picture (skip deltas)
// until the next keyframe rather than showing corruption.
var NDEC_NEEDKEY=false, ndecLost=0;
function ndecSubmit(nal, isKey){
  // drain first (frees input buffers + renders what's ready), queue the NAL, drain again so the
  // frame we just submitted is rendered this tick rather than next.
  var q=false, tries=0;
  while(!q && tries<8){
    try{ GB_NDEC.drain(); }catch(e){}
    try{ q=GB_NDEC.feed(nal, ptsUs, isKey, tries===0?0:4000); }catch(e){ log('NDEC','feed threw: '+e); break; }
    tries++;
  }
  if(q){ if(isKey) NDEC_NEEDKEY=false; }
  else{
    NDEC_NEEDKEY=true; ndecLost++;
    if(ndecLost<4||ndecLost%50===0) log('NDEC','LOST '+(isKey?'keyframe':'delta')+' after '+tries+' tries (lost#'+ndecLost+') — holding picture until next keyframe');
  }
  try{ GB_NDEC.drain(); }catch(e){}
  ptsUs+=Math.floor(1000000/FPS);
  if(fed<3||fed%120===0){ var st=GB_NDEC.stats(); log('NDEC','frame#'+fed+(isKey?' KEY':' delta')+' '+nal.length+'B in='+st.in+' out='+st.out+(st.err?(' lastErr='+st.err):'')); }
  fed++; globalThis.__GB_FED=fed;
  if(fed%60===0){ try{ send({gb:'frame', n:fed}); }catch(e){} }
}
function feedKey(sps,pps,idr){
  if(ndecAvailable()){ ndecFeedKey(sps,pps,idr); return; }
  if(!RECV) return;
  var csdLen=sps.length+pps.length, total=30+csdLen+idr.length;
  ensureBB(total+64);
  RADDR.writeU8(0x00); RADDR.add(1).writeU8(0x01);
  wU32(2,W); wU32(6,H); wU32(10,csdLen); wU64(14,ptsUs); wU32(22,0); wU32(26,0);
  RADDR.add(30).writeByteArray(u8buf(sps));
  RADDR.add(30+sps.length).writeByteArray(u8buf(pps));
  RADDR.add(30+csdLen).writeByteArray(u8buf(idr));
  RBB.limit(total); RBB.position(0);
  RECV.onNativeFrameReceived(RBB, ptsUs, false);
  ptsUs+=Math.floor(1000000/FPS); if(fed<3||fed%60===0) log('LIVE','frame#'+fed+' KEY '+total+'B pts='+ptsUs); fed++;
  globalThis.__GB_FED=fed;   // appcast_loopback waits for this before stripping the codec hooks
  if(fed%60===0){ try{ send({gb:'frame', n:fed}); }catch(e){} }
}
function feedDelta(slice){
  if(ndecAvailable()){ ndecFeedDelta(slice); return; }
  if(!RECV) return;
  var total=18+slice.length;
  ensureBB(total+64);
  RADDR.writeU8(0x00); RADDR.add(1).writeU8(0x00);
  wU64(2,ptsUs); wU32(10,0); wU32(14,0);
  RADDR.add(18).writeByteArray(u8buf(slice));
  RBB.limit(total); RBB.position(0);
  RECV.onNativeFrameReceived(RBB, ptsUs, false);
  ptsUs+=Math.floor(1000000/FPS); if(fed%60===0) log('LIVE','frame#'+fed+' delta '+total+'B'); fed++;
  globalThis.__GB_FED=fed;
  if(fed%60===0){ try{ send({gb:'frame', n:fed}); }catch(e){} }
}
// ---- BACKPRESSURE -------------------------------------------------------------------------
// jys.f(bArr,j) allocates `new jyq(bArr,j)` and offers it into jys.t (a LinkedBlockingQueue that
// f() trims at 151); jys.h() drains that queue into MediaCodec from its own thread. If we read the
// socket faster than h() drains, f()'s overflow path drops frames AFTER allocating their ~24 KB
// payload, jys.E trips its "consecutive frames queued without output" counter (>300) and the
// receiver starts asking for keyframes -> more bytes, more allocation, faster OOM.
//
// So gate the READER on the decoder's real queue depth. While we stop calling recv(), the bytes
// stay in the socket buffers, host_relay.py blocks inside conn.sendall(), and screenrecord blocks
// on its pipe: the whole chain throttles to what the decoder can actually consume, and no payload
// is ever allocated for a frame we cannot hand over. Frame loss (if any) happens upstream in the
// encoder, not mid-GOP here, so no broken reference chains.
var MAX_INFLIGHT=8;          // frames allowed sitting in jys.t before we stop reading
var MAX_STALL_TICKS=900;     // safety valve (~7s): never let a dead decoder freeze the stream
var maxSeen=0, stalledTicks=0, stallEpisodes=0;
function inflight(){
  if(ndecAvailable()) return 0;
  try{ return (RECV && JYS) ? JYS.t.value.size() : 0; }catch(e){ return 0; }
}
// Reconnect after the relay dropped us. host_relay caches SPS/PPS + the last IDR, so a fresh
// connection resumes with a keyframe instead of a frozen picture.
//
// Two hazards measured 2026-10-31 while proving this path:
//  * a connect that "succeeds" can be adb-reverse accepting and closing at once (relay gone), which
//    looks like an instant drop — so every attempt is delayed, otherwise it spins at thousands/s;
//  * more than one pump timer chain can exist, and they each see the same EOF and each start a
//    reconnect, so the counter is what authorises a pump loop (pumpGen).
var RECONNECTING=false, reconTries=0, pumpGen=0, bytesSinceConnect=0;
// MEASURED 2026-11-01 (in-place agent re-attach): the fresh agent replayed the IDR, fed frame#0
// and then sat on a socket the relay had stopped feeding — transport healthy, receiver bound,
// counter frozen at 0 forever. That is worth one re-dial, which lets the relay replay its window.
//
// SCOPE MATTERS: a phone with a static screen legitimately sends almost nothing (MEASURED ~0.5 KB/s
// vs ~6 fps while animating), so "silence" is normal while idle. A blanket 6 s rule re-dialled every
// 7 s on an idle Pixel and kept replaying the same stale window into the decoder. So the re-dial is
// only allowed in the few seconds right after a fresh attach, and only a couple of times.
var lastByteAt=0, BYTE_STALL_MS=8000, lastStallLog=0;
var dialGuardUntil=0, dialGuardBudget=0;
function scheduleReconnect(){
  if(STOP || RECONNECTING) return;
  RECONNECTING=true;
  // Only a connection that actually DELIVERED bytes counts as a fresh start. adb reverse accepts
  // connects even with no relay behind it, then closes at once — that reads as a "successful"
  // reconnect followed by EOF, which is how this used to spin at ~140/s.
  if(bytesSinceConnect > 0) reconTries = 0;
  var gen = ++pumpGen;                     // invalidate every other pump chain
  var attempt = function(){
    if(STOP){ RECONNECTING=false; return; }
    reconTries++;
    var ok=false;
    try{ ok=connectHost(); }catch(e){ ok=false; }
    if(ok && gen===pumpGen){               // sets FD
      RECONNECTING=false; pumping=true;
      gotFirstKey=false; pending=new Uint8Array(0); bytesSinceConnect=0;
      lastByteAt=Date.now();
      if(reconTries===1) log('LIVE','video reconnected after drop');
      setTimeout(pump, 6);
      return;
    }
    if(gen!==pumpGen){ RECONNECTING=false; return; }
    if(reconTries===1 || reconTries % 5 === 0)
      log('WARN','video relay unreachable ('+reconTries+' attempt'+(reconTries===1?'':'s')+') — retrying');
    setTimeout(attempt, Math.min(5000, 800 + 200 * reconTries));   // ALWAYS delayed
  };
  setTimeout(attempt, 800);                // no immediate retry, even for the first try
}
function pump(){
  if(STOP||FD<0) return;
  if(RECV && lastByteAt && dialGuardBudget > 0 && Date.now() < dialGuardUntil
     && Date.now()-lastByteAt > BYTE_STALL_MS){
    dialGuardBudget--;
    lastStallLog = Date.now();
    log('WARN','no video bytes for '+Math.round((Date.now()-lastByteAt)/1000)+'s since the attach — '
      +'re-dialling the relay (was frame#'+fed+', '+dialGuardBudget+' re-dial(s) left)');
    try{ closef(FD); }catch(e){}
    FD=-1; lastByteAt=Date.now();          // one re-dial per window, not one per tick
    scheduleReconnect();
    return;
  }
  var gen=pumpGen;
  var depth=RECV?inflight():0;
  if(depth>maxSeen) maxSeen=depth;
  if(RECV && depth>MAX_INFLIGHT && stalledTicks<MAX_STALL_TICKS){
    if(stalledTicks===0) stallEpisodes++;
    stalledTicks++;
    if(stalledTicks%250===1){
      log('FLOW','reader stalled: inflight='+depth+' maxInflight='+maxSeen+' stallEpisodes='+stallEpisodes+' fed='+fed);
    }
    setTimeout(pump, 8); return;   // do NOT recv() — let TCP/relay/screenrecord absorb the stall
  }
  if(RECV && depth>MAX_INFLIGHT){
    log('FLOW','WARN decoder not draining (inflight='+depth+' for '+MAX_STALL_TICKS+' ticks) — resuming reads anyway');
  }
  if(stalledTicks>0){
    if(stalledTicks>=250) log('FLOW','reader resumed after '+stalledTicks+' stalled ticks (inflight='+depth+', max seen '+maxSeen+')');
    stalledTicks=0;
  }
  var doPumpStep = function(){
    try{
      for(var iter=0; iter<128; iter++){
        var n=recvf(FD,RBUF,65536,0x40).toNumber();  // MSG_DONTWAIT (recv returns long)
        if(n>0){ bytesSinceConnect += n; lastByteAt=Date.now(); appendBytes(RBUF.readByteArray(n)); continue; }
        // MEASURED 2026-10-31: this used to `break` on anything <= 0, so a socket the relay had
        // closed (recv -> 0) looked identical to "nothing arrived yet" (-1/EAGAIN) and the reader
        // span forever. The window froze on its last frame while TOUCH kept working (it has its own
        // socket on 8100), which is exactly "touch works but the cast doesn't".
        if(n === 0){
          if(!RECONNECTING) log('WARN','video socket closed by relay — reconnecting (was frame#'+fed+')');
          try{ closef(FD); }catch(e){}
          FD=-1;
          scheduleReconnect();
          return;
        }
        var err = 0; try{ err = errnoPtr.readS32(); }catch(e){}
        if(err !== 0 && err !== 11 && err !== 4){          // not EAGAIN/EINTR: a real error
          if(!RECONNECTING) log('WARN','video recv error errno='+err+' — reconnecting (was frame#'+fed+')');
          try{ closef(FD); }catch(e){}
          FD=-1;
          scheduleReconnect();
          return;
        }
        break;                                            // idle, try again next tick
      }
      processNALs();
    }catch(e){ if(fed%120===0) log('WARN','pump: '+e); }
  };
  if(ndecAvailable()) doPumpStep(); else Java.perform(doPumpStep);
  if(!STOP && gen===pumpGen) setTimeout(pump, 6);
}

// ---- bring up the receiver's Surface (inject our own SurfaceView), then start streaming ----
var ACT_REF=null, OLD_SV=null, LAST_ACT_HASH=0, LAST_INTENT_HASH=0, LAST_CLOSED_ACT_HASH=0, CLOSED_LOST_FOCUS=false, REATTACHING=false;
function ensureSurface(recv, cb){
  Java.scheduleOnMainThread(function(){
    try{
      var System=Java.use('java.lang.System');
      var Activity=Java.use('android.app.Activity');
      var act=null;
      Java.choose(ACT_CLS,{
        onMatch:function(x){
          try{
            var a=Java.cast(x, Activity);
            if(!a.isFinishing() && !a.isDestroyed()){
              var h=System.identityHashCode(x);
              if(h!==LAST_CLOSED_ACT_HASH){ act=x; }
            }
          }catch(e){ if(!act) act=x; }
        },
        onComplete:function(){}
      });
      if(!act){ log('WARN','no active AppCastActivity instance'); cb(false); return; }
      try{ ACT_REF=Java.retain(act); }catch(e){ ACT_REF=act; }
      LAST_ACT_HASH=System.identityHashCode(act);
      OUR_RECEIVER_PTR=null;
      if(OLD_SV){
        try{
          var p=OLD_SV.getParent();
          if(p){ Java.cast(p, Java.use('android.view.ViewGroup')).removeView(OLD_SV); log('LIVE','removed previous SurfaceView before re-skin'); }
        }catch(e){}
        OLD_SV=null;
      }

      // LAUNCH-ON-TAP: the AppCastActivity intent carries the exact extra "packageName" (set by
      // AppCastTrampolineActivity from the grid tile OR a notification click) — confirmed in the
      // decompile. Read it directly (no heuristic, which could misfire on deviceId/other extras).
      var launchPkg=null, launchNkey=null;
      try{
        var intent=Java.cast(act, Activity).getIntent();
        if(intent){
          LAST_INTENT_HASH=System.identityHashCode(intent);
          var pn=intent.getStringExtra('packageName');
          launchPkg = (pn!==null) ? (''+pn) : null;
          var nk=intent.getStringExtra('notificationKey');
          launchNkey = (nk!==null && (''+nk).length) ? (''+nk) : null;
          log('LIVE','launch intent: packageName='+launchPkg+' notificationKey='+(launchNkey||'null'));
        }
      }catch(e){ log('WARN','intent read: '+e); }
      var SurfaceView=Java.use('android.view.SurfaceView');
      var sv=SurfaceView.$new(Java.cast(act, Java.use('android.content.Context')));
      try{ sv.setZOrderOnTop(true); }catch(e){}
      try{
        var hScale = 1376.0 / 331.2;
        sv.setScaleX(hScale);
        log('LIVE', 'set SurfaceView horizontal scale to ' + hScale.toFixed(2));
      }catch(e){ log('WARN', 'setScaleX: ' + e); }
      var LP=Java.use('android.view.ViewGroup$LayoutParams');
      act.addContentView(sv, LP.$new(-1,-1));
      try{
        var tid = Java.cast(act, Activity).getTaskId();
        if(tid > 0){ send({gb:'resize', taskId:tid}); log('LIVE','requested portrait resize for taskId='+tid); }
      }catch(e){ log('WARN','taskId resize: '+e); }
      log('LIVE','added SurfaceView to AppCastActivity (hash='+LAST_ACT_HASH+'); waiting for surface…');
      var tries=25;
      (function poll(){
        Java.scheduleOnMainThread(function(){
          var s=null; try{ s=sv.getHolder().getSurface(); }catch(e){}
          if(s && s.isValid()){
            // OWN-DECODER PATH: don't hand the surface to the app's jys decoder — keep it for our
            // native GB_NDEC decoder, which renders into it directly. Still wire kjq/input/touch.
            // Gated on ndecAvailable() (NOT just FO.ownDecoder): if the native decoder failed to load
            // (e.g. a missing symbol), fall through to the baseline jys.d path rather than half-break it.
            if(ndecAvailable()){
              // reopened window => new SurfaceView: tear down the decoder so the next keyframe
              // re-inits it against THIS surface (this is the app-switch recovery).
              try{ GB_NDEC.destroy(); }catch(e){}
              NDEC_LIVE=false; ndecInitTried=0;
              try{ globalThis.__GB_SURFACE=Java.retain(s); }catch(e){ globalThis.__GB_SURFACE=s; }
              SVREF=sv;
              try{ JYS=Java.cast(recv.b.value, Java.use(PKG+N_JYS)); JYS=Java.retain(JYS); }catch(e){ JYS=null; }
              // Make the ANativeWindow NOW, on the main thread, from the FRESH surface wrapper (its
              // $handle + this thread's JNIEnv are reliable here, unlike a retained wrapper on the pump).
              var anwOk=false; try{ anwOk=GB_NDEC.attachSurface(s); }catch(e){ log('NDEC','attachSurface threw: '+e); }
              log('NDEC','captured our Surface for the native decoder (jys.d skipped); surface valid='+s.isValid()+' anwOk='+anwOk);
              // Install the resident kjq.b suppression ONCE. Re-replacing a resident Java method
              // implementation on every in-place re-skin churns the moving GC (ART17) and recurred as
              // "Implementation for b expected return value compatible with java.lang.Object" + an
              // agent-unload SIGSEGV that took ambient down. The hook persists across re-skins anyway.
              if(!KJQ_HOOKED){ try{ Java.use(PKG+N_CONN).b.implementation=function(obj){ return unitSentinel(); }; KJQ_HOOKED=true; }catch(e){ log('WARN','kjq.b suppress: '+e); } }
              suppressReleaseSurface();
              connectInput();
              phoneOpen(launchPkg, launchNkey, 'window opened');
              installTouchForward(sv);
              watchSurface(sv);
              if(KEEPER_ON) surfaceKeeper();
              cb(true); return;
            }
            // the receiver decodes via ITS OWN jys = recv.b (field b). Use THAT exact instance,
            // not Java.choose('jys') (which, with moving GC disabled, returns a stale lingering jys).
            var jys=null;
            try{ jys=Java.cast(recv.b.value, Java.use(PKG+N_JYS)); }catch(e){ log('WARN','recv.b cast: '+e); }
            if(jys){
              try{ log('DIAG','recv.b class = '+recv.b.value.getClass().getName()+' ; our surface='+s+' valid='+s.isValid()); }catch(e){}
              try{ jys.d(s); }catch(e){ log('WARN','jys.d: '+e); }
              try{ log('DIAG','after d(): jys.k='+jys.k.value+' (null='+(jys.k.value===null)+')'); }catch(e){}
              log('LIVE','jys.d(surface) done on recv.b');
              try{ JYS=Java.retain(jys); }catch(e){ JYS=jys; }
              SVREF=sv;
              // Once first receiver is established, short-circuit kjq.b so reopening AppCastActivity
              // from Phone Hub reuses RECV/JYS instead of starting a new OpenScreen connection attempt.
              // MEASURED BUG (2026-10-31): returning a Unit obtained here threw
              // "Implementation for b expected return value compatible with java.lang.Object" the first
              // time ambient called it on a reopened window, which killed addContentView (surface never
              // added, no video). The sentinel is now captured ONCE at load and Java.retain()ed, so it
              // survives the moving GC that is resumed a few seconds into the session.
              // Install ONCE (see note in the ownDecoder branch above) — re-replacing it per re-skin
              // churned the moving GC and crashed the agent.
              if(!KJQ_HOOKED){ try{
                Java.use(PKG+N_CONN).b.implementation=function(obj){ return unitSentinel(); };
                KJQ_HOOKED=true;
              }catch(e){ log('WARN','kjq.b suppress: '+e); } }
              suppressReleaseSurface();
              connectInput();
              phoneOpen(launchPkg, launchNkey, 'window opened');
              installTouchForward(sv);
              watchSurface(sv);
              if(KEEPER_ON) surfaceKeeper();
              cb(true); return;
            }
            log('WARN','no jys to attach surface'); cb(false); return;
          }
          if(tries-->0) setTimeout(poll,200); else { log('WARN','surface never valid'); cb(false); }
        });
      })();
    }catch(e){ log('WARN','ensureSurface: '+e); cb(false); }
  });
}
// A NEW AppCastActivity means the previous window (and its decoder) is gone. There is no point
// attaching a SurfaceView to the retained receiver: MEASURED 2026-11-01, the reopened window then
// stays blank while the frame counter climbs (900 frames "fed" into a decoder the app released with
// the old surface). The app only builds a new receiver when its cast request is bridged, and llz.a
// cannot be re-armed in a live agent, so ask the host to recycle the agent — a fresh load re-arms
// llz.a and the relaunched window establishes normally.
var lastRecastAt=0;
function requestRecast(why, pkg){
  if(Date.now()-lastRecastAt < 10000) return;      // both watch branches can see the same window
  lastRecastAt=Date.now();
  log('WATCH',why+' — asking the host to recycle the agent for '+(pkg||'the clicked app'));
  try{ send({gb:'recast', pkg:pkg||''}); }catch(e){}
}
var seenRecvIds={}, pumping=false;
function attachToReceiver(rv){
  log('LIVE','attaching to JniOpenScreenReceiver; ensuring surface then connecting to relay');
  ensureSurface(rv, function(ok){
    REATTACHING=false;
    if(!ok) return;
    Java.perform(function(){
      RECV=rv;
      gotFirstKey=false;
      // Always reconnect host_relay so it immediately sends cached SPS/PPS + IDR keyframe
      if(FD>=0){ try{ closef(FD); }catch(e){} FD=-1; }
      if(connectHost()){
        lastByteAt=Date.now();
        // A fresh attach may land on a socket the relay is no longer feeding; give it a short,
        // bounded window in which silence justifies a re-dial.
        dialGuardUntil=Date.now()+30000; dialGuardBudget=2;
        if(!pumping){ pumping=true; pump(); }
      } else {
        log('ERR','no relay — is host_relay.py running + adb reverse set?');
      }
    });
  });
}
function pollForRecv(tries){
  if(RECV || STOP) return;
  Java.perform(function(){
    try{
      var System=Java.use('java.lang.System');
      var newest=null;
      Java.choose(RECV_CLS,{
        onMatch:function(x){
          var id=System.identityHashCode(x);
          if(!seenRecvIds[id]){ seenRecvIds[id]=true; newest=x; }
        },
        onComplete:function(){}
      });
      if(newest){
        try{ Java.use(RECV_CLS).$init.overloads.forEach(function(o){ try{ o.implementation=null; }catch(e){} }); }catch(e){}
        attachToReceiver(newest);
        return;
      }
    }catch(e){}
    if(!STOP && !RECV) setTimeout(function(){ pollForRecv(tries); }, POLL_MS);
  });
}

Java.perform(function(){
  log('LIVE','feed_live ready — will stream Pixel screen from '+HOST_IP+':'+PORT+' once receiver is up.');
  log('LIVE','[FO] switches: '+JSON.stringify(FO));
  suppressLaunchTimeout();   // Phase-A freeze fix (keep the activity alive past the 15s launch timeout)
  if(FO.noInit){ log('LIVE','[FO] JniOpenScreenReceiver.$init hook DISABLED (pollForRecv only)'); pollForRecv(POLL_TRIES); return; }
  try{
    var RecvCls=Java.use(RECV_CLS);
    RecvCls.$init.overloads.forEach(function(ov){
      ov.implementation=function(){
        var r=ov.apply(this,arguments);
        try{
          var self=Java.retain(this);
          var id=Java.use('java.lang.System').identityHashCode(this);
          seenRecvIds[id]=true;
          log('LIVE','JniOpenScreenReceiver.$init fired (id='+id+'); reverting $init hook');
          try{ RecvCls.$init.overloads.forEach(function(o){ try{ o.implementation=null; }catch(e){} }); }catch(e){}
          setTimeout(function(){ Java.perform(function(){ attachToReceiver(self); }); }, 250);
        }catch(e){ log('WARN','RecvCls.$init hook: '+e); }
        return r;
      };
    });
    log('LIVE','hooked JniOpenScreenReceiver.$init');
  }catch(e){ log('WARN','hook RecvCls.$init: '+e); }
  pollForRecv(POLL_TRIES);
});

// ---- surface + Phone Hub app-launch watcher & orderly shutdown ----------------------------
// Polls every 500ms (zero Java hooks):
// 1. Updates SV_W, SV_H, SV_TOP, SV_FOCUSED, and OUR_RECEIVER_PTR for native C++ touch/scroll.
// 2. Detects Phone Hub app clicks when AppCastActivity is already open (onNewIntent updates
//    act.getIntent() with the clicked app's "packageName") and launches that app on the Pixel.
// 3. If the user closes the AppCastActivity window, pauses the relay socket and stands by; when
//    the user clicks any app in Phone Hub to open a new AppCastActivity, injects a fresh
//    SurfaceView, resizes to 376x880, launches the clicked app on the Pixel, and resumes!
var ENDED=false, WATCH_ON=false;
// What to open on the Pixel for an App Cast window's intent. A Phone Hub tile carries
// "packageName"; a tapped mirrored notification carries "notificationKey" instead (GBOS's
// MirroredNotificationBodyClickReceiver -> keb.b: deviceId + notificationKey, NO packageName), which
// is the phone's own StatusBarNotification key "user|pkg|id|tag|uid". The phone's GBOS Cast app fires
// that notification's own tap action (the deep link, e.g. the exact post); the key is hex-encoded
// because tags can hold spaces/brackets. If no key, launch the app as before.
function phoneOpen(pkg, nkey, why){
  if(nkey){
    var b=unescape(encodeURIComponent(nkey)), h='';             // UTF-8 bytes
    for(var i=0;i<b.length;i++){ var c=b.charCodeAt(i); h+=(c<16?'0':'')+c.toString(16); }
    sendLine('opennotif '+h+'\n');
    log('LIVE',why+': opening phone notification '+nkey);
    return;
  }
  // The GBOS widget's Open button asks for the phone's home screen: not an app, so no launch — wake the
  // phone and press Home (both verbs the agent already has; the key path needs no keyguard dance).
  if(pkg==='gbos.phone.home'){
    sendLine('key KEYCODE_WAKEUP\n'); sendLine('key KEYCODE_HOME\n');
    log('LIVE',why+': showing the Pixel home screen'); return;
  }
  if(pkg){ sendLine('launch '+pkg+'\n'); log('LIVE',why+': launching '+pkg+' on Pixel'); }
}
// AGENT HEARTBEAT. The supervisor's stall watchdog used to treat "no frame-counter movement for 120s"
// as a dead agent and re-attach in place — but the counter only moves every 60 frames, and a closed
// window or a static phone screen legitimately produces none. MEASURED 2026-11-01: that false
// positive fired 11 times and the re-attach unload (frida-agent dlclose) took ambient down in at
// least 6 of them (tombstone_02, tombstone_12). A pure-JS timer (no Java, no GC exposure) proves the
// agent is alive whatever the stream is doing; the supervisor now only re-attaches when BOTH frames
// and heartbeats stop, i.e. a genuinely wedged agent.
(function hb(){ if(STOP) return; try{ send({gb:'hb'}); }catch(e){} setTimeout(hb, 5000); })();
// OWN-DECODER app-switch: a reopened window no longer needs a full session recast. Because WE decode
// (not the app's receiver), we can just re-skin the new AppCastActivity in place — inject a fresh
// SurfaceView, re-attach+re-init the native decoder, read the new window's packageName and launch it
// on the Pixel, and reconnect the relay. ensureSurface()/attachToReceiver() already do all of that in
// the ownDecoder branch; we reuse the retained RECV purely as a token (its dead jys decoder is unused).
function reopenInPlace(why, markClosedHash){
  log('WATCH', why+' — re-skinning in place (own decoder, no recast)');
  if(markClosedHash) LAST_CLOSED_ACT_HASH=markClosedHash;
  SVREF=null; ACT_REF=null; OUR_RECEIVER_PTR=null;
  REATTACHING=true;
  attachToReceiver(RECV);
}
function stopStream(){
  if(STOP) return;
  STOP=true;
  if(FD>=0){ try{ closef(FD); }catch(e){} FD=-1; }
  if(INPUT_FD>=0){ try{ closef(INPUT_FD); }catch(e){} INPUT_FD=-1; }
  log('STOP','reader + input sockets closed ('+fed+' frames fed this session)');
}
var watchErrs=0;
function watchSurface(sv){
  if(WATCH_ON) return;
  WATCH_ON=true;
  log('WATCH','watching App Cast surface & Phone Hub app intents (500ms poll, zero Java hooks)');
  (function tick(){
    if(STOP) return;
    Java.perform(function(){
      try{
        var System=Java.use('java.lang.System');
        var Activity=Java.use('android.app.Activity');

        // A NEW AppCastActivity instance is the one liveness signal that cannot lie. Measured
        // 2026-10-31: after the user closes the window, the retained activity/surface wrappers keep
        // answering isDestroyed()=false and surface.isValid()=true, so the release branch never
        // fires — and because the release branch is what led to the standby branch, clicking a new
        // app icon then did nothing at all (no launch, no surface). Checking for a new instance
        // FIRST makes the reopen path independent of close detection.
        if(RECV && !REATTACHING){
          var reopened=null;
          Java.choose(ACT_CLS,{
            onMatch:function(x){
              try{
                var a=Java.cast(x, Activity);
                if(!a.isFinishing() && !a.isDestroyed() && System.identityHashCode(x)!==LAST_ACT_HASH) reopened=x;
              }catch(e){}
            },
            onComplete:function(){}
          });
          if(reopened){
            if(ndecAvailable()){
              // Our decoder re-inits against the new window's surface; no recast needed.
              reopenInPlace('window reopened (new AppCastActivity)', LAST_ACT_HASH);
              return;
            }
            var rpkg='';
            try{
              var rit=Java.cast(reopened, Activity).getIntent();
              if(rit){ var rpn=rit.getStringExtra('packageName'); if(rpn!==null) rpkg=''+rpn; }
            }catch(e){}
            LAST_CLOSED_ACT_HASH=LAST_ACT_HASH;
            LAST_ACT_HASH=System.identityHashCode(reopened);
            SVREF=null; ACT_REF=null; OUR_RECEIVER_PTR=null;
            requestRecast('window reopened (new AppCastActivity)', rpkg);
            return;
          }
        }

        if(SVREF){
          var s=null; try{ s=SVREF.getHolder().getSurface(); }catch(e){}
          var alive=false; try{ alive=!!(s && s.isValid()); }catch(e){}
          if(alive && ACT_REF){
            try{
              var actCast=Java.cast(ACT_REF, Activity);
              if(actCast.isFinishing() || actCast.isDestroyed()) alive=false;
            }catch(e){}
          }
          if(alive){
            // 1. Update cached geometry & NativeInputEventReceiver pointer for C++ input hook
            try{
              var w=SVREF.getWidth(), h=SVREF.getHeight();
              if(w>0) SV_W=w;
              if(h>0) SV_H=h;
              if(ACT_REF){
                var ac=Java.cast(ACT_REF, Activity);
                SV_FOCUSED=!!ac.hasWindowFocus();
                var dv=ac.getWindow().getDecorView();
                if(dv){
                  var dh=dv.getHeight(), dw=dv.getWidth();
                  if(dh>SV_H && SV_H>0) SV_TOP=dh-SV_H;
                  var lx=0, ly=0;
                  try{
                    var loc=Java.array('int',[0,0]);
                    dv.getLocationOnScreen(loc);
                    lx=loc[0]; ly=loc[1];
                  }catch(eLoc){}
                  if(lx===0 && ly===0){
                    try{
                      var b=ac.getResources().getConfiguration().windowConfiguration.value.getBounds();
                      if(b.left.value!==0 || b.top.value!==0){ lx=b.left.value; ly=b.top.value; }
                      if(dw<=0 || dh<=0){ dw=b.width(); dh=b.height(); }
                    }catch(eCfg){
                      try{
                        var b2=ac.getWindowManager().getCurrentWindowMetrics().getBounds();
                        if(b2.left.value!==0 || b2.top.value!==0){ lx=b2.left.value; ly=b2.top.value; }
                        if(dw<=0 || dh<=0){ dw=b2.width(); dh=b2.height(); }
                      }catch(eWm){}
                    }
                  }
                  if(dw>0 && dh>0 && (lx!==WIN_X || ly!==WIN_Y || dw!==WIN_W || dh!==WIN_H)){
                    WIN_X=lx; WIN_Y=ly; WIN_W=dw; WIN_H=dh;
                    log('LIVE','window geometry -> ('+WIN_X+','+WIN_Y+') '+WIN_W+'x'+WIN_H+' sv='+SV_W+'x'+SV_H+' top='+SV_TOP);
                  }
                }
              }
              if(OUR_RECEIVER_PTR===null){
                var vri=SVREF.getViewRootImpl();
                if(vri){
                  var f1=vri.getClass().getDeclaredField('mInputEventReceiver');
                  f1.setAccessible(true);
                  var ier=f1.get(vri);
                  if(ier){
                    var f2=ier.getClass().getSuperclass().getDeclaredField('mReceiverPtr');
                    f2.setAccessible(true);
                    var rptr=f2.getLong(ier);
                    if(rptr){
                      OUR_RECEIVER_PTR=ptr(rptr.toString());
                      log('LIVE','bound NativeInputEventReceiver ptr='+OUR_RECEIVER_PTR+' SV_TOP='+SV_TOP+' sv='+SV_W+'x'+SV_H);
                    }
                  }
                }
              }
            }catch(e){}

            // 2. Check if user clicked a Pixel app in Phone Hub (onNewIntent on existing Activity)
            if(ACT_REF){
              try{
                var intent=Java.cast(ACT_REF, Activity).getIntent();
                if(intent){
                  var iHash=System.identityHashCode(intent);
                  if(iHash!==LAST_INTENT_HASH){
                    LAST_INTENT_HASH=iHash;
                    var pn=intent.getStringExtra('packageName');
                    var nk2=intent.getStringExtra('notificationKey');
                    phoneOpen(pn!==null?(''+pn):null, (nk2!==null&&(''+nk2).length)?(''+nk2):null,
                              'Phone Hub/notification click (onNewIntent)');
                    try{
                      var tid=Java.cast(ACT_REF, Activity).getTaskId();
                      if(tid>0) send({gb:'resize', taskId:tid});
                    }catch(e2){}
                  }
                }
              }catch(e){}
            }

            // 3. Check if a newer AppCastActivity instance replaced ACT_REF
            var newerAct=null;
            Java.choose(ACT_CLS,{
              onMatch:function(x){
                try{
                  var a=Java.cast(x, Activity);
                  if(!a.isFinishing() && !a.isDestroyed()){
                    var h=System.identityHashCode(x);
                    if(h!==LAST_ACT_HASH && h!==LAST_CLOSED_ACT_HASH) newerAct=x;
                  }
                }catch(e){}
              },
              onComplete:function(){}
            });
            if(newerAct && !REATTACHING){
              log('WATCH','new AppCastActivity instance detected while live; switching surface');
              LAST_CLOSED_ACT_HASH=LAST_ACT_HASH;
              SVREF=null; ACT_REF=null; OUR_RECEIVER_PTR=null;
              REATTACHING=true;
              attachToReceiver(RECV);
            }
          } else {
            // Window closed or backgrounded: pause stream and stand by for next Phone Hub click or refocus!
            log('WATCH','App Cast surface released — pausing stream and standing by for next Phone Hub app click');
            LAST_CLOSED_ACT_HASH=LAST_ACT_HASH;
            CLOSED_LOST_FOCUS=false;
            if(FD>=0){ try{ closef(FD); }catch(e){} FD=-1; }
            pumping=false;
            if(ndecAvailable()){ try{ GB_NDEC.destroy(); }catch(e){} NDEC_LIVE=false; }
            else if(JYS){ try{ JYS.u(); }catch(e){} }
            OLD_SV=SVREF;
            SVREF=null; ACT_REF=null; OUR_RECEIVER_PTR=null; SV_FOCUSED=false;
          }
        } else if(RECV && !REATTACHING){
          // Standing by while window is closed/backgrounded: check if user clicked an app in Phone Hub
          // or brought the existing singleInstance AppCastActivity back to the foreground.
          var newAct=null, sameActResumed=false;
          Java.choose(ACT_CLS,{
            onMatch:function(x){
              try{
                var a=Java.cast(x, Activity);
                if(!a.isFinishing() && !a.isDestroyed()){
                  var h=System.identityHashCode(x);
                  if(h!==LAST_CLOSED_ACT_HASH){
                    newAct=x;
                  } else {
                    var foc=!!a.hasWindowFocus();
                    if(!foc) CLOSED_LOST_FOCUS=true;
                    else if(CLOSED_LOST_FOCUS){ newAct=x; sameActResumed=true; }
                  }
                }
              }catch(e){}
            },
            onComplete:function(){}
          });
          if(newAct){
            if(ndecAvailable() || sameActResumed){
              // Own decoder OR same singleInstance activity returning from background (jxj.close never ran):
              // re-skin this window in place.
              if(sameActResumed) LAST_CLOSED_ACT_HASH=0;
              reopenInPlace(sameActResumed ? 'AppCastActivity returned to foreground' : 'new AppCastActivity opened from Phone Hub', 0);
              return;
            }
            // What did the user click? (the window carries it as an intent extra)
            var wantPkg='';
            try{
              var it=Java.cast(newAct, Activity).getIntent();
              if(it){ var pn=it.getStringExtra('packageName'); if(pn!==null) wantPkg=''+pn; }
            }catch(e){}

            // The retained receiver lost its decoder along with the old surface, so attaching to
            // it only fills a dead decoder — the window stays blank while frames are "fed". A
            // fresh agent is the only path to a working window here, so ask the host for one.
            LAST_CLOSED_ACT_HASH=System.identityHashCode(newAct);   // ask once for this window
            requestRecast('new AppCastActivity opened from Phone Hub', wantPkg);
          }
        }
      }catch(e){
        // This loop IS the Phone Hub feature: it notices a closed window (so the next app-icon
        // click re-attaches) and a reopened one. A silent throw here stops all of that with no
        // trace, which looked exactly like "clicking an icon does nothing".
        if(watchErrs < 5){ watchErrs++; log('WARN','watch tick: '+e); }
      }
    });
    if(!STOP) setTimeout(tick, 500);
  })();
}

// Revert every Java replacement this script installed. Entry-point rewrites are only safe to
// leave behind while the script owns them; they must all be gone before the agent is unloaded.
function revertJavaHooks(){
  try{ Java.use('android.media.MediaCodec').configure.overloads.forEach(function(ov){ try{ ov.implementation=null; }catch(e){} }); }catch(e){}
  try{ Java.use(RECV_CLS).$init.overloads.forEach(function(ov){ try{ ov.implementation=null; }catch(e){} }); }catch(e){}
  N_STALL.forEach(function(c){ try{ Java.use(PKG+c).b.implementation=null; }catch(e){} });
  try{ Java.use(PKG+N_CONN).b.implementation=null; }catch(e){}
  try{ Java.use(PKG+N_JYS).u.implementation=null; }catch(e){}
  SV_HOOKED=false;
}
function shutdown(){
  try{ stopStream(); }catch(e){}
  if(globalThis.GB_NDEC){ try{ GB_NDEC.destroy(); }catch(e){} }
  try{
    INPUT_LISTENERS.forEach(function(l){ try{ l.detach(); }catch(e){} });
    INPUT_LISTENERS=[];
  }catch(e){}
  try{
    Java.perform(function(){
      try{ revertJavaHooks(); }catch(e){ log('WARN','shutdown revert: '+e); }
      // drop every retained Java/ByteBuffer reference: these were the GC roots that made a
      // resident replacement unsafe, and they must not outlive the session either.
      try{ RECV=null; JYS=null; SVREF=null; ACT_REF=null; OUR_RECEIVER_PTR=null; RBB=null; RADDR=null; RCAP=0; pending=new Uint8Array(0); }catch(e){}
    });
  }catch(e){ log('WARN','shutdown Java.perform: '+e); }
  log('SHUTDOWN','feed_live: Java + native input hooks reverted, sockets closed, refs dropped ('+fed+' frames)');
  return {frames:fed, ended:ENDED};
}
rpc.exports = { shutdown: shutdown };
