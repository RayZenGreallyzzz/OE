package game.oe.editor;

import android.content.Context;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/**
 * Stage12: import the owner's unmodified UV44 APK into the SAME OE application.
 * No Phoenix Builder sources, 1,832 bundled models, or user projects are
 * committed to the public repository. Original JS/GLB remain byte-identical.
 * This is a compatibility editor module, NOT a second installed Android app.
 */
final class PhoenixUv44Bundle {
    static final String ORIGIN = "https://uv44.oe.local";
    static final int MAX_ENTRIES = 2200;
    static final long MAX_UNPACKED = 160L * 1024 * 1024;
    static final long MAX_ENTRY = 24L * 1024 * 1024;
    private static final String DIR = "phoenix_uv44_bundle";
    static final int ORIGINAL_PWB_MODELS=1832;
    private PhoenixUv44Bundle(){}

    static File root(Context c){return new File(c.getFilesDir(),DIR);}
    static boolean ready(Context c){
        File base=root(c);
        File verified=new File(base,".uv44-validated");
        if(!new File(base,"index.html").isFile()||
           !new File(base,"js/clean-app.js").isFile()||
           !new File(base,"js/phoenix-files.js").isFile()||
           !new File(base,"js/bundled-catalog.js").isFile()||
           !new File(base,"js/three.min.js").isFile()||
           !verified.isFile())return false;
        try{
            // A partial/older extraction must not masquerade as the FULL
            // original owner's 1,832-part catalog.
            return new String(Files.readAllBytes(verified.toPath()),
                StandardCharsets.UTF_8).contains("models="+ORIGINAL_PWB_MODELS+"\\n");
        }catch(IOException ignored){return false;}
    }
    static boolean legalPath(String entry){
        if(entry==null||!entry.startsWith("assets/")||entry.endsWith("/"))return false;
        String rel=entry.substring(7);
        if(rel.isEmpty()||rel.startsWith("/")||rel.startsWith(".")||
           rel.contains("..")||rel.contains("//")||rel.contains("\\")||
           rel.indexOf(':')>=0||rel.indexOf('%')>=0||
           rel.indexOf('?')>=0||rel.indexOf('#')>=0)return false;
        if(!rel.matches("[A-Za-z0-9_./-]+"))return false;
        return rel.equals("index.html")||rel.equals("clean.css")||
               rel.equals("style.css")||rel.startsWith("js/")||
               rel.startsWith("pwb/");
    }
    static void removeTree(File root) throws IOException {
        if(!root.exists())return;
        File[] contents=root.listFiles();
        if(contents!=null)for(File f:contents)removeTree(f);
        if(!root.delete())throw new IOException("Не удалить временный файл UV44");
    }
    static void install(Context c, Uri uri) throws Exception {
        if(ready(c))return; // don't replace owner's stable, already imported copy
        File parent=c.getFilesDir(),staging=new File(parent,DIR+".staging");
        removeTree(staging);
        if(!staging.mkdir())throw new IOException("Не создать папку UV44");
        Set<String> names=new HashSet<>();
        long total=0;int entries=0,models=0;
        boolean index=false,clean=false,three=false,files=false;
        try {
            InputStream raw=c.getContentResolver().openInputStream(uri);
            if(raw==null)throw new IOException("Не открыть Phoenix UV44 APK");
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(raw))){
                ZipEntry entry;
                byte[] buffer=new byte[32768];
                while((entry=zip.getNextEntry())!=null){
                    String path=entry.getName();
                    if(path.endsWith("/")){zip.closeEntry();continue;}
                    // Ignore Android executable: use ONLY the embedded UV44
                    // builder assets, never launch another APK or execute DEX.
                    if(!path.startsWith("assets/")){zip.closeEntry();continue;}
                    if(!legalPath(path)||!names.add(path)||
                       ++entries>MAX_ENTRIES)throw new IOException("Опасные файлы UV44");
                    String rel=path.substring(7);
                    File target=new File(staging,rel);
                    String safe=target.getCanonicalPath();
                    if(!safe.startsWith(staging.getCanonicalPath()+File.separator))
                        throw new IOException("Некорректный путь в UV44");
                    File dir=target.getParentFile();
                    if(!dir.isDirectory()&&!dir.mkdirs())
                        throw new IOException("Не создать каталог UV44");
                    long size=0;
                    try(FileOutputStream out=new FileOutputStream(target)){
                        int n;
                        while((n=zip.read(buffer))!=-1){
                            size+=n;total+=n;
                            if(size>MAX_ENTRY||total>MAX_UNPACKED)
                                throw new IOException("Слишком большой архив UV44");
                            out.write(buffer,0,n);
                        }
                    }
                    if(size==0)throw new IOException("Пустой файл в UV44: "+rel);
                    if("index.html".equals(rel))index=true;
                    if("js/clean-app.js".equals(rel))clean=true;
                    if("js/three.min.js".equals(rel))three=true;
                    if("js/phoenix-files.js".equals(rel))files=true;
                    if(rel.startsWith("pwb/")&&rel.endsWith(".glb"))models++;
                    zip.closeEntry();
                }
            }
            if(!index||!clean||!three||!files||
               models!=ORIGINAL_PWB_MODELS||entries<ORIGINAL_PWB_MODELS||
               total<4_000_000)
                throw new IOException("Нужен полный оригинальный UV44 APK: "+
                    "получено "+models+" из "+ORIGINAL_PWB_MODELS+" GLB");
            Files.write(new File(staging,".uv44-validated").toPath(),
                ("uv44\nassets="+entries+"\nmodels="+models+"\n").getBytes(StandardCharsets.UTF_8));
            // Staging directory is in private app storage on the same volume.
            // Don't delete an already valid UV44 in favour of a bad new APK.
            if(root(c).exists())removeTree(root(c));
            if(!staging.renameTo(root(c)))
                throw new IOException("Не закрепить библиотеку UV44");
        }finally{
            if(staging.exists())removeTree(staging);
        }
    }
    private static WebResourceResponse reply(int status,String text) {
        WebResourceResponse r=new WebResourceResponse("text/plain","UTF-8",
            new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
        r.setStatusCodeAndReasonPhrase(status,status==404?"Not Found":"Forbidden");
        return r;
    }
    static WebResourceResponse resolve(Context c,String url) {
        if(url==null||!url.startsWith(ORIGIN+"/"))return reply(403,"External network disabled");
        String path=url.substring((ORIGIN+"/").length()).split("[?#]",2)[0];
        if(path.isEmpty())path="index.html";
        if(!legalPath("assets/"+path))return reply(403,"Invalid asset");
        try {
            File file=new File(root(c),path);
            if(!file.getCanonicalPath().startsWith(root(c).getCanonicalPath()+File.separator))
                return reply(403,"Invalid path");
            if(!file.isFile())return reply(404,"Not found");
            String lower=path.toLowerCase(Locale.ROOT),mime;
            if(lower.endsWith(".html"))mime="text/html";
            else if(lower.endsWith(".js"))mime="application/javascript";
            else if(lower.endsWith(".css"))mime="text/css";
            else if(lower.endsWith(".json"))mime="application/json";
            else if(lower.endsWith(".wasm"))mime="application/wasm";
            else if(lower.endsWith(".glb"))mime="model/gltf-binary";
            else if(lower.endsWith(".png"))mime="image/png";
            else if(lower.endsWith(".jpg")||lower.endsWith(".jpeg"))mime="image/jpeg";
            else if(lower.endsWith(".webp"))mime="image/webp";
            else mime="application/octet-stream";
            WebResourceResponse response=new WebResourceResponse(mime,
                mime.startsWith("text/")||mime.endsWith("json")||mime.contains("javascript")
                    ?"UTF-8":null,new BufferedInputStream(new FileInputStream(file)));
            Map<String,String> headers=new HashMap<>();
            headers.put("Access-Control-Allow-Origin",ORIGIN);
            headers.put("Cache-Control","private, max-age=3600");
            response.setResponseHeaders(headers);
            return response;
        }catch(IOException ex){return reply(404,"Not available");}
    }
}
