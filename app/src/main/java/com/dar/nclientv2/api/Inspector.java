package com.dar.nclientv2.api;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import com.dar.nclientv2.GalleryActivity;
import com.dar.nclientv2.MainActivity;
import com.dar.nclientv2.adapters.ListAdapter;
import com.dar.nclientv2.api.components.Gallery;
import com.dar.nclientv2.api.enums.ApiRequestType;
import com.dar.nclientv2.components.BaseActivity;
import com.dar.nclientv2.settings.Global;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Inspector {
    private static int actualPage;
    private static String actualQuery;
    private static ApiRequestType actualRequestType;

    public static int getActualPage() {
        return actualPage;
    }

    public static String getActualQuery() {
        return actualQuery;
    }

    public static ApiRequestType getActualRequestType() {
        return actualRequestType;
    }

    private final boolean byPopular;
    private final int page;
    private int pageCount;
    private final String query;
    private String url;
    private final ApiRequestType requestType;
    private List<Gallery> galleries;
    private static final OkHttpClient client=new OkHttpClient();

    public String getUrl() {
        return url;
    }
    public String getUsableURL(){
        StringBuilder builder = new StringBuilder("https://nhentai.net/?");
        String tagQuery=Global.getQueryString(query);
        switch (requestType){
            case BYALL: if(tagQuery.length()>0||Global.getOnlyLanguage()!=null) builder.append("q=").append(appendedLanguage()).append(tagQuery);break;
            case BYSEARCH:case BYTAG:
                builder.append("q=").append(query).append('+').append(appendedLanguage());
                if(requestType!=ApiRequestType.BYTAG||!Global.isOnlyTag())builder.append(tagQuery);
                if(byPopular)builder.append("&sort=popular");
        }
        if(page>1)builder.append("page=").append(actualPage);
        return builder.toString();
    }

    public Inspector(final BaseActivity activity, final int page, String query, final ApiRequestType requestType) {
        client.dispatcher().cancelAll();
        activity.getRefresher().setRefreshing(true);
        this.byPopular = Global.isByPopular();
        this.page=actualPage= page;
        this.query=actualQuery =query;
        this.requestType=actualRequestType = requestType;
        createUrl();
        client.newCall(new Request.Builder().url(url).build()).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activity.getRefresher().setRefreshing(false);
                        galleries=new ArrayList<>(1);
                        if(activity instanceof MainActivity){
                            activity.getRecycler().setAdapter(new ListAdapter(activity,galleries));
                            ((MainActivity)activity).hidePageSwitcher();
                        }
                        else if(activity instanceof GalleryActivity)activity.getRefresher().setEnabled(false);
                    }
                });
            }

            @Override
            public void onResponse(@NonNull Call call,@NonNull Response response) throws IOException {
                Log.d(Global.LOGTAG,"Response of "+url);
                parseGalleries(response.body().charStream());
                for (Gallery x:galleries)if(x.getId()>Global.getMaxId())Global.updateMaxId(activity,x.getId());
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(requestType!=ApiRequestType.BYSINGLE){
                            activity.getRecycler().setAdapter(new ListAdapter(activity,galleries));
                            ((MainActivity)activity).setInspector(Inspector.this);
                            ((MainActivity)activity).showPageSwitcher(Inspector.this.page,Inspector.this.pageCount);
                        }
                        else{
                            Intent intent=new Intent(activity, GalleryActivity.class);
                            intent.putExtra(activity.getPackageName()+".GALLERY",galleries.get(0));
                            intent.putExtra(activity.getPackageName()+".ZOOM",page-1);
                            activity.startActivity(intent);
                            activity.getRefresher().setEnabled(false);
                            activity.finish();
                        }
                        activity.getRefresher().setRefreshing(false);
                    }
                });
            }
        });

    }

    @Override
    public String toString() {
        return "Inspector{" +
                "byPopular=" + byPopular +
                ", page=" + page +
                ", pageCount=" + pageCount +
                ", query='" + query + '\'' +
                ", url='" + url + '\'' +
                ", requestType=" + requestType +
                ", galleries=" + galleries +
                '}';
    }

    private void parseGalleries(Reader s) throws IOException {
        JsonReader reader=new JsonReader(s);
        if (requestType == ApiRequestType.BYSINGLE){
            galleries = new ArrayList<>(1);
            galleries.add(new Gallery(reader));
            pageCount=1;
        } else {
            switch (requestType) {
                case RELATED:
                    galleries = new ArrayList<>(5);
                    break;
                default:
                    galleries = new ArrayList<>(25);
            }
            reader.beginObject();
            while (reader.peek() != JsonToken.END_OBJECT) {
                switch (reader.nextName()) {
                    case "error":
                        reader.skipValue();
                        break;
                    case "result":
                        reader.beginArray();
                        while (reader.hasNext()) galleries.add(new Gallery(reader));
                        reader.endArray();
                        break;
                    case "num_pages":
                        pageCount = reader.nextInt();
                        break;
                    case "per_page":
                        reader.skipValue();
                }
            }
        }
        reader.close();
    }

    private String appendedLanguage(){
        if(Global.getOnlyLanguage()==null)return "";
        switch (Global.getOnlyLanguage()){
            case ENGLISH:return "language:english";
            case CHINESE:return "language:chinese";
            case JAPANESE:return "language:japanese";
            case UNKNOWN:return "-language:japanese+-language:chinese+-language:english";
        }
        return "";
    }

    private void createUrl() {
        StringBuilder builder = new StringBuilder("https://nhentai.net/api/");
        String tagQuery=Global.getQueryString(query);
        switch (requestType) {
            case BYSINGLE:
            case RELATED:
                builder.append("gallery/").append(query);
                break;
            default:
                builder.append("galleries/");
        }
        switch (requestType) {
            case RELATED:
                builder.append("/related");
                break;
            case BYALL:
                if(tagQuery.length()==0&&Global.getOnlyLanguage()==null)builder.append("all?");
                else builder.append("search?query=").append(appendedLanguage()).append(tagQuery).append('&');
                break;
            case BYSEARCH:case BYTAG:
                builder.append("search?query=").append(query).append('+').append(appendedLanguage());
                if(requestType!=ApiRequestType.BYTAG||!Global.isOnlyTag())builder.append(tagQuery);
                builder.append('&');
                break;
        }
        if (page > 1&&requestType!=ApiRequestType.BYSINGLE) builder.append("page=").append(page);
        if (byPopular&&requestType!=ApiRequestType.BYSINGLE) builder.append("&sort=popular");
        url = builder.toString().replace(' ','+');
    }

    public boolean isByPopular() {
        return byPopular;
    }

    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return pageCount;
    }

    public String getQuery() {
        return query;
    }

    public ApiRequestType getRequestType() {
        return requestType;
    }

    public List<Gallery> getGalleries() {
        return galleries;
    }
}
