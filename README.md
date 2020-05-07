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
Consumer<String> SERVER_LOGGER = s -> System.out.println("[server] - " + s);
Consumer<String> CLIENT_LOGGER = s -> System.out.println("[client] - " + s);

// add passing logic
Function<String, String> passSuccessStringExample = s -> {
	SERVER_LOGGER.accept("got success message:" + s);
	return "success - " + s;
};
Function<Payload, Mono<Payload>> passSuccessFunction = passSuccessStringExample
		.andThen(ByteBufPayload::create).andThen(Mono::just).compose(Payload::getDataUtf8);
serverRouter.addRequestResponseHandler(payload -> {
	// require password in the metadata
	return payload.getMetadataUtf8().contains("password");
}, passSuccessFunction);

// add failing fallback logic
Function<String, String> passFailFallbackStringFunction = s -> {
	SERVER_LOGGER.accept("got fail message:" + s);
	return "fail - " + s;
};
Function<Payload, Mono<Payload>> passFailFallbackFunction = passFailFallbackStringFunction
		.andThen(ByteBufPayload::create).andThen(Mono::just).compose(Payload::getDataUtf8);
serverRouter.addRequestResponseHandler(payload -> {
	// accept everything
	return true;
}, passFailFallbackFunction);

// create client function
Function<Payload, Mono<Payload>> clientFunction = clientRouter.getRequestResponseFunction();
Function<Tuple2<String, String>, String> clientTupleFunction = clientFunction.andThen(Mono::block)
		.andThen(Payload::getDataUtf8).compose(tup -> ByteBufPayload.create(tup.getT1(), tup.getT2()));

// use the client function
CLIENT_LOGGER.accept(clientTupleFunction.apply(Tuples.of("this should work", "xxxpasswordxxx")));
CLIENT_LOGGER.accept(clientTupleFunction.apply(Tuples.of("this should NOT work", "xxxzzzzzxxx")));
```
