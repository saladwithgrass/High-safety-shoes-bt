package com.example.fragmentlrn;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

public class TextDrawable extends Drawable {

    private final String text;
    private final Paint paint;

    public TextDrawable(String text) {

        this.text = text;
        this.paint = new Paint();
        paint.setColor(Color.GRAY);
        paint.setTextSize(40f);
        paint.setAntiAlias(true);
        paint.setFakeBoldText(false);
        // paint.setShadowLayer(6f, 0, 0, Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        // this.set
    }

    @Override
    public void draw(Canvas canvas) {
        float x = canvas.getWidth() / 2;
        float y = canvas.getHeight() / 2;
        float textWidth = paint.measureText(text), textHeight = paint.measureText(text);
        canvas.drawText(text, x /*- textWidth / 2f*/, y /*+ textHeight / 2f*/, paint);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
