package game.oe.editor;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import java.util.Map;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import java.io.File;
import java.util.Locale;

/**
 * Native Android sidebar adapted from the owner's UV56 landscape demo video.
 * This is a view of the one R5.7 GLES city, NOT a Phoenix WebView/Activity.
 * It does not bundle the unstable UV56 building editor or private textures.
 */
final class NativeLandscapePanel extends LinearLayout {
    interface Host {
        void tool(int choice); // 0 look; 1..5 UV56 sculpt; 6 paint; 7 erase
        void brushCentimeters(int centimeters);
        void brushPower(float power);
        void createArea(int areaSqM);
        void uploadTexture();
        void chooseTexture(String key);
        void neutralPreview(boolean neutral);
        void reverseCamera(boolean reverse);
        void close();
    }
    private static final int[] AREAS={1000,2500,5000,10000,50000,250000,1000000,2000000};
    private static final String[] AREA_NAMES={
        "10 соток · 32 × 32 м","25 соток · 50 × 50 м",
        "50 соток · 71 × 71 м","1 гектар · 100 × 100 м",
        "5 гектаров · 224 × 224 м","25 гектаров · 500 × 500 м",
        "1 км² · 1000 × 1000 м","2 км² · 1414 × 1414 м"
    };
    private static final String[] TOOLS={
        "🖐 Осмотреть / повернуть камеру",
        "⛰ Поднять землю (мягкий склон)",
        "⬇ Опустить землю",
        "🌄 Горный хребет — вести пальцем",
        "〰 Сгладить землю",
        "▰ Выровнять по точке касания",
        "🎨 Покрасить землю",
        "🧽 Стереть выбранное покрытие"
    };
    private final Host host;
    private final Context ctx;
    private final TextView sizeValue;
    private final TextView powerValue;
    private final TextView status;
    private final TextView exact;
    private final SeekBar size;
    private final Spinner areasSpinner;
    private int centimeters;
    private int selectedTool;
    private boolean initialized=false;

