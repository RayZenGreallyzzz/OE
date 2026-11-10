package game.oe.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import android.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Minimal, offline glTF 2.0 GLB STATIC triangle importer.
 *
 * Supports: GLB 2; float32 or quantized POSITION/COLOR_0; binary bufferViews
 * compressed with EXT_meshopt_compression / KHR_meshopt_compression;
 * uint8/uint16/uint32 indices, node TRS hierarchy and baseColorFactor.
 * Builds static face normals. Meshopt is decoded OFFLINE by vendored C++.
 * GPU-native ETC2/RGBA8 KTX2 and embedded PNG/JPG baseColor are supported.
 * Draco, BasisU ETC1S/UASTC, skinning and animation remain unsupported.
 * No original source data is rewritten by this reader.
 * Defensive limits are intentional for low-memory Android tablets.
 */
public final class GlbReader {
    public static final int MAX_BYTES = 64 * 1024 * 1024;
    public static final int MAX_TRIANGLES = 90000;
    public static final int MAX_NODES = 1024;
    private static final int MAX_DECODED_VIEW = 32 * 1024 * 1024;
    private static final int MAX_DECODED_TOTAL = 48 * 1024 * 1024;
    // Bound native codec inputs/outputs before allocation.
    private static native byte[] nativeDecodeMeshopt(byte[] compressed,int count,int stride,int mode,int filter);

    public static final class Result {
        public final float[] vertices; // [pos.xyz, normal.xyz, color.rgb, uv.xy] per vertex
        public final int[] draws; // consecutive triplets [firstVertex,vertexCount,textureSlot] (-1 = plain color)
        public final byte[][] imageData; // RGBA8 or ETC2, indexed by textureSlot
        public final int[] imageInfo; // [width,height,pixelFormat] per textureSlot
        public final int triangles;
        // Bounds are computed after normalization (the resulting local origin
        // is centered on X/Z and grounded at Y=0). Used for conservative culling.
        public final float boundCenterY;
        public final float boundRadius;
        // Visual preview limitations: original GLB bytes are never rewritten.
        public final boolean previewOmitsTextures, previewOmitsRig;
        Result(float[] vertices, int triangles, float boundCenterY, float boundRadius,
               boolean previewOmitsTextures, boolean previewOmitsRig,
               int[] draws, byte[][] imageData, int[] imageInfo) {
            this.vertices = vertices;
            this.triangles = triangles;
            this.boundCenterY = boundCenterY;
            this.boundRadius = boundRadius;
            this.previewOmitsTextures=previewOmitsTextures;
            this.previewOmitsRig=previewOmitsRig;
            this.draws=draws;
            this.imageData=imageData;
            this.imageInfo=imageInfo;
        }
    }

    private static final class Access {
        int offset, step, count, component, components;
        boolean normalized;
        ByteBuffer data;
    }
    private static final class Job {
        Access positions, indices, uv, colors;
        int triangles, textureIndex=-1;
        float[] matrix, color;
    }
    private static final class State {
        final JSONObject gltf;
        final ByteBuffer bin;
        final ArrayList<Job> jobs = new ArrayList<>();
        final Map<Integer,ByteBuffer> decodedViews = new HashMap<>();
        int decodedBytes;
        int triangles;
        State(JSONObject json, ByteBuffer binary) { gltf=json;bin=binary; }
    }
    private GlbReader() {}

    public static String key(byte[] data) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha.digest(data);
        StringBuilder b = new StringBuilder("oe_");
        for (int i=0;i<12;i++) b.append(String.format(java.util.Locale.US,"%02x",digest[i] & 255));
        return b.append(".glb").toString();
    }

    private static void require(boolean ok, String error) throws Exception {
        if (!ok) throw new Exception(error);
    }
    private static int u32(ByteBuffer data, int offset) {
        return data.getInt(offset);
    }
    private static float[] identity() {
        return new float[]{1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1};
    }
    private static float[] multiply(float[] a,float[] b) {
        float[] m = new float[16];
        for(int c=0;c<4;c++) for(int r=0;r<4;r++)
            for(int k=0;k<4;k++) m[c*4+r]+=a[k*4+r]*b[c*4+k];
        return m;
    }
    private static float[] transform(float[] m,float x,float y,float z) {
        float w=m[3]*x+m[7]*y+m[11]*z+m[15];
        if(Math.abs(w)<.00001f) w=1f;
        return new float[]{(m[0]*x+m[4]*y+m[8]*z+m[12])/w,
                (m[1]*x+m[5]*y+m[9]*z+m[13])/w,
                (m[2]*x+m[6]*y+m[10]*z+m[14])/w};
    }
    private static float[] numbers(JSONArray a,int count,float[] fallback) throws Exception {
        if(a==null) return fallback;
        require(a.length()==count,"Неверная матрица/вектор в GLB");
        float[] values = new float[count];
        for(int i=0;i<count;i++) {
            double v=a.getDouble(i);
            require(Double.isFinite(v) && Math.abs(v)<1e6,"Неверные координаты GLB");
            values[i]=(float)v;
        }
        return values;
    }
    private static float[] nodeTransform(JSONObject node) throws Exception {
        JSONArray matrix = node.optJSONArray("matrix");
        if(matrix!=null) return numbers(matrix,16,identity());
        float[] t = numbers(node.optJSONArray("translation"),3,new float[]{0,0,0});
        float[] s = numbers(node.optJSONArray("scale"),3,new float[]{1,1,1});
        float[] q = numbers(node.optJSONArray("rotation"),4,new float[]{0,0,0,1});
        float len=(float)Math.hypot(Math.hypot(q[0],q[1]),Math.hypot(q[2],q[3]));
        require(len>.0001f,"Неверный поворот узла");
        for(int i=0;i<4;i++)q[i]/=len;
        float x=q[0],y=q[1],z=q[2],w=q[3];
        float[] m=identity();
        m[0]=(1-2*y*y-2*z*z)*s[0];m[1]=(2*x*y+2*z*w)*s[0];m[2]=(2*x*z-2*y*w)*s[0];
        m[4]=(2*x*y-2*z*w)*s[1];m[5]=(1-2*x*x-2*z*z)*s[1];m[6]=(2*y*z+2*x*w)*s[1];
        m[8]=(2*x*z+2*y*w)*s[2];m[9]=(2*y*z-2*x*w)*s[2];m[10]=(1-2*x*x-2*y*y)*s[2];
        m[12]=t[0];m[13]=t[1];m[14]=t[2];return m;
    }
    private static ByteBuffer decodeView(State st,int viewIndex,JSONObject view) throws Exception {
        ByteBuffer already=st.decodedViews.get(viewIndex);
        if(already!=null)return already;
        JSONObject extensions=view.optJSONObject("extensions");
        JSONObject packed=extensions==null?null:extensions.optJSONObject("EXT_meshopt_compression");
        if(packed==null&&extensions!=null)
            packed=extensions.optJSONObject("KHR_meshopt_compression");
        require(packed!=null,"Неверный bufferView Meshopt");
        require(packed.optInt("buffer",-1)==0,
                "Meshopt с внешним буфером пока не поддерживается");
        int count=packed.getInt("count"),stride=packed.getInt("byteStride");
        long decodedLength=(long)count*stride;
        int expected=view.getInt("byteLength");
        require(count>0&&count<=MAX_TRIANGLES*3&&stride>0&&stride<=256&&
                decodedLength==expected&&decodedLength<=MAX_DECODED_VIEW&&
                (long)st.decodedBytes+decodedLength<=MAX_DECODED_TOTAL,
                "Слишком большой распакованный Meshopt bufferView");
        String mode=packed.getString("mode"),filter=packed.optString("filter","NONE");
        int modeId="ATTRIBUTES".equals(mode)?0:("TRIANGLES".equals(mode)?1:
                   ("INDICES".equals(mode)?2:-1));
        int filterId="NONE".equals(filter)?0:("OCTAHEDRAL".equals(filter)?1:
                     ("QUATERNION".equals(filter)?2:("EXPONENTIAL".equals(filter)?3:
                     ("COLOR".equals(filter)?4:-1))));
        require(modeId>=0&&filterId>=0,"Неизвестный формат сжатия Meshopt");
        require(!view.has("byteStride")||view.getInt("byteStride")==stride,
                "Несовместимый шаг Meshopt bufferView");
        int off=packed.optInt("byteOffset",0),len=packed.getInt("byteLength");
        require(off>=0&&len>0&&len<=MAX_BYTES&&(long)off+len<=st.bin.capacity(),
                "Повреждённые байты сжатого Meshopt");
        byte[] compressed=new byte[len];
        ByteBuffer input=st.bin.duplicate();
        input.position(off);
        input.get(compressed);
        byte[] decoded=nativeDecodeMeshopt(compressed,count,stride,modeId,filterId);
        require(decoded!=null&&decoded.length==expected,
                "Не удалось декодировать Meshopt bufferView");
        ByteBuffer result=ByteBuffer.wrap(decoded).order(ByteOrder.LITTLE_ENDIAN);
        st.decodedViews.put(viewIndex,result);
        st.decodedBytes+=decoded.length;
        return result;
    }
    private static Access accessor(State st,int index,int role) throws Exception {
        JSONArray accessors=st.gltf.getJSONArray("accessors");
        require(index>=0 && index<accessors.length(),"Нет accessor");
        JSONObject obj=accessors.getJSONObject(index);
        require(!obj.has("sparse"),"Sparse accessor пока не поддерживается");
        int component=obj.getInt("componentType"),count=obj.getInt("count");
        String type=obj.getString("type");
        require(count>0 && count<=MAX_TRIANGLES*3,"Слишком много вершин или индексов");
        boolean validPosition=component==5126||component==5120||component==5121||
                              component==5122||component==5123;
        require((role==1&&"VEC3".equals(type)&&validPosition)||
                (role==2&&"VEC2".equals(type)&&validPosition)||
                (role==3&&("VEC3".equals(type)||"VEC4".equals(type))&&validPosition)||
                (role==0&&"SCALAR".equals(type)&&
                  (component==5121||component==5123||component==5125)),
                "Формат вершин, цвета, UV или индексов GLB не поддерживается");
        int componentBytes=component==5120||component==5121?1:
                           (component==5122||component==5123?2:4);
        int components=role==1||role==3?("VEC4".equals(type)?4:3):
            (role==2?2:1);
        int width=componentBytes*components;
        int viewIndex=obj.getInt("bufferView");
        JSONArray views=st.gltf.getJSONArray("bufferViews");
        require(viewIndex>=0 && viewIndex<views.length(),"Нет bufferView");
        JSONObject view=views.getJSONObject(viewIndex);
        JSONObject extensions=view.optJSONObject("extensions");
        boolean meshopt=extensions!=null&&
               (extensions.has("EXT_meshopt_compression")||extensions.has("KHR_meshopt_compression"));
        ByteBuffer data=meshopt?decodeView(st,viewIndex,view):st.bin;
        int length=view.getInt("byteLength");
        int stride=meshopt?
            data.capacity()>0&&extensions.has("EXT_meshopt_compression")?
                extensions.getJSONObject("EXT_meshopt_compression").getInt("byteStride"):
                extensions.getJSONObject("KHR_meshopt_compression").getInt("byteStride"):
            view.optInt("byteStride",width);
        int off=meshopt?0:view.optInt("byteOffset",0);
        int accessorOffset=obj.optInt("byteOffset",0);
        long required=(long)accessorOffset+(long)(count-1)*stride+width;
        require((meshopt||view.optInt("buffer",0)==0)&&
                off>=0&&accessorOffset>=0&&length>=0&&stride>=width&&
                required<=length&&(long)off+required<=data.capacity(),
                "Повреждённый accessor GLB");
        Access a=new Access();
        a.offset=off+accessorOffset;a.step=stride;a.count=count;a.component=component;
        a.components=components;
        a.normalized=obj.optBoolean("normalized",false);a.data=data;
        return a;
    }
    private static int readIndex(State st,Access a,int index) throws Exception {
        if(a==null)return index;
        require(index>=0 && index<a.count,"Индекс за пределами");
        int at=a.offset+index*a.step;
        long value=a.component==5121?(a.data.get(at)&255):
                a.component==5123?(a.data.getShort(at)&65535):
                Integer.toUnsignedLong(a.data.getInt(at));
        require(value<Integer.MAX_VALUE,"Слишком большой индекс");return (int)value;
    }
    private static float positionComponent(Access a,int byteOffset) throws Exception {
        switch(a.component){
            case 5126: return a.data.getFloat(byteOffset);
            case 5120: {
                int n=a.data.get(byteOffset);
                return a.normalized?Math.max(-1f,n/127f):n;
            }
            case 5121: {
                int n=a.data.get(byteOffset)&255;
                return a.normalized?n/255f:n;
            }
            case 5122: {
                int n=a.data.getShort(byteOffset);
                return a.normalized?Math.max(-1f,n/32767f):n;
            }
            case 5123: {
                int n=a.data.getShort(byteOffset)&65535;
                return a.normalized?n/65535f:n;
            }
            default: throw new Exception("Неизвестный компонент позиции GLB");
        }
    }
    private static float[] position(State st,Access a,int index,float[] matrix) throws Exception {
        require(index>=0 && index<a.count,"Индекс вершины за пределами");
        int at=a.offset+index*a.step;
        int componentBytes=a.component==5120||a.component==5121?1:
                            (a.component==5122||a.component==5123?2:4);
        float x=positionComponent(a,at),y=positionComponent(a,at+componentBytes),
              z=positionComponent(a,at+2*componentBytes);
        require(Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(z),"NaN в геометрии GLB");
        return transform(matrix,x,y,z);
    }
    private static float[] color(State st,JSONObject primitive) throws Exception {
        int index=primitive.optInt("material",-1);
        if(index<0)return new float[]{.8f,.76f,.69f};
        JSONArray materials=st.gltf.optJSONArray("materials");
        if(materials==null||index>=materials.length())return new float[]{.8f,.76f,.69f};
        JSONObject material=materials.getJSONObject(index);
        JSONArray factor=material.optJSONObject("pbrMetallicRoughness")==null?null:
                material.getJSONObject("pbrMetallicRoughness").optJSONArray("baseColorFactor");
        float[] rgba=numbers(factor,4,new float[]{.85f,.80f,.73f,1});
        return new float[]{Math.max(0,Math.min(1,rgba[0])),
                Math.max(0,Math.min(1,rgba[1])),
                Math.max(0,Math.min(1,rgba[2]))};
    }
    private static float[] vertexColor(Access colors,int vertex) throws Exception {
        require(colors!=null&&vertex>=0&&vertex<colors.count,"Индекс цвета за пределами GLB");
        int step=colors.component==5120||colors.component==5121?1:
                (colors.component==5122||colors.component==5123?2:4);
        int offset=colors.offset+vertex*colors.step;
        float[] rgb=new float[3];
        for(int i=0;i<3;i++){
            float component=positionComponent(colors,offset+i*step);
            require(Float.isFinite(component)&&component>=0&&component<=1.f,
                "Некорректный COLOR_0");
            rgb[i]=component;
        }
        return rgb;
    }
    private static float[] texCoord(Access uv,int vertex) throws Exception {
        require(uv!=null&&vertex>=0&&vertex<uv.count,"UV за пределами");
        int step=uv.component==5120||uv.component==5121?1:
                 (uv.component==5122||uv.component==5123?2:4);
        int offset=uv.offset+vertex*uv.step;
        float u=positionComponent(uv,offset),v=positionComponent(uv,offset+step);
        require(Float.isFinite(u)&&Float.isFinite(v)&&Math.abs(u)<8192&&Math.abs(v)<8192,
                "Некорректные координаты UV");
        return new float[]{u,v};
    }
    private static TextureReader.Image readTexture(State st,int textureIndex,int limit) throws Exception {
        JSONArray textures=st.gltf.optJSONArray("textures"),images=st.gltf.optJSONArray("images");
        require(textures!=null&&images!=null&&textureIndex>=0&&textureIndex<textures.length(),
            "Недоступна текстура GLB");
        JSONObject texture=textures.getJSONObject(textureIndex);
        JSONObject ext=texture.optJSONObject("extensions");
        JSONObject basis=ext==null?null:ext.optJSONObject("KHR_texture_basisu");
        if(basis!=null){
            int imageIndex=basis.getInt("source");
            try{return readImage(st,images,imageIndex,limit);}
            catch(Exception ktxProblem){
                if(!texture.has("source"))
                    throw new Exception("KTX2 нельзя загрузить: "+ktxProblem.getMessage());
                // A valid PNG/JPEG fallback may be used instead of unsupported BasisU.
            }
        }
        require(texture.has("source"),"В GLB нет изображения для текстуры");
        return readImage(st,images,texture.getInt("source"),limit);
    }
    private static TextureReader.Image readImage(State st,JSONArray images,
            int imageIndex,int limit) throws Exception {
        require(imageIndex>=0&&imageIndex<images.length(),"Неверная ссылка на изображение");
        JSONObject image=images.getJSONObject(imageIndex);
        String mime=image.optString("mimeType","");
        byte[] bytes;
        if(image.has("bufferView")){
            int viewIndex=image.getInt("bufferView");
            JSONArray views=st.gltf.getJSONArray("bufferViews");
            require(viewIndex>=0&&viewIndex<views.length(),"Неверный bufferView изображения");
            JSONObject view=views.getJSONObject(viewIndex);
            require(view.optInt("buffer",0)==0,"Нельзя импортировать внешнюю текстуру");
            int start=view.optInt("byteOffset",0),length=view.getInt("byteLength");
            require(start>=0&&length>0&&length<=TextureReader.MAX_SOURCE_BYTES&&
                (long)start+length<=st.bin.capacity(),"Повреждённый bufferView текстуры");
            bytes=new byte[length];
            ByteBuffer b=st.bin.duplicate();
            b.position(start);b.get(bytes);
        }else{
            String uri=image.optString("uri","");
            int separator=uri.indexOf(",");
            require(uri.startsWith("data:image/")&&separator>0&&
                uri.substring(0,separator).endsWith(";base64"),
                "В GLB нужны встроенные PNG/JPG/KTX2 изображения");
            if(mime.isEmpty())mime=uri.substring(5,uri.indexOf(";base64"));
            String encoded=uri.substring(separator+1);
            require(encoded.length()<=TextureReader.MAX_SOURCE_BYTES*2,
                "Слишком большое изображение data URI");
            bytes=Base64.decode(encoded,Base64.DEFAULT);
        }
        return TextureReader.decode(bytes,mime,limit);
    }
    private static void visit(State st,int nodeIndex,float[] parent,Set<Integer> path,int depth) throws Exception {
        JSONArray nodes=st.gltf.getJSONArray("nodes");
        require(nodeIndex>=0&&nodeIndex<nodes.length(),"Неверный узел");
        require(depth<32 && path.add(nodeIndex),"Цикл или слишком глубокая иерархия GLB");
        JSONObject node=nodes.getJSONObject(nodeIndex);
        float[] matrix=multiply(parent,nodeTransform(node));
        int meshIndex=node.optInt("mesh",-1);
        if(meshIndex>=0) {
            JSONArray meshes=st.gltf.getJSONArray("meshes");
            require(meshIndex<meshes.length(),"Неверный mesh");
            JSONArray primitives=meshes.getJSONObject(meshIndex).getJSONArray("primitives");
            for(int i=0;i<primitives.length();i++){
                JSONObject p=primitives.getJSONObject(i);
                JSONObject primitiveExt=p.optJSONObject("extensions");
                if(primitiveExt!=null&&primitiveExt.has("KHR_draco_mesh_compression"))
                    throw new Exception("GLB использует Draco: декодер ещё не встроен в OE");
                if(p.optInt("mode",4)!=4)continue; // only triangle lists
                JSONObject attrs=p.optJSONObject("attributes");
                if(attrs==null||!attrs.has("POSITION"))continue;
                Access positions=accessor(st,attrs.getInt("POSITION"),1);
                Access indices=p.has("indices")?accessor(st,p.getInt("indices"),0):null;
                Access uv=attrs.has("TEXCOORD_0")?accessor(st,attrs.getInt("TEXCOORD_0"),2):null;
                Access colors=attrs.has("COLOR_0")?accessor(st,attrs.getInt("COLOR_0"),3):null;
                int count=indices==null?positions.count:indices.count;
                int triangles=count/3;
                if(triangles==0)continue;
                require((long)st.triangles+triangles<=MAX_TRIANGLES,"Модель больше 90 000 треугольников");
                Job j=new Job();j.positions=positions;j.indices=indices;j.uv=uv;
                j.colors=colors;
                j.triangles=triangles;j.matrix=matrix;j.color=color(st,p);
                int materialIndex=p.optInt("material",-1);
                JSONArray materials=st.gltf.optJSONArray("materials");
                if(materialIndex>=0&&materials!=null&&materialIndex<materials.length()&&uv!=null){
                    JSONObject mat=materials.getJSONObject(materialIndex);
                    JSONObject pbr=mat.optJSONObject("pbrMetallicRoughness");
                    JSONObject textureInfo=pbr==null?null:pbr.optJSONObject("baseColorTexture");
                    if(textureInfo!=null){
                        require(textureInfo.optInt("texCoord",0)==0,
                            "GLB: поддерживается только UV TEXCOORD_0");
                        JSONObject ex=textureInfo.optJSONObject("extensions");
                        require(ex==null||!ex.has("KHR_texture_transform"),
                            "GLB: трансформация UV пока не поддерживается");
                        j.textureIndex=textureInfo.getInt("index");
                    }
                }
                st.jobs.add(j);st.triangles+=triangles;
            }
        }
        JSONArray children=node.optJSONArray("children");
        if(children!=null)for(int i=0;i<children.length();i++)visit(st,children.getInt(i),matrix,path,depth+1);
        path.remove(nodeIndex);
    }
    public static Result decode(byte[] data) throws Exception {
        return decode(data,TextureReader.DEFAULT_LIMIT);
    }
    public static Result decode(byte[] data,int textureLimit) throws Exception {
        require(textureLimit==1024||textureLimit==2048,"Укажи 1024 или 2048 px");
        require(data!=null && data.length>=20 && data.length<=MAX_BYTES,"Размер GLB должен быть до 24 МБ");
        ByteBuffer src=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        require(u32(src,0)==0x46546c67 && u32(src,4)==2 && Integer.toUnsignedLong(u32(src,8))==data.length,
                "Нужен бинарный файл GLB версии 2.0");
        int at=12,jsonAt=-1,jsonLen=0,binAt=-1,binLen=0;
        while(at+8<=data.length){
            int len=u32(src,at),kind=u32(src,at+4);
            long end=(long)at+8+Integer.toUnsignedLong(len);
            require(len>=0&&end<=data.length,"Повреждённая структура GLB");
            if(kind==0x4e4f534a){jsonAt=at+8;jsonLen=len;}
            if(kind==0x004e4942){binAt=at+8;binLen=len;}
            at=(int)end;
        }
        require(at==data.length && jsonAt>=0 && binAt>=0 && binLen>0,"GLB без JSON или BIN");
        JSONObject gltf=new JSONObject(new String(data,jsonAt,jsonLen,StandardCharsets.UTF_8).trim());
        JSONObject meta=gltf.optJSONObject("asset");
        require(meta!=null&&meta.optString("version","").startsWith("2."),
            "GLB содержит неподдерживаемую версию glTF");
        JSONArray required=gltf.optJSONArray("extensionsRequired");
        if(required!=null)for(int i=0;i<required.length();i++){
            String extension=required.getString(i);
            if(!"EXT_meshopt_compression".equals(extension)&&
               !"KHR_meshopt_compression".equals(extension)&&
               !"KHR_mesh_quantization".equals(extension)&&
               !"KHR_texture_basisu".equals(extension))
                throw new Exception("Неподдерживаемое обязательное расширение GLB: "+extension);
        }
        ByteBuffer binary=ByteBuffer.wrap(data,binAt,binLen).slice().order(ByteOrder.LITTLE_ENDIAN);
        State st=new State(gltf,binary);
        JSONArray nodes=gltf.optJSONArray("nodes");
        if(nodes!=null && nodes.length()>0){
            require(nodes.length()<=MAX_NODES,"Слишком много узлов в GLB");
            JSONArray scenes=gltf.optJSONArray("scenes");
            if(scenes!=null && scenes.length()>0) {
                int index=gltf.optInt("scene",0);
                require(index>=0&&index<scenes.length(),"Неверная сцена GLB");
                JSONArray roots=scenes.getJSONObject(index).optJSONArray("nodes");
                if(roots!=null)for(int i=0;i<roots.length();i++)visit(st,roots.getInt(i),identity(),new HashSet<>(),0);
            } else for(int i=0;i<nodes.length();i++)visit(st,i,identity(),new HashSet<>(),0);
        } else throw new Exception("В GLB нет узлов сцены");
        require(st.triangles>0,"В GLB нет доступной треугольной геометрии");
        float[] verts=new float[st.triangles*33];
        int[] draws=new int[st.jobs.size()*3];
        Map<Integer,Integer> usedTextures=new LinkedHashMap<>();
        int drawIndex=0;
        int out=0;float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX;
        float maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(Job j:st.jobs){
            int slot=-1;
            if(j.textureIndex>=0&&j.uv!=null){
                Integer previous=usedTextures.get(j.textureIndex);
                if(previous==null){
                    require(usedTextures.size()<8,"В GLB больше восьми базовых текстур");
                    slot=usedTextures.size();
                    usedTextures.put(j.textureIndex,slot);
                }else slot=previous;
            }
            draws[drawIndex++]=out/11;
            draws[drawIndex++]=j.triangles*3;
            draws[drawIndex++]=slot;
            for(int t=0;t<j.triangles;t++){
                float[][] v=new float[3][],uvs=new float[3][],colors=new float[3][];
                for(int k=0;k<3;k++){
                    int vertex=readIndex(st,j.indices,t*3+k);
                    v[k]=position(st,j.positions,vertex,j.matrix);
                    uvs[k]=j.uv==null?new float[]{0,0}:texCoord(j.uv,vertex);
                    colors[k]=j.colors==null?new float[]{1,1,1}:vertexColor(j.colors,vertex);
                }
                float ax=v[1][0]-v[0][0],ay=v[1][1]-v[0][1],az=v[1][2]-v[0][2];
                float bx=v[2][0]-v[0][0],by=v[2][1]-v[0][1],bz=v[2][2]-v[0][2];
                float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(n<.00001f){nx=0;ny=1;nz=0;}else{nx/=n;ny/=n;nz/=n;}
                for(int k=0;k<3;k++){
                    float[] p=v[k];
                    for(float coordinate:p)require(Float.isFinite(coordinate)&&Math.abs(coordinate)<100000,
                            "Некорректные координаты меша");
                    minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);minZ=Math.min(minZ,p[2]);
                    maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);maxZ=Math.max(maxZ,p[2]);
                    verts[out++]=p[0];verts[out++]=p[1];verts[out++]=p[2];
                    verts[out++]=nx;verts[out++]=ny;verts[out++]=nz;
                    for(int c=0;c<3;c++)verts[out++]=j.color[c]*colors[k][c];
                    verts[out++]=uvs[k][0];verts[out++]=uvs[k][1];
                }
            }
        }
        float extent=Math.max(.001f,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ)));
        float normalized=6.0f/extent,centerX=(maxX+minX)*.5f,centerZ=(maxZ+minZ)*.5f;
        for(int i=0;i<verts.length;i+=11){
            verts[i]=(verts[i]-centerX)*normalized;
            verts[i+1]=(verts[i+1]-minY)*normalized;
            verts[i+2]=(verts[i+2]-centerZ)*normalized;
        }
        // Every transformed vertex lies inside these axis-aligned bounds.
        // The enclosing sphere can only OVER-estimate visibility, never
        // incorrectly omit a valid triangle due to a too-small bounding box.
        float dx=(maxX-minX)*normalized;
        float dy=(maxY-minY)*normalized;
        float dz=(maxZ-minZ)*normalized;
        float sphere=(float)(.5*Math.sqrt((double)dx*dx+(double)dy*dy+(double)dz*dz));
        require(Float.isFinite(sphere)&&sphere>=0&&sphere<100,"Неверные границы GLB");
        JSONArray images=gltf.optJSONArray("images"),skins=gltf.optJSONArray("skins");
        JSONArray animations=gltf.optJSONArray("animations"),textures=gltf.optJSONArray("textures");
        boolean rigged=(skins!=null&&skins.length()>0)||
            (animations!=null&&animations.length()>0);
        // Morph target position/normal deltas are NOT part of the static
        // renderer; don't simplify them as though they were plain meshes.
        JSONArray meshes=gltf.optJSONArray("meshes");
        if(meshes!=null)for(int mi=0;mi<meshes.length();mi++){
            JSONArray primitives=meshes.getJSONObject(mi).optJSONArray("primitives");
            if(primitives==null)continue;
            for(int pi=0;pi<primitives.length();pi++){
                JSONArray targets=primitives.getJSONObject(pi).optJSONArray("targets");
                if(targets!=null&&targets.length()>0)rigged=true;
            }
        }
        TextureReader.Image[] decoded=new TextureReader.Image[usedTextures.size()];
        int memoryUsed=0;
        for(Map.Entry<Integer,Integer> item:usedTextures.entrySet()){
            TextureReader.Image image=readTexture(st,item.getKey(),textureLimit);
            memoryUsed+=image.bytes.length;
            require(memoryUsed<=32*1024*1024,"Слишком много памяти для текстур GLB");
            decoded[item.getValue()]=image;
        }
        byte[][] dataOut=new byte[decoded.length][];
        int[] imageInfo=new int[decoded.length*3];
        for(int i=0;i<decoded.length;i++){
            TextureReader.Image texture=decoded[i];
            dataOut[i]=texture.bytes;
            imageInfo[i*3]=texture.width;
            imageInfo[i*3+1]=texture.height;
            imageInfo[i*3+2]=texture.format;
        }
        boolean omitted=(images!=null&&images.length()>0&&usedTextures.isEmpty());
        return new Result(verts,st.triangles,dy*.5f,sphere+.05f,omitted,rigged,
            draws,dataOut,imageInfo);
    }
}
