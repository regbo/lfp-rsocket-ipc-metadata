# Updated RSocket IPC Metadata Handling

This is intended to be used as an add on to the IPC module of rsocket/rsocket-rpc-java found here: https://github.com/rsocket/rsocket-rpc-java

The rsocket/rsocket-rpc-java project uses an outdated version of RSocket and doesn't work well with CompositeMetadata. It uses custom parsing and encoding of metadata content to route messages.

This project provides drop in (see note) replacement MetadataDecoder and MetadataEncoder classes.

**NOTE:**

*Because rsocket-rpc-java isn't on maven, I'm using a fork of the project that can be accessed from jitpack. (the original rsocket-rpc-java fails on jitback because of protobuf requirements) That fork can be found here: https://github.com/regbo/lfp-rsocket-rpc-java*

The two classes at work are MetadataDecoderLFP and MetadataEncoderLFP. They use MetadataWriter and MetadataReader classes to allow for custom serialization of metadata content.

Out of the box they support the service/method/trace requirements of rsocket/rsocket-rpc-java but also allow for custom interceptors.

For example, we can use the following code to require a password on all requests:

```java
MetadataDecoderLFP decoder = new MetadataDecoderLFP();
RequestHandlingRSocket requestHandler = new RequestHandlingRSocket(decoder);
{// start server
    SocketAcceptor socketAcceptor = (setup, client) -> Mono.just(requestHandler);
    RSocketServer.create(socketAcceptor).interceptors(ir -> {
    }).errorConsumer(t -> {
        java.util.logging.Logger.getLogger("[server]").log(Level.SEVERE, "uncaught error", t);
    }).bind(TcpServerTransport.create("localhost", 7000)).block();
}
decoder.addInterceptor(reader -> {
    boolean match = reader.containsString(MimeTypes.create("password"), "thisIsACoolPassWord!");
    if (!match)
        throw new IllegalArgumentException("not authorized");
});
```
If we try to access the server, we will receive the following:

```
SEVERE: uncaught error
java.lang.IllegalArgumentException: not authorized
	at com.lfp.rsocket.ipc.metadata.IntegrationTest.lambda$13(IntegrationTest.java:116)
```

We can then modify the client to add the password, and everything works fine:

```java
MetadataEncoderLFP encoder = new MetadataEncoderLFP();
RSocket rsocket;
{// start client
    rsocket = RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
}
encoder.addInterceptor(
    writer -> writer.writeString(MimeTypes.create("password"), "thisIsACoolPassWord!"));
```
As a bonus, the writers and readers can handle Multimap values, by encoding the content as a url query. (EX: "key=val1&key=val2&neat=wow")

To illustrate this we can look at how tracing is handled, which requires a multimap of key value pairs to be stored in metadata.

Here's how it's encoded:

```java
private void appendTracing(MetadataWriter metadataWriter, SpanContext spanContext) {
	if (spanContext == null)
		return;
	Iterable<Entry<String, String>> items = spanContext.baggageItems();
	if (items == null)
		return;
	Map<String, Collection<String>> paramMap = new LinkedHashMap<>();
	for (Entry<String, String> ent : items)
		paramMap.computeIfAbsent(ent.getKey(), nil -> new LinkedHashSet<>()).add(ent.getValue());
	metadataWriter.writeEntries(MimeTypes.MIME_TYPE_TRACER, paramMap);
}
```
Here's how it's decoded:

```java
private SpanContext getTracingSpanContext(MetadataReader metadataReader) {
	if (tracer == null)
		return null;
	Map<String, String> tracerMetadata = new LinkedHashMap<>();
	metadataReader.streamEntriesNonEmpty(MimeTypes.MIME_TYPE_TRACER)
			.forEach(ent -> tracerMetadata.computeIfAbsent(ent.getKey(), nil -> ent.getValue()));
	if (tracerMetadata.isEmpty())
		return null;
	return Tracing.deserializeTracingMetadata(tracer, tracerMetadata);
}
```