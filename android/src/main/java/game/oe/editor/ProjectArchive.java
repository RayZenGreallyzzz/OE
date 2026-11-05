package game.oe.editor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Portable OE project container (ordinary ZIP containing scene.json + models/).
 * Does not modify an existing scene or private model directory.
 *
 * All I/O is bounded; only exact safe paths are accepted (no ZIP traversal).
 * Caller must commit the parsed scene on the GLES thread after validation.
 */
public final class ProjectArchive {
    private ProjectArchive() {}
    public static final long MAX_UNPACKED_BYTES=128L*1024*1024;
    public static final int MAX_MODELS=12;
    public static final int MAX_SCENE_BYTES=200000;
    public static final int MAX_TEXTURES=8,MAX_TEXTURE_BYTES=8*1024*1024;

    public static final class StagedProject {
        public final File root, sceneFile, modelDir, textureDir, sourceDir;
        private StagedProject(File root){
            this.root=root;
            this.sceneFile=new File(root,"scene.json");
            this.modelDir=new File(root,"models");
            this.textureDir=new File(root,"textures");
            this.sourceDir=new File(root,"sources");
        }
    }

    private static boolean validModel(String name){
        return name!=null && name.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private static boolean validSource(String name){
        return name!=null&&name.matches("oe_[0-9a-f]{24}\\.(vox|gltf)");
    }
    private static String sourceModelName(String source){
        return source.substring(0,source.lastIndexOf('.'))+".glb";
    }
    private static void validateSource(File file,String name) throws Exception{
        check(validSource(name)&&file.isFile()&&file.length()>0&&
            file.length()<=GlbReader.MAX_BYTES,"Неверный исходный файл 3D");
        byte[] bytes=Files.readAllBytes(file.toPath());
        UniversalModelReader.Converted result=UniversalModelReader.convert(bytes);
        check(("."+result.originalType).equals(name.substring(name.lastIndexOf('.'))),
            "Исходный формат не совпадает с расширением");
        check(GlbReader.key(result.glb).equals(sourceModelName(name)),
            "Исходная модель не совпадает с GLB: "+name);
    }
    private static boolean validTexture(String name){
        return name!=null&&name.matches("tx_[0-9a-f]{24}\\.(png|jpg)");
    }
    private static Set<String> referencedTextures(String scene) throws Exception{
        JSONObject document=new JSONObject(scene);
        Set<String> keys=new HashSet<>();
        JSONArray objects=document.getJSONArray("items");
        for(int i=0;i<objects.length();i++){
            String key=objects.getJSONObject(i).optString("paint","");
            if(!key.isEmpty()){
                check(validTexture(key),"Недопустимая текстура объекта");keys.add(key);
            }
        }
        JSONArray strokes=document.optJSONArray("terrainPaint");
        if(strokes!=null){
            check(strokes.length()<=48,"Слишком много кистей текстуры");
            for(int i=0;i<strokes.length();i++){
                JSONArray stroke=strokes.getJSONArray(i);
                check(stroke.length()==4,"Неверная запись покраски земли");
                String key=stroke.getString(3);
                check(validTexture(key),"Недопустимая текстура земли");keys.add(key);
            }
        }
        check(keys.size()<=MAX_TEXTURES,"Более 8 пользовательских текстур");
        return keys;
    }
    private static void check(boolean ok,String reason) throws IOException{
        if(!ok)throw new IOException(reason);
    }
    private static Set<String> referencedModels(String scene) throws Exception {
        check(scene.getBytes(StandardCharsets.UTF_8).length<=MAX_SCENE_BYTES,"Сцена больше 200 КБ");
        JSONObject json=new JSONObject(scene);
        check(json.optInt("version",-1)==1,"Неподдерживаемая версия сцены");
        JSONArray items=json.getJSONArray("items");
        check(items.length()<=300,"Слишком много объектов");
        Set<String> names=new HashSet<>();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);
            if(item.getInt("type")!=6)continue;
            String key=item.getString("asset");
            check(validModel(key),"Недопустимое имя модели");
            names.add(key);
        }
        check(names.size()<=MAX_MODELS,"Слишком много разных GLB");
        return names;
    }
    private static String calculatedName(File file) throws Exception{
        MessageDigest md=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            byte[] buf=new byte[8192];
            int n;
            while((n=in.read(buf))!=-1)md.update(buf,0,n);
        }
        byte[] digest=md.digest();
        StringBuilder name=new StringBuilder("oe_");
        final char[] hex="0123456789abcdef".toCharArray();
        for(int i=0;i<12;i++){
            name.append(hex[(digest[i]>>>4)&15]);
            name.append(hex[digest[i]&15]);
        }
        return name.append(".glb").toString();
    }
    public static void exportProject(String scene,File modelFolder,File textureFolder,
             File originalFolder,OutputStream output) throws Exception{
        Set<String> names=referencedModels(scene);
        Set<String> textures=referencedTextures(scene);
        long budget=scene.getBytes(StandardCharsets.UTF_8).length;
        // Do all preflight validation before emitting an archive.
        for(String name:names){
            File f=new File(modelFolder,name);
            check(f.isFile()&&f.length()>0&&f.length()<=GlbReader.MAX_BYTES,
                    "Не найден GLB для экспорта: "+name);
            budget+=f.length();
            check(budget<=MAX_UNPACKED_BYTES,"Проект слишком большой");
            check(name.equals(calculatedName(f)),"Повреждён GLB: "+name);
        }
        for(String name:textures){
            File image=new File(textureFolder,name);
            check(image.isFile()&&image.length()>0&&image.length()<=MAX_TEXTURE_BYTES,
                "Не найдена текстура для экспорта: "+name);
            check(name.equals(calculatedTextureName(image)),
                "Повреждена текстура: "+name);
            budget+=image.length();
            check(budget<=MAX_UNPACKED_BYTES,"Проект слишком большой");
        }
        Set<String> sources=new HashSet<>();
        for(String model:names){
            String base=model.substring(0,model.length()-4);
            for(String extension:new String[]{"vox","gltf"}){
                String sourceName=base+"."+extension;
                File original=new File(originalFolder,sourceName);
                if(original.isFile()){
                    validateSource(original,sourceName);
                    sources.add(sourceName);
                    budget+=original.length();
                    check(budget<=MAX_UNPACKED_BYTES,"Слишком большие оригиналы моделей");
                }
            }
        }
        check(sources.size()<=MAX_MODELS*2,"Слишком много оригиналов");
        try(ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(output))){
            zip.putNextEntry(new ZipEntry("scene.json"));
            zip.write(scene.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            byte[] bytes=new byte[8192];
            for(String name:names){
                zip.putNextEntry(new ZipEntry("models/"+name));
                try(InputStream in=new BufferedInputStream(new FileInputStream(new File(modelFolder,name)))){
                    int count;
                    while((count=in.read(bytes))!=-1)zip.write(bytes,0,count);
                }
                zip.closeEntry();
            }
            for(String name:textures){
                zip.putNextEntry(new ZipEntry("textures/"+name));
                try(InputStream in=new BufferedInputStream(
                    new FileInputStream(new File(textureFolder,name)))){
                    int count;while((count=in.read(bytes))!=-1)zip.write(bytes,0,count);
                }
                zip.closeEntry();
            }
            // Keep unmodified VOX and standalone glTF for source re-editing.
            // The canonical GLB is still sufficient to render this scene.
            for(String name:sources){
                zip.putNextEntry(new ZipEntry("sources/"+name));
                try(InputStream in=new BufferedInputStream(
                    new FileInputStream(new File(originalFolder,name)))){
                    int count;while((count=in.read(bytes))!=-1)zip.write(bytes,0,count);
                }
                zip.closeEntry();
            }
        }
    }
    private static String calculatedTextureName(File image) throws Exception{
        String suffix=image.getName().endsWith(".jpg")?".jpg":".png";
        String modelHash=calculatedName(image);
        return "tx_"+modelHash.substring(3,27)+suffix;
    }
    public static StagedProject importProject(InputStream input,File cacheRoot) throws Exception{
        File root=new File(cacheRoot,"oe-import-"+UUID.randomUUID());
        check(root.mkdir(),"Нельзя создать временный каталог");
        StagedProject staged=new StagedProject(root);
        try{
            check(staged.modelDir.mkdir(),"Нельзя создать каталог моделей");
            check(staged.textureDir.mkdir(),"Нельзя создать каталог текстур");
            check(staged.sourceDir.mkdir(),"Нельзя создать каталог оригиналов");
            long unpacked=0;
            Set<String> seen=new HashSet<>();
            try(ZipInputStream zip=new ZipInputStream(new BufferedInputStream(input))){
                ZipEntry entry;
                int entries=0;
                byte[] buf=new byte[8192];
                while((entry=zip.getNextEntry())!=null){
                    if(entry.isDirectory()){
                        check("models/".equals(entry.getName())||
                              "textures/".equals(entry.getName())||
                              "sources/".equals(entry.getName()),"Неверная папка в проекте");
                        zip.closeEntry();continue;
                    }
                    String name=entry.getName();
                    boolean scene="scene.json".equals(name);
                    boolean model=name.startsWith("models/")&&
                        validModel(name.substring("models/".length()));
                    boolean texture=name.startsWith("textures/")&&
                        validTexture(name.substring("textures/".length()));
                    boolean original=name.startsWith("sources/")&&
                        validSource(name.substring("sources/".length()));
                    check(scene||model||texture||original,"Запрещён файл в архиве: "+name);
                    check(seen.add(name),"Повторяющийся файл: "+name);
                    entries++;
                    check(entries<=MAX_MODELS*3+MAX_TEXTURES+1,
                          "Слишком много файлов");
                    File target=scene?staged.sceneFile:(model?
                        new File(staged.modelDir,name.substring("models/".length())):
                        (texture?new File(staged.textureDir,name.substring("textures/".length())):
                            new File(staged.sourceDir,name.substring("sources/".length()))));
                    int fileBytes=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                        int n;
                        while((n=zip.read(buf))!=-1){
                            fileBytes+=n;
                            unpacked+=n;
                            check(unpacked<=MAX_UNPACKED_BYTES,"Архив слишком большой");
                            check(fileBytes<=(scene?MAX_SCENE_BYTES:
                                  ((model||original)?GlbReader.MAX_BYTES:MAX_TEXTURE_BYTES)),
                                  "Файл внутри архива слишком большой");
                            out.write(buf,0,n);
                        }
                    }
                    check(fileBytes>0,"Пустой файл внутри проекта");
                    zip.closeEntry();
                }
            }
            check(staged.sceneFile.isFile(),"В проекте отсутствует scene.json");
            String json=new String(Files.readAllBytes(staged.sceneFile.toPath()),StandardCharsets.UTF_8);
            Set<String> required=referencedModels(json);
            for(String name:required){
                File f=new File(staged.modelDir,name);
                check(f.isFile(),"Нет модели: "+name);
                check(name.equals(calculatedName(f)),"Неверный хеш GLB: "+name);
            }
            Set<String> neededTextures=referencedTextures(json);
            for(String name:neededTextures){
                File image=new File(staged.textureDir,name);
                check(image.isFile(),"Нет текстуры: "+name);
                check(name.equals(calculatedTextureName(image)),
                    "Неверная контрольная сумма текстуры: "+name);
            }
            File[] textures=staged.textureDir.listFiles();
            check(textures!=null&&textures.length==neededTextures.size(),
                "Есть лишние или недостающие текстуры");
            File[] originals=staged.sourceDir.listFiles();
            check(originals!=null&&originals.length<=MAX_MODELS*2,
                "Слишком много оригиналов");
            for(File original:originals){
                String sourceName=original.getName();
                check(validSource(sourceName)&&required.contains(sourceModelName(sourceName)),
                    "Оригинал не относится к сцене: "+sourceName);
                validateSource(original,sourceName);
            }
            // Every model in the archive must be referenced by the scene.
            File[] files=staged.modelDir.listFiles();
            check(files!=null&&files.length==required.size(),
                  "Есть лишние или недостающие модели");
            return staged;
        }catch(Exception e){
            removeTree(root);
            throw e;
        }
    }
    public static void removeTree(File root){
        if(root==null||!root.exists())return;
        if(root.isDirectory()){
            File[] children=root.listFiles();
            if(children!=null)for(File child:children)removeTree(child);
        }
        root.delete();
    }
}
