package io.va.gallery;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;

public class ViewerActivity extends Activity {

    private ArrayList<String> paths;
    private int index;
    private FrameLayout stage;
    private TextView caption;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        paths = getIntent().getStringArrayListExtra("paths");
        index = getIntent().getIntExtra("index", 0);
        if (paths == null || paths.isEmpty()) {
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        stage = new FrameLayout(this);
        root.addView(stage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        caption = new TextView(this);
        caption.setTextColor(0xFFFFFFFF);
        caption.setTextSize(13);
        caption.setPadding(24, 12, 24, 12);
        caption.setBackgroundColor(0x66000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.gravity = Gravity.BOTTOM;
        root.addView(caption, cp);

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getX();
                    int w = v.getWidth();
                    if (x < w / 3f) {
                        show(index - 1);
                    } else if (x > w * 2f / 3f) {
                        show(index + 1);
                    }
                }
                return true;
            }
        });

        setContentView(root);
        show(index);
    }

    private void show(int i) {
        if (paths == null || paths.isEmpty()) {
            return;
        }
        if (i < 0) {
            i = 0;
        }
        if (i >= paths.size()) {
            i = paths.size() - 1;
        }
        index = i;
        stage.removeAllViews();
        final File f = new File(paths.get(index));
        caption.setText((index + 1) + " / " + paths.size() + "   " + f.getName());

        if (MainActivity.isVideo(f)) {
            VideoView video = new VideoView(this);
            video.setVideoPath(f.getAbsolutePath());
            MediaController mc = new MediaController(this);
            mc.setAnchorView(video);
            video.setMediaController(mc);
            stage.addView(video, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            video.start();
        } else {
            final ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            stage.addView(img, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    Bitmap bmp = null;
                    try {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                        int sample = 1;
                        while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440) {
                            sample *= 2;
                        }
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = sample;
                        bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                    } catch (Throwable ignored) {
                    }
                    final Bitmap result = bmp;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (result != null) {
                                img.setImageBitmap(result);
                            }
                        }
                    });
                }
            }).start();
        }
    }
}
