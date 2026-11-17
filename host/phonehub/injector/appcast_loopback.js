/*
 * appcast_loopback.js — THE BRIDGE: make GBOS cast one of its own apps to its OWN
 *                       AppCast window. Pixels on screen, no root, 100% Google code.
 *
 * Everything works now (proven): attestation bypass, control plane, ktk AES transport,
 * OpenScreen sender (kry/jxp/JniOpenScreenSender), MediaCodec decode+encode, and the
 * sender's VirtualDisplay source (Settings launches on it). The only thing left is to
 * point GBOS's sender at GBOS's own receiver so neither of us implements OpenScreen.
 *
 * WHY IT WORKS: the RECEIVER authenticates the inbound media stream solely by the AES
 * key it put in its EstablishOpenScreenConnection request (kdk_A). We copy that key +
 * the receiver's listen ports into the request we hand the SENDER (kdk_B). GBOS's own
 * sender then connects to GBOS's own receiver on 10.77.0.1 and streams; the receiver
 * accepts it because the key matches. The OFFER/ANSWER/RTP handshake is entirely
 * GBOS<->GBOS — we only bridge ports+keys on the control plane.
 *
 * Two synthetic device-link channels in one GBOS process:
 *   A ("synthetic-appcast"): WE are the cast SENDER, GBOS is the RECEIVER. GBOS shows
 *      a grid, you click an app, GBOS opens AppCastActivity and emits kdk_A (its own
 *      ktf TCP + kty UDP listen ports P_recv and AES keys K_recv), then accept()s.
 *   B ("synthetic-recv"): WE are the cast RECEIVER, GBOS is the SENDER. GBOS builds its
 *      real kry. On kdk_A capture we send kdk_B with candidates = P_recv/K_recv, so
 *      GBOS's ktd connects to GBOS's own receiver (loopback) and streams Settings.
 *
 * RUN (load the MediaCodec unblock alongside; CDM fix runs in system_server):
 *   system_server:  frida -U -p $(adb shell pidof system_server) -l cdm_profile_fix.js
 *   ambient:        frida -U -p <ambient_pid> -l mediacodec_inject_info.js -l appcast_loopback.js
 * Then: launch the grid, click an app (Settings). Keeper paused.
 */

'use strict';

var PKG='';
// R8-obfuscated Cross-Device Services names; they change with every update of the app.
// Before the 2026-11-15 update: codec list=jwi (decoder-name method f), display session=kan, connect hook=llz, device link=lmb.
// 1.0.1387 (versionCode 100143140): ljx (decoder-name method is now e; the new f is a different method), lnp, ncd, ncf.
// Codec-class methods by role (old jwi -> new ljx): mime-from-list h(List)->g, decoder name f(String,Z,Z)->e,
// codec name g(String,Z)->f. Hooks are pinned to their argument types so a rename fails loudly instead of
// replacing an unrelated method (a mismatched replacement crashed ART's moving GC on 2026-11-16).
var N_CODECS='ljx', N_CODECS_DECNAME='e', N_CODECS_MIME='g', N_CODECS_NAME='f', N_DISPLAY='lnp', N_CONNECT='ncd', N_LINK='ncf';
var MEDIA_IP='10.77.0.1';
var DEV_A='synthetic-appcast', SELF_A='Pixel 7',  UUID_A='b1c2d3e4-0000-4a4a-b4b4-c5c5c5c5c5c5';
var DEV_B='synthetic-recv',    SELF_B='SyntheticPhone',  UUID_B='c1d2e3f4-0000-4b4b-b5b5-d6d6d6d6d6d6';
var OLD_UUID='a94f0289-6941-41a4-b4ed-cdcc81da3f6e';
var DISP_W=1372, DISP_H=727, DISP_DPI=180, SESSION_ID=12345;
// RECEIVER_ONLY: do NOT bridge to GBOS's sender (kry/encoder/VirtualDisplay). That sender
// spin-up churns the heap and trips ART's mark-compact GC ("GC tried to mark invalid reference").
// For the feed_receiver.js injection path we only need JniOpenScreenReceiver to instantiate
// (from answering kdk_A with kdl_A); frames go straight into onNativeFrameReceived. So after the
// receiver establishes we skip kdk_B and detach our hooks EARLY to clean the heap before any GC.
var RECEIVER_ONLY=false;  // need kdk_B: JniOpenScreenReceiver only instantiates once a sender connects

var WMK_HEX='0abc0122b9010ab6010a2461393466303238392d363934312d343161342d623465642d63646363383164613366366510001a47676f6f676c652f70616e746865722f70616e746865723a31372f435033412e3236313030352e3030322e41312f31363236393237333a757365722f72656c656173652d6b6579732213312e302e313334362e313030303930353438372a1c32362e34322e363120283236303430302d313030363030353836342932105d2395559bf1e9254fff8db3fae9d240';
var ACK_HEX='0a0422021200';
var OBSERVE_REQ_HEX='0a280a260800120e41707043617374536572766963651a120a0e4f6273657276654170704c6973741200';
var MOCK_APPS=[
  ['com.android.settings','Settings'],
  ['com.android.chrome','Chrome'],
  ['com.google.android.apps.messaging','Messages'],
  ['com.google.android.gm','Gmail'],
  ['com.google.android.apps.photos','Photos'],
  ['com.google.android.GoogleCamera','Camera'],
  ['com.google.android.dialer','Phone'],
  ['com.google.android.contacts','Contacts'],
  ['com.google.android.calendar','Calendar'],
  ['com.google.android.calculator','Calculator'],
  ['com.google.android.deskclock','Clock'],
  ['com.google.android.apps.maps','Maps'],
  ['com.google.android.youtube','YouTube'],
  ['com.google.android.apps.youtube.music','YT Music'],
  ['com.google.android.apps.docs','Drive'],
  ['com.google.android.apps.docs.editors.docs','Docs'],
  ['com.google.android.keep','Keep Notes'],
  ['com.google.android.apps.bard','Gemini'],
  ['com.google.android.apps.tachyon','Meet'],
  ['com.google.android.apps.translate','Translate'],
  ['com.google.android.apps.recorder','Recorder'],
  ['com.google.android.apps.nbu.files','Files'],
  ['com.google.android.apps.nbu.paisa.user','Wallet'],
  ['com.google.android.videos','Google TV'],
  ['com.android.vending','Play Store'],
  ['com.anthropic.claude','Claude'],
  ['com.discord','Discord'],
  ['com.reddit.frontpage','Reddit'],
  ['com.twitter.android','X'],
  ['com.github.android','GitHub'],
  ['com.sirma.mobile.bible.android','Bible'],
  ['com.bibleproject','BibleProject'],
  ['com.ministrycentered.churchcenter','Church Center'],
  ['com.ministrycentered.PlanningCenter','Services'],
  ['com.fitbit.FitbitMobile','Fitbit'],
  ['com.netflix.mediaclient','Netflix'],
  ['com.disney.disneyplus','Disney+'],
  ['com.hulu.plus','Hulu'],
  ['com.peacocktv.peacockandroid','Peacock'],
  ['com.crunchyroll.crunchyroid','Crunchyroll'],
  ['com.valvesoftware.android.steam.community','Steam'],
  ['com.valvesoftware.steamlink','Steam Link'],
  ['com.microsoft.xboxone.smartglass','Xbox'],
  ['com.gamepass','Xbox Game Pass'],
  ['com.chase.sig.android','Chase'],
  ['com.citi.citimobile','Citi Mobile'],
  ['com.paypal.android.p2pmobile','PayPal'],
  ['com.walmart.android','Walmart'],
  ['com.ebay.mobile','eBay'],
  ['com.bestbuy.android','Best Buy'],
  ['com.thehomedepot','Home Depot'],
  ['com.starbucks.mobilecard','Starbucks'],
  ['com.chickfila.cfaflagship','Chick-fil-A'],
  ['com.mcdonalds.app','McDonald\'s'],
  ['org.kde.kdeconnect_tp','KDE Connect'],
  ['org.zwanoo.android.speedtest','Speedtest']
];

