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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.exporters.inmemory.InMemorySpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SpanData;
import io.opentelemetry.sdk.trace.TracerSdkFactory;
import io.opentelemetry.sdk.trace.export.SimpleSpansProcessor;
import io.opentelemetry.trace.AttributeValue;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import io.opentelemetry.trace.Tracer;
import io.opentelemetry.trace.propagation.HttpTraceContext;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;


public class Server {

    private class helloHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange he) throws IOException {
            try {
                // Extract the context from the request
                SpanContext ctx = extractCtx(he);

                // Build a span based on the received context
                Span span = OTel
                        .spanBuilder("hello handler")
                        .setParent(ctx)
                        .startSpan();

                // Process the request
                answer(he, span);

                // Close the span
                span.end();
            } catch (StringIndexOutOfBoundsException e) {
                // msg without ctx
            } catch(Exception e){
                // catch other errors
                System.out.println(e.getMessage());
            }
        }

        private SpanContext extractCtx(HttpExchange he) throws IOException {
            SpanContext ctx = (SpanContext) textFormat.extract(he, getter);
            return ctx;
        }

        private void answer(HttpExchange he, Span span) throws IOException {
            String response = "Hello World!";
            he.sendResponseHeaders(200, response.length());
            OutputStream os = he.getResponseBody();
            os.write(response.getBytes());
            os.close();
            System.out.println("Served Client: " + he.getRemoteAddress());
            Map<String, AttributeValue> event = new HashMap<>();
            event.put(
                    "client.info",
                    AttributeValue.stringAttributeValue(he.getRemoteAddress().getHostString())
            );
            // Attach some events to the request
            span.addEvent("event", event);
        }
    }

    HttpServer server;
    static int port = 8080;

    // OTel API
    Tracer OTel;
    // Export traces in memory
    InMemorySpanExporter inMemexporter = InMemorySpanExporter.create();
    // Receive context via http headers
    HttpTextFormat.Getter<HttpExchange> getter = new HttpTextFormat.Getter<HttpExchange>() {
        @Override
        public String get(HttpExchange carrier, String key) {
            if(carrier.getRequestHeaders().containsKey(key)){
                return carrier.getRequestHeaders().get(key).get(0);
            }
            return "";
        }
    };
    // Extract context from the HTTP request
    HttpTextFormat textFormat = new HttpTraceContext();

    public Server() throws IOException {
        this(port);
    }

    public Server(int port) throws IOException {
        initTracer();
        server = HttpServer.create(new InetSocketAddress(port), 0);
        //Test urls
        server.createContext("/", new helloHandler());
        server.start();
        System.out.println("Server ready on http://127.0.0.1:" + port);
    }

    private void initTracer(){
        // Get the tracer
        TracerSdkFactory tracer = OpenTelemetrySdk.getTracerFactory();
        // Set to process in memory the spans
        tracer.addSpanProcessor(
                SimpleSpansProcessor.newBuilder(inMemexporter).build()
        );
        // Give the name to the traces
        OTel = tracer.get("example/http/server");
    }

    private void stop(){
        server.stop(0);
    }

    public static void main(String[] args) throws Exception {
        final Server s = new Server();
        // Gracefully close the server
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                s.stop();
            }
        });
        // Print new traces every 1s
        Thread t = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        Thread.sleep(1000);
                        for (SpanData spanData : s.inMemexporter.getFinishedSpanItems()) {
                            System.out.println("  - " + spanData);
                        }
                        s.inMemexporter.reset();
                    } catch (Exception e) {
                    }
                }
            }
        };
        t.start();
    }
}