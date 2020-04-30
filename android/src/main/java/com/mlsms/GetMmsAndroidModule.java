package com.mlsms;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import android.util.Log;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import java.io.InputStream;
import java.lang.StringBuilder;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// https://stackoverflow.com/questions/3012287/how-to-read-mms-data-in-android

public class GetMmsAndroidModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private static Context context;

    public GetMmsAndroidModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        context = reactContext.getApplicationContext();
    }

    @Override
    public String getName() {
        return "GetMmsAndroid";
    }

    @ReactMethod
    public void countMMS(String threadId, Callback callback) {
        String selection = "thread_id=" + threadId;
        Cursor cursor = context.getContentResolver().query(Uri.parse("content://mms"), null, selection, null, null);
        int result = cursor.getCount();
        cursor.close();
        callback.invoke(result);
    }

    @ReactMethod
    public void getMMS(String threadId, Callback errorCallback, Callback successCallback) {
        try {

            String selection = "thread_id=" + threadId;
            Cursor cursor = context.getContentResolver().query(Uri.parse("content://mms/inbox"), null, selection, null, null);
            JSONArray jsons = new JSONArray();

            if(cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject json;
                    json = getJsonFromCursor(cursor);
                    JSONArray attachments = getMMSWithId(cursor.getString(cursor.getColumnIndex("_id")));
                    json.put("attachments", attachments);
                    jsons.put(json);
                } while (cursor.moveToNext());

                cursor.close();
            }

            try {
                successCallback.invoke(jsons.toString());
            } catch (Exception e) {
                errorCallback.invoke(e.getMessage());
            }
        } catch (Exception e) {
            errorCallback.invoke(e.getMessage());
            return;
        }
    }

    @ReactMethod
    public void getMMSWithIdPublic(String mmsId, Callback callback) {
        callback.invoke(this.getMMSWithId(mmsId));
    }

    private JSONArray getMMSWithId(String mmsId) {
        String selectionPart = "mid=" + mmsId;
        Uri uri = Uri.parse("content://mms/part");
        Cursor cursor = context.getContentResolver().query(uri, null, selectionPart, null, null);
        JSONArray jsons = new JSONArray();
        if(cursor != null && cursor.moveToFirst()) {
            do {
                String partId = cursor.getString(cursor.getColumnIndex("_id"));
                String type = cursor.getString(cursor.getColumnIndex("ct"));

                JSONObject json;
                json = getJsonFromCursor(cursor);

                if("text/plain".equals(type)) {
                    String data = cursor.getString(cursor.getColumnIndex("_data"));
                    String body;
                    if (data != null) {
                        // implementation of this method below
                        body = getMmsText(partId);
                    } else {
                        body = cursor.getString(cursor.getColumnIndex("text"));
                    }
                    if(body != null && body != "") {
                        // json.put("content", body);
                        jsons.put(json);
                    }
                } else if(this.isImageType(type)) {
                    // Bitmap bitmap = getMmsImage(partId);
                    // jsons.put(bitmap);

                    jsons.put(json);
                }
            } while (cursor.moveToNext());
        }
        return jsons;
    }

    @ReactMethod
    public void getMMSTextPublic(String mmsId, Callback callback) {
        callback.invoke(this.getMmsText(mmsId));
    }

    private String getMmsText(String id) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        StringBuilder sb = new StringBuilder();
        try {
            is = context.getContentResolver().openInputStream(partURI);
            if (is != null) {
                InputStreamReader isr = new InputStreamReader(is, "UTF-8");
                BufferedReader reader = new BufferedReader(isr);
                String temp = reader.readLine();
                while (temp != null) {
                    sb.append(temp);
                    temp = reader.readLine();
                }
            }
        } catch (IOException e) {}
        finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return sb.toString();
    }

    @ReactMethod
    public void getMMSImagePublic(String mmsId, Callback callback) {
        Bitmap bitmap = this.getMMSImage(mmsId);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);

        callback.invoke(Base64.encodeToString(outputStream.toByteArray(), Base64.DEFAULT));
    }

    private Bitmap getMMSImage(String id) {
        Uri partURI = Uri.parse("content://mms/part/" + id);
        InputStream is = null;
        Bitmap bitmap = null;
        try {
            is = context.getContentResolver().openInputStream(partURI);
            bitmap = BitmapFactory.decodeStream(is);
        } catch (IOException e) {

        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch (IOException e) {}
            }
        }
        return bitmap;
    }

    private JSONObject getJsonFromCursor(Cursor cur) {
        JSONObject json = new JSONObject();

        int nCol = cur.getColumnCount();
        String[] keys = cur.getColumnNames();
        try {
            for (int j = 0; j < nCol; j++)
                switch (cur.getType(j)) {
                    case Cursor.FIELD_TYPE_NULL:
                        json.put(keys[j], null);
                        break;
                    case Cursor.FIELD_TYPE_INTEGER:
                        json.put(keys[j], cur.getLong(j));
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        json.put(keys[j], cur.getFloat(j));
                        break;
                    case Cursor.FIELD_TYPE_STRING:
                        json.put(keys[j], cur.getString(j));
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        json.put(keys[j], cur.getBlob(j));
                }
        } catch (Exception e) {
            return null;
        }

        return json;
    }

    private Boolean isImageType(String type) {
        return "image/jpeg".equals(type) || "image/bmp".equals(type) ||
                "image/gif".equals(type) || "image/jpg".equals(type) ||
                "image/png".equals(type);
        // Boolean result = false;
        // if (type.equalsIgnoreCase("image/jpg")
        //         || type.equalsIgnoreCase("image/jpeg")
        //         || type.equalsIgnoreCase("image/png")
        //         || type.equalsIgnoreCase("image/gif")
        //         || type.equalsIgnoreCase("image/bmp")) {
        //     result = true;
        // }
        // return result;
    }
}
