package org.jeecg.modules.packagemanagement.application.packstat.detect;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Isolated OkHttp GET client. Does not use HttpClientUtil.
 */
public class PackStatHttpClient
{
    private static final Logger log = LoggerFactory.getLogger(PackStatHttpClient.class);

    private final OkHttpClient client;

    public PackStatHttpClient()
    {
        this.client = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build();
    }

    public ProbeOutcome probe(String storeUrl)
    {
        Response response = null;
        try
        {
            Request request = new Request.Builder().url(storeUrl).get().build();
            response = client.newCall(request).execute();
            int status = response.code();
            PackStatDetectResult result = PackStatDetectResult.fromHttpStatus(status);
            return new ProbeOutcome(result, status, messageOf(result, status));
        }
        catch (Exception ex)
        {
            log.warn("packstat detect failed url={}", storeUrl, ex);
            String detail = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            return new ProbeOutcome(PackStatDetectResult.INVALID, -1, "检测无效: " + detail);
        }
        finally
        {
            if (response != null)
            {
                response.close();
            }
        }
    }

    private static String messageOf(PackStatDetectResult result, int status)
    {
        if (result == PackStatDetectResult.DELISTED)
        {
            return "下架（404）";
        }
        if (result == PackStatDetectResult.NORMAL)
        {
            return "上架（" + status + "）";
        }
        return "无效（HTTP " + status + "）";
    }

    public static final class ProbeOutcome
    {
        private final PackStatDetectResult result;
        private final int httpStatus;
        private final String message;

        public ProbeOutcome(PackStatDetectResult result, int httpStatus, String message)
        {
            this.result = result;
            this.httpStatus = httpStatus;
            this.message = message;
        }

        public PackStatDetectResult getResult()
        {
            return result;
        }

        public int getHttpStatus()
        {
            return httpStatus;
        }

        public String getMessage()
        {
            return message;
        }
    }
}
