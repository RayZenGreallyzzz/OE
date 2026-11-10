package game.oe.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import android.content.res.AssetManager;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Individual medieval library assets, with source images kept byte-identical.
 * Installed packs remain compressed on disk; a selected GLB or shared-texture
 * glTF is resolved on demand rather than unpacking 105+ GPU assets on launch.
 *
 * The lightweight tree archive has trees/*.gltf, trees/*.bin and shared
 * textures/*.png|jpg. For each selected tree, we build a standard GLB whose
 * BIN chunk contains the same geometric bytes and original image bytes.
 * No JPEG/PNG resizing, transcoding, resampling or source ZIP rewrite.
 */
final class AssetLibrary {
    static final int PICK_LIBRARY = 706;
    static final String BUILTIN_KENNEY="medieval/kenney_retro_105.zip";
    static final String BUILTIN_MEDIEVAL="medieval/oe_medieval_editor_addon.zip";
    static final long MAX_PACK_BYTES = 190L*1024*1024;
    private static final long MAX_EXPANDED = 550L*1024*1024;
    private static final int MAX_FILES = 650;
    private static final int MAX_ASSET_ENTRIES = 250;
    private static final int MAX_JSON_BYTES = 1024*1024;
    private static final int MAX_SCAN_CACHE = 8;
    static final String TREES="Деревья",HOUSES="Дома",FORTRESS="Крепость",
                        DECOR="Декорации",ENVIRONMENT="Окружение";
    static final class Entry {
        final File pack;
        final String path,name,category;
        final long bytes;
        Entry(File p,String path,String category,long bytes){
            this.pack=p;this.path=path;this.category=category;this.bytes=bytes;
            final String file=path.substring(path.lastIndexOf('/')+1);
            this.name=file.substring(0,file.lastIndexOf('.'));
        }
        String label(){return name.replace('-', ' ').replace('_',' ');}
    }
    private AssetLibrary(){}

