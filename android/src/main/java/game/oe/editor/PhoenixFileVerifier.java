package game.oe.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Streaming verification of an Android Storage Access Framework export.
 * Deliberately no Android imports, so its exact logic can run in JVM CI.
 *
 * A completed ContentResolver.openOutputStream().close() is NOT proof that
 * a provider saved the full file. Reopen it and compare the SHA-256 digest
 * against the staged original before the caller deletes that original.
 */
public final class PhoenixFileVerifier {
    private static final int BUFFER_BYTES=65536;
    private PhoenixFileVerifier() {}

    private static byte[] digest(InputStream stream) throws IOException {
        final MessageDigest hash;
        try {
            hash=MessageDigest.getInstance("SHA-256");
        }catch(NoSuchAlgorithmException error){
            throw new IOException("SHA-256 недоступен",error);
        }
        byte[] buffer=new byte[BUFFER_BYTES];
        try(InputStream in=stream){
            for(int count;(count=in.read(buffer))!=-1;){
                if(count>0)hash.update(buffer,0,count);
            }
        }
        return hash.digest();
    }

    public static void verify(File original,InputStream providerCopy)
        throws IOException {
        if(original==null||!original.isFile())
            throw new IOException("Нет исходного файла Phoenix");
        if(providerCopy==null)
            throw new IOException("Android не разрешил перечитать экспортированный файл");
        byte[] originalHash=digest(new FileInputStream(original));
        byte[] copiedHash=digest(providerCopy);
        if(!Arrays.equals(originalHash,copiedHash))
            throw new IOException(
                "Сохранённый файл не совпадает с оригиналом. "+
                "Исходные данные сохранены для повторной попытки.");
    }
}
