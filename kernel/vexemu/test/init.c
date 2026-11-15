#define _GNU_SOURCE
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <sys/mount.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <sys/reboot.h>
#include <linux/reboot.h>
int main(int argc, char **argv)
{
	int nomod = 0, st = 0, i;
	for (i = 1; i < argc; i++) if (!strcmp(argv[i], "nomod")) nomod = 1;
	const char *prog = "/vextest", *params = ""; for (i = 1; i < argc; i++) { if (!strcmp(argv[i], "addr")) prog = "/addr"; if (!strcmp(argv[i], "ratest")) prog = "/ratest"; if (!strcmp(argv[i], "ra0")) params = "runahead=0"; }
	mount("proc", "/proc", "proc", 0, 0); mount("sysfs", "/sys", "sysfs", 0, 0);
	if (!nomod) {
		int fd = open("/gbos_vexemu.ko", O_RDONLY);
		long r = fd < 0 ? -1 : syscall(SYS_finit_module, fd, params, 0);
		printf("INIT: insmod gbos_vexemu -> %ld\n", r); fflush(stdout);
		if (r) { perror("finit_module"); }
	} else printf("INIT: control run, module NOT loaded\n");
	pid_t p = fork();
	if (!p) { execl(prog, "t", "--all", (char *)0); _exit(99); }
	waitpid(p, &st, 0);
	printf("INIT: vextest exit status %d\n", WIFEXITED(st) ? WEXITSTATUS(st) : -1);
	FILE *f = fopen("/sys/module/gbos_vexemu/parameters/stats", "r");
	if (f) { char l[128]; printf("INIT: --- stats\n"); while (fgets(l, sizeof l, f)) fputs(l, stdout); fclose(f); }
	fflush(stdout); sync(); reboot(LINUX_REBOOT_CMD_POWER_OFF);
	return 0;
}
