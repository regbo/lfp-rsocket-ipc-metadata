# lfp-rsocket-router

```java
private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy");

public static void main(String[] args) {
	RSocketServerRouter serverRouter = RSocketServerRouter.create();
	RSocketClientRouter clientRouter;
	{
		RSocketServer.create(SocketAcceptor.with(serverRouter)).errorConsumer(t -> t.printStackTrace())
				.bind(TcpServerTransport.create("localhost", 7000)).block();
		RSocket client = RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
		clientRouter = RSocketClientRouter.createAttached(client);
	}
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
	Function<Payload, Mono<Payload>> mapped = mappedInput.andThen(d -> d.getTime() + "")
			.andThen(ByteBufPayload::create).andThen(Mono::just);
	return mapped;
}



//prints:
//Thu May 07 12:24:02 EDT 2020
//Thu May 07 00:00:00 EDT 2020
//Wed Dec 31 19:00:00 EST 1969
```