    static File directory(File filesRoot) throws Exception {
        final File dir=new File(filesRoot,"medieval-libraries");
        if(!dir.exists()&&!dir.mkdirs())throw new Exception("Не создать библиотеку объектов");
        return dir;
    }
    private static boolean safeName(String name) {
        if(name==null||name.length()==0||name.length()>180||
           name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0||
           name.indexOf('\0')>=0)return false;
        final Path normalized=Paths.get(name).normalize();
        return !normalized.isAbsolute()&&!normalized.startsWith("..")&&
               !name.startsWith("./");
    }
    private static String category(String path){
        final String s=path.toLowerCase(java.util.Locale.ROOT);
        if(s.startsWith("trees/")||s.contains("tree-")||s.contains("tree_")||
           s.contains("shrub")||s.contains("bush")||s.contains("forest"))
            return TREES;
        if(s.contains("battlement")||s.contains("tower")||
           s.contains("fortified")||s.contains("wall-gate")||
           s.contains("wall-flat-gate")||s.contains("wall-fort")||
           s.contains("gate"))return FORTRESS;
        if(s.contains("environment")||s.contains("grass")||s.contains("ground")||
           s.contains("rock")||s.contains("terrain")||s.contains("landscape")||
           s.contains("lamp")||s.contains("crate"))return ENVIRONMENT;
        if(s.contains("roof")||s.contains("wall-pane")||
           s.contains("floor")||s.contains("structure")||s.contains("house")||
           s.contains("building")||s.contains("stairs")||s.contains("column"))
            return HOUSES;
        return DECOR;
    }
    static List<Entry> scan(File archive) throws Exception {
        final List<Entry> result=new ArrayList<>();
        try(ZipFile zip=new ZipFile(archive)){
            final Enumeration<? extends ZipEntry> entries=zip.entries();
            final Set<String> seen=new HashSet<>();
            long uncompressed=0;
            int fileCount=0;
            while(entries.hasMoreElements()){
                ZipEntry e=entries.nextElement();
                if(e.isDirectory())continue;
                if(++fileCount>MAX_FILES)throw new Exception("Слишком много файлов в ZIP");
                String n=e.getName();
                if(!safeName(n)||!seen.add(n))
                    throw new Exception("Недопустимое или повторяющееся имя в ZIP");
                long size=e.getSize();
                if(size<0||size>MAX_EXPANDED||
                   (uncompressed+=size)>MAX_EXPANDED)
                    throw new Exception("ZIP превышает предел распаковки");
                final String lower=n.toLowerCase(java.util.Locale.ROOT);
                if(lower.endsWith(".glb")||lower.endsWith(".gltf")){
                    if(result.size()>=MAX_ASSET_ENTRIES)
                        throw new Exception("Больше 250 отдельных моделей в библиотеке");
                    if(size<=0||size>GlbReader.MAX_BYTES)
                        throw new Exception("Слишком большая модель: "+n);
                    result.add(new Entry(archive,n,category(n),size));
                }
            }
        }
        if(result.isEmpty())throw new Exception("В ZIP не найдено отдельных GLB / glTF");
        result.sort(Comparator.comparing((Entry e)->e.category)
                              .thenComparing(e->e.name));
        return result;
    }
    static int install(InputStream stream,File root) throws Exception {
        final File dir=directory(root);
        final File temp=File.createTempFile(".import-", ".part",dir);
        try{
            final MessageDigest sha=MessageDigest.getInstance("SHA-256");
            long count=0;
            try(OutputStream out=new FileOutputStream(temp)){
                byte[] buf=new byte[64*1024];
                int n;
                while((n=stream.read(buf))!=-1){
                    count+=n;
                    if(count>MAX_PACK_BYTES)
                        throw new Exception("Архив больше 190 МиБ — выбери меньший набор");
                    sha.update(buf,0,n);
                    out.write(buf,0,n);
                }
            }
            if(count==0)throw new Exception("Архив пуст");
            final int items=scan(temp).size();
            StringBuilder id=new StringBuilder();
            for(byte b:sha.digest())id.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
            final File destination=new File(dir,"lib-"+id.substring(0,20)+".zip");
            if(!destination.exists()){
                final File[] existing=dir.listFiles((d,name)->
                        name.startsWith("lib-")&&name.endsWith(".zip"));
                if(existing!=null&&existing.length>=MAX_SCAN_CACHE)
                    throw new Exception("Установлено 8 библиотек. Освободи место перед добавлением новой.");
                Files.move(temp.toPath(),destination.toPath(),StandardCopyOption.ATOMIC_MOVE);
            }
            return items;
        }finally{Files.deleteIfExists(temp.toPath());}
    }
    // First use opens the small, read-only ZIP shipped INSIDE the installed APK.
    // Install a private indexed copy (about 250 KB), not every model or texture.
    // This runs off the UI/GL threads; subsequent library browsing is offline.
    static void ensureBundled(AssetManager manager,File root) throws Exception {
        final File installedFlag=new File(directory(root),"kenney-105-bundled-v1.ok");
        if(installedFlag.isFile())return;
        try(InputStream input=manager.open(BUILTIN_KENNEY)){
            final int total=install(input,root);
            if(total!=105)
                throw new Exception("Встроенная библиотека содержит "+total+
                                    " моделей вместо 105");
        }
        final File pending=new File(installedFlag.getPath()+".part");
        try{
            Files.write(pending.toPath(),"105".getBytes(StandardCharsets.UTF_8));
            Files.move(pending.toPath(),installedFlag.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(pending.toPath());}
    }
    /** Bundled paid asset data is optional at build time; the final signed test
     *  APK includes the exact extra ZIP inside /assets/medieval/. If the
     *  archive is not present, only the verified Kenney baseline is shown.
     *  A marker is written only after the entire pack is scanned and
     *  atomically installed; no silent empty library.
     */
    static void ensureMedievalAddon(AssetManager manager,File root) throws Exception {
        final File marker=new File(directory(root),"medieval-addon-011-v1.ok");
        if(marker.isFile())return;
        try(InputStream stream=manager.open(BUILTIN_MEDIEVAL)){
            int total=install(stream,root);
            if(total<140)
                throw new Exception("Неполная средневековая библиотека: "+total+" объектов");
        }catch(java.io.FileNotFoundException e){
            return; // CI without paid external assets: offline tree/building add-on absent.
        }
        final File tmp=new File(marker.getPath()+".part");
        try{
            Files.write(tmp.toPath(),"medieval-ok".getBytes(StandardCharsets.UTF_8));
            Files.move(tmp.toPath(),marker.toPath(),StandardCopyOption.REPLACE_EXISTING);
        }finally{Files.deleteIfExists(tmp.toPath());}
    }
    static List<Entry> list(File root,String category) throws Exception {
        final List<Entry> list=new ArrayList<>();
        final File[] packs=directory(root).listFiles((d,name)->
                 name.startsWith("lib-")&&name.endsWith(".zip"));
        if(packs==null)return list;
        if(packs.length>MAX_SCAN_CACHE)
            throw new Exception("Слишком много библиотек; оставь не более 8 ZIP");
        java.util.Arrays.sort(packs,Comparator.comparing(File::getName));
        for(File pack:packs){
            for(Entry entry:scan(pack))
                if(category.equals(entry.category))list.add(entry);
        }
        return list;
    }
    private static byte[] read(ZipFile zip,ZipEntry item,int maxBytes)
            throws Exception {
        if(item==null||item.isDirectory()||item.getSize()<0||
           item.getSize()>maxBytes)throw new Exception("Ресурс отсутствует или слишком большой");
        try(InputStream input=zip.getInputStream(item);
            ByteArrayOutputStream out=new ByteArrayOutputStream(
                (int)Math.min(item.getSize(),65536L))){
            byte[] buf=new byte[32*1024];
            int n;
            while((n=input.read(buf))!=-1){
                if(out.size()+n>maxBytes)throw new Exception("Превышен лимит ресурса");
                out.write(buf,0,n);
            }
            if(out.size()!=item.getSize())throw new Exception("ZIP повреждён");
            return out.toByteArray();
        }
    }
    private static ZipEntry linked(ZipFile zip,String from,String uri)
            throws Exception {
        if(uri==null||uri.isEmpty()||uri.indexOf('\\')>=0||
           uri.contains(":")||uri.startsWith("/")||uri.indexOf('?')>=0||
           uri.indexOf('#')>=0)throw new Exception("Небезопасная ссылка glTF");
        final Path parent=Paths.get(from).getParent();
        final Path resolved=(parent==null?Paths.get(uri):parent.resolve(uri)).normalize();
        final String name=resolved.toString().replace('\\','/');
        if(!safeName(name))throw new Exception("Путь glTF выходит за пределы ZIP");
        return zip.getEntry(name);
    }
    private static void pad(ByteArrayOutputStream stream) {
        while((stream.size()&3)!=0)stream.write(0);
    }
    private static byte[] externalGltf(ZipFile zip,Entry item) throws Exception {
        final JSONObject doc=new JSONObject(new String(
                read(zip,zip.getEntry(item.path),MAX_JSON_BYTES),
                StandardCharsets.UTF_8));
        if(!doc.getJSONObject("asset").optString("version","").startsWith("2."))
            throw new Exception("Требуется glTF 2.0");
        final JSONArray buffers=doc.getJSONArray("buffers");
        if(buffers.length()!=1)throw new Exception("Нужен один бинарный буфер");
        final JSONObject buffer=buffers.getJSONObject(0);
        final String binPath=buffer.getString("uri");
        final byte[] binary=read(zip,linked(zip,item.path,binPath),GlbReader.MAX_BYTES);
        if(binary.length!=buffer.getInt("byteLength"))
            throw new Exception("Размер BIN не совпадает");
        final ByteArrayOutputStream aggregate=new ByteArrayOutputStream();
        aggregate.write(binary);
        buffer.remove("uri");
        JSONArray views=doc.optJSONArray("bufferViews");
        if(views==null){views=new JSONArray();doc.put("bufferViews",views);}
        final JSONArray images=doc.optJSONArray("images");
        if(images!=null)for(int i=0;i<images.length();++i){
            final JSONObject img=images.getJSONObject(i);
            if(!img.has("uri"))continue;
            final String uri=img.getString("uri");
            final String lower=uri.toLowerCase(java.util.Locale.ROOT);
            if(!lower.endsWith(".png")&&!lower.endsWith(".jpg")&&
               !lower.endsWith(".jpeg"))
                throw new Exception("Поддерживаются только исходные PNG/JPEG");
            final byte[] picture=read(zip,linked(zip,item.path,uri),
                                      GlbReader.MAX_BYTES);
            pad(aggregate);
            final int offset=aggregate.size();
            if((long)offset+picture.length>GlbReader.MAX_BYTES-4096L)
                throw new Exception("Дерево слишком большое для GLB (24 МиБ)");
            aggregate.write(picture);
            final JSONObject view=new JSONObject();
            view.put("buffer",0);view.put("byteOffset",offset);
            view.put("byteLength",picture.length);
            final int viewId=views.length();views.put(view);
            img.remove("uri");img.put("bufferView",viewId);
            img.put("mimeType",lower.endsWith(".png")?"image/png":"image/jpeg");
        }
        buffer.put("byteLength",aggregate.size());
        // Geometry and image bytes are kept byte-identical. Only the glTF
        // container changes to make one independently importable GLB.
        return GltfReader.packGlb(doc,aggregate.toByteArray());
    }
    // Kenney GLBs reference sibling PNGs inside their official distribution.
    // Embed those exact bytes into the selected GLB, without altering image
    // content, resizing, recompressing or loading unrelated objects into RAM.
    private static byte[] externalImageGlb(ZipFile zip,Entry item)
            throws Exception {
        final byte[] source=read(zip,zip.getEntry(item.path),GlbReader.MAX_BYTES);
        if(source.length<20)throw new Exception("Повреждённый GLB");
        final ByteBuffer bb=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
        if(bb.getInt(0)!=0x46546c67||bb.getInt(4)!=2||
           bb.getInt(8)!=source.length)
            throw new Exception("Нужен бинарный glTF 2.0");
        int pos=12,jsonStart=-1,jsonLength=0,binStart=-1,binLength=0;
        while(pos+8<=source.length){
            int length=bb.getInt(pos),kind=bb.getInt(pos+4);
            if(length<0||(long)pos+8+length>source.length)
                throw new Exception("GLB имеет повреждённые чанки");
            if(kind==0x4e4f534a){
                if(jsonStart>=0)throw new Exception("Повторный JSON-чанк");
                jsonStart=pos+8;jsonLength=length;
            }
            if(kind==0x004e4942){
                if(binStart>=0)throw new Exception("Повторный BIN-чанк");
                binStart=pos+8;binLength=length;
            }
            pos+=8+length;
        }
        if(pos!=source.length||jsonStart<0||binStart<0||binLength<=0)
            throw new Exception("Неверные чанки GLB");
        final JSONObject doc=new JSONObject(new String(source,jsonStart,
                        jsonLength,StandardCharsets.UTF_8).trim());
        final JSONArray images=doc.optJSONArray("images");
        if(images==null||images.length()==0)return source;
        boolean hasExternal=false;
        for(int i=0;i<images.length();++i){
            String uri=images.getJSONObject(i).optString("uri","");
            if(!uri.isEmpty()&&!uri.startsWith("data:"))hasExternal=true;
        }
        if(!hasExternal)return source;
        final JSONArray buffers=doc.getJSONArray("buffers");
        if(buffers.length()!=1)throw new Exception("GLB: несколько BIN-буферов");
        final ByteArrayOutputStream aggregate=new ByteArrayOutputStream();
        aggregate.write(source,binStart,binLength);
        JSONArray views=doc.optJSONArray("bufferViews");
        if(views==null){views=new JSONArray();doc.put("bufferViews",views);}
        for(int i=0;i<images.length();++i){
            final JSONObject img=images.getJSONObject(i);
            final String uri=img.optString("uri","");
            if(uri.isEmpty()||uri.startsWith("data:"))continue;
            final String lower=uri.toLowerCase(java.util.Locale.ROOT);
            if(!lower.endsWith(".png")&&!lower.endsWith(".jpg")&&
               !lower.endsWith(".jpeg"))
                throw new Exception("Текстура в GLB должна быть PNG/JPG");
            final byte[] original=read(zip,linked(zip,item.path,uri),
                                        GlbReader.MAX_BYTES);
            pad(aggregate);
            final int offset=aggregate.size();
            if((long)offset+original.length>GlbReader.MAX_BYTES-4096L)
                throw new Exception("GLB с текстурами слишком большой");
            aggregate.write(original);
            JSONObject view=new JSONObject();
            view.put("buffer",0);view.put("byteOffset",offset);
            view.put("byteLength",original.length);
            final int index=views.length();views.put(view);
            img.remove("uri");img.put("bufferView",index);
            img.put("mimeType",lower.endsWith(".png")?"image/png":"image/jpeg");
        }
        buffers.getJSONObject(0).put("byteLength",aggregate.size());
        return GltfReader.packGlb(doc,aggregate.toByteArray());
    }
    static byte[] load(Entry entry) throws Exception {
        try(ZipFile zip=new ZipFile(entry.pack)){
            if(entry.path.toLowerCase(java.util.Locale.ROOT).endsWith(".glb"))
                return externalImageGlb(zip,entry);
            return externalGltf(zip,entry);
        }
    }
}
