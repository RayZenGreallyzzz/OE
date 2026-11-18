import game.oe.editor.PhoenixFileVerifier;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

/** Real JVM tests for the same streaming SHA-256 verifier used on Android. */
public final class PhoenixFileVerifierTest {
    private static void demand(boolean ok,String reason) {
        if(!ok)throw new AssertionError(reason);
    }
    private static boolean throwsVerification(File original,byte[] candidate) {
        try {
            PhoenixFileVerifier.verify(original,new ByteArrayInputStream(candidate));
            return false;
        }catch(IOException expected){
            return true;
        }
    }
    public static void main(String[] ignored) throws Exception {
        File source=Files.createTempFile("phoenix-saf-test-",".json").toFile();
        try {
            byte[] data=new byte[8*1024*1024+7]; // checks across 64 KiB boundaries
            for(int i=0;i<data.length;i++)
                data[i]=(byte)(i*31+(i>>>11));
            Files.write(source.toPath(),data);
            PhoenixFileVerifier.verify(source,new ByteArrayInputStream(data));
            byte[] corrupt=data.clone();
            corrupt[65536+17]^=127;
            demand(throwsVerification(source,corrupt),
                "Different content with equal byte length must be rejected");
            demand(throwsVerification(source,Arrays.copyOf(data,data.length-4)),
                "Truncated Android SAF export must be rejected");
            demand(throwsVerification(source,Arrays.copyOf(data,data.length+1)),
                "Extra bytes must be rejected");
            demand(throwsVerification(source,new byte[0]),
                "Empty document must be rejected");
            demand(throwsVerification(source,null),
                "Unreadable Android document provider must be rejected");
            demand(source.isFile()&&source.length()==data.length,
                "Verification failure must never delete staged original");
            System.out.println("OK: original GLB/backup retained until exact SHA-256 round-trip");
        }finally{
            source.delete();
        }
    }
}
