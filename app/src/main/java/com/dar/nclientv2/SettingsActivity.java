package com.dar.nclientv2;

import android.content.ContentUris;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.provider.MediaStore;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

import com.dar.nclientv2.settings.DefaultDialogs;
import com.dar.nclientv2.settings.Global;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Global.loadTheme(this);
        Global.initHideFromGallery(this);
        setContentView(R.layout.activity_settings);
        GeneralPreferenceFragment.act=this;
        getFragmentManager().beginTransaction().replace(android.R.id.content,
                new GeneralPreferenceFragment()).commit();
    }
    public static class GeneralPreferenceFragment extends PreferenceFragment {
        static SettingsActivity act;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            final PreferenceManager prefMgr = getPreferenceManager();
            prefMgr.setSharedPreferencesName("Settings");
            addPreferencesFromResource(R.xml.settings);
            findPreference(getString(R.string.key_hide_saved_images)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    if(Global.hasStoragePermission(getActivity())) {
                        Global.saveNoMedia(GeneralPreferenceFragment.this.getActivity());
                        if (!((SwitchPreference) preference).isChecked()) galleryAddPics();
                        else removePic();
                    }
                    return true;
                }
            });
            findPreference(getString(R.string.key_theme_select)).setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object o) {
                    act.recreate();
                    return true;
                }
            });
            findPreference(getString(R.string.key_cache)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    AlertDialog.Builder builder=new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.clear_cache);
                    builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Global.recursiveDelete(getActivity().getCacheDir());
                        }
                    }).setNegativeButton(R.string.no,null).setCancelable(true);
                    builder.show();

                    return true;
                }
            });
            findPreference(getString(R.string.image_quality_key)).setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    DefaultDialogs.pageChangerDialog(
                            new DefaultDialogs.Builder(getActivity())
                            .setDrawable(R.drawable.ic_image)
                            .setTitle(R.string.image_quality)
                            .setMax(100)
                            .setActual(Global.initImageQuality(getActivity()))
                            .setDialogs(new DefaultDialogs.DialogResults() {
                                @Override
                                public void positive(int actual) {
                                    Log.d(Global.LOGTAG,"progress: "+actual);
                                    Global.updateImageQuality(getActivity(),actual);
                                }

                                @Override public void negative() {}
                            })
                    );
                    return true;
                }
            });
            setHasOptionsMenu(true);
        }
        private void removePic(){
            Log.i(Global.LOGTAG,"Removing");
            String[] retCol = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA };
            Cursor cur = getActivity().getContentResolver().query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    retCol,
                    MediaStore.MediaColumns.DATA+" LIKE  '"+Global.GALLERYFOLDER.getAbsolutePath()+"%'", null, null
            );
            Log.i(Global.LOGTAG,"Count: "+cur.getCount());
            if (cur.getCount() == 0) {

                return;
            }
            while(cur.moveToNext()){
                Log.i(Global.LOGTAG,"DATA: "+cur.getString(1));
                deleteId(cur.getString(1),cur.getInt(0));
            }
            cur.close();
        }
        private void deleteId(String file,int id){
            try{
                File f = new File(file);
                File dest=File.createTempFile("temp",".jpg");
                copyFile(f,dest);
                Uri uri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                );
                getActivity().getContentResolver().delete(uri, null, null);
                copyFile(dest,f);
                dest.delete();
            } catch (IOException e) {
                Log.e(Global.LOGTAG, e.getLocalizedMessage(), e);
            }

        }
        private void copyFile(File source,File dest) throws IOException{
            try (FileChannel sourceChannel = new FileInputStream(source).getChannel(); FileChannel destChannel = new FileOutputStream(dest).getChannel()) {
                destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
            }
        }

        private void galleryAddPics() {
            Log.i(Global.LOGTAG,"Adding");
            for(File file:Global.GALLERYFOLDER.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jpg");
                }
            }))
                Global.addToGallery(getActivity(),file);

        }
    }

}