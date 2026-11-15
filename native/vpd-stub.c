// OS bringup stub for Googlebook-on-Waydroid.
//
// Googlebook's DesktopRepairModeService.onBootPhase() blocks the system_server main thread forever in
// ServiceManager.waitForService("vendor.google.desktop.vpd_executor.IVpdExecutor/default") -> the
// Watchdog kills system_server at 65s. That VPD HAL reads factory data from a vpd partition that does
// not exist in the Waydroid container, so no real implementation is possible or meaningful here.
//
// This registers a placeholder binder under that exact name. waitForService() then returns; every
// transaction we get is answered with STATUS_UNKNOWN_TRANSACTION, so the service's own fallback
// ("Failed to get IVpdExecutor service" / "Failed to read or parse post manufacturing config") runs
// and boot continues. Native (libbinder_ndk) on purpose: no ART/zygote, unaffected by the system_server
// crash loop.
#include <android/binder_ibinder.h>
#include <android/binder_status.h>
#include <stdio.h>

// AServiceManager_* and ABinderProcess_* live in binder_manager.h / binder_process.h, which are
// platform-only (not in the public NDK sysroot). The symbols exist in the container's libbinder_ndk.so,
// so forward-declare them and resolve at runtime (linked with --unresolved-symbols=ignore-all).
binder_status_t AServiceManager_addService(AIBinder* binder, const char* instance);
void ABinderProcess_startThreadPool(void);
void ABinderProcess_joinThreadPool(void);

static const char* kDescriptor = "vendor.google.desktop.vpd_executor.IVpdExecutor";
static const char* kInstance    = "vendor.google.desktop.vpd_executor.IVpdExecutor/default";

static void* OnCreate(void* args) { return args; }
static void  OnDestroy(void* userData) { (void)userData; }
static binder_status_t OnTransact(AIBinder* binder, transaction_code_t code,
                                  const AParcel* in, AParcel* out) {
    (void)binder; (void)code; (void)in; (void)out;
    // Answer everything as "unknown transaction" so callers fall through to their error path.
    return STATUS_UNKNOWN_TRANSACTION;
}

int main(void) {
    AIBinder_Class* clazz = AIBinder_Class_define(kDescriptor, OnCreate, OnDestroy, OnTransact);
    AIBinder* binder = AIBinder_new(clazz, NULL);

    binder_status_t st = AServiceManager_addService(binder, kInstance);
    if (st != STATUS_OK) {
        fprintf(stderr, "vpd-stub: addService(%s) failed: %d\n", kInstance, st);
        return 1;
    }
    fprintf(stderr, "vpd-stub: registered %s\n", kInstance);

    ABinderProcess_startThreadPool();
    ABinderProcess_joinThreadPool();   // blocks, serving transactions
    return 0;
}