// ---------- helpers -------------------------------------------------------
function utf8(s){var r=[];for(var i=0;i<s.length;i++){var c=s.charCodeAt(i);
  if(c<0x80)r.push(c);else if(c<0x800){r.push(0xc0|(c>>6),0x80|(c&0x3f));}
  else{r.push(0xe0|(c>>12),0x80|((c>>6)&0x3f),0x80|(c&0x3f));}}return r;}
function varint(n){var r=[];n=n>>>0;do{var b=n&0x7f;n>>>=7;if(n)b|=0x80;r.push(b);}while(n);return r;}
function field(tag,c){return [tag].concat(varint(c.length)).concat(c);}
function hexToBytes(h){var r=[];for(var i=0;i<h.length;i+=2)r.push(parseInt(h.substr(i,2),16));return r;}
function bytesToHex(b){var o=[];for(var i=0;i<b.length;i++)o.push(('0'+(b[i]&0xff).toString(16)).slice(-2));return o.join('');}
function jbytes(a){var s=[];for(var i=0;i<a.length;i++){var v=a[i]&0xff;s.push(v>127?v-256:v);}return Java.array('byte',s);}
function ts(){return new Date().toISOString().slice(11,23);}
function log(t,m){console.log('['+ts()+']['+t+'] '+m);}
function sstr(x){try{return ''+x;}catch(e){return '<?>';}}
function hook(cls,method,impl){try{var C=Java.use(cls);var m=C[method];if(m===undefined)return;m.overloads.forEach(function(ov){impl(ov);});log('HOOK',cls+'.'+method);}catch(e){log('WARN','hook '+cls+'.'+method+': '+e);}}
function hookInit(cls,impl){try{var C=Java.use(cls);C.$init.overloads.forEach(function(ov){impl(ov);});log('HOOK',cls+'.$init');}catch(e){log('WARN','hookInit '+cls+': '+e);}}

// ---------- channel A (we = sender, GBOS = receiver) responders -----------
function buildWnc(services){var e=[];services.forEach(function(s){e=e.concat(field(0x0a,utf8(s)));});return field(0x0a,field(0x22,field(0x12,e)));}
var APP_LIMIT=MOCK_APPS.length;  // advertised app count; MOCK_APPS.length = all
var SKIP_ICONS=true;             // don't answer GetAdaptiveIcon with the 1x1 placeholder (blank tiles) — real icons from the phone bundle below ARE answered
// ---- the phone's REAL app list + icons -------------------------------------------------------
// appcast_session.py prepends `globalThis.GB_APPS = {apps:[[pkg,label],...], icons:{pkg:<base64 png>}}`
// from what the gbcast phone app pushed to the laptop (AppSync.java -> host_relay.py). Without it we
// fall back to the hand-typed MOCK_APPS and unanswered (grey) icons, exactly as before.
// MEASURED 2026-11-01: GBOS asks for icons only at startup (6 requests, GetAdaptiveIcon seq 2..7, all
// within 10 ms of the app list), and the request message (kdp) is just {1: packageName}.
var BUNDLE=(globalThis.GB_APPS&&globalThis.GB_APPS.apps&&globalThis.GB_APPS.apps.length)?globalThis.GB_APPS:null;
var BUNDLE_APP_CAP=250;          // sanity cap on the advertised list
function b64decode(s){
  var r=[],buf=0,bits=0;
  for(var i=0;i<s.length;i++){
    var c=s.charCodeAt(i),v;
    if(c>=65&&c<=90)v=c-65;else if(c>=97&&c<=122)v=c-71;else if(c>=48&&c<=57)v=c+4;else if(c===43)v=62;else if(c===47)v=63;else continue;
    buf=(buf<<6)|v;bits+=6;
    if(bits>=8){bits-=8;r.push((buf>>bits)&0xff);buf&=(1<<bits)-1;}
  }
  return r;
}
// GetAdaptiveIcon / GetIcon request: ... 1a <n> { 0a <len> "GetAdaptiveIcon", 12 <n> { 0a <len> <package> } }
function extractIconPkg(b){
  function idx(pat){
    outer: for(var i=0;i+pat.length<b.length;i++){
      for(var j=0;j<pat.length;j++) if(b[i+j]!==pat[j]) continue outer;
      return i;
    }
    return -1;
  }
  var m=utf8('GetAdaptiveIcon'), at=idx(m), len=m.length;
  if(at<0){ m=utf8('GetIcon'); at=idx(m); len=m.length; }
  if(at<0) return null;
  var i=at+len, c, s;
  if(b[i]!==0x12) return null;
  i++; s=0; do{ c=b[i++]; s+=7; }while(c&0x80);                       // skip the payload length varint
  if(b[i]!==0x0a) return null;
  i++; var pl=0; s=0; do{ c=b[i++]; pl|=(c&0x7f)<<s; s+=7; }while(c&0x80);
  if(pl<=0||pl>200||i+pl>b.length) return null;
  var out=''; for(var k=0;k<pl;k++) out+=String.fromCharCode(b[i+k]);
  return out;
}
function buildObserveAppListResponse(seq){
  var APPS=BUNDLE?BUNDLE.apps.slice(0,BUNDLE_APP_CAP):MOCK_APPS.slice(0,APP_LIMIT);
  var kdi=[];APPS.forEach(function(a){var it=field(0x0a,utf8(a[0])).concat(field(0x12,utf8(a[1])));kdi=kdi.concat(field(0x0a,it));});
  log('APPS','advertising '+APPS.length+' apps ('+(BUNDLE?'FROM THE PHONE':'hand-typed MOCK_APPS, limit='+APP_LIMIT)+')');
  var kdn=field(0x0a,kdi),kdf=field(0x0a,kdn),payld=field(0x1a,kdf);
  return field(0x0a,field(0x12,[0x08].concat(varint(seq)).concat(payld)));
}
var PNG_1x1=hexToBytes('89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4890000000d49444154789c62000100000500010d0a2db40000000049454e44ae426082');
function buildGetIconResponse(seq,png){
  var kdq=field(0x0a,png||PNG_1x1).concat([0x10,0x00]);   // kdq {1: png bytes, 2: isAdaptive=false (already masked on the phone)}
  // UNARY reply => the result goes in wmu FIELD 2 (0x12) as wnb{1: kdq}. FIELD 3 (0x1a) is the
  // streaming slot: lqa.c (the client's unary handler) requires `wmu.c == 2` and throws "Failed
  // requirement" for anything else, so every icon reply sent as 0x1a was discarded and the tiles stayed
  // grey — even the earlier 1x1 placeholders. Same framing as buildEstablishResponse (kdl).
  return field(0x0a,field(0x12,[0x08].concat(varint(seq)).concat(field(0x12,field(0x0a,kdq)))));
}
// kdl_A: minimal valid response so GBOS receiver proceeds to accept() on its own ports.
// Note: kdl.f (token) must be non-empty and ports > 0 or kov.a throws "invalid response".
function buildEstablishResponse(seq, tcpPort, udpPort, tokenStr, ipStr){
  var tok = tokenStr || 'valid_token';
  var ip = ipStr || MEDIA_IP;
  var kdl=field(0x0a,utf8(ip))
    .concat([0x10]).concat(varint(tcpPort || 1))
    .concat([0x18]).concat(varint(udpPort || 2))
    .concat(field(0x22,utf8(tok)))
    .concat(field(0x2a,utf8('video/avc')));
  return field(0x0a,field(0x12,[0x08].concat(varint(seq)).concat(field(0x12,field(0x0a,kdl)))));
}
// parse GBOS receiver's kdk_A: its ktf(TCP, f7/0x3a) + kty(UDP, f8/0x42) listen ports + 32B keys.
function extractCandidatePorts(b){
  function portAfter(tag){for(var i=0;i<b.length;i++){if(b[i]===tag){for(var j=i;j<b.length&&j<i+40;j++){if(b[j]===0x18){var k=j+1,p=0,s=0,x;do{x=b[k++];p|=(x&0x7f)<<s;s+=7;}while(x&0x80);return p;}}}}return 0;}
  return {tcp:portAfter(0x3a),udp:portAfter(0x42)};
}
function extractKdkKeys(b){var h=bytesToHex(b),k=[],i=0;while(k.length<2){var p=h.indexOf('2220',i);if(p===-1)break;k.push(h.substr(p+4,64));i=p+4+64;}return k;}
// extract kdk_A's RAW inner kdk bytes (the 0x12 payload after "EstablishOpenScreenConnection"),
// so kdk_B mirrors the receiver's real endpoints EXACTLY (incl. field2 = real RTP port, not our 12345).
function extractRawKdk(bytes){
  var h=bytesToHex(bytes);
  var marker='45737461626c6973684f70656e53637265656e436f6e6e656374696f6e'; // "EstablishOpenScreenConnection"
  var mi=h.indexOf(marker); if(mi===-1) return null;
  var i=(mi/2)+(marker.length/2);              // byte index just past the method name
  while(i<bytes.length && bytes[i]!==0x12) i++; // next 0x12 field = the kdk
  if(bytes[i]!==0x12) return null;
  i++;
  var len=0,s=0,c; do{ c=bytes[i++]; len|=(c&0x7f)<<s; s+=7; }while(c&0x80);
  return bytes.slice(i, i+len);
}

// ---------- channel B (we = receiver, GBOS = sender) request builders -----
function buildCandidate(tag,type,ip,port,keyArr){
  var inner=[0x08].concat(varint(type)).concat(field(0x12,utf8(ip))).concat([0x18]).concat(varint(port)).concat(field(0x22,keyArr));
  return field(tag,inner);
}
function buildKdk(tcpPort,udpPort,key1,key2){
  var k=field(0x0a,utf8(MEDIA_IP));
  k=k.concat([0x10]).concat(varint(SESSION_ID));
  k=k.concat([0x18]).concat(varint(DISP_W));
  k=k.concat([0x20]).concat(varint(DISP_H));
  k=k.concat([0x28]).concat(varint(DISP_DPI));
  k=k.concat(field(0x32,utf8('video/avc')));                    // f6 video codecs
  k=k.concat(buildCandidate(0x3a,2,MEDIA_IP,tcpPort,key1));      // f7 TCP -> connect target = receiver's ktf
  k=k.concat(buildCandidate(0x42,1,MEDIA_IP,udpPort,key2));      // f8 UDP -> receiver's kty
  // NO f9 audio: 'audio/raw' is PCM (no decoder) -> receiver's jxc MediaCodec.configure throws
  // IllegalArgumentException ("Failed to start audio decoder/track") and aborts the session.
  // GBOS's real kdk_A carried no audio field either; video-only lets the session proceed.
  return k;
}
function buildEstablishRequest(seq,kdk){
  var inner=field(0x0a,utf8('EstablishOpenScreenConnection')).concat(field(0x12,kdk));
  var wms=[0x08].concat(varint(seq)).concat(field(0x12,utf8('AppCastService'))).concat(field(0x1a,inner));
  return field(0x0a,field(0x0a,wms));
}

// ---------- RPC classify --------------------------------------------------
var METHODS={'4f6273657276654170704c697374':'ObserveAppList','476574417070436173744c697374':'GetAppList',
  '476574416461707469766549636f6e':'GetAdaptiveIcon','47657449636f6e':'GetIcon',
  '45737461626c6973684f70656e53637265656e436f6e6e656374696f6e':'EstablishOpenScreenConnection'};
function parseRpc(bytes){
  var h=bytesToHex(bytes);if(h.indexOf('41707043617374536572766963')===-1)return null;
  var method=null;for(var k in METHODS){if(h.indexOf(k)!==-1){method=METHODS[k];break;}}
  if(!method)return null;
  var i=0;function skip(tag){if(bytes[i]!==tag)return false;i++;var b;do{b=bytes[i++];}while(b&0x80);return true;}
  var seq=0;if(skip(0x0a)&&skip(0x0a)&&bytes[i]===0x08){i++;var s=0,b;do{b=bytes[i++];seq|=(b&0x7f)<<s;s+=7;}while(b&0x80);}
  return {method:method,seq:seq};
}

