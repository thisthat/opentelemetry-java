# HTTP Example

This is a simple example that demonstrates how to use the OpenTelemetry SDK 
to instrument a simple HTTP based Client/Server application. 
The example creates the **Root Span** on the client and sends the distributed context
over the HTTP request. On the server side, the example shows how to extract the context
and create a **Child Span** with attached a **Span Event**. 

# How to run

## Prerequisites
* Java 1.8.231

## 1 - Compile 
```bash
gradlew fatJar
```

## 2 - Start the Server
```bash
java -cp build/libs/opentelemetry-example-http-all-0.3.0-SNAPSHOT.jar io.opentelemetry.example.Server
```
 
## 3 - Start the Client
```bash
java -cp build/libs/opentelemetry-example-http-all-0.3.0-SNAPSHOT.jar io.opentelemetry.example.Client
```