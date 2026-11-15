/*
 * mediacodec_inject_info.js — make android.media.MediaCodec usable in Waydroid by
 * INJECTING real MediaCodecInfo for c2.android.avc.decoder and c2.android.avc.encoder.
 */

'use strict';

function ts(){return new Date().toISOString().slice(11,23);}
function log(t,m){console.log('['+ts()+']['+t+'] '+m);}
var PS=Process.pointerSize;
// Every entry-point rewrite is recorded so shutdown can undo exactly what was installed.
// Interceptor.revert() in this build takes the patched ADDRESS (revert() with no argument throws
// "missing argument", and passing a listener throws "expected a pointer"), so the addresses are
// what we keep.
var REPLACED=[], LISTENERS=[];

var LIBS=['libstagefright.so','libstagefright_codecbase.so','libmedia_codeclist.so',
          'libsfplugin_ccodec.so','libutils.so','libmedia.so','libc.so'];

var symCache=null;
function buildCache(){
  symCache=[];
  LIBS.forEach(function(l){
    var m; try{ m=Process.getModuleByName(l); }catch(e){ return; }
    try{ m.enumerateExports().forEach(function(s){ if(s.address&&!s.address.isNull()) symCache.push(s); }); }catch(e){}
    try{ m.enumerateSymbols().forEach(function(s){ if(s.address&&!s.address.isNull()) symCache.push(s); }); }catch(e){}
  });
  log('SYMS','indexed '+symCache.length+' symbols across '+LIBS.length+' libs');
}
function sym(sub){
  if(!symCache) buildCache();
  for(var i=0;i<symCache.length;i++){ if(symCache[i].name===sub) return symCache[i].address; }
  for(var i=0;i<symCache.length;i++){ if(symCache[i].name.indexOf(sub)!==-1) return symCache[i].address; }
  return null;
}
function off(lib,offset){ try{ return Process.getModuleByName(lib).base.add(offset); }catch(e){ return null; } }
function nf(a,r,g){ return new NativeFunction(a,r,g); }

// Ensure debug.stagefright.c2inputsurface is set to -1
(function(){
  var sps = sym('__system_property_set');
  if(sps){
    var spsFn = nf(sps, 'int', ['pointer', 'pointer']);
    var k = Memory.allocUtf8String('debug.stagefright.c2inputsurface');
    var v = Memory.allocUtf8String('-1');
    spsFn(k, v);
    log('PROP', 'debug.stagefright.c2inputsurface set to -1');
  }
})();

// ---- 1. RMS stubs --------------------------------------------------------
(function(){
  var gsl=sym('ResourceManagerServiceProxy12getService_l') || off('libstagefright.so',0x146290);
  var rmi=sym('ResourceManagerServiceProxy4init')          || off('libstagefright.so',0x1474c0);
  if(gsl){ Interceptor.replace(gsl, new NativeCallback(function(sret,thiz){ sret.writePointer(ptr(0)); sret.add(PS).writePointer(ptr(0)); return sret; },'pointer',['pointer','pointer'])); REPLACED.push(gsl); log('STUB','getService_l -> null  @'+gsl); }
  else log('WARN','getService_l not found');
  if(rmi){ Interceptor.replace(rmi, new NativeCallback(function(thiz){ return 0; },'int',['pointer'])); REPLACED.push(rmi); log('STUB','Proxy::init -> 0  @'+rmi); }
})();

// ---- 2. MediaResource stubs in libmedia.so (avoids CFI/lazy-link crash) ---
(function(){
  [0x75220, 0x75250, 0x89cf0, 0x89ce0, 0x89cd0, 0x89cc0].forEach(function(o){
    var a = off('libmedia.so', o);
    if(a){
      try{
        Interceptor.replace(a, new NativeCallback(function(sret){
          for(var i=0; i<40; i+=PS) sret.add(i).writePointer(ptr(0));
          return sret;
        }, 'pointer', ['pointer', 'pointer', 'pointer', 'pointer']));
        REPLACED.push(a);
        log('STUB', 'libmedia.so 0x' + o.toString(16) + ' @ ' + a);
      }catch(e){}
    }
  });
})();

