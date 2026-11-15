import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public class Sign {
    public static void main(String[] a) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(a[0])) { ks.load(in, "stubpass".toCharArray()); }
        PrivateKey key = (PrivateKey) ks.getKey("stub", "stubpass".toCharArray());
        X509Certificate cert = (X509Certificate) ks.getCertificate("stub");
        ApkSigner.SignerConfig cfg = new ApkSigner.SignerConfig.Builder("stub", key, Collections.singletonList(cert)).build();
        new ApkSigner.Builder(Collections.singletonList(cfg))
            .setInputApk(new File(a[1])).setOutputApk(new File(a[2]))
            .setV1SigningEnabled(true).setV2SigningEnabled(true).setV3SigningEnabled(true)
            .setMinSdkVersion(31).build().sign();
        System.out.println("signed -> " + a[2]);
    }
}
