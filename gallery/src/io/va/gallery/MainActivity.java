package io.va.gallery;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ThumbnailUtils;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final int REQ_PERM = 42;
    private static final Set<String> IMAGE_EXT = new HashSet<String>();
    private static final Set<String> VIDEO_EXT = new HashSet<String>();

    static {
        IMAGE_EXT.add("jpg"); IMAGE_EXT.add("jpeg"); IMAGE_EXT.add("png");
        IMAGE_EXT.add("gif"); IMAGE_EXT.add("webp"); IMAGE_EXT.add("bmp");
        VIDEO_EXT.add("mp4"); VIDEO_EXT.add("mkv"); VIDEO_EXT.add("3gp");
        VIDEO_EXT.add("webm"); VIDEO_EXT.add("avi"); VIDEO_EXT.add("mov");
    }

    public static boolean isVideo(File f) {
        return VIDEO_EXT.contains(ext(f.getName()));
    }

    private static String ext(String name) {
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase();
    }

    private final List<File> media = new ArrayList<File>();
    private final ExecutorService pool = Executors.newFixedThreadPool(4);
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(64);
    private GridView grid;
    private TextView emptyText;
    private MediaAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF000000);

        TextView title = new TextView(this);
        title.setText("Gallery");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(18);
        title.setPadding(32, 28, 32, 28);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(4);
        grid.setVerticalSpacing(4);
        grid.setGravity(Gravity.CENTER);
        adapter = new MediaAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                ArrayList<String> paths = new ArrayList<String>();
                for (File f : media) {
                    paths.add(f.getAbsolutePath());
                }
                Intent it = new Intent(MainActivity.this, ViewerActivity.class);
                it.putStringArrayListExtra("paths", paths);
                it.putExtra("index", position);
                startActivity(it);
            }
        });
        root.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        emptyText = new TextView(this);
        emptyText.setText("No photos or videos yet");
        emptyText.setTextColor(0xFFAAAAAA);
        emptyText.setTextSize(15);
        emptyText.setGravity(Gravity.CENTER);
        emptyText.setVisibility(View.GONE);
        root.addView(emptyText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERM) {
            refresh();
        }
    }

    private void refresh() {
        pool.execute(new Runnable() {
            @Override
            public void run() {
                final List<File> found = new ArrayList<File>();
                File rootDir = Environment.getExternalStorageDirectory();
                if (rootDir != null) {
                    scan(rootDir, 0, found);
                }
                Collections.sort(found, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return Long.compare(b.lastModified(), a.lastModified());
                    }
                });
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        media.clear();
                        media.addAll(found);
                        adapter.notifyDataSetChanged();
                        boolean empty = media.isEmpty();
                        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
                        grid.setVisibility(empty ? View.GONE : View.VISIBLE);
                    }
                });
            }
        });
    }

    private void scan(File dir, int depth, List<File> out) {
        if (dir == null || depth > 5) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".")) {
                    scan(f, depth + 1, out);
                }
            } else {
                String e = ext(f.getName());
                if (IMAGE_EXT.contains(e) || VIDEO_EXT.contains(e)) {
                    out.add(f);
                }
            }
        }
    }

    private class MediaAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return media.size();
        }

        @Override
        public Object getItem(int position) {
            return media.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            FrameLayout cell;
            ImageView img;
            TextView badge;
            if (convertView == null) {
                cell = new FrameLayout(MainActivity.this);
                int size = parent.getWidth() / 3;
                if (size <= 0) {
                    size = 300;
                }
                cell.setLayoutParams(new GridView.LayoutParams(size, size));
                img = new ImageView(MainActivity.this);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                img.setBackgroundColor(0xFF222222);
                cell.addView(img, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                badge = new TextView(MainActivity.this);
                badge.setText("▶");
                badge.setTextColor(0xFFFFFFFF);
                badge.setTextSize(16);
                badge.setPadding(10, 4, 10, 4);
                FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                bp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
                cell.addView(badge, bp);
                cell.setTag(new Object[]{img, badge});
            } else {
                cell = (FrameLayout) convertView;
                Object[] parts = (Object[]) cell.getTag();
                img = (ImageView) parts[0];
                badge = (TextView) parts[1];
            }
            final File f = media.get(position);
            badge.setVisibility(isVideo(f) ? View.VISIBLE : View.GONE);
            final String key = f.getAbsolutePath() + "@" + f.lastModified();
            img.setTag(key);
            Bitmap hit = cache.get(key);
            if (hit != null) {
                img.setImageBitmap(hit);
            } else {
                img.setImageBitmap(null);
                loadThumb(f, key, img);
            }
            return cell;
        }
    }

    private void loadThumb(final File f, final String key, final ImageView img) {
        pool.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                try {
                    if (isVideo(f)) {
                        bmp = ThumbnailUtils.createVideoThumbnail(
                                f.getAbsolutePath(), MediaStore.Images.Thumbnails.MINI_KIND);
                    } else {
                        BitmapFactory.Options bounds = new BitmapFactory.Options();
                        bounds.inJustDecodeBounds = true;
                        BitmapFactory.decodeFile(f.getAbsolutePath(), bounds);
                        int sample = 1;
                        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
                            sample *= 2;
                        }
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = sample;
                        bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
                    }
                } catch (Throwable ignored) {
                }
                if (bmp != null) {
                    cache.put(key, bmp);
                    final Bitmap result = bmp;
                    ui.post(new Runnable() {
                        @Override
                        public void run() {
                            if (key.equals(img.getTag())) {
                                img.setImageBitmap(result);
                            }
                        }
                    });
                }
            }
        });
    }
}