// ---- 3. build real MediaCodecInfo for decoder & encoder via writer API ---
var decInfo=null, encInfo=null, ffmpegDecInfo=null, incStrong=null;
(function(){
  var addMcI     = sym('20MediaCodecListWriter17addMediaCodecInfo');
  var writeInfos = sym('20MediaCodecListWriter15writeCodecInfos');
  var setName    = sym('20MediaCodecInfoWriter7setName');
  var setOwner   = sym('20MediaCodecInfoWriter8setOwner');
  var addType    = sym('20MediaCodecInfoWriter12addMediaType');
  var setAttr    = sym('20MediaCodecInfoWriter13setAttributes');
  var incStrongA = sym('7RefBase9incStrong');

  var tbl={addMcI:addMcI,writeInfos:writeInfos,setName:setName,setOwner:setOwner,addType:addType,setAttr:setAttr,incStrong:incStrongA};
  var missing=[];
  Object.keys(tbl).forEach(function(k){ if(!tbl[k]) missing.push(k); });
  if(missing.length){ log('ABORT','missing: '+missing.join(', ')); return; }

  incStrong = nf(incStrongA,'void',['pointer','pointer']);
  var addMcIFn = nf(addMcI,'pointer',['pointer','pointer']);
  var writeFn  = nf(writeInfos,'void',['pointer','pointer']);
  var setNameFn= nf(setName,'void',['pointer','pointer']);
  var setOwnerFn=nf(setOwner,'void',['pointer','pointer']);
  var addTypeFn= nf(addType,'pointer',['pointer','pointer','pointer']);
  var setAttrFn= nf(setAttr,'void',['pointer','int']);

  function makeInfo(name, isEncoder, ownerStr, attrVal){
    var writer=Memory.alloc(128); for(var i=0;i<128;i+=PS) writer.add(i).writePointer(ptr(0));
    var sret=Memory.alloc(PS); sret.writePointer(ptr(0));
    addMcIFn(sret, writer);
    var iw=sret.readPointer();
    setNameFn(iw, Memory.allocUtf8String(name));
    setOwnerFn(iw, Memory.allocUtf8String(ownerStr || 'codec2'));
    var sretCap = Memory.alloc(PS); sretCap.writePointer(ptr(0));
    addTypeFn(sretCap, iw, Memory.allocUtf8String('video/avc'));
    setAttrFn(iw, (attrVal !== undefined) ? attrVal : (isEncoder ? 1 : 0));

    var vec=Memory.alloc(3*PS);
    for(var j=0; j<3*PS; j+=PS) vec.add(j).writePointer(ptr(0));
    writeFn(writer, vec);
    var info = vec.readPointer().readPointer();
    incStrong(info, info);
    log('BUILD', name + ' (isEnc=' + isEncoder + ', owner=' + (ownerStr||'codec2') + ') -> ' + info);
    return info;
  }

  try{
    decInfo = makeInfo('c2.android.avc.decoder', false, 'codec2::software', 0);
    ffmpegDecInfo = makeInfo('c2.ffmpeg.h264.decoder', false, 'codec2::ffmpeg', 0xa);
    encInfo = makeInfo('c2.android.avc.encoder', true, 'codec2::software', 1);
  }catch(e){ log('ERR','building MediaCodecInfo: '+e); }
})();

