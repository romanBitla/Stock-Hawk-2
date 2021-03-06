package com.sam_chordas.android.stockhawk.service;

import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.os.RemoteException;
import android.util.Log;

import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.GcmTaskService;
import com.google.android.gms.gcm.TaskParams;
import com.sam_chordas.android.stockhawk.provider.QuoteContract;
import com.sam_chordas.android.stockhawk.rest.Utils;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
 * Created by maayy on 04-06-2016.
 */
public class HistoricalStockService extends GcmTaskService {
    private String LOG_TAG = HistoricalStockService.class.getSimpleName();

    private OkHttpClient client = new OkHttpClient();
    private Context mContext;
    private StringBuilder mStoredSymbols = new StringBuilder();
    public HistoricalStockService() {
    }

    public HistoricalStockService(Context context) {
        mContext = context;
    }

    String fetchData(String url) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .build();

        Response response = client.newCall(request).execute();
        return response.body().string();
    }

    @Override
    public int onRunTask(TaskParams params) {
        Cursor initQueryCursor;
        if (mContext == null) {
            mContext = this;
        }

        StringBuilder urlStringBuilder = new StringBuilder();
        try {
            // Base URL for the Yahoo query
            //and%20endDate%20%3D%20%222010-03-10%22
            urlStringBuilder.append("https://query.yahooapis.com/v1/public/yql?q=");
            urlStringBuilder.append(URLEncoder.encode("select * from yahoo.finance.historicaldata where symbol "
                    + "in (", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        if (params.getTag().equals("init")) {
            initQueryCursor = mContext.getContentResolver().query(QuoteContract.Quotes.CONTENT_URI,
                    new String[]{"Distinct " + QuoteContract.Historical.SYMBOL}, null,
                    null, null);
            if (initQueryCursor.getCount() == 0 || initQueryCursor == null) {
                // Init task. Populates DB with quotes for the symbols seen below
                try {
                    urlStringBuilder.append(
                            URLEncoder.encode("\"YHOO\",\"AAPL\",\"GOOG\",\"MSFT\")", "UTF-8"));
                    urlStringBuilder.append(URLEncoder.encode(" and startDate = \""+ Utils.getStartDate()+"\" and endDate = \""+Utils.getEndDate()+"\"","UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            } else {
                DatabaseUtils.dumpCursor(initQueryCursor);
                initQueryCursor.moveToFirst();
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    mStoredSymbols.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                try {
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                    urlStringBuilder.append(URLEncoder.encode(" and startDate = \""+ Utils.getStartDate()+"\" and endDate = \""+Utils.getEndDate()+"\"","UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }else if(params.getTag().equals("periodic")){
            initQueryCursor = mContext.getContentResolver().query(QuoteContract.Historical.CONTENT_URI,
                    new String[]{"Distinct " + QuoteContract.Historical.SYMBOL}, null,
                    null, null);
            if (initQueryCursor.getCount() != 0 || initQueryCursor != null){
                for (int i = 0; i < initQueryCursor.getCount(); i++) {
                    mStoredSymbols.append("\"" +
                            initQueryCursor.getString(initQueryCursor.getColumnIndex("symbol")) + "\",");
                    initQueryCursor.moveToNext();
                }
                mStoredSymbols.replace(mStoredSymbols.length() - 1, mStoredSymbols.length(), ")");
                try {
                    urlStringBuilder.append(URLEncoder.encode(mStoredSymbols.toString(), "UTF-8"));
                    urlStringBuilder.append(URLEncoder.encode(" and startDate = \""+ Utils.getPeriodicDate()+"\" and endDate = \""+Utils.getEndDate()+"\"","UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }else if (params.getTag().equals("add")) {
            // get symbol from params.getExtra and build query
            String stockInput = params.getExtras().getString("symbol");
            try {
                urlStringBuilder.append(URLEncoder.encode("\"" + stockInput + "\")", "UTF-8"));
                urlStringBuilder.append(URLEncoder.encode(" and startDate = \""+ Utils.getStartDate()+"\" and endDate = \""+Utils.getEndDate()+"\"","UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        urlStringBuilder.append("&env=store%3A%2F%2Fdatatables.org%2Falltableswithkeys&format=json");

        String urlString;
        String getResponse;
        int result = GcmNetworkManager.RESULT_FAILURE;

        if (urlStringBuilder != null) {
            urlString = urlStringBuilder.toString();
            Log.e(LOG_TAG, "Generated URL: " + urlString);
            try {
                getResponse = fetchData(urlString);
                result = GcmNetworkManager.RESULT_SUCCESS;
                try{
                    mContext.getContentResolver().applyBatch(QuoteContract.CONTENT_AUTHORITY,
                            Utils.histJsonToContentVals(getResponse));
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(LOG_TAG, "Error applying batch insert", e);
                }
            }catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }
}