// ---------- main ----------------------------------------------------------
Java.perform(function(){
  log('INIT','LOOPBACK bridge loading — GBOS will cast its own app to its own AppCast window.');
  var ballastRef=null;

  // Crash #3 real root cause: ART's concurrent mark-compact GC moves objects on its
  // own thread while frida is mid-CallVoidMethod -> frida's cached pointer dangles ->
  // SIGSEGV at 0x10 in InvokeVirtualOrInterfaceWithVarArgs. Main-thread hopping shrank
  // but didn't close the window (a concurrent collector moves regardless of our thread).
  // So starve the collector: drop target heap utilization (ART keeps a big free margin)
  // and push the growth limit up, so no GC/compaction runs during our ~1s handshake.
  try{
    var VMRuntime=Java.use('dalvik.system.VMRuntime');
    var rt=VMRuntime.getRuntime();
    try{ rt.setTargetHeapUtilization(0.1); }catch(e){ log('WARN','setTargetHeapUtilization: '+e); }
    try{ rt.clearGrowthLimit(); log('GC','clearGrowthLimit() — heap may grow to full max'); }catch(e){ log('WARN','clearGrowthLimit: '+e); }
    // (ballast removed: allocating a large array itself forces the compaction we want to avoid.)
  }catch(e){ log('WARN','VMRuntime tune failed: '+e); }

  // NOTE on deopt: Java.deoptimizeEverything() was tried to dodge a GC-vs-trampoline
  // crash, but that crash only happened for Java calls made on frida's JS timer thread.
  // All our calls now run on the main thread (withLmb -> scheduleOnMainThread), so that
  // crash can't recur — and deopt forces this app's many threads into the interpreter,
  // which balloons allocation/GC frequency and WIDENS the CMC-GC race that was killing
  // the main-thread CallVoidMethod. So we deliberately do NOT deopt. (Re-enable only if
  // the frida-thread trampoline crash returns.)

  var WMK_A=hexToBytes(WMK_HEX.replace(bytesToHex(utf8(OLD_UUID)),bytesToHex(utf8(UUID_A))));
  var WMK_B=hexToBytes(WMK_HEX.replace(bytesToHex(utf8(OLD_UUID)),bytesToHex(utf8(UUID_B))));
  var ACK=hexToBytes(ACK_HEX), OBS=hexToBytes(OBSERVE_REQ_HEX);
  var WNC=buildWnc(['MultideviceSyncSetupService','AppCastService']);
  var lmbInst=null, handled={}, bridged=false, LlzRef=null;

  // Post-bridge: strip the llz handshake hook + drop retained lmb refs.
  // We keep JwiRef and MediaCodec hooks live because the receiver builds (and may
  // recreate) its MediaCodec lazily on keyframes via jys.n -> jwi.f/g / createDecoderByType.
  // Stripping them left jys.n failing with NAME_NOT_FOUND and a black window.
  function detachForMediaPlane(){
    try{
      if(LlzRef){ LlzRef.a.overloads.forEach(function(ov){ try{ ov.implementation=null; }catch(e){} }); LlzRef=null; }
      lmbInst=null;
      log('DETACH','reverted llz hook + dropped lmb ref (codec hooks kept live for decoder creation/recreation).');
    }catch(e){ log('WARN','detachForMediaPlane: '+e); }
  }

  // Crash #3 fix: run EVERY Java call to lmb on the app's MAIN thread, not frida's
  // JS timer thread. The timer thread is not a managed Java thread with a walkable
  // stack, so when ART's MarkCompact GC moves the byte[] we just built (or the lmb
  // receiver) mid-call, frida's cached pointer dangles -> SIGSEGV at 0x10 inside
  // JNI::CallVoidMethod/InvokeVirtualOrInterfaceWithVarArgs (seen Thread-2, two runs).
  // scheduleOnMainThread gives GC a proper frame to root-visit. The action builds its
  // own jbytes INSIDE this callback so arg + call live on the same main-thread frame.
  // ---- icon reply throttle -----------------------------------------------------------------
  // MEASURED 2026-11-01: answering every GetAdaptiveIcon instantly made GBOS keep asking (43 in ~1 s)
  // and it never sent EstablishOpenScreenConnection — the cast window timed out. Left unanswered it
  // asks ~6 and establishes normally. GBOS STORES icons permanently and only requests ones it lacks,
  // so we answer at most ICON_MAX per session, ICON_GAP_MS apart; the rest fill in on later sessions.
  // Tunable via FEED_OPTS {iconMax, iconGapMs} (e.g. --opts / GB_OPTS).
  var _fo=globalThis.FEED_OPTS||{};
  var ICON_MAX=(_fo.iconMax!==undefined)?(_fo.iconMax|0):10;
  var ICON_GAP_MS=(_fo.iconGapMs!==undefined)?(_fo.iconGapMs|0):300;
  var iconQ=[], iconQueued=0, iconSent=0, iconPumping=false;
  function queueIcon(seq,pkg,resp){
    if(iconQueued>=ICON_MAX){ log('ICON','cap '+ICON_MAX+' reached — leaving '+pkg+' (seq '+seq+') unanswered for a later session'); return; }
    iconQueued++; iconQ.push({seq:seq,pkg:pkg,resp:resp});
    if(!iconPumping){ iconPumping=true; setTimeout(pumpIcon, ICON_GAP_MS); }
  }
  function pumpIcon(){
    var it=iconQ.shift();
    if(!it){ iconPumping=false; return; }
    withLmb(function(lmb){
      try{ lmb.d(DEV_A,jbytes(it.resp)); iconSent++; log('ICON','answered seq='+it.seq+' '+it.pkg+' ('+iconSent+'/'+ICON_MAX+')'); }
      catch(e){ log('WARN','icon reply '+it.pkg+': '+e); }
    });
    setTimeout(pumpIcon, ICON_GAP_MS);
  }
  function withLmb(action){
    Java.scheduleOnMainThread(function(){
      try{
        if(!lmbInst){
          Java.choose(PKG+N_LINK,{
            onMatch:function(x){ try{ lmbInst=Java.retain(x); }catch(e){ lmbInst=x; } return 'stop'; },
            onComplete:function(){}
          });
        }
        if(!lmbInst){ log('WARN','withLmb: lmb unavailable on main thread'); return; }
        action(lmbInst);
      }catch(e){ log('WARN','withLmb main-thread action error: '+e); }
    });
  }

  // AssociationInfo profile override is handled cleanly in system_server (cdm_profile_fix.js).
  // Do not hook AssociationInfo in the app process as modifying boot-framework.art triggers GC corruption.

  // codec negotiation — REQUIRED on the sender: kry.a calls jwi.h(kdk.field6) during
  // EstablishOpenScreenConnection; if it returns null the sender throws "No mutually
  // supported video codec found" and the whole establish fails (that was the kry.a:585
  // blocker). Real jwi.h enumerates codecs via the natively-EMPTY Waydroid MediaCodecList
  // → null. So we must force jwi.h→video/avc. BUT a jwi hook that ALLOCATES per call
  // (ArrayList/jstring) on GBOS's codec thread, concurrent with our main-thread lmb.d,
  // triggers the mark-compact GC mid-call → crash #3's first trigger. FIX = allocation-free:
  // build the return values ONCE as retained globals and return the same instances every
  // call (no per-call allocation). detachForMediaPlane() reverts these after the bridge.
  function extractCandidateIp(b){
    if(!b||b.length<3 || b[0]!==0x0a) return null;
    var len=b[1]&0x7f; if(len<=0||len>45||2+len>b.length) return null;
    var s=''; for(var i=0;i<len;i++) s+=String.fromCharCode(b[2+i]&0xff);
    return s;
  }
  function completeReceiverMediaHandshake(ip, cands, keys){
    try{
      var targetIp = ip || '127.0.0.1';
      var gx = Module.getGlobalExportByName;
      var socketFn = new NativeFunction(gx('socket'), 'int', ['int', 'int', 'int']);
      var connectFn = new NativeFunction(gx('connect'), 'int', ['int', 'pointer', 'int']);
      var sendtoFn = new NativeFunction(gx('sendto'), 'int', ['int', 'pointer', 'int', 'int', 'pointer', 'int']);
      var inet_addrFn = new NativeFunction(gx('inet_addr'), 'uint32', ['pointer']);
      var fcntlFn = new NativeFunction(gx('fcntl'), 'int', ['int', 'int', 'int']);
      var mkAddr = function(host, port){
        var a = Memory.alloc(16);
        for(var i=0;i<16;i++) a.add(i).writeU8(0);
        a.writeU16(2); // AF_INET
        a.add(2).writeU8((port>>8)&0xff);
        a.add(3).writeU8(port&0xff);
        a.add(4).writeU32(inet_addrFn(Memory.allocUtf8String(host)));
        return a;
      };
      // 1. TCP connect to receiver's ktf listen port -> completes ktf.b() immediately
      if(cands && cands.tcp > 0){
        var tfd = socketFn(2, 1, 0); // AF_INET, SOCK_STREAM
        if(tfd >= 0){
          fcntlFn(tfd, 4, 0x800); // F_SETFL, O_NONBLOCK
          var rc = connectFn(tfd, mkAddr(targetIp, cands.tcp), 16);
          globalThis.__GB_DUMMY_TCP_FD = tfd;
          log('HANDSHAKE', 'TCP connected to receiver ktf ' + targetIp + ':' + cands.tcp + ' (fd=' + tfd + ', rc=' + rc + ')');
        }
      }
      // 2. UDP encrypted "UDP_EMISSARY" packet to receiver's kty port -> completes kts.b() immediately
      if(cands && cands.udp > 0 && keys && keys.length >= 2 && keys[1]){
        var ufd = socketFn(2, 2, 0); // AF_INET, SOCK_DGRAM
        if(ufd >= 0){
          var Cipher = Java.use('javax.crypto.Cipher');
          var SecretKeySpec = Java.use('javax.crypto.spec.SecretKeySpec');
          var GCMParameterSpec = Java.use('javax.crypto.spec.GCMParameterSpec');
          var SecureRandom = Java.use('java.security.SecureRandom');
          var sr = SecureRandom.$new();
          var ivJ = Java.array('byte', [0,0,0,0,0,0,0,0,0,0,0,0]);
          sr.nextBytes(ivJ);
          var iv = []; for(var j=0;j<12;j++) iv.push(ivJ[j]&0xff);
          var keyBytes = hexToBytes(keys[1]);
          var seqBytes = [0,0,0,0,0,0,0,0];
          var aad = seqBytes.concat([0x01]); // dir=0x01 (sender -> receiver)
          var c = Cipher.getInstance('AES/GCM/NoPadding');
          c.init(1, SecretKeySpec.$new(jbytes(keyBytes), 'AES'), GCMParameterSpec.$new(128, ivJ));
          c.updateAAD(jbytes(aad));
          var ctJ = c.doFinal(jbytes(utf8('UDP_EMISSARY')));
          var ct = []; for(var m=0;m<ctJ.length;m++) ct.push(ctJ[m]&0xff);
          var pkt = seqBytes.concat(iv).concat(ct);
          var ob = Memory.alloc(pkt.length);
          for(var z=0;z<pkt.length;z++) ob.add(z).writeU8(pkt[z]&0xff);
          var uaddr = mkAddr(targetIp, cands.udp);
          var sent = sendtoFn(ufd, ob, pkt.length, 0, uaddr, 16);
          globalThis.__GB_DUMMY_UDP_FD = ufd;
          log('HANDSHAKE', 'UDP_EMISSARY sent to receiver kty ' + targetIp + ':' + cands.udp + ' (' + sent + 'B, fd=' + ufd + ')');
        }
      }
    }catch(e){
      log('WARN', 'completeReceiverMediaHandshake: ' + e);
    }
  }

  var JwiRef=null;
  try {
    JwiRef = Java.use(PKG+N_CODECS);
    // Do NOT hook jwi.a on bare-metal Android 17 hardware: jwi.a(false) natively returns
    // [video/x-vnd.on2.vp9, video/avc, video/hevc, video/x-vnd.on2.vp8, video/av01] and
    // jwi.a(true) natively returns [video/avc]. Hooking jwi.a with a Java.retain(ArrayList)
    // wrapper fails Frida's java.util.List return-type check after GC ("Implementation for a
    // expected return value compatible with java.util.List") and throws NullPointerException in kov.a.
    // Also detect whether native hardware-accelerated c2.ffmpeg.h264.decoder is present in MediaCodecList:
    // on bare-metal ruby, c2.android.avc.decoder (in mediaswcodec's minijail) crashes with SIGSYS when
    // libgallium_dri.so calls sched_setaffinity, whereas c2.ffmpeg.h264.decoder runs in
    // android.hardware.media.c2-ffmpeg-service without minijail!
    var nativeDecs = null;
    try { nativeDecs = JwiRef.a(false); } catch(e) {}
    var hasNativeAvc = false;
    try { hasNativeAvc = (nativeDecs && nativeDecs.size() > 0); } catch(e) {}
    var prefDecName = 'c2.android.avc.decoder';
    try {
      var hwDec = JwiRef[N_CODECS_DECNAME]('video/avc', false, false);
      if (hwDec && ('' + hwDec).length > 0) prefDecName = '' + hwDec;
    } catch(e) {}
    globalThis.__GB_PREF_AVC_DEC = prefDecName;
    if (!hasNativeAvc) {
      var ArrayListCls = Java.use('java.util.ArrayList');
      JwiRef.a.implementation = function(isEncoder) {
        var l = ArrayListCls.$new();
        l.add('video/avc');
        return l;
      };
      log('HOOK', N_CODECS+'.a -> fresh ArrayList([video/avc]) (empty native MediaCodecList fallback)');
    } else {
      log('HOOK', 'jwi.a left NATIVE (' + nativeDecs.size() + ' decoders present; skipping java.util.List hook)');
    }
    JwiRef[N_CODECS_MIME].overload('java.util.List').implementation = function(list) { return 'video/avc'; };
    JwiRef[N_CODECS_DECNAME].overload('java.lang.String', 'boolean', 'boolean').implementation = function(mime, isEncoder, isHardware) { return isEncoder ? 'c2.android.avc.encoder' : prefDecName; };
    JwiRef[N_CODECS_NAME].overload('java.lang.String', 'boolean').implementation = function(mime, isEncoder) { return isEncoder ? 'c2.android.avc.encoder' : prefDecName; };
    log('HOOK', N_CODECS + ' {' + [N_CODECS_MIME, N_CODECS_DECNAME, N_CODECS_NAME].join(',') + '} -> plain JS strings video/avc, dec=' + prefDecName + ', enc=c2.android.avc.encoder');
  } catch(e) { log('WARN', 'jwi hooks: ' + e); }

  // Direct-launch shortcut: once kry.a has built the sender's VirtualDisplay (via krr/kbl/kbk->kan),
  // find the live kan and launch Settings straight onto its display — exactly what kan.e does
  // (startActivity + ActivityOptions.setLaunchDisplayId) — using kan's own context (field y) so the
  // FLAG_OWN_CONTENT_ONLY virtual display accepts it. The encoder is already capturing that display,
  // so its frames flow over the (loopback) OpenScreen link to the receiver -> pixels. Bypasses the
  // receiver->sender uit launch protocol entirely.
  var launchedApp=false;
  function launchSettingsOnDisplay(retries){
    if(launchedApp)return;
    log('KAN','launch attempt (retries='+retries+')');
    var inst=null;
    try{ Java.choose(PKG+N_DISPLAY,{ onMatch:function(x){ inst=x; return 'stop'; }, onComplete:function(){} }); }
    catch(e){ log('WARN','kan choose: '+e); }
    if(!inst){
      if(retries>0){ setTimeout(function(){ Java.perform(function(){ launchSettingsOnDisplay(retries-1); }); },1500); }
      else log('WARN','no live kan found (gave up)');
      return;
    }
    var displayId=null, ctxG=null;
    try{
      // read the VirtualDisplay + Context fields BY TYPE via reflection (obfuscation-proof)
      var VD=Java.use('android.hardware.display.VirtualDisplay');
      var Ctx=Java.use('android.content.Context');
      var fields=inst.getClass().getDeclaredFields();
      var vd=null, ctx=null;
      for(var i=0;i<fields.length;i++){
        var f=fields[i], tn=''+f.getType().getName();
        try{
          if(tn==='android.hardware.display.VirtualDisplay'){ f.setAccessible(true); vd=f.get(inst); }
          else if(tn==='android.content.Context' && !ctx){ f.setAccessible(true); ctx=f.get(inst); }
        }catch(e){}
      }
      if(!vd){ log('WARN','no VirtualDisplay field on kan'); return; }
      displayId=Java.cast(vd, VD).getDisplay().getDisplayId();
      if(!ctx){ ctx=Java.use('android.app.ActivityThread').currentApplication().getApplicationContext(); }
      ctxG=Java.retain(Java.cast(ctx, Ctx));
      log('KAN','found kan -> display '+displayId+'; launching Settings on main thread');
    }catch(e){ log('WARN','kan read: '+e); return; }
    Java.scheduleOnMainThread(function(){
      try{
        var intent=ctxG.getPackageManager().getLaunchIntentForPackage('com.android.settings');
        if(!intent){ log('WARN','no settings launch intent'); return; }
        intent.setFlags(872415232);  // same flags kan.e uses
        var opts=Java.use('android.app.ActivityOptions').makeBasic();
        opts.setLaunchDisplayId(displayId);
        ctxG.startActivity(intent, opts.toBundle());
        launchedApp=true;
        log('LAUNCH','startActivity(com.android.settings) on VirtualDisplay '+displayId+' — encoder captures it -> frames to receiver.');
      }catch(e){ log('WARN','startActivity: '+e); }
    });
  }

  var kdkBSeq=7;
  function sendKdkB(rawKdk,cands,keys){
    if(RECEIVER_ONLY){
      log('BRIDGE','RECEIVER_ONLY: skipping sender bridge (no kdk_B). JniOpenScreenReceiver is up; feed_receiver.js injects directly into onNativeFrameReceived.');
      return;
    }
    // mirror kdk_A's EXACT bytes (real receiver endpoints incl. field2 RTP port); fall back to rebuild
    var kdk = rawKdk && rawKdk.length ? rawKdk : buildKdk(cands.tcp,cands.udp,hexToBytes(keys[0]||''),hexToBytes(keys[1]||''));
    var req=buildEstablishRequest(kdkBSeq++,kdk);
    log('BRIDGE','>>> sending kdk_B to GBOS SENDER ('+(rawKdk&&rawKdk.length?'MIRRORED kdk_A '+rawKdk.length+'B':'rebuilt')+'): receiver '+MEDIA_IP+' tcp='+cands.tcp+' udp='+cands.udp+' key1='+(keys[0]||'').slice(0,16)+'…');
    withLmb(function(lmb){
      lmb.d(DEV_B,jbytes(req));
      log('BRIDGE','kdk_B sent. GBOS sender should now connect to its own receiver and stream. Watch the AppCast window + logcat "First frame rendered on Surface!"');
    });
  }

  // ---- llz.a observation hook: ARMED ONLY AROUND A CAST REQUEST ----------------
  // Measured 2026-10-30 (19-run bisect): a frida replacement left on llz.a across the
  // first ART mark-compact makes MarkRoots scan a root whose class pointer is dead
  // (MarkCompact::ReloadScanObjClass -> LogHeapCorruption -> BFSFindReachable, fault
  // addr 0x10) and the process dies at t=11-12s. It does NOT have to be called: the
  // hook never fired (0 hits) in any run, install-and-revert-after-3s survived 60s, and
  // install-and-leave died with zero frames and zero traffic. Disabling moving GC hides
  // it — which is what the old guard did, at the cost of the ~11-minute OOM.
  // So: armed for the setup handshake, DISARMED the moment it completes, re-armed only
  // when the cast grid opens (the only UI that can emit Establish), and disarmed again
  // once kdl_A is answered / kdk_B bridged. No frida-Java hook stays resident across a GC.
  var Llz=null, llzAttached=false, llzArmTimer=null;
  function makeLlzImpl(ov){
    return function(obj,cont){
        try{
          var dev=''+((this.b&&this.b.value!==undefined)?this.b.value:(this._b?this._b.value:''));
          var arr=[];
          if(obj){ var A=Java.use('java.lang.reflect.Array'),n=A.getLength(obj); for(var i=0;i<n;i++)arr.push(A.getByte(obj,i)&0xff); }
          if(arr.length){
            var onA=dev.indexOf(DEV_A)!==-1, onB=dev.indexOf(DEV_B)!==-1;
            if(onA){
              var rpc=parseRpc(arr);
              if(rpc && !handled[rpc.method+':'+rpc.seq]){
                handled[rpc.method+':'+rpc.seq]=true;
                log('A<out',rpc.method+' seq='+rpc.seq);
                var resp=null;
                if(rpc.method==='ObserveAppList'||rpc.method==='GetAppList') resp=buildObserveAppListResponse(rpc.seq);
                else if(rpc.method==='GetAdaptiveIcon'||rpc.method==='GetIcon'){
                  var ipkg=null; try{ ipkg=extractIconPkg(arr); }catch(e){}
                  var ib64=(BUNDLE&&BUNDLE.icons&&ipkg)?BUNDLE.icons[ipkg]:null;
                  log('ICON','request seq='+rpc.seq+' pkg='+ipkg+(ib64?' -> real icon from the phone (queued)':' -> not answered (no phone icon)'));
                  // Throttled (see queueIcon): never set resp here — an instant reply per request is
                  // exactly what stalled the cast. The 1x1 placeholder is no longer sent at all.
                  if(ib64) queueIcon(rpc.seq, ipkg, buildGetIconResponse(rpc.seq,b64decode(ib64)));
                }
                else if(rpc.method==='EstablishOpenScreenConnection'){
                  var cands=extractCandidatePorts(arr), keys=extractKdkKeys(arr);
                  var rawKdk=extractRawKdk(arr);
                  var candIp=extractCandidateIp(rawKdk) || MEDIA_IP;
                  log('CAPTURE','GBOS receiver kdk_A: ip='+candIp+' tcp='+cands.tcp+' udp='+cands.udp+' keys='+keys.length+' rawKdk='+(rawKdk?rawKdk.length+'B':'null'));
                  var estResp=buildEstablishResponse(rpc.seq, cands.tcp, cands.udp, keys[0], candIp); // answer kdl_A -> receiver accept()s
                  setTimeout(function(){
                    Java.perform(function(){
                      try{
                        log('A>in','kdl_A answered — receiver now listening on its ports');
                        withLmb(function(lmb){
                          lmb.d(DEV_A,jbytes(estResp));
                          // Immediately complete the receiver's ktf(TCP) + kts(UDP_EMISSARY) transport
                          // handshake so kov.a instantiates jxj / JniOpenScreenReceiver right away,
                          // even if DEV_B (kry sender) is disabled ("Invalid service" on bare-metal).
                          setTimeout(function(){ Java.perform(function(){ completeReceiverMediaHandshake(candIp, cands, keys); }); }, 150);
                          // Then bridge to sender (mirror kdk_A) and strip before the media plane
                          // churns the heap. The strip must wait for the SENDER to finish establishing:
                          // kry.a calls jwi.h() for codec negotiation, so revoking jwi at +600ms makes
                          // establish fail with "No mutually supported video codec found" (measured).
                          // GC stays gated across that window and is released only after the strip.
                          setTimeout(function(){Java.perform(function(){
                            sendKdkB(rawKdk,cands,keys);
                            if(llzBootTimer){ clearTimeout(llzBootTimer); llzBootTimer=null; }
                            setTimeout(function(){ Java.perform(function(){
                              // STRIP TIMING (2026-10-30): the receiver builds its MediaCodec lazily on the
                              // FIRST frame (jys.n -> MediaCodec.createDecoderByType), and that call only
                              // resolves GBOS's obfuscated codec name while the jwi/create*ByType hooks are
                              // live. Stripping a fixed 4 s after the bridge (before any frame exists) left
                              // every decoder creation failing:
                              //   [jys] Failed to start MediaCodec video decoder
                              //   IllegalArgumentException: Failed to initialize 424231aa… (NAME_NOT_FOUND)
                              // -> permanently black App Cast window with a perfectly healthy transport.
                              // So wait for feed_live's first fed frame (it sets globalThis.__GB_FED), then
                              // strip immediately: that keeps the GC-risky Java hooks out of the steady-state
                              // media plane while still letting the decoder come up.
                              var strip=function(tag){
                                try{
                                  detachLlz('kdk_B bridged — link handshake done', true);
                                  detachForMediaPlane();
                                }catch(e){ log('WARN','strip after establish: '+e); }
                                resumeGc(tag);
                              };
                              var waited=0, CAP=120000;
                              var waitDec=function(){
                                if((globalThis.__GB_FED||0)>0){ log('LIVE','first frame fed — llz handshake stripped, GC resumed'); strip('media plane up — first frame fed, GC resumed'); return; }
                                waited+=500;
                                if(waited>=CAP){ log('WARN','no frame in '+(CAP/1000)+'s — stripping llz anyway'); strip('media plane idle — llz stripped on timeout'); return; }
                                setTimeout(waitDec,500);
                              };
                              setTimeout(waitDec,500);
                            }); }, 4000);
                          });},600);
                        });
                      }catch(e){log('WARN','kdl_A dispatch: '+e);}
                    });
                  },50);
                  return ov.call(this,obj,cont);
                }
                if(resp){
                  var rMethod=rpc.method, respData=resp;
                  log('A>in','reply '+rMethod);
                  setTimeout(function(){
                    Java.perform(function(){
                      try{
                        withLmb(function(lmb){
                          lmb.d(DEV_A,jbytes(respData));
                        });
                      }catch(e){log('WARN','lmb.d reply '+rMethod+': '+e);}
                    });
                  },50);
                }
              }
            } else if(onB){
              log('B<out','['+arr.length+'B] '+bytesToHex(arr.slice(0,40))+(arr.length>40?'…':''));
            }
          }
        }catch(e){log('WARN','llz.a: '+e);}
        return ov.call(this,obj,cont);
    };
  }
  // ---- GC gate ------------------------------------------------------------------
  // While a frida Java replacement is installed, ANY collection is dangerous here:
  //  - compaction with the hook resident -> MarkRoots scans a dead class pointer
  //    ("klass pointer for obj ... found to be 0");
  //  - a GC checkpoint during one of our cached-handle calls -> the object moves under
  //    us -> SIGSEGV at 0x10 in InvokeVirtualOrInterfaceWithVarArgs (crash #3).
  // So the gate is tied to the arm window: paused while armed, released on disarm. The
  // window is seconds long, so the skipped collection is worth a few MB - and unlike the
  // old always-on guard, GC is fully live for the rest of the session (no 11-min OOM).
  var HEAP_OFF=0x200, COUNT_OFF=0x428, gcCountPtr=null, gcPaused=false;   // Art::Runtime::heap_ , Heap::disable_moving_gc_count_
  function gcCounter(){
    if(gcCountPtr) return gcCountPtr;
    try{
      var sym=null;
      try{ sym=Module.getGlobalExportByName('_ZN3art7Runtime9instance_E'); }catch(e){}
      if(!sym){
        var art=null;
        try{ art=Process.getModuleByName('libart.so'); }catch(e){}
        if(!art){ try{ Process.enumerateModules().forEach(function(m){ if(!art && /libart\.so$/.test(m.path)) art=m; }); }catch(e){} }
        if(!art) return null;
        try{ sym=art.findExportByName('_ZN3art7Runtime9instance_E'); }catch(e){}
        if(!sym){ try{ art.enumerateSymbols().forEach(function(s){ if(!sym && s.name==='_ZN3art7Runtime9instance_E' && !s.address.isNull()) sym=s.address; }); }catch(e){} }
      }
      if(!sym) return null;
      var rt=sym.readPointer(); if(rt.isNull()) return null;
      var heap=rt.add(HEAP_OFF).readPointer(); if(heap.isNull()) return null;
      gcCountPtr=heap.add(COUNT_OFF);
      return gcCountPtr;
    }catch(e){ log('WARN','gcCounter: '+e); return null; }
  }
  function pauseGc(why){
    if(gcPaused) return;
    var p=gcCounter(); if(!p){ log('WARN','GCGATE','unavailable — proceeding without the gate'); return; }
    try{
      var b=p.readU64(); p.writeU64(b.add(1)); gcPaused=true;
      log('GCGATE','PAUSED moving GC count '+b+' -> '+p.readU64()+' — '+why);
    }catch(e){ log('WARN','pauseGc: '+e); }
  }
  function resumeGc(why){
    if(!gcPaused) return;
    var p=gcCounter(); if(!p) return;
    try{
      var b=p.readU64();
      if(b.compare(0)>0) p.writeU64(b.sub(1));
      gcPaused=false;
      log('GCGATE','RESUMED moving GC count '+b+' -> '+p.readU64()+' — '+why);
    }catch(e){ log('WARN','resumeGc: '+e); }
  }

  function attachLlz(why){
    try{
      if(!Llz){ Llz=Java.use(PKG+N_CONNECT); LlzRef=Llz; }
      if(llzAttached){ log('HOOK','llz.a already armed — '+why); return; }
      pauseGc('arming llz.a');
      Llz.a.overloads.forEach(function(ov){ ov.implementation=makeLlzImpl(ov); });
      llzAttached=true;
      log('HOOK','llz.a ARMED (A='+DEV_A+', B='+DEV_B+') — '+why);
    }catch(e){ log('WARN','attachLlz: '+e); resumeGc('arm failed'); }
  }
  function detachLlz(why,keepPaused){
    if(!Llz || !llzAttached) { if(!keepPaused) resumeGc('nothing armed'); return; }
    try{
      Llz.a.overloads.forEach(function(ov){ try{ ov.implementation=null; }catch(e){} });
      llzAttached=false;
      log('DETACH','llz.a DISARMED — '+why+' (no frida Java hook left resident across GC)');
    }catch(e){ log('WARN','detachLlz: '+e); }
    if(!keepPaused) resumeGc('llz.a disarmed');
  }
  // Bounded window: if no click arrives, drop the hook rather than risk a compaction.
  function armLlzWindow(why,ms){
    if(llzArmTimer){ clearTimeout(llzArmTimer); llzArmTimer=null; }
    if(llzBootTimer){ clearTimeout(llzBootTimer); llzBootTimer=null; }
    attachLlz(why);
    var w=ms||6000;
    llzArmTimer=setTimeout(function(){ llzArmTimer=null; detachLlz('arm window expired ('+w+'ms)'); }, w);
  }

  // The arm window is BOUNDED: if no Establish arrives the hook is dropped rather than
  // left resident. Tests on 2026-10-30 also showed a re-arm hook on the grid/AppCast
  // Activity.onCreate is itself unsafe — a replacement live while art::StackVisitor walks
  // that frame dies in CodeInfo::DecodeGcMasksOnly — so the trigger must stay
  // hook-free; raising this timeout is cheaper than re-arming from Java.
  // 45 s, not 10 s: the host arms this at load and only launches the App Cast window once the
  // synthetic channels report READY, and a slow `adb am start` used to land outside a 10 s window.
  // A missed Establish is not an error the host can see — it just renders 0 frames forever — and
  // the workaround (re-arming llz.a after a first disarm) was measured to abort the process in the
  // establish path, so the window is made long enough to cover the launch instead.
  attachLlz('startup — waiting for Establish');
  var llzBootTimer=setTimeout(function(){ llzBootTimer=null; detachLlz('armed 45s with no Establish'); }, 45000);

  function findLmb(retries){
    Java.choose(PKG+N_LINK,{
      onMatch:function(x){
        try{
          if(!lmbInst) lmbInst=Java.retain(x);
        }catch(e){
          if(!lmbInst) lmbInst=x;
        }
        return 'stop';
      },
      onComplete:function(){
        if(!lmbInst){
          if(retries>0){
            if(retries===20 || retries===15){
              try{
                var Intent=Java.use('android.content.Intent');
                var ComponentName=Java.use('android.content.ComponentName');
                var ActivityThread=Java.use('android.app.ActivityThread');
                var ctx=ActivityThread.currentApplication().getApplicationContext();
                var intent=Intent.$new();
                intent.setComponent(ComponentName.$new('com.google.ambient.streaming','com.google.ambient.streaming.multidevicesync.devicelink.ipc.DeviceLinkService'));
                ctx.startService(intent);
                log('STEP','Sent startService intent for DeviceLinkService');
              }catch(e){log('WARN','startService: '+e);}
            }
            setTimeout(function(){Java.perform(function(){findLmb(retries-1);});},1000);return;
          }
          log('FATAL','no lmb');return;
        }
        log('OK','found lmb: '+lmbInst);
        var step=function(n,f,d){setTimeout(function(){Java.perform(function(){try{f();}catch(e){log('WARN',n+': '+e);}});},d);};
        // bring up BOTH channels; channel B (sender) primed so kry is ready before the click.
        step('A.open',function(){log('STEP','A openChannel');withLmb(function(lmb){lmb.b(DEV_A,SELF_A);});},200);
        step('B.open',function(){log('STEP','B openChannel');withLmb(function(lmb){lmb.b(DEV_B,SELF_B);});},500);
        step('A.wmk',function(){log('STEP','A wmk');withLmb(function(lmb){lmb.d(DEV_A,jbytes(WMK_A));});},900);
        step('B.wmk',function(){log('STEP','B wmk');withLmb(function(lmb){lmb.d(DEV_B,jbytes(WMK_B));});},1150);
        step('A.ack',function(){log('STEP','A ack');withLmb(function(lmb){lmb.d(DEV_A,jbytes(ACK));});},1400);
        step('B.ack',function(){log('STEP','B ack');withLmb(function(lmb){lmb.d(DEV_B,jbytes(ACK));});},1550);
        step('A.wnc',function(){log('STEP','A wnc (AppCastService) — GBOS receiver will ask ObserveAppList');withLmb(function(lmb){lmb.d(DEV_A,jbytes(WNC));});},1900);
        step('B.observe',function(){log('STEP','B ObserveAppList (prime GBOS sender/kry)');withLmb(function(lmb){lmb.d(DEV_B,jbytes(OBS));});},2300);
        // RE-PRIME after a Cross-Device drop. When the Dtdi link to the phone closes, GBOS cancels every
        // device's setup flow ("[ltl] Setup flow for device=... failed / Job was cancelled", MEASURED
        // 2026-11-03 16:18 and 16:52) and the Phone Hub drawer falls back to "Get started"; nothing asks for the
        // app list again until a new session. The host calls this when the link is back: it replays the same
        // handshake the session start runs, so GBOS asks ObserveAppList again. UNVERIFIED on a live drop.
        globalThis.__GB_LOOPBACK_REPRIME=function(){
          log('STEP','RE-PRIME: replaying the startup handshake after a link drop');
          step('R.A.open',function(){withLmb(function(lmb){lmb.b(DEV_A,SELF_A);});},200);
          step('R.B.open',function(){withLmb(function(lmb){lmb.b(DEV_B,SELF_B);});},500);
          step('R.A.wmk',function(){withLmb(function(lmb){lmb.d(DEV_A,jbytes(WMK_A));});},900);
          step('R.B.wmk',function(){withLmb(function(lmb){lmb.d(DEV_B,jbytes(WMK_B));});},1150);
          step('R.A.ack',function(){withLmb(function(lmb){lmb.d(DEV_A,jbytes(ACK));});},1400);
          step('R.B.ack',function(){withLmb(function(lmb){lmb.d(DEV_B,jbytes(ACK));});},1550);
          step('R.A.wnc',function(){log('STEP','R A wnc — GBOS receiver will ask ObserveAppList again');withLmb(function(lmb){lmb.d(DEV_A,jbytes(WNC));});},1900);
          step('R.B.observe',function(){log('STEP','R B ObserveAppList');withLmb(function(lmb){lmb.d(DEV_B,jbytes(OBS));});},2300);
          return true;
        };
        step('ready',function(){
          // console.log goes to the client's log handler, NOT to scripts' message stream, so the
          // host needs a real send() to sequence its launch against this moment.
          try{ send({gb:'ready'}); }catch(e){}
          log('READY','Both channels up. NOW on GBOS: launch the grid and CLICK Settings:');
          log('READY','  am start --user 10 -n com.google.ambient.streaming/.multidevicesync.appcast.receiver.launcher.RemoteAppsLauncherActivity');
          log('READY','On click, GBOS emits kdk_A -> we bridge it to the sender -> loopback stream -> pixels.');
        },2800);
      }
    });
  }
  findLmb(20);

  // ---- orderly shutdown (the host calls this before unloading the agent) --------------------
  // Everything this script installed is removed in the reverse order of installation, and the
  // moving-GC counter is released, so the agent can be unloaded with no frida code reachable
  // from the app's own call paths. Without this the only way out was killing the client, which
  // unloads the agent under a live process and faults in its destructors (tombstone_42).
  // The host arms this immediately before it launches the App Cast window. The startup window
  // (10 s from load) is too short to cover a slow adb `am start`: a missed Establish means no
  // kdk_A capture, no kdk_B, no sender, and the window opens to 0 frames.
  globalThis.__GB_LOOPBACK_ARM = function(ms){ armLlzWindow('host requested (window launch)', ms||20000); return true; };

  globalThis.__GB_LOOPBACK_SHUTDOWN = function(){
    try{ if(llzArmTimer){ clearTimeout(llzArmTimer); llzArmTimer=null; } }catch(e){}
    try{ if(llzBootTimer){ clearTimeout(llzBootTimer); llzBootTimer=null; } }catch(e){}
    try{ detachLlz('host shutdown', false); }catch(e){}
    try{ if(JwiRef){ ['a',N_CODECS_MIME,N_CODECS_DECNAME,N_CODECS_NAME].forEach(function(m){ try{ JwiRef[m].implementation=null; }catch(e){} }); } }catch(e){}
    try{
      var closeFn = new NativeFunction(Module.getGlobalExportByName('close'), 'int', ['int']);
      if(globalThis.__GB_DUMMY_TCP_FD >= 0){ closeFn(globalThis.__GB_DUMMY_TCP_FD); globalThis.__GB_DUMMY_TCP_FD = -1; }
      if(globalThis.__GB_DUMMY_UDP_FD >= 0){ closeFn(globalThis.__GB_DUMMY_UDP_FD); globalThis.__GB_DUMMY_UDP_FD = -1; }
    }catch(e){}
    try{ lmbInst=null; LlzRef=null; }catch(e){}
    log('SHUTDOWN','loopback: llz/jwi reverted, moving-GC counter released, lmb ref dropped');
    return {gcPaused:gcPaused, bridged:bridged, llzArmed:llzAttached};
  };
});

rpc.exports = {
  shutdown: function(){
    var f=globalThis.__GB_LOOPBACK_SHUTDOWN;
    if(!f) return {skipped:'bridge not initialised yet'};
    return f();
  },
  armCast: function(ms){
    var f=globalThis.__GB_LOOPBACK_ARM;
    if(!f) return {skipped:'bridge not initialised yet'};
    return f(ms||20000);
  }
};
