package game.oe.editor;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Bounded offline decode for embedded GLB baseColor images.
 * Supported: PNG/JPEG -> RGBA8, KTX2 raw RGBA8 and GPU-native ETC2.
 * KTX2 BasisLZ/ETC1S/UASTC still need a transcoder and are NOT supported.
 * This reader never writes to or mutates the source GLB.
 */
final class TextureReader {
    private TextureReader(){}
    static final int MAX_SOURCE_BYTES=24*1024*1024;
    static final int MAX_DIMENSION=8192;
    static final int DEFAULT_LIMIT=1024;
    static final int MAX_LIMIT=2048;
    static final int FORMAT_RGBA=0, FORMAT_ETC2_RGB=1, FORMAT_ETC2_RGBA=2;
    static final class Image {
        final byte[] bytes;
        final int width,height,format;
        Image(byte[] b,int w,int h,int f){bytes=b;width=w;height=h;format=f;}
    }
    private static void need(boolean ok,String message) throws Exception{
        if(!ok)throw new Exception(message);
    }
    static Image decode(byte[] input,String mime,int limit) throws Exception{
        need(input!=null&&input.length>=16&&input.length<=MAX_SOURCE_BYTES,
            "Размер файла текстуры недопустим");
        need(limit==1024||limit==2048,"Неверное качество текстур");
        if("image/ktx2".equals(mime))return readKtx2(input,limit);
        need("image/png".equals(mime)||"image/jpeg".equals(mime),
            "Текстура должна быть PNG, JPG или совместимым KTX2");
        return readBitmap(input,limit);
    }
    private static Image readBitmap(byte[] source,int limit) throws Exception{
        BitmapFactory.Options header=new BitmapFactory.Options();
        header.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(source,0,source.length,header);
        need(header.outWidth>0&&header.outHeight>0&&
            header.outWidth<=MAX_DIMENSION&&header.outHeight<=MAX_DIMENSION,
            "Изображение имеет недопустимый размер");
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
        int sample=1;
        while(Math.max(header.outWidth,header.outHeight)/sample>limit*2)sample*=2;
        opts.inSampleSize=sample;
        Bitmap loaded=BitmapFactory.decodeByteArray(source,0,source.length,opts);
        need(loaded!=null,"Повреждена PNG/JPG текстура");
        Bitmap resized=loaded;
        try{
            int w=loaded.getWidth(),h=loaded.getHeight();
            if(w>limit||h>limit){
                float scale=Math.min((float)limit/w,(float)limit/h);
                int width=Math.max(1,Math.round(w*scale));
                int height=Math.max(1,Math.round(h*scale));
                resized=Bitmap.createScaledBitmap(loaded,width,height,true);
            }
            int width=resized.getWidth(),height=resized.getHeight();
            need((long)width*height<=((long)limit*limit),"Текстура превышает лимит");
            int[] argb=new int[width*height];
            resized.getPixels(argb,0,width,0,0,width,height);
            byte[] rgba=new byte[width*height*4];
            for(int i=0;i<argb.length;i++){
                int c=argb[i],o=i*4;
                rgba[o]=(byte)((c>>>16)&255);
                rgba[o+1]=(byte)((c>>>8)&255);
                rgba[o+2]=(byte)(c&255);
                rgba[o+3]=(byte)((c>>>24)&255);
            }
            return new Image(rgba,width,height,FORMAT_RGBA);
        }finally{
            if(resized!=loaded)resized.recycle();
            loaded.recycle();
        }
    }
    private static long u64(ByteBuffer b,int at) {
        return b.getLong(at);
    }
    private static Image readKtx2(byte[] src,int limit) throws Exception{
        final byte[] id={(byte)0xAB,0x4B,0x54,0x58,0x20,0x32,0x30,(byte)0xBB,
            0x0D,0x0A,0x1A,0x0A};
        need(src.length>=104,"Усечённый KTX2");
        need(Arrays.equals(id,Arrays.copyOfRange(src,0,12)),"Неверная сигнатура KTX2");
        ByteBuffer b=ByteBuffer.wrap(src).order(ByteOrder.LITTLE_ENDIAN);
        int vkFormat=b.getInt(12),typeSize=b.getInt(16);
        int width=b.getInt(20),height=b.getInt(24);
        int depth=b.getInt(28),layers=b.getInt(32),faces=b.getInt(36);
        int levels=b.getInt(40),scheme=b.getInt(44);
        need(width>0&&height>0&&width<=limit&&height<=limit&&
            depth==0&&layers==0&&faces==1&&levels>=1&&levels<=16,
            "KTX2: поддерживаются только 2D-текстуры до "+limit+" px");
        need(scheme==0&&vkFormat!=0,
            "KTX2 BasisU/ETC1S/UASTC ещё требует отдельный транскодер");
        int format,bytesPerBlock;
        if(vkFormat==37||vkFormat==43){
            format=FORMAT_RGBA;bytesPerBlock=0;
            need(typeSize==1,"KTX2 RGBA8 требует typeSize=1");
        }else if(vkFormat==147||vkFormat==148){
            format=FORMAT_ETC2_RGB;bytesPerBlock=8;
        }else if(vkFormat==151||vkFormat==152){
            format=FORMAT_ETC2_RGBA;bytesPerBlock=16;
        }else throw new Exception("KTX2 vkFormat "+vkFormat+" пока не поддерживается");
        long expected=bytesPerBlock==0?(long)width*height*4:
            (long)((width+3)/4)*((height+3)/4)*bytesPerBlock;
        long offset=u64(b,80),length=u64(b,88),unpacked=u64(b,96);
        need(offset>=104&&length==expected&&unpacked==expected&&
            length<=MAX_SOURCE_BYTES&&offset<=src.length&&length<=src.length-offset,
            "Некорректные границы уровня KTX2");
        return new Image(Arrays.copyOfRange(src,(int)offset,(int)(offset+length)),
            width,height,format);
    }
}
