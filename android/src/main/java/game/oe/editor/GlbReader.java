package game.oe.editor;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * Minimal, offline glTF 2.0 GLB STATIC triangle importer.
 *
 * Supports: GLB 2, uncompressed POSITION float32 VEC3,
 * uint8/uint16/uint32 indices, node matrix/TRS hierarchy,
 * unindexed triangles, baseColorFactor. Builds face normals.
 * No Draco, meshopt, morphing, skinning, animation, textures or embedded .gltf.
 * Defensive limits are intentional for low-memory Android tablets.
 */
public final class GlbReader {
    public static final int MAX_BYTES = 24 * 1024 * 1024;
    public static final int MAX_TRIANGLES = 90000;
    public static final int MAX_NODES = 1024;

    public static final class Result {
        public final float[] vertices; // [pos.xyz, normal.xyz, color.rgb] repeated per vertex
        public final int triangles;
        // Bounds are computed after normalization (the resulting local origin
        // is centered on X/Z and grounded at Y=0). Used for conservative culling.
        public final float boundCenterY;
        public final float boundRadius;
        Result(float[] vertices, int triangles, float boundCenterY, float boundRadius) {
            this.vertices = vertices;
            this.triangles = triangles;
            this.boundCenterY = boundCenterY;
            this.boundRadius = boundRadius;
        }
    }

    private static final class Access {
        int offset, step, count, component;
    }
    private static final class Job {
        Access positions, indices;
        int triangles;
        float[] matrix, color;
    }
    private static final class State {
        final JSONObject gltf;
        final ByteBuffer bin;
        final ArrayList<Job> jobs = new ArrayList<>();
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
    private static Access accessor(State st,int index,boolean position) throws Exception {
        JSONArray accessors=st.gltf.getJSONArray("accessors");
        require(index>=0 && index<accessors.length(),"Нет accessor");
        JSONObject obj=accessors.getJSONObject(index);
        require(!obj.has("sparse"),"Sparse accessor пока не поддерживается");
        int component=obj.getInt("componentType"),count=obj.getInt("count");
        String type=obj.getString("type");
        require(count>0 && count<=MAX_TRIANGLES*3,"Слишком много вершин или индексов");
        require((position && component==5126 && "VEC3".equals(type)) ||
                (!position && "SCALAR".equals(type) && (component==5121||component==5123||component==5125)),
                "Формат вершин/индексов GLB пока не поддерживается");
        int componentBytes=component==5121?1:component==5123?2:4;
        int width=position?12:componentBytes;
        int viewIndex=obj.getInt("bufferView");
        JSONArray views=st.gltf.getJSONArray("bufferViews");
        require(viewIndex>=0 && viewIndex<views.length(),"Нет bufferView");
        JSONObject view=views.getJSONObject(viewIndex);
        require(view.optInt("buffer",0)==0,"Внешние буферы не поддерживаются");
        int length=view.getInt("byteLength"),stride=view.optInt("byteStride",width);
        int off=view.optInt("byteOffset",0),accessOffset=obj.optInt("byteOffset",0);
        long required=(long)accessOffset+(long)(count-1)*stride+width;
        require(off>=0 && accessOffset>=0 && length>=0 && stride>=width &&
                required<=length && (long)off+required<=st.bin.capacity(),
                "Повреждённый bufferView GLB");
        Access a=new Access();a.offset=off+accessOffset;a.step=stride;a.count=count;a.component=component;return a;
    }
    private static int readIndex(State st,Access a,int index) throws Exception {
        if(a==null)return index;
        require(index>=0 && index<a.count,"Индекс за пределами");
        int at=a.offset+index*a.step;
        long value=a.component==5121?(st.bin.get(at)&255):
                a.component==5123?(st.bin.getShort(at)&65535):Integer.toUnsignedLong(st.bin.getInt(at));
        require(value<Integer.MAX_VALUE,"Слишком большой индекс");return (int)value;
    }
    private static float[] position(State st,Access a,int index,float[] matrix) throws Exception {
        require(index>=0 && index<a.count,"Индекс вершины за пределами");
        int at=a.offset+index*a.step;
        float x=st.bin.getFloat(at),y=st.bin.getFloat(at+4),z=st.bin.getFloat(at+8);
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
                if(p.optInt("mode",4)!=4)continue; // only triangle lists
                JSONObject attrs=p.optJSONObject("attributes");
                if(attrs==null||!attrs.has("POSITION"))continue;
                Access positions=accessor(st,attrs.getInt("POSITION"),true);
                Access indices=p.has("indices")?accessor(st,p.getInt("indices"),false):null;
                int count=indices==null?positions.count:indices.count;
                int triangles=count/3;
                if(triangles==0)continue;
                require((long)st.triangles+triangles<=MAX_TRIANGLES,"Модель больше 90 000 треугольников");
                Job j=new Job();j.positions=positions;j.indices=indices;
                j.triangles=triangles;j.matrix=matrix;j.color=color(st,p);
                st.jobs.add(j);st.triangles+=triangles;
            }
        }
        JSONArray children=node.optJSONArray("children");
        if(children!=null)for(int i=0;i<children.length();i++)visit(st,children.getInt(i),matrix,path,depth+1);
        path.remove(nodeIndex);
    }
    public static Result decode(byte[] data) throws Exception {
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
        require(st.triangles>0,"Не найдена обычная треугольная геометрия (без Draco/meshopt)");
        float[] verts=new float[st.triangles*27];
        int out=0;float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX;
        float maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
        for(Job j:st.jobs){
            for(int t=0;t<j.triangles;t++){
                float[][] v=new float[3][];
                for(int k=0;k<3;k++){
                    int vertex=readIndex(st,j.indices,t*3+k);
                    v[k]=position(st,j.positions,vertex,j.matrix);
                }
                float ax=v[1][0]-v[0][0],ay=v[1][1]-v[0][1],az=v[1][2]-v[0][2];
                float bx=v[2][0]-v[0][0],by=v[2][1]-v[0][1],bz=v[2][2]-v[0][2];
                float nx=ay*bz-az*by,ny=az*bx-ax*bz,nz=ax*by-ay*bx;
                float n=(float)Math.sqrt(nx*nx+ny*ny+nz*nz);
                if(n<.00001f){nx=0;ny=1;nz=0;}else{nx/=n;ny/=n;nz/=n;}
                for(float[] p:v){
                    for(float coordinate:p)require(Float.isFinite(coordinate)&&Math.abs(coordinate)<100000,
                            "Некорректные координаты меша");
                    minX=Math.min(minX,p[0]);minY=Math.min(minY,p[1]);minZ=Math.min(minZ,p[2]);
                    maxX=Math.max(maxX,p[0]);maxY=Math.max(maxY,p[1]);maxZ=Math.max(maxZ,p[2]);
                    verts[out++]=p[0];verts[out++]=p[1];verts[out++]=p[2];
                    verts[out++]=nx;verts[out++]=ny;verts[out++]=nz;
                    verts[out++]=j.color[0];verts[out++]=j.color[1];verts[out++]=j.color[2];
                }
            }
        }
        float extent=Math.max(.001f,Math.max(maxX-minX,Math.max(maxY-minY,maxZ-minZ)));
        float normalized=6.0f/extent,centerX=(maxX+minX)*.5f,centerZ=(maxZ+minZ)*.5f;
        for(int i=0;i<verts.length;i+=9){
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
        return new Result(verts,st.triangles,dy*.5f,sphere+.05f);
    }
}
