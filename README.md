# lfp-rsocket-router

Routing and serializing payloads in RSocket can be a PIA. This library hopes to minimize that by wrapping both clients and servers in "routers". These routers allow clients to generate functions, and servers to accept handlers. Client routers can be attached to multiple client RSockets, and each server router accepts mutiple handlers.

Here is a simple example. We can add a filter to the server that requires a password:
```java
Function<Payload, Mono<Payload>> passSuccessFunction = null; // implement however you want
serverRouter.addRequestResponseHandler(payload -> {
	//require password in the metadata
	return payload.getMetadataUtf8().contains("password");
}, passSuccessFunction);
Function<Payload, Mono<Payload>> passFailFallbackFunction = null; // implement however you want
serverRouter.addRequestResponseHandler(payload -> {
	//accept everything
	return true;
}, passFailFallbackFunction);
```
We could expand on this and map the server handler and the client function into something more useable:

```java
Consumer<String> SERVER_LOGGER = s -> System.out.println("[server] - " + s);
Consumer<String> CLIENT_LOGGER = s -> System.out.println("[client] - " + s);

//add passing logic
Function<String, String> passSuccessStringExample = s -> {
	SERVER_LOGGER.accept("got success message:" + s);
	return "success - " + s;
};
Function<Payload, Mono<Payload>> passSuccessFunction = passSuccessStringExample
		.andThen(ByteBufPayload::create).andThen(Mono::just).compose(p -> p.getDataUtf8());
serverRouter.addRequestResponseHandler(payload -> {
	return payload.getMetadataUtf8().contains("password");
}, passSuccessFunction);

//add failing fallback logic
Function<String, String> passFailFallbackStringFunction = s -> {
	SERVER_LOGGER.accept("got fail message:" + s);
	return "fail - " + s;
};
Function<Payload, Mono<Payload>> passFailFallbackFunction = passFailFallbackStringFunction
		.andThen(ByteBufPayload::create).andThen(Mono::just).compose(p -> p.getDataUtf8());
serverRouter.addRequestResponseHandler(payload -> true, passFailFallbackFunction);

//create client function
Function<Payload, Mono<Payload>> clientFunction = clientRouter.getRequestResponseFunction();
Function<Tuple2<String, String>, String> clientTupleFunction = clientFunction.andThen(Mono::block)
		.andThen(Payload::getDataUtf8).compose(tup -> ByteBufPayload.create(tup.getT1(), tup.getT2()));

//use the client function			
CLIENT_LOGGER.accept(clientTupleFunction.apply(Tuples.of("this should work", "xxxpasswordxxx")));
CLIENT_LOGGER.accept(clientTupleFunction.apply(Tuples.of("this should NOT work", "xxxzzzzzxxx")));
```
This outputs:
```java
[server] - got success message:this should work
[client] - success - this should work
[server] - got fail message:this should NOT work
[client] - fail - this should NOT work
```
Here's a more complex example that creates a date parsing function.
```java
private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy");

public static void main(String[] args) {
	RSocketServerRouter serverRouter = RSocketServerRouter.create();
	createServer(serverRouter);
	RSocket client = createClient();
	RSocketClientRouter clientRouter = RSocketClientRouter.createAttached(client);
	Disposable serverFuncDisposable = serverRouter.addRequestResponseHandler(p -> true, createDateParserHandler());
	Function<String, Date> clientFunction = createClientFunction(clientRouter.getRequestResponseFunction());
	System.out.println(clientFunction.apply(new Date().getTime() + ""));
	System.out.println(clientFunction.apply(DATE_FORMAT.format(new Date())));
	System.out.println(clientFunction.apply("0"));
	serverFuncDisposable.dispose();
	clientRouter.detachAll().forEach(rs -> rs.dispose());
}

private static Function<String, Date> createClientFunction(Function<Payload, Mono<Payload>> func) {
	Function<String, Mono<Payload>> mappedInput = func.compose(ByteBufPayload::create);
	Function<String, Date> mapped = mappedInput.andThen(Mono::block).andThen(Payload::getDataUtf8)
			.andThen(Long::valueOf).andThen(Date::new);
	return mapped;
}

private static Function<Payload, Mono<Payload>> createDateParserHandler() {
	Function<String, Date> parser = s -> {
		if (s == null)
			return null;
		s = s.trim();
		if (s.isEmpty())
			return null;
		try {
			return new Date(Long.parseLong(s));
		} catch (NumberFormatException e) {
			// suppress
		}
		try {
			return DATE_FORMAT.parse(s);
		} catch (ParseException e) {
			// suppress
		}
		return null;
	};
	parser = parser.andThen(v -> Objects.requireNonNull(v));
	Function<Payload, Date> mappedInput = parser.compose(p -> p.getDataUtf8());
	Function<Payload, Mono<Payload>> mapped = mappedInput.andThen(Date::getTime).andThen(Object::toString)
			.andThen(ByteBufPayload::create).andThen(Mono::just);
	return mapped;
}

private static CloseableChannel createServer(RSocketServerRouter serverRouter) {
	return RSocketServer.create(SocketAcceptor.with(serverRouter)).errorConsumer(t -> t.printStackTrace())
			.bind(TcpServerTransport.create("localhost", 7000)).block();

}

private static RSocket createClient() {
	return RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
}
```
