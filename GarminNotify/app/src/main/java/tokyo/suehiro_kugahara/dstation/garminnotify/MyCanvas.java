package tokyo.suehiro_kugahara.dstation.garminnotify;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

/**
 * Created by user on 2016/05/31.
 */
public class MyCanvas extends Canvas {
    private Paint objPaint;
    private Bitmap objBitmap;
    private int textSize = 20;
    private int drawLine = 0;
    private int Width;
    private int Height;
    private ByteArrayOutputStream out;

    public void setTextSize(int size) {
        textSize = size;
    }
    public MyCanvas(int width, int height) {
        super();
        objBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        setBitmap(objBitmap);
        objPaint = new Paint();
        Width = width;
        Height = height;
    }

    public void Clear(int color) {
        drawColor(color);
        drawLine = 0;
        if (out != null) {
            try {
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        out = new ByteArrayOutputStream();
    }

    public void DrawBitmap(int id, Resources res) {
        Bitmap bm = BitmapFactory.decodeResource(res, id);
        if (bm == null) return;
        drawBitmap(bm, 0, drawLine + 10, objPaint);
        drawLine += bm.getHeight() + 10;
    }
    public void DrawText(String text, int color) {
        objPaint.setAntiAlias(true);
        objPaint.setColor(color);
        objPaint.setTextSize(textSize);

        int lineBreakPoint = Width;
        int currentIndex = 0;
        if (text == null) return;
        Log.d("MyCanvas", text);

        while((lineBreakPoint != 0) && (drawLine + textSize < Height)) {
            String mesureString = text.substring(currentIndex);
            lineBreakPoint = objPaint.breakText(mesureString, true, Width, null);
            if(lineBreakPoint != 0){
                String line = text.substring(currentIndex, currentIndex + lineBreakPoint);
                drawLine += textSize;
                drawText(line, 0, drawLine, objPaint);
                currentIndex += lineBreakPoint;
            }
        }
    }

    public InputStream getPngImageStream() {
        objBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        ByteArrayInputStream bais = new ByteArrayInputStream(out.toByteArray());
        return bais;
    }
}
