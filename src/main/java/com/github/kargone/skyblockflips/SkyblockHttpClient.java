package com.github.kargone.skyblockflips;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.IOException;

public class SkyblockHttpClient {

    /**
     * The local server is on the same machine, so anything slower than this means
     * it is wedged. Without explicit timeouts a stalled server would park the
     * calling thread forever.
     */
    private static final RequestConfig TIMEOUTS = RequestConfig.custom()
            .setConnectTimeout(2000)
            .setConnectionRequestTimeout(2000)
            .setSocketTimeout(5000)
            .build();

    public String sendPOST(String url, String postMessage) throws IOException {
        String result = "";
        HttpPost post = new HttpPost(url);

        // Set content type to JSON so the server knows how to parse it
        post.setHeader("Content-Type", "application/json");
        post.setConfig(TIMEOUTS);
        post.setEntity(new StringEntity(postMessage));

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(post)) {

            result = EntityUtils.toString(response.getEntity());
        }

        return result;
    }

    /** Reads one of the server's {@code /api/...} endpoints. */
    public String sendGET(String url) throws IOException {
        HttpGet get = new HttpGet(url);
        get.setConfig(TIMEOUTS);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse response = httpClient.execute(get)) {

            int status = response.getStatusLine().getStatusCode();
            if (status < 200 || status >= 300) {
                throw new IOException("HTTP " + status + " from " + url);
            }
            return EntityUtils.toString(response.getEntity());
        }
    }
}
