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
    public static final long MAX_UNPACKED_BYTES=384L*1024*1024; // bounded UV44 authoring backups
    public static final int MAX_MODELS=12;
    public static final int MAX_SCENE_BYTES=192*1024*1024; // bounded editable terrain masks (includes base64)
    public static final int MAX_TEXTURES=32,MAX_TEXTURE_BYTES=8*1024*1024;
    // Paint is budgeted by mask bytes, not by tile count or material layers per tile.
    // Match the native 64 MiB budgets in paint_tiles.h / material_layers.h.
    public static final int MAX_PAINT_TILES_BY_MEMORY=(64*1024*1024)/(128*128*4);
    public static final int MAX_MATERIAL_MASKS_BY_MEMORY=(64*1024*1024)/(128*128);
    public static final int MAX_REFERENCE_BYTES=8*1024*1024;

    public static final class StagedProject {
        public final File root, sceneFile, modelDir, textureDir, sourceDir, referenceDir,builderDir;
        private StagedProject(File root){
            this.root=root;
            this.sceneFile=new File(root,"scene.json");
            this.modelDir=new File(root,"models");
            this.textureDir=new File(root,"textures");
            this.sourceDir=new File(root,"sources");
            this.referenceDir=new File(root,"references");
            this.builderDir=new File(root,"builders");
        }
    }

    private static boolean validModel(String name){
        return name!=null && name.matches("oe_[0-9a-f]{24}\\.glb");
    }
    private static boolean validReference(String name){
        return name!=null&&name.matches("ref_[0-9a-f]{24}\\.png");
    }
    private static String referenceName(String scene) throws Exception{
        JSONObject json=new JSONObject(scene);
        int version=json.optInt("version",-1);
        JSONObject guide=json.optJSONObject("mapReference");
        check(version>=1&&version<=13,"Неподдерживаемая версия сцены");
        if(version<3){
            check(!json.has("mapReference"),"Подложка требует сцену версии 3+");
            return null;
        }
        if(version==3)check(guide!=null,"В сцене версии 3 нет карты-подложки");
        if(version>=4&&guide==null){
            check(!json.has("mapReference"),"Повреждённые данные карты");
            return null;
        }
        check(guide!=null,"Неверная PNG-карта");
        String key=guide.getString("key");
        check(validReference(key),"Неверное имя PNG-подложки");
        double x=guide.getDouble("centerX"),z=guide.getDouble("centerZ"),
            width=guide.getDouble("widthMetres"),opacity=guide.getDouble("opacity");
        check(Double.isFinite(x)&&Double.isFinite(z)&&
              Double.isFinite(width)&&Double.isFinite(opacity)&&
              Math.abs(x)<=8192&&Math.abs(z)<=8192&&
              width>=8&&width<=16384&&opacity>=.05&&opacity<=.95,
              "Неверное положение PNG-подложки");
        return key;
    }
    private static String calculatedReferenceName(File file) throws Exception{
        return "ref_"+calculatedName(file).substring(3,27)+".png";
    }
    private static boolean pngSignature(File file) throws IOException{
        final byte[] sig={(byte)137,80,78,71,13,10,26,10};
        try(InputStream in=new BufferedInputStream(new FileInputStream(file))){
            for(byte b:sig)if(in.read()!=(b&255))return false;
        }
        return true;
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
        // R5.5: road image materials are first-class project assets.
        // The referenced PNG/JPG must be packed into .oezip just like
        // a painted building or terrain material; not merely cached in app.
        JSONArray roads=document.optJSONArray("roads");
        int sceneVersion=document.optInt("version",-1);
        if(roads!=null){
            check(roads.length()<=256,"Слишком много дорог");
            for(int i=0;i<roads.length();++i){
                JSONObject road=roads.getJSONObject(i);
                String material=road.getString("material");
                boolean custom=validTexture(material);
                check(!custom||sceneVersion>=8,
                      "PNG/JPG для дороги требует сцену v8");
                if(custom)keys.add(material);
                if(road.has("tileMetres")){
                    check(sceneVersion>=8,"Масштаб дороги требует v8");
                    double metres=road.getDouble("tileMetres");
                    check(Double.isFinite(metres)&&metres>=.5&&metres<=32.,
                          "Неверный масштаб дорожной текстуры");
                }
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
        JSONArray independent=document.optJSONArray("paintLayers");
        int version=document.optInt("version",-1);
        if(version>=5){
            check((version!=5||independent!=null)&&
                  (!document.has("paintLayers")||independent!=null)&&
                  (independent==null||independent.length()<=MAX_MATERIAL_MASKS_BY_MEMORY),
                  "Неверные материальные маски R4.1");
            Set<String> uniqueLayers=new HashSet<>();
            for(int i=0;i<(independent==null?0:independent.length());i++){
                JSONArray layer=independent.getJSONArray(i);
                check(layer.length()==4,"Неверная запись маски");
                int tx=layer.getInt(0),tz=layer.getInt(1);
                String key=layer.getString(2);
                String mask=layer.getString(3);
                String pair=tx+":"+tz;
                check(tx>=-128&&tx<=127&&tz>=-128&&tz<=127&&
                      validTexture(key)&&uniqueLayers.add(pair+":"+key)&&
                      mask.length()>=4&&mask.length()<=28000&&
                      mask.matches("[A-Za-z0-9+/=]+"),
                      "Неверные материальные слои");
                keys.add(key);
            }
        }else check(!document.has("paintLayers"),"Неверная версия слоёв R4.1");
        check(keys.size()<=MAX_TEXTURES,"Более 32 пользовательских текстур");
        return keys;
    }
    private static void check(boolean ok,String reason) throws IOException{
        if(!ok)throw new IOException(reason);
    }
    // Detect invalid or unversioned water bodies before writing/opening an
    // archive. The actual Android editor performs the same preflight again.
    private static void validateLakes(JSONObject json,int version) throws Exception{
        JSONArray water=json.optJSONArray("lakes");
        check((version==7&&water!=null&&water.length()>0)||
              (version<7&&!json.has("lakes"))||
              (version>=8&&(!json.has("lakes")||
                  (water!=null&&water.length()>0))),
              "Повреждённые данные озера или несовместимая версия сцены");
        if(water==null)return;
        check(water.length()<=8,"Слишком много водоёмов");
        int worldSize=json.optInt("worldSize",-1);
        check(worldSize>=1024&&worldSize<=16384,
              "Некорректные размеры мира для озёр");
        for(int i=0;i<water.length();++i){
            JSONArray values=water.getJSONArray(i);
            check(values.length()==4,"Неверная запись озера");
            double x=values.getDouble(0),z=values.getDouble(1);
            double radius=values.getDouble(2),level=values.getDouble(3);
            check(Double.isFinite(x)&&Double.isFinite(z)&&
                  Double.isFinite(radius)&&Double.isFinite(level)&&
                  radius>=8&&radius<=160&&level>=-24&&level<=160&&
                  Math.abs(x)+radius<=worldSize/2.0&&
                  Math.abs(z)+radius<=worldSize/2.0,
                  "Озеро выходит за пределы карты или имеет неверную высоту");
        }
    }
    // UV56 native heightfield is permitted ONLY in a version-9 city file.
    // Previous v1-v8 cities continue to be read exactly as before.
    private static void validateNativeUv56(JSONObject doc,int version) throws Exception {
        JSONObject field=doc.optJSONObject("uv56Terrain");
        check((version==9&&field!=null)||
              (version<9&&!doc.has("uv56Terrain"))||
              (version>=10&&(!doc.has("uv56Terrain")||field!=null)),
            "Повреждённое расширение UV56 / неправильная версия");
        if(field==null)return;
        check(field.optInt("version",-1)==1&&
              "m".equals(field.optString("unit",""))&&
              field.optInt("step",-1)==1,"Неверные единицы высот UV56");
        final double area=field.getDouble("areaSqM");
        final double world=doc.getDouble("worldSize");
        check(Double.isFinite(area)&&area==Math.rint(area)&&
              area>=1000&&area<=16384.0*16384.0&&Math.sqrt(area)<=world,
              "Рельеф UV56 не помещается в сцене");
        JSONArray nodes=field.getJSONArray("nodes");
        check(nodes.length()>0&&nodes.length()<=32768,
              "Превышено количество узлов земли");
        java.util.HashSet<String> unique=new java.util.HashSet<>();
        for(int i=0;i<nodes.length();i++){
            JSONArray row=nodes.getJSONArray(i);
            check(row.length()==4,"Повреждён узел UV56");
            int[] ints=new int[4];
            for(int j=0;j<4;j++){
                double number=row.getDouble(j);
                check(Double.isFinite(number)&&number==Math.rint(number)&&
                      number>=Integer.MIN_VALUE&&number<=Integer.MAX_VALUE,
                      "Некорректное значение UV56");
                ints[j]=(int)number;
            }
            check(Math.abs(ints[0])<=Math.sqrt(area)/2+1&&
                  Math.abs(ints[1])<=Math.sqrt(area)/2+1&&
                  ints[2]>=-60000&&ints[2]<=60000&&
                  ints[3]>=0&&ints[3]<=5&&
                  unique.add(ints[0]+":"+ints[1]),"Неверные узлы UV56");
        }
    }
    // The frame layer never enters Runtime Core or the game scene.
    // v10 is required only when at least one authoring site is saved.
    private static void validateEditorPlots(JSONObject doc,int version) throws Exception {
        JSONArray plots=doc.optJSONArray("editorPlots");
        check(((version==10||version==11)&&plots!=null&&plots.length()>0)||
              (version>=12&&((plots==null&&!doc.has("editorPlots"))||
                  (plots!=null&&plots.length()>0)))||
              (version<10&&!doc.has("editorPlots")),
              "Рамки участков доступны только в сцене v10");
        if(plots==null)return;
        check(plots.length()<=32,"Допускается до 32 участков в одном секторе");
        final double world=doc.getDouble("worldSize")*.5;
        java.util.HashSet<Integer> ids=new java.util.HashSet<>();
        for(int i=0;i<plots.length();i++){
            JSONObject p=plots.getJSONObject(i);
            int id=p.getInt("id"),size=p.getInt("size");
            String label=p.getString("name");
            double x=p.getDouble("x"),z=p.getDouble("z");
            check(id>0&&id<=1000000&&ids.add(id),
                  "Некорректный идентификатор участка");
            check(label!=null&&!label.trim().isEmpty()&&label.length()<=80&&
                  label.getBytes(StandardCharsets.UTF_8).length<=100&&
                  label.indexOf('|')<0&&label.chars().noneMatch(c->Character.isISOControl((char)c)),
                  "Некорректное название участка");
            check(size>=16&&size<=world*2&&Double.isFinite(x)&&
                  Double.isFinite(z)&&Math.abs(x)+size*.5<=world&&
                  Math.abs(z)+size*.5<=world,
                  "Участок выходит за пределы мира");
        }
    }
    // Stage 7: one active PNG on screen, but each editor plot retains its
    // own independent original file in the portable .oezip archive.
    // No picture is included in gameplay collision or runtime entities.
    private static Set<String> referenceNames(String scene) throws Exception {
        JSONObject doc=new JSONObject(scene);
        int version=doc.optInt("version",-1);
        Set<String> names=new HashSet<>();
        String legacy=referenceName(scene);
        if(legacy!=null)names.add(legacy);
        JSONArray plans=doc.optJSONArray("plotPlans");
        check((version==11&&plans!=null&&plans.length()>0)||
              (version>=12&&((plans==null&&!doc.has("plotPlans"))||
                  (plans!=null&&plans.length()>0)))||
              (version<11&&!doc.has("plotPlans")),
              "Сцена v11 должна содержать PNG-планы участков");
        if(plans==null)return names;
        check(plans.length()<=8,"В первой версии доступно не больше 8 PNG-планов");
        JSONArray plots=doc.getJSONArray("editorPlots");
        Set<Integer> usedPlotIds=new HashSet<>();
        int world=doc.getInt("worldSize");
        for(int i=0;i<plans.length();i++){
            JSONObject ref=plans.getJSONObject(i);
            int id=ref.getInt("plotId");
            check(usedPlotIds.add(id),"Участок имеет повторяющийся PNG-план");
            JSONObject target=null;
            for(int k=0;k<plots.length();k++){
                JSONObject plot=plots.getJSONObject(k);
                if(plot.getInt("id")==id){target=plot;break;}
            }
            check(target!=null,"PNG-план без участка");
            String key=ref.getString("key");
            check(validReference(key),"Некорректное имя PNG-плана участка");
            double x=ref.getDouble("centerX"),z=ref.getDouble("centerZ"),
                width=ref.getDouble("widthMetres"),opacity=ref.getDouble("opacity");
            ref.getBoolean("visible");
            double size=target.getDouble("size");
            double px=target.getDouble("x"),pz=target.getDouble("z");
            check(Double.isFinite(x)&&Double.isFinite(z)&&
                  Double.isFinite(width)&&Double.isFinite(opacity)&&
                  width>=8&&width<=size&&opacity>=.05&&opacity<=.95&&
                  Math.abs(x-px)+width*.5<=size*.5&&
                  Math.abs(z-pz)<=size*.5&&
                  Math.abs(x)+width*.5<=world*.5,
                  "PNG-план выходит за пределы участка");
            names.add(key);
        }
        return names;
    }
    private static void validateCollisionWalls(JSONObject doc,int version)
        throws Exception {
        JSONArray walls=doc.optJSONArray("collisionWalls");
        check((version>=12&&((walls==null&&!doc.has("collisionWalls"))||
                  (walls!=null&&walls.length()>0)))||
              (version<12&&!doc.has("collisionWalls")),
              "Физические коллизии доступны только в сцене v12");
        if(walls==null)return;
        check(walls.length()<=64,"Максимум 64 коллизионных отрезка");
        double half=doc.getDouble("worldSize")*.5;
        Set<Integer> ids=new HashSet<>();
        for(int i=0;i<walls.length();i++){
            JSONArray row=walls.getJSONArray(i);
            check(row.length()==7,"Физическая стена требует семь параметров");
            double idRaw=row.getDouble(0);
            check(Double.isFinite(idRaw)&&idRaw>0&&idRaw<=1000000&&
                  Math.rint(idRaw)==idRaw&&ids.add((int)idRaw),
                  "Некорректный номер коллизии");
            double ax=row.getDouble(1),az=row.getDouble(2),
                   bx=row.getDouble(3),bz=row.getDouble(4),
                   width=row.getDouble(5),height=row.getDouble(6);
            check(Double.isFinite(ax)&&Double.isFinite(az)&&
                  Double.isFinite(bx)&&Double.isFinite(bz)&&
                  Double.isFinite(width)&&Double.isFinite(height)&&
                  Math.abs(ax)<=half-1&&Math.abs(bx)<=half-1&&
                  Math.abs(az)<=half-1&&Math.abs(bz)<=half-1&&
                  Math.hypot(bx-ax,bz-az)>=1.&&
                  Math.hypot(bx-ax,bz-az)<=512.&&
                  width>=.3&&width<=4.&&height>=1.&&height<=30.,
                  "Физическая коллизия вне карты или имеет неверный размер");
        }
    }

    private static Set<String> referencedModels(String scene) throws Exception {
        check(scene.getBytes(StandardCharsets.UTF_8).length<=MAX_SCENE_BYTES,"Сцена превышает безопасный лимит");
        JSONObject json=new JSONObject(scene);
        int version=json.optInt("version",-1);
        check(version>=1&&version<=13,"Неподдерживаемая версия сцены");
        check(version==1||json.optJSONArray("roads")!=null,"Нет дорожной сети");
        validateLakes(json,version);
        validateNativeUv56(json,version);
        validateEditorPlots(json,version);
        validateCollisionWalls(json,version);
        PhoenixHouseLinks.parseSceneLinks(json);
        referenceNames(scene);
        JSONArray tileData=json.optJSONArray("paintTiles");
        if(version>=4){
            check((version!=4||tileData!=null)&&
                  (tileData==null||tileData.length()<=MAX_PAINT_TILES_BY_MEMORY),
                  "В сцене R4 отсутствуют или превышены текстурные участки");
            Set<String> seenTiles=new HashSet<>();
            for(int i=0;i<(tileData==null?0:tileData.length());i++){
                JSONArray tile=tileData.getJSONArray(i);
                check(tile.length()==3,"Неверный формат R4");
                int tx=tile.getInt(0),tz=tile.getInt(1);
                String bytes=tile.getString(2);
                check(tx>=-128&&tx<=127&&tz>=-128&&tz<=127&&
                      seenTiles.add(tx+":"+tz)&&bytes.length()>=4&&
                      bytes.length()<=100000&&bytes.matches("[A-Za-z0-9+/=]+"),
                      "Неверный участок кисти R4");
            }
        }else check(!json.has("paintTiles"),"Участки кисти требуют формат v4+");
        JSONArray items=json.getJSONArray("items");
        check(items.length()<=300,"Слишком много объектов");
        Set<String> names=new HashSet<>();
        for(int i=0;i<items.length();i++){
            JSONObject item=items.getJSONObject(i);
            int kind=item.getInt("type");
            check(kind>=0&&kind<=28&&(kind<=16||version>=6),
                  "Недопустимый тип средневекового объекта");
            if(kind!=6)continue;
            String key=item.getString("asset");
            check(validModel(key),"Недопустимое имя модели");
            names.add(key);
        }
        check(names.size()<=MAX_MODELS,"Слишком много разных GLB");
        return names;
    }
    private static Set<String> referencedBuilderSources(String scene)
        throws Exception {
        return new HashSet<>(
            PhoenixHouseLinks.parseSceneLinks(new JSONObject(scene)).values());
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
             File originalFolder,File referenceFolder,OutputStream output) throws Exception{
        exportProject(scene,modelFolder,textureFolder,originalFolder,
            referenceFolder,null,output);
    }
    public static void exportProject(String scene,File modelFolder,File textureFolder,
             File originalFolder,File referenceFolder,File builderFolder,
             OutputStream output) throws Exception{
        Set<String> names=referencedModels(scene);
        Set<String> sourcesUV44=referencedBuilderSources(scene);
        Set<String> textures=referencedTextures(scene);
        Set<String> references=referenceNames(scene);
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
        for(String reference:references){
            File png=new File(referenceFolder,reference);
            check(png.isFile()&&png.length()>0&&
                  png.length()<=MAX_REFERENCE_BYTES,"PNG-подложка отсутствует");
            check(reference.equals(calculatedReferenceName(png))&&pngSignature(png),
                  "Повреждённая PNG-подложка");
            budget+=png.length();
            check(budget<=MAX_UNPACKED_BYTES,"Слишком много PNG-планов");
        }
        // Editable house backups never enter the GLES frame or scene.json.
        for(String uv44:sourcesUV44){
            check(builderFolder!=null,"Нет каталога редактируемых домов UV44");
            File source=new File(builderFolder,uv44);
            PhoenixHouseLinks.validateBackup(source);
            check(PhoenixHouseLinks.shaName(source).equals(uv44),
                "Повреждён редактируемый дом UV44: "+uv44);
            budget+=source.length();
            check(budget<=MAX_UNPACKED_BYTES,"Слишком большой архив домов OE");
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
            // PNG city plan remains an editor-only source; Runtime never sees it.
            for(String reference:references){
                zip.putNextEntry(new ZipEntry("references/"+reference));
                try(InputStream in=new BufferedInputStream(
                    new FileInputStream(new File(referenceFolder,reference)))){
                    int count;while((count=in.read(bytes))!=-1)zip.write(bytes,0,count);
                }
                zip.closeEntry();
            }
            for(String name:sourcesUV44){
                zip.putNextEntry(new ZipEntry("builders/"+name));
                try(InputStream in=new BufferedInputStream(
                    new FileInputStream(new File(builderFolder,name)))){
                    int count;while((count=in.read(bytes))!=-1)
                        zip.write(bytes,0,count);
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
            check(staged.referenceDir.mkdir(),"Нельзя создать каталог карт-подложек");
            check(staged.builderDir.mkdir(),"Нельзя создать каталог UV44-домов");
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
                              "sources/".equals(entry.getName())||
                              "references/".equals(entry.getName())||
                              "builders/".equals(entry.getName()),"Неверная папка в проекте");
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
                    boolean reference=name.startsWith("references/")&&
                        validReference(name.substring("references/".length()));
                    boolean builder=name.startsWith("builders/")&&
                        PhoenixHouseLinks.backupKey(
                            name.substring("builders/".length()));
                    check(scene||model||texture||original||reference||builder,
                          "Запрещён файл в архиве: "+name);
                    check(seen.add(name),"Повторяющийся файл: "+name);
                    entries++;
                    check(entries<=MAX_MODELS*3+MAX_TEXTURES+12+
                          PhoenixHouseLinks.MAX_ARCHIVED_LINKS,
                          "Слишком много файлов");
                    File target;
                    if(scene)target=staged.sceneFile;
                    else if(model)target=new File(staged.modelDir,
                        name.substring("models/".length()));
                    else if(texture)target=new File(staged.textureDir,
                        name.substring("textures/".length()));
                    else if(original)target=new File(staged.sourceDir,
                        name.substring("sources/".length()));
                    else if(reference)target=new File(staged.referenceDir,
                        name.substring("references/".length()));
                    else target=new File(staged.builderDir,
                        name.substring("builders/".length()));
                    int fileBytes=0;
                    try(OutputStream out=new BufferedOutputStream(new FileOutputStream(target))){
                        int n;
                        while((n=zip.read(buf))!=-1){
                            fileBytes+=n;
                            unpacked+=n;
                            check(unpacked<=MAX_UNPACKED_BYTES,"Архив слишком большой");
                            check(fileBytes<=(scene?MAX_SCENE_BYTES:
                                  ((model||original)?GlbReader.MAX_BYTES:
                                   (builder?PhoenixHouseLinks.MAX_BACKUP_BYTES:
                                    (reference?MAX_REFERENCE_BYTES:MAX_TEXTURE_BYTES)))),
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
            Set<String> references=referenceNames(json);
            Set<String> uv44Sources=referencedBuilderSources(json);
            File[] builderFiles=staged.builderDir.listFiles();
            check(builderFiles!=null&&builderFiles.length==uv44Sources.size(),
                "В архиве лишние или отсутствующие редактируемые дома UV44");
            for(String name:uv44Sources){
                File source=new File(staged.builderDir,name);
                PhoenixHouseLinks.validateBackup(source);
                check(PhoenixHouseLinks.shaName(source).equals(name),
                      "Неверная контрольная сумма UV44-дома");
            }
            File[] referenceFiles=staged.referenceDir.listFiles();
            check(referenceFiles!=null&&referenceFiles.length==references.size(),
                "Неправильный состав PNG-подложек");
            for(String reference:references){
                File png=new File(staged.referenceDir,reference);
                check(png.isFile()&&png.length()>0&&
                      png.length()<=MAX_REFERENCE_BYTES&&
                      reference.equals(calculatedReferenceName(png))&&pngSignature(png),
                      "Неверная PNG-подложка в архиве");
            }
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
