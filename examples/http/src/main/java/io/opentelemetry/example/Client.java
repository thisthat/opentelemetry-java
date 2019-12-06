/*
 * Copyright 2019, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.example;

import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.exporters.inmemory.InMemorySpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.TracerSdkFactory;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.Status;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.propagation.HttpTraceContext;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Client {

    static int port = 8080;
    // OTel API
    Tracer OTel;
    // Export traces in memory
    InMemorySpanExporter inMemexporter = InMemorySpanExporter.create();
    // Share context via http headers
    HttpTextFormat textFormat = new HttpTraceContext();
    // Inject context into the request
    HttpTextFormat.Setter<HttpURLConnection> setter = new HttpTextFormat.Setter<HttpURLConnection>() {
        @Override
        public void put(HttpURLConnection carrier, String key, String value) {
            carrier.setRequestProperty(key, value);
        }
    };

    private void initTracer(){
        // Get the tracer
        TracerSdkFactory tracer = OpenTelemetrySdk.getTracerFactory();
        // Set to process in memory the spans
        tracer.addSpanProcessor(
                SimpleSpansProcessor.newBuilder(inMemexporter).build()
        );
        // Give the name to the traces
        OTel = tracer.get("example/http/client");
    }

    public Client() throws Exception {
        initTracer();

        // Connect to the server locally
        URL url = new URL("http://127.0.0.1:" + port);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        // Start a span
        Span span = OTel.spanBuilder("Request hello API").startSpan();
        // Inject the request with the context
        textFormat.inject(span.getContext(), con, setter);

        // Process the request
        con.setRequestMethod("GET");
        int status = con.getResponseCode();
        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();
        // Close the Span
        span.setStatus(Status.OK);
        span.end();

        // Output the result of the request
        System.out.println("Response Code: " + status);
        System.out.println("Response Msg: " + content);
    }


    public static void main(String[] args) throws Exception {
        // Perform request every 5s
        Thread t = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Client c = new Client();
                        Thread.sleep(5000);
                        for (SpanData spanData : c.inMemexporter.getFinishedSpanItems()) {
                            System.out.println("  - " + spanData);
                        }
                        c.inMemexporter.reset();
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace(System.out);
                    }
                }
            }
        };
        t.start();
    }
}
