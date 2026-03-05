package com.file.cumanager;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import com.bumptech.glide.Glide;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;

public class MainActivity extends Activity {
    private ListView listView;
    private TextView pathTxt, statusTxt;
    private EditText searchEdit;
    private View rootLayout, toolbar, bottomBar;
    private ProgressBar progress;
    private File currentDir;
    private List<File> filesList = new ArrayList<>();
    private List<File> fullList = new ArrayList<>();
    private boolean isDark = true;
    private int scrollIndex = 0;
    private int scrollTop = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        rootLayout = findViewById(R.id.main_root);
        toolbar = findViewById(R.id.toolbar);
        bottomBar = findViewById(R.id.bottom_bar);
        listView = findViewById(R.id.main_list);
        pathTxt = findViewById(R.id.current_path);
        statusTxt = findViewById(R.id.status_bar);
        searchEdit = findViewById(R.id.search_bar);
        progress = findViewById(R.id.top_progress);

        checkPermissions();
        currentDir = Environment.getExternalStorageDirectory();
        refresh(currentDir);

        searchEdit.addTextChangedListener(new TextWatcher() {
            public void onTextChanged(CharSequence s, int start, int b, int c) { filter(s.toString()); }
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void afterTextChanged(Editable s) {}
        });

        findViewById(R.id.btn_sort).setOnClickListener(v -> showSortMenu());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsMenu());

        listView.setOnItemClickListener((p, v, pos, id) -> {
            File f = filesList.get(pos);
            if (f.isDirectory()) { 
                savePos();
                currentDir = f; 
                refresh(currentDir); 
            } else { 
                handleAction(f); 
            }
        });

        listView.setOnItemLongClickListener((p, v, pos, id) -> {
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            showContext(filesList.get(pos));
            return true;
        });
        
        applyTheme();
    }

    private void savePos() {
        scrollIndex = listView.getFirstVisiblePosition();
        View v = listView.getChildAt(0);
        scrollTop = (v == null) ? 0 : (v.getTop() - listView.getPaddingTop());
    }

    private void restorePos() {
        listView.setSelectionFromTop(scrollIndex, scrollTop);
    }

    private void applyTheme() {
        int bg = isDark ? Color.parseColor("#121212") : Color.WHITE;
        int card = isDark ? Color.parseColor("#1E1E1E") : Color.WHITE;
        int text = isDark ? Color.WHITE : Color.BLACK;

        rootLayout.setBackgroundColor(bg);
        toolbar.setBackgroundColor(card);
        bottomBar.setBackgroundColor(card);
        pathTxt.setTextColor(text);
        searchEdit.setBackgroundColor(isDark ? Color.parseColor("#2C2C2C") : Color.WHITE);
        searchEdit.setTextColor(text);
        listView.setAdapter(new CustomAdapter());
    }

    private void refresh(File dir) {
        progress.setVisibility(View.VISIBLE);
        pathTxt.setText(dir.getAbsolutePath());
        File[] fArray = dir.listFiles();
        fullList.clear();
        if (fArray != null) fullList.addAll(Arrays.asList(fArray));
        Collections.sort(fullList, (a, b) -> {
            if (a.isDirectory() && !b.isDirectory()) return -1;
            if (!a.isDirectory() && b.isDirectory()) return 1;
            return a.getName().toLowerCase().compareTo(b.getName().toLowerCase());
        });
        filter(searchEdit.getText().toString());
        progress.setVisibility(View.GONE);
    }

    private void filter(String q) {
        filesList.clear();
        for (File f : fullList) if (f.getName().toLowerCase().contains(q.toLowerCase())) filesList.add(f);
        listView.setAdapter(new CustomAdapter());
        restorePos();
        statusTxt.setText("NovaDraG | " + filesList.size() + " об.");
    }

    private void handleAction(File f) {
        String n = f.getName().toLowerCase();
        if (n.endsWith(".apk")) install(f);
        else if (n.endsWith(".zip")) viewZip(f);
        else share(f);
    }

    private void install(File f) {
        try {
            Uri u = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(u, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка установки", 0).show();
        }
    }

    private void viewZip(File f) {
        ArrayList<String> entries = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(f))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) entries.add(ze.getName());
            new AlertDialog.Builder(this, 3).setTitle("Архив").setItems(entries.toArray(new String[0]), null).show();
        } catch (Exception e) {}
    }

    private void showContext(File f) {
        String[] opts = {"Имя", "Удалить", "Свойства"};
        new AlertDialog.Builder(this, 3).setTitle(f.getName()).setItems(opts, (d, w) -> {
            if (w == 0) rename(f);
            else if (w == 1) { f.delete(); refresh(currentDir); }
            else showProps(f);
        }).show();
    }

    private void showProps(File f) {
        String s = "Путь: " + f.getAbsolutePath() + "\nРазмер: " + (f.length() / 1024) + " KB";
        new AlertDialog.Builder(this, 3).setTitle("Инфо").setMessage(s).show();
    }

    private void rename(File f) {
        EditText input = new EditText(this); input.setText(f.getName());
        new AlertDialog.Builder(this).setTitle("Новое имя").setView(input).setPositiveButton("OK", (d, w) -> {
            if (f.renameTo(new File(f.getParent(), input.getText().toString()))) refresh(currentDir);
        }).show();
    }

    private void share(File f) {
        Uri u = FileProvider.getUriForFile(this, getPackageName() + ".provider", f);
        startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("*/*").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "NovaDraG Share"));
    }

    private void showSettingsMenu() {
        String[] opts = {"Тема", "Лицензия", "О программе"};
        new AlertDialog.Builder(this, 3).setTitle("NovaDraG Settings").setItems(opts, (d, w) -> {
            if (w == 0) { isDark = !isDark; applyTheme(); }
            else if (w == 1) showLicense();
            else showAbout();
        }).show();
    }

    private void showLicense() {
        new AlertDialog.Builder(this, 3).setTitle("MIT License")
            .setMessage("Copyright (c) 2026 NovaDraG IT Software\nВсе права защищены.")
            .setPositiveButton("OK", null).show();
    }

    private void showAbout() {
        new AlertDialog.Builder(this, 3).setTitle("NovaDraG IT")
            .setMessage("HP-Manager v1.0 beta\nProfessional File Tool.")
            .setPositiveButton("OK", null).show();
    }

    private void showSortMenu() {
        String[] opts = {"Имя", "Дата", "Размер"};
        new AlertDialog.Builder(this, 3).setTitle("Сортировать").setItems(opts, (d, w) -> refresh(currentDir)).show();
    }

    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + getPackageName())));
        }
    }

    @Override
    public void onBackPressed() {
        if (currentDir.getParentFile() != null && !currentDir.getPath().equals("/storage/emulated/0")) {
            currentDir = currentDir.getParentFile(); refresh(currentDir);
        } else super.onBackPressed();
    }

    class CustomAdapter extends BaseAdapter {
        public int getCount() { return filesList.size(); }
        public Object getItem(int i) { return filesList.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int i, View v, ViewGroup vg) {
            if (v == null) v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_file, vg, false);
            File f = filesList.get(i);
            TextView name = v.findViewById(R.id.name_txt);
            TextView desc = v.findViewById(R.id.desc_txt);
            ImageView img = v.findViewById(R.id.img_preview);
            
            name.setText(f.getName());
            name.setTextColor(isDark ? Color.WHITE : Color.BLACK);
            desc.setTextColor(isDark ? Color.GRAY : Color.DKGRAY);
            
            if (f.isDirectory()) {
                img.setImageResource(android.R.drawable.ic_menu_more);
                desc.setText("Папка");
            } else if (f.getName().toLowerCase().endsWith(".apk")) {
                try {
                    PackageManager pm = getPackageManager();
                    PackageInfo pi = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
                    if (pi != null) {
                        pi.applicationInfo.sourceDir = f.getAbsolutePath();
                        pi.applicationInfo.publicSourceDir = f.getAbsolutePath();
                        img.setImageDrawable(pi.applicationInfo.loadIcon(pm));
                        name.setText(pi.applicationInfo.loadLabel(pm).toString());
                        desc.setText(pi.packageName);
                    } else {
                        img.setImageResource(android.R.drawable.sym_def_app_icon);
                        desc.setText("Android Package");
                    }
                } catch (Exception e) {
                    img.setImageResource(android.R.drawable.sym_def_app_icon);
                }
            } else if (f.getName().toLowerCase().endsWith(".zip")) {
                img.setImageResource(android.R.drawable.ic_menu_save);
                desc.setText("Архив");
            } else {
                Glide.with(MainActivity.this).load(f).placeholder(android.R.drawable.ic_menu_agenda).into(img);
                desc.setText(f.length()/1024 + " KB");
            }
            return v;
        }
    }
}

