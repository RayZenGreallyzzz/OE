package game.oe.editor;

import android.content.Context;
import android.util.AtomicFile;
import android.webkit.WebResourceResponse;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * Stage13: a stable UV44 editable source paired with a content-addressed OE
 * GLB. Entirely PRIVATE: never publish the owner's Builder library/projects.
 *
 * This is intentionally an app-local cache for the first round-trip test.
 * Portable .oezip source packaging comes AFTER tablet QA; do not confuse
 * this with an already portable editable building format.
 */
final class PhoenixHouseLinks {
    static final String EXTRA_EDIT_MODEL="game.oe.editor.UV44_EDIT_MODEL";
    static final String EXTRA_EDIT_ENTITY="game.oe.editor.UV44_EDIT_ENTITY";
    static final String EXTRA_BACKUP_PATH="game.oe.editor.UV44_BACKUP_PATH";
    static final String EDIT_ROUTE="/__oe_house.phoenix.json";
    static final long MAX_BACKUP_BYTES=96L*1024L*1024L;
    private static final int MAX_LINKS=8;
    private static final String FOLDER="uv44_editable_houses",INDEX="model-links.json";
    private PhoenixHouseLinks(){}

    private static boolean modelKey(String key){
        return key!=null&&key.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private static boolean backupKey(String key){
        return key!=null&&key.matches("house_[0-9a-f]{24}\\.phoenix\\.json");
    }
    private static File directory(Context context) throws IOException{
        File out=new File(context.getFilesDir(),FOLDER);
        if(!out.isDirectory()&&!out.mkdirs())
            throw new IOException("Не создать каталог редактируемых домов");
        return out;
    }
    private static String shaName(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[32768];
            int n;while((n=in.read(buf))!=-1)digest.update(buf,0,n);
        }
        byte[] bytes=digest.digest();
        StringBuilder key=new StringBuilder("house_");
        final String digits="0123456789abcdef";
        for(int i=0;i<12;i++) {
            key.append(digits.charAt((bytes[i]>>4)&15));
            key.append(digits.charAt(bytes[i]&15));
        }
        return key.append(".phoenix.json").toString();
    }
    private static void validateBackup(File file) throws Exception {
        if(!file.isFile()||file.length()<64||file.length()>MAX_BACKUP_BYTES)
            throw new IOException("Редактируемый UV44-проект отсутствует или больше 96 МБ");
        byte[] header=new byte[3072];
        int n;
        try(InputStream in=new FileInputStream(file)){n=in.read(header);}
        if(n<64)throw new IOException("Пустой проект UV44");
        String beginning=new String(header,0,n,StandardCharsets.UTF_8);
        if(!beginning.contains("\"format\":\"PhoenixBuilderBackupV1\"")||
           !beginning.contains("\"project\":"))
            throw new IOException("Нужен переносимый резервный проект UV44: «Сохранить проект (.phoenix.json)»");
    }
    private static Map<String,String> readLinks(Context c) throws Exception{
        File list=new File(directory(c),INDEX);
        Map<String,String> links=new HashMap<>();
        if(!list.isFile())return links;
        if(list.length()>16384)throw new IOException("Индекс UV44 повреждён");
        JSONObject json=new JSONObject(new String(
            Files.readAllBytes(list.toPath()),StandardCharsets.UTF_8));
        if(json.length()>MAX_LINKS)throw new IOException("Слишком много ссылок UV44");
        java.util.Iterator<String> keys=json.keys();
        while(keys.hasNext()){
            String asset=keys.next(),source=json.getString(asset);
            if(!modelKey(asset)||!backupKey(source))
                throw new IOException("Повреждён индекс дома UV44");
            links.put(asset,source);
        }
        return links;
    }
    private static void writeLinks(Context c,Map<String,String> links) throws Exception{
        JSONObject json=new JSONObject();
        for(Map.Entry<String,String> entry:links.entrySet())
            json.put(entry.getKey(),entry.getValue());
        AtomicFile file=new AtomicFile(new File(directory(c),INDEX));
        FileOutputStream out=file.startWrite();
        try{
            out.write(json.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(out);
        }catch(Exception ex){file.failWrite(out);throw ex;}
    }
    static synchronized void remember(Context c,String model,File source)
        throws Exception{
        if(!modelKey(model))throw new IOException("Неизвестная модель OE");
        File approved=new File(c.getFilesDir(),"uv44_files");
        if(!source.getCanonicalPath().startsWith(
               approved.getCanonicalPath()+File.separator)||
           !source.getName().endsWith(".phoenix.json"))
            throw new IOException("Проект должен быть сохранён исходным UV44");
        validateBackup(source);
        String key=shaName(source);
        File dir=directory(c),destination=new File(dir,key);
        if(destination.isFile()) {
            if(!shaName(destination).equals(key))
                throw new IOException("Сохранённый оригинал UV44 повреждён");
        }else{
            File part=new File(dir,key+".part");
            try{
                Files.copy(source.toPath(),part.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
                if(!shaName(part).equals(key))
                    throw new IOException("Ошибка контрольной суммы UV44");
                Files.move(part.toPath(),destination.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
            }finally{Files.deleteIfExists(part.toPath());}
        }
        Map<String,String> links=readLinks(c);
        if(links.size()>=MAX_LINKS&&!links.containsKey(model))
            throw new IOException("Максимум восемь разных редактируемых домов");
        links.put(model,key);
        writeLinks(c,links);
    }
    static synchronized File sourceFor(Context c,String model){
        try {
            if(!modelKey(model))return null;
            String key=readLinks(c).get(model);
            if(!backupKey(key))return null;
            File file=new File(directory(c),key);
            validateBackup(file);
            return file;
        }catch(Exception e){return null;}
    }
    static synchronized String sourceName(Context c,String model){
        File source=sourceFor(c,model);
        return source==null?null:source.getName();
    }
    static WebResourceResponse serveHouse(Context c,String model){
        File source=sourceFor(c,model);
        if(source==null)return new WebResourceResponse("text/plain","UTF-8",
            new ByteArrayInputStream("UV44 editable source missing".getBytes(StandardCharsets.UTF_8)));
        try{
            WebResourceResponse response=new WebResourceResponse(
                "application/json","UTF-8",
                new BufferedInputStream(new FileInputStream(source)));
            Map<String,String> headers=new HashMap<>();
            headers.put("Access-Control-Allow-Origin",PhoenixUv44Bundle.ORIGIN);
            headers.put("Cache-Control","no-store");
            response.setResponseHeaders(headers);
            return response;
        }catch(Exception e){
            return new WebResourceResponse("text/plain","UTF-8",
                new ByteArrayInputStream("UV44 source cannot open".getBytes(StandardCharsets.UTF_8)));
        }
    }
}