// ---- 4. hook mGetCodecInfo default lambda & attach MediaCodec::getCodecInfo ---
(function(){
  if(!decInfo || !encInfo){ log('SKIP','missing decInfo/encInfo'); return; }

  var defaultLambda = off('libstagefright.so', 0x14f5a0);
  if(defaultLambda){
    Interceptor.replace(defaultLambda, new NativeCallback(function(thiz, nameAString, outSpPtr){
      var namePtr = nameAString.readPointer();
      var nameStr = namePtr.readCString();
      if(nameStr && (nameStr.indexOf('audio') !== -1 || nameStr.indexOf('opus') !== -1)){
        return -2; // NAME_NOT_FOUND: do not hijack audio codecs with video info
      }
      var isEnc = (nameStr && nameStr.indexOf('encoder') !== -1);
      var isFfmpeg = (nameStr && nameStr.indexOf('ffmpeg') !== -1);
      var chosen = isEnc ? encInfo : (isFfmpeg && ffmpegDecInfo ? ffmpegDecInfo : decInfo);
      var targetSp = outSpPtr.readPointer();
      targetSp.writePointer(chosen);
      incStrong(chosen, targetSp);
      log('INJECT', 'GetCodecInfoByComponentName: ' + nameStr + ' -> ' + (isEnc?'encInfo':(isFfmpeg?'ffmpegDecInfo':'decInfo')));
      return 0;
    }, 'int', ['pointer', 'pointer', 'pointer']));
    REPLACED.push(defaultLambda);
    log('HOOK', 'GetCodecInfoByComponentName default lambda @ ' + defaultLambda);
  }

  var gci=sym('10MediaCodec12getCodecInfoEPN') || sym('10MediaCodec12getCodecInfo') || off('libstagefright.so',0x181370);
  if(gci){
    LISTENERS.push(Interceptor.attach(gci, {
      onEnter:function(a){ this.out=a[1]; },
      onLeave:function(retval){
        try{
          var st=retval.toInt32();
          var cur=this.out.readPointer();
          if(st!==0 || cur.isNull()){
            this.out.writePointer(decInfo);
            incStrong(decInfo, this.out);
            retval.replace(ptr(0));
            log('INJECT','getCodecInfo failed (st='+st+') -> substituted decInfo');
          }
        }catch(e){ log('WARN','getCodecInfo onLeave: '+e); }
      }
    }));
    log('HOOK','MediaCodec::getCodecInfo @'+gci);
  }
})();

// ---- 5. Java redirects ---------------------------------------------------
Java.perform(function(){
  var MediaCodec=Java.use('android.media.MediaCodec');
  try{
    MediaCodec.createDecoderByType.implementation=function(type){
      var t=''+type;
      var isAudio = (t.indexOf('audio')!==-1 || t.indexOf('opus')!==-1);
      var pref = globalThis.__GB_PREF_AVC_DEC || 'c2.android.avc.decoder';
      var name = isAudio ? t : pref;
      log('MediaCodec','createDecoderByType("'+t+'") -> createByCodecName("'+name+'")');
      return MediaCodec.createByCodecName(name);
    };
  }catch(e){ log('WARN','createDecoderByType hook: '+e); }

  try{
    MediaCodec.createEncoderByType.implementation=function(type){
      var t=''+type;
      var isAudio = (t.indexOf('audio')!==-1 || t.indexOf('opus')!==-1);
      var name = isAudio ? t : 'c2.android.avc.encoder';
      log('MediaCodec','createEncoderByType("'+t+'") -> createByCodecName("'+name+'")');
      return MediaCodec.createByCodecName(name);
    };
  }catch(e){ log('WARN','createEncoderByType hook: '+e); }
});

// ---- 6. orderly shutdown ------------------------------------------------------------------
// Undo every native replacement before the host unloads the agent: the RMS/MediaResource stubs
// and the GetCodecInfo lambda are entry-point rewrites into frida's own code, and the Java
// create*ByType redirects are replacements on GC-scanned methods. Both are safe while loaded and
// unsafe to unmap underneath a live process. Interceptor.revert() undoes attach+replace for THIS
// script only.
//
// Note: the two injected MediaCodecInfo objects stay strong-ref'd (2 small native objects per
// session). Dropping our ref needs RefBase::decStrong on a codec that may still hold the pointer,
// and risking a use-after-free during teardown in the exact path we are trying to make safe is a
// bad trade for a few KB.
globalThis.__GB_MEDIA_SHUTDOWN = function(){
    var out={javaReverted:false, nativeReverted:false};
    try{
      Java.perform(function(){
        try{
          var MC=Java.use('android.media.MediaCodec');
          try{ MC.createDecoderByType.implementation=null; }catch(e){}
          try{ MC.createEncoderByType.implementation=null; }catch(e){}
          out.javaReverted=true;
        }catch(e){ log('WARN','shutdown java: '+e); }
      });
    }catch(e){}
    try{
      LISTENERS.forEach(function(l){ try{ l.detach(); }catch(e){} });
      LISTENERS=[];
      REPLACED.forEach(function(a){ try{ Interceptor.revert(a); }catch(e){ log('WARN','revert '+a+': '+e); } });
      out.reverted=REPLACED.length;
      REPLACED=[];
      Interceptor.flush();
      out.nativeReverted=true;
    }catch(e){ log('WARN','shutdown interceptor: '+e); }
    log('SHUTDOWN','mediacodec: RMS/MediaResource/getCodecInfo hooks reverted, create*ByType redirects removed');
    return out;
};
