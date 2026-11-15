// SPDX-License-Identifier: GPL-2.0
/*
 * gbos_secctx - make binder's "security context" requests work on a kernel whose only LSM is AppArmor.
 *
 * Android services such as keystore2 ask binder to attach the caller's security context to each transaction
 * (FLAT_BINDER_FLAG_TXN_SECURITY_CTX). Binder gets the caller's secid with security_cred_getsecid(), but no AppArmor
 * version implements that hook, so the secid is always 0, which AppArmor never allocates; the lookup then fails with
 * -EINVAL and the transaction fails ("binder: ... failed to get security context"). Android 17's odsign aborts on its
 * first keystore2 call, boot never completes. The real fix is a three-hunk AppArmor patch (see kernel/README.md).
 *
 * This module is the no-rebuild alternative: a kprobe on apparmor_secid_to_secctx() answers the one query that can
 * never succeed (secid 0) with a fixed context string instead of -EINVAL, and returns to the caller. Every other query
 * runs the original function untouched. The string is what the patched kernel would report for the Waydroid
 * container: its AppArmor profile name.
 */
#include <linux/module.h>
#include <linux/kprobes.h>
#include <linux/security.h>
#include <linux/lsm.h>
#include <linux/string.h>
#include <linux/slab.h>
#include <asm/linkage.h>
#include <linux/version.h>

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("Answer binder's secid-0 security-context lookups on AppArmor-only kernels");

static char *context = "lxc-waydroid";
module_param(context, charp, 0444);
MODULE_PARM_DESC(context, "security context string reported for callers (default: the Waydroid AppArmor profile name)");

static bool trace;
module_param(trace, bool, 0644);
MODULE_PARM_DESC(trace, "log (rate-limited) every lookup with its secid and result, for diagnosis");

static atomic64_t served;
static int served_get(char *buf, const struct kernel_param *kp)
{
	return scnprintf(buf, PAGE_SIZE, "%lld\n", atomic64_read(&served));
}
static const struct kernel_param_ops served_ops = { .get = served_get };
module_param_cb(served, &served_ops, NULL, 0444);

/* a function that just returns: kprobe redirects the probed function here, with rax already set */
extern void gbos_secctx_ret(void);
asm(".pushsection .text\n"
    ".type gbos_secctx_ret, @function\n"
    "gbos_secctx_ret:\n"
    ASM_RET
    ".size gbos_secctx_ret, .-gbos_secctx_ret\n"
    ".popsection\n");

/*
 * The signature of apparmor_secid_to_secctx() changed in 6.14. Support both:
 *   >= 6.14:  int apparmor_secid_to_secctx(u32 secid, struct lsm_context *cp)        -> returns the length
 *   <  6.14:  int apparmor_secid_to_secctx(u32 secid, char **secdata, u32 *seclen)   -> returns 0 on success
 * x86-64 args: secid=rdi; then (rsi) is cp, or (rsi, rdx) are (secdata, seclen).
 */
static int pre(struct kprobe *p, struct pt_regs *regs)
{
	u32 secid = (u32)regs->di;
	size_t len;

	if (trace && printk_ratelimit())
		pr_info("gbos_secctx: lookup secid=%u by %s[%d]\n", secid, current->comm, task_pid_nr(current));
	if (secid != 0)
		return 0;			/* a real secid: let the original run */
	len = strlen(context);
#if LINUX_VERSION_CODE >= KERNEL_VERSION(6, 14, 0)
	{
		struct lsm_context *cp = (struct lsm_context *)regs->si;

		if (cp) {
			char *s = kstrdup(context, GFP_ATOMIC);

			if (!s) {
				regs->ax = (unsigned long)-ENOMEM;
			} else {
				cp->context = s;	/* freed by apparmor_release_secctx() -> kfree() */
				cp->len = len;
				cp->id = LSM_ID_APPARMOR;
				regs->ax = len;
			}
		} else {
			regs->ax = len;			/* size query */
		}
	}
#else
	{
		char **secdata = (char **)regs->si;
		u32 *seclen = (u32 *)regs->dx;

		if (secdata) {
			char *s = kstrdup(context, GFP_ATOMIC);

			if (!s) {
				regs->ax = (unsigned long)-ENOMEM;
			} else {
				*secdata = s;		/* freed by apparmor_release_secctx() -> kfree() */
				if (seclen)
					*seclen = len;
				regs->ax = 0;		/* old API returns 0 on success */
			}
		} else {
			if (seclen)
				*seclen = len;		/* size query */
			regs->ax = 0;
		}
	}
#endif
	atomic64_inc(&served);
	regs->ip = (unsigned long)gbos_secctx_ret;
	return 1;				/* registers changed: do not single-step the original instruction */
}

static int ret_handler(struct kretprobe_instance *ri, struct pt_regs *regs)
{
	if (trace && (int)regs_return_value(regs) < 0 && printk_ratelimit())
		pr_info("gbos_secctx: original returned %d for %s[%d]\n", (int)regs_return_value(regs), current->comm, task_pid_nr(current));
	return 0;
}

static struct kretprobe krp = {
	.kp.symbol_name = "apparmor_secid_to_secctx",
	.handler = ret_handler,
	.maxactive = 32,
};

/*
 * An empty post_handler is deliberate: kprobes with one are never "optimized" (turned into a jump to a trampoline),
 * and an optimized probe ignores a pre_handler's change of regs->ip, which this module relies on.
 */
static void post(struct kprobe *p, struct pt_regs *regs, unsigned long flags)
{
}

static struct kprobe kp = {
	.symbol_name = "apparmor_secid_to_secctx",
	.pre_handler = pre,
	.post_handler = post,
};

static int __init secctx_init(void)
{
	int ret;

	if (!context || !*context || strlen(context) > 200)
		return -EINVAL;
	ret = register_kprobe(&kp);
	if (ret) {
		pr_err("gbos_secctx: cannot probe apparmor_secid_to_secctx (%d): AppArmor not built in, or the symbol is not unique/inlined\n", ret);
		return ret;
	}
	if (register_kretprobe(&krp))
		pr_warn("gbos_secctx: trace of failures unavailable\n");
	pr_info("gbos_secctx: loaded; secid-0 lookups answer \"%s\"\n", context);
	return 0;
}

static void __exit secctx_exit(void)
{
	unregister_kretprobe(&krp);
	unregister_kprobe(&kp);
	pr_info("gbos_secctx: unloaded (%lld lookups served)\n", atomic64_read(&served));
}
module_init(secctx_init);
module_exit(secctx_exit);
