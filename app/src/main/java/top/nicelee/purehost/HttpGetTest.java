package top.nicelee.purehost;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class HttpGetTest {
    public void get() throws Exception {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
//                    URL url = new URL("https://baidu.com");
                    URL url = new URL("https://httpbin.org/get");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    // Set request method to GET
                    connection.setRequestMethod("GET");

                    // Get the input stream of the connection
                    BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                    String inputLine;
                    StringBuffer content = new StringBuffer();

                    // Read the response
                    while ((inputLine = in.readLine()) != null) {
                        content.append(inputLine);
                    }

                    // Close connections
                    in.close();
                    connection.disconnect();

                    Log.i("HttpGetTest", "Response content: " + content);
                } catch (Exception e) {
                    Log.e("HttpGetTest", "Error: " + e.getMessage());
                }
            }
        });

        thread.start();
    }
}
