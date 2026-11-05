package game.oe.editor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Single authoritative format sniffer; never trusts external filename/MIME. */
final class UniversalModelReader {
    static final class Converted {
        final byte[] glb;
        final String originalType;
        Converted(byte[] canonicalGlb,String type){glb=canonicalGlb;originalType=type;}
    }
    private UniversalModelReader(){}
    static Converted convert(byte[] source) throws Exception{
        if(source==null||source.length<4||source.length>GlbReader.MAX_BYTES)
            throw new Exception("Файл модели слишком большой или пустой");
        if(source[0]=='g'&&source[1]=='l'&&source[2]=='T'&&source[3]=='F'){
            ByteBuffer bb=ByteBuffer.wrap(source).order(ByteOrder.LITTLE_ENDIAN);
            if(source.length<20||bb.getInt(4)!=2||bb.getInt(8)!=source.length)
                throw new Exception("Неверный glTF Binary (.glb)");
            return new Converted(source,"glb");
        }
        if(source[0]=='V'&&source[1]=='O'&&source[2]=='X'&&source[3]==' ')
            return new Converted(VoxReader.toGlb(source),"vox");
        String header=new String(source,0,Math.min(64,source.length),
            StandardCharsets.UTF_8).trim();
        if(header.startsWith("{")||header.startsWith("\uFEFF{"))
            return new Converted(GltfReader.toGlb(source),"gltf");
        throw new Exception("Формат модели не распознан. Выбери GLB 2.0, "
            +"glTF со встроенными ресурсами или одиночный MagicaVoxel VOX.");
    }
}
