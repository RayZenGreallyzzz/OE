package game.oe.editor;

import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Converts a single-file glTF 2.0 JSON + embedded base64 BIN into a canonical
 * GLB 2.0 used by the existing OE archive/renderer. External .bin and image
 * paths are rejected (instead of importing a visibly incomplete object).
 * This does NOT implement the complete glTF spec: OE remains static-only.
 */
final class GltfReader {
    private GltfReader(){}
    static byte[] toGlb(byte[] original) throws Exception{
        if(original==null||original.length==0||original.length>GlbReader.MAX_BYTES)
            throw new Exception("glTF: недопустимый размер файла");
        String jsonText=new String(original,StandardCharsets.UTF_8).trim();
        if(jsonText.startsWith("\uFEFF"))jsonText=jsonText.substring(1);
        JSONObject doc=new JSONObject(jsonText);
        JSONObject asset=doc.getJSONObject("asset");
        if(!asset.optString("version","").startsWith("2."))
            throw new Exception("glTF: нужна версия 2.0");
        JSONArray buffers=doc.optJSONArray("buffers");
        if(buffers==null||buffers.length()!=1)
            throw new Exception("glTF: нужен ровно один встроенный BIN-буфер");
        JSONObject buffer=buffers.getJSONObject(0);
        String uri=buffer.optString("uri","");
        int at=uri.indexOf(',');
        if(at<0||at>100||!uri.substring(0,at).startsWith("data:")||
            !uri.substring(0,at).endsWith(";base64"))
            throw new Exception("glTF: отдельные .bin пока не поддерживаются. Экспортируй GLB из Blender.");
        byte[] binary=Base64.decode(uri.substring(at+1),Base64.DEFAULT);
        int expected=buffer.getInt("byteLength");
        if(expected<=0||binary.length!=expected||expected>GlbReader.MAX_BYTES-256)
            throw new Exception("glTF: повреждён BIN-буфер");
        buffer.remove("uri");
        JSONArray images=doc.optJSONArray("images");
        if(images!=null)for(int i=0;i<images.length();i++){
            JSONObject img=images.getJSONObject(i);
            if(img.has("uri")&&!img.getString("uri").startsWith("data:image/"))
                throw new Exception("glTF: внешняя PNG/JPG/KTX2 текстура пока не поддерживается. Экспортируй GLB.");
        }
        JSONArray views=doc.optJSONArray("bufferViews");
        if(views!=null)for(int i=0;i<views.length();i++){
            if(views.getJSONObject(i).optInt("buffer",0)!=0)
                throw new Exception("glTF: внешний bufferView не поддерживается");
        }
        return packGlb(doc,binary);
    }
    static byte[] packGlb(JSONObject doc,byte[] binary) throws Exception{
        byte[] json=doc.toString().getBytes(StandardCharsets.UTF_8);
        int jsonPadded=(json.length+3)&~3;
        int binPadded=(binary.length+3)&~3;
        long total=12L+8L+jsonPadded+8L+binPadded;
        if(total>GlbReader.MAX_BYTES)throw new Exception("GLB больше 24 МиБ после конвертации");
        ByteBuffer out=ByteBuffer.allocate((int)total).order(ByteOrder.LITTLE_ENDIAN);
        out.putInt(0x46546c67).putInt(2).putInt((int)total);
        out.putInt(jsonPadded).putInt(0x4e4f534a);
        out.put(json);
        while(out.position()%4!=0)out.put((byte)' ');
        out.putInt(binPadded).putInt(0x004e4942);
        out.put(binary);
        while(out.position()%4!=0)out.put((byte)0);
        return out.array();
    }
}
