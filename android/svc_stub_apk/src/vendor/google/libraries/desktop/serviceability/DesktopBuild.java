package vendor.google.libraries.desktop.serviceability;

import android.os.Build;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/* loaded from: classes.dex */
public class DesktopBuild {
    private static final VersionMapping[] MAPPINGS = {new VersionMapping("CL2B", 1), new VersionMapping("CL3B", 2), new VersionMapping("CL4B", 3)};
    public static final int VERSION_CL2B = 1;
    public static final int VERSION_CL3B = 2;
    public static final int VERSION_CL4B = 3;
    public static final int VERSION_MAIN = Integer.MAX_VALUE;

    @Retention(RetentionPolicy.SOURCE)
    public @interface DesktopVersion {
    }

    private static final class VersionMapping {
        final String prefix;
        final int version;

        VersionMapping(String prefix, int version) {
            this.prefix = prefix;
            this.version = version;
        }
    }

    public static boolean isAtLeast(int version) {
        return getVersion() >= version;
    }

    private static int getVersion() {
        String buildId = Build.ID;
        if (buildId == null) {
            return VERSION_MAIN;
        }
        for (VersionMapping mapping : MAPPINGS) {
            if (buildId.startsWith(mapping.prefix)) {
                return mapping.version;
            }
        }
        return VERSION_MAIN;
    }
}