    NativeLandscapePanel(Context ctx,Host host,int activeMode,int brushCm,
                         float strength,File[] userTiles,String selectedKey,
                         Map<String,String> tileTitles,boolean neutralPreview,
                         boolean reverseCamera){
        super(ctx);
        this.ctx=ctx;this.host=host;
        setOrientation(VERTICAL);
        setPadding(px(8),px(6),px(8),px(12));
        setBackgroundColor(Color.rgb(17,33,43));
        centimeters=Math.max(100,Math.min(2400,brushCm));
        selectedTool=Math.max(0,Math.min(7,activeMode));

        LinearLayout title=new LinearLayout(ctx);
        title.setOrientation(HORIZONTAL);
        TextView header=label("🌳 Ландшафт · UV56",16,true);
        title.addView(header,new LayoutParams(0,px(46),1));
        Button city=button("🏙 К городу",host::close);
        title.addView(city,new LayoutParams(px(118),px(46)));
        addView(title);
        addView(label("ОДИН 3D-МИР • один палец — выбранная кисть; "+
            "два пальца — камера и масштаб.",12,true));
        LinearLayout modes=new LinearLayout(ctx);
        modes.setOrientation(HORIZONTAL);
        Button beige=button(neutralPreview?"✓ Бежевый":"🟨 Холст",
            ()->host.neutralPreview(true));
        Button textured=button(neutralPreview?"👁 Показать текстуры":"✓ Текстуры видны",
            ()->host.neutralPreview(false));
        modes.addView(beige,new LayoutParams(0,px(46),1));
        modes.addView(textured,new LayoutParams(0,px(46),1));
        addView(modes,new LayoutParams(-1,px(48)));
        addView(label("Кнопки выше только показывают/скрывают покрытие. "+
            "Для рисования выбери «Покрасить землю» и материал ниже.",11,false));
        addView(button(reverseCamera?
            "🎥 Камера: обратное направление ✓":
            "🎥 Камера: стандартное направление ✓",
            ()->host.reverseCamera(!reverseCamera)),
            new LayoutParams(-1,px(44)));
        addView(button("🖼 Загрузить сразу 10–32 PNG/JPG",host::uploadTexture),
            new LayoutParams(-1,px(46)));

        addView(label("Размер территории",12,false));
        Spinner areas=spinner(AREA_NAMES);
        areasSpinner=areas;
        areas.setSelection(3);
        addView(areas,new LayoutParams(-1,px(46)));
        Button applyArea=button("🌍 Создать / заменить землю",
            ()->host.createArea(AREAS[areas.getSelectedItemPosition()]));
        addView(applyArea,new LayoutParams(-1,px(48)));
        status=label("Общая земля OE · высоты UV56 · шаг 1 м",11,false);
        status.setTextColor(Color.rgb(117,217,192));
        addView(status,new LayoutParams(-1,px(52)));

        addView(label(selectedTool>0&&selectedTool<=5?
            "✓ Кисть готова. Проведи пальцем по земле.":
            "Инструмент кисти · выбери «Поднять», чтобы изменить землю",12,true));
        Spinner tools=spinner(TOOLS);
        tools.setSelection(selectedTool); // starts with Raise, not Look, on first entry
        addView(tools,new LayoutParams(-1,px(48)));
        sizeValue=label("",12,true);
        addView(sizeValue);
        size=new SeekBar(ctx);
        size.setMax(2300);
        size.setProgress(centimeters-100);
        addView(size,new LayoutParams(-1,px(45)));
        size.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int progress,boolean fromUser){
                if(fromUser)setCentimeters(progress+100,true);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });

        addView(label("Точный размер кисти",12,false));
        exact=label("",14,true);
        exact.setGravity(Gravity.CENTER);
        addView(exact,new LayoutParams(-1,px(29)));
        LinearLayout exactButtons=new LinearLayout(ctx);
        exactButtons.setOrientation(HORIZONTAL);
        exactButtons.addView(button("−1 см",()->setCentimeters(centimeters-1,true)),
            new LayoutParams(0,px(46),1));
        exactButtons.addView(button("+1 см",()->setCentimeters(centimeters+1,true)),
            new LayoutParams(0,px(46),1));
        exactButtons.addView(button("⌗ Ввести см",this::showCentimeterPad),
            new LayoutParams(0,px(46),1.3f));
        addView(exactButtons);
        powerValue=label("",12,true);
        addView(powerValue);
        SeekBar power=new SeekBar(ctx);
        power.setMax(39); // 0.2 .. 8.0, step 0.2
        power.setProgress(Math.max(0,Math.min(39,Math.round(strength/.2f)-1)));
        addView(power,new LayoutParams(-1,px(45)));
        power.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int v,boolean fromUser){
                float selected=(v+1)*.2f;
                powerValue.setText("Сила кисти: "+
                    String.format(Locale.ROOT,"%.1f",selected).replace('.',','));
                if(fromUser)host.brushPower(selected);
            }
            @Override public void onStartTrackingTouch(SeekBar b){}
            @Override public void onStopTrackingTouch(SeekBar b){}
        });
        powerValue.setText("Сила кисти: "+
            String.format(Locale.ROOT,"%.1f",strength).replace('.',','));
        tools.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){
            @Override public void onItemSelected(AdapterView<?> parent,View view,
                                                  int i,long id){
                selectedTool=i;
                if(initialized){
                    setCentimeters(centimeters,true);
                    host.tool(i);
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent){}
        });
        setCentimeters(centimeters,false);
        initialized=true;

        addView(label("Покрытие / загруженный тайл",13,true));
        addView(label("Нажми на плитку ниже — включится кисть покраски. "
                +"Текущий нативный материал OE имеет минимальный диаметр 1 м; "
                +"сантиметровые маски UV56 пока переносим.",11,false));
        addView(button("🖼 Добавить ещё PNG / JPG одним выбором",host::uploadTexture),
            new LayoutParams(-1,px(46)));
        addView(label("Мои материалы: "+(userTiles==null?0:userTiles.length)+
            " / 32 · миниатюры показывают повторение рисунка",12,true));
        if(userTiles==null||userTiles.length==0){
            addView(label("Пока нет загруженных плиток. Добавь PNG/JPG, "
                +"чтобы увидеть здесь изображения материалов.",12,false));
        } else {
            GridLayout grid=new GridLayout(ctx);
            grid.setColumnCount(2);
            int shown=0;
            for(File file:userTiles){
                if(file==null||!file.isFile()||shown>=32)continue;
                final String key=file.getName();
                boolean chosen=key.equals(selectedKey);
                LinearLayout card=new LinearLayout(ctx);
                card.setPadding(px(5),px(5),px(5),px(5));
                card.setOrientation(VERTICAL);
                card.setBackgroundColor(chosen?Color.rgb(17,103,128):
                                               Color.rgb(31,50,62));
                ImageView thumb=new ImageView(ctx);
                thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Bitmap image=smallPreview(file);
                if(image!=null)thumb.setImageBitmap(image);
                else thumb.setBackgroundColor(Color.rgb(62,78,80));
                card.addView(thumb,new LayoutParams(-1,px(112)));
                String nice=tileTitles!=null?tileTitles.get(key):null;
                if(nice==null||nice.trim().isEmpty())nice="Тайл "+(shown+1);
                TextView caption=label((chosen?"✓ ":"")+nice,11,false);
                caption.setGravity(Gravity.CENTER);
                caption.setMaxLines(2);
                caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
                card.addView(caption,new LayoutParams(-1,px(48)));
                card.setOnClickListener(v->host.chooseTexture(key));
                GridLayout.LayoutParams gridParams=new GridLayout.LayoutParams();
                gridParams.width=px(146);gridParams.height=px(168);
                gridParams.setMargins(px(3),px(3),px(3),px(3));
                grid.addView(card,gridParams);
                ++shown;
            }
            addView(grid,new LayoutParams(-1,-2));
        }
        addView(button("🧽 Ластик покрытия",()->host.tool(7)),
            new LayoutParams(-1,px(46)));
        addView(label("Дороги, мосты и озёра R5.7 сохранены. "
            +"Их отдельные инструменты доступны в каталоге.",11,false));
    }

    void setAreaStatus(int nativeArea){
        if(nativeArea>0){
            for(int i=0;i<AREAS.length;i++)
                if(AREAS[i]==nativeArea)areasSpinner.setSelection(i);
            status.setText("✓ Территория "+nativeArea+" м² · шаг 1 м · "+
                "редактирование в городе OE");
        }else status.setText("Новая территория ещё не создана · "+
            "нажми «Создать землю»");
    }
    private void setCentimeters(int input,boolean fire){
        int min=selectedTool<=5?275:100;
        centimeters=Math.max(min,Math.min(2400,input));
        size.setProgress(centimeters-100);
        updateSizeLabel();
        if(fire)host.brushCentimeters(centimeters);
    }
    private void updateSizeLabel(){
        boolean painting=selectedTool>=6;
        sizeValue.setText((painting?"Диаметр кисти":"Радиус кисти")+
            ": "+(centimeters<100?centimeters+" см":
            String.format(Locale.ROOT,"%.2f м",centimeters/100.f)));
        exact.setText(centimeters+" см");
    }
    private void showCentimeterPad(){
        final StringBuilder input=new StringBuilder(String.valueOf(centimeters));
        LinearLayout body=new LinearLayout(ctx);
        body.setPadding(px(10),px(10),px(10),px(10));
        body.setOrientation(VERTICAL);
        final TextView digits=label(input+" см",22,true);
        digits.setGravity(Gravity.CENTER);
        body.addView(digits,new LayoutParams(-1,px(48)));
        final GridLayout grid=new GridLayout(ctx);
        grid.setColumnCount(3);
        for(String key:new String[]{"1","2","3","4","5","6","7","8","9","C","0","⌫"}){
            Button b=button(key,()->{
                if("C".equals(key))input.setLength(0);
                else if("⌫".equals(key)){
                    if(input.length()>0)input.deleteCharAt(input.length()-1);
                }else if(input.length()<4){
                    if(input.length()==1&&input.charAt(0)=='0')input.setLength(0);
                    input.append(key);
                }
                digits.setText((input.length()==0?"0":input.toString())+" см");
            });
            GridLayout.LayoutParams p=new GridLayout.LayoutParams();
            p.width=px(85);p.height=px(48);
            grid.addView(b,p);
        }
        body.addView(grid);
        new AlertDialog.Builder(ctx)
            .setTitle("Точный размер кисти UV56")
            .setView(body)
            .setPositiveButton("✓ Применить",(dialog,which)->{
                try{
                    int cm=Integer.parseInt(input.toString());
                    int min=selectedTool<=5?275:100;
                    if(cm<min||cm>2400){
                        new AlertDialog.Builder(ctx).setMessage("Для этого инструмента "
                            +"доступно от "+min+" до 2400 см.").setPositiveButton("OK",null)
                            .show();
                    }else setCentimeters(cm,true);
                }catch(NumberFormatException ex){
                    new AlertDialog.Builder(ctx).setMessage("Введи размер в сантиметрах")
                        .setPositiveButton("OK",null).show();
                }
            }).setNegativeButton("Отмена",null).show();
    }
    private Spinner spinner(String[] options){
        Spinner s=new Spinner(ctx);
        ArrayAdapter<String> a=new ArrayAdapter<>(ctx,
             android.R.layout.simple_spinner_dropdown_item,options);
        s.setAdapter(a);return s;
    }
    private TextView label(String text,int size,boolean bold){
        TextView tv=new TextView(ctx);
        tv.setText(text);tv.setTextColor(Color.rgb(226,239,245));
        tv.setTextSize(size);tv.setPadding(px(4),px(6),px(4),px(5));
        if(bold)tv.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        return tv;
    }
    private Button button(String text,Runnable click){
        Button b=new Button(ctx);
        b.setAllCaps(false);b.setText(text);b.setTextSize(12);
        b.setTextColor(Color.WHITE);
        b.setOnClickListener(v->click.run());return b;
    }
    private static Bitmap smallPreview(File file){
        try{
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
            if(opts.outWidth<1||opts.outHeight<1)return null;
            int scale=1;
            while(Math.max(opts.outWidth,opts.outHeight)/scale>256)scale*=2;
            opts.inJustDecodeBounds=false;
            opts.inSampleSize=scale;
            Bitmap source=BitmapFactory.decodeFile(file.getAbsolutePath(),opts);
            if(source==null)return null;
            // Show a true 2×2 repeat of the owner's actual tile image.
            // Do not generate/recolour/recompress the source file itself.
            Bitmap preview=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(preview);
            Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
            Rect entire=new Rect(0,0,source.getWidth(),source.getHeight());
            for(int row=0;row<2;row++)for(int col=0;col<2;col++)
                canvas.drawBitmap(source,entire,
                    new Rect(col*96,row*96,(col+1)*96,(row+1)*96),paint);
            source.recycle();
            return preview;
        }catch(RuntimeException e){return null;}
    }
    private int px(float dp){
        return (int)(dp*ctx.getResources().getDisplayMetrics().density+.5f);
    }
}
