package com.lfp.rsocketrouter;

import java.text.SimpleDateFormat;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.ByteBufPayload;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public class ExampleGithub {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy");

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		RSocketServerRouter serverRouter = RSocketServerRouter.create();
		createServer(serverRouter);
		RSocket client = createClient();
		RSocketClientRouter clientRouter = RSocketClientRouter.createAttached(client);

		if (false) {
			Function<Payload, Mono<Payload>> passSuccessFunction = null; // implement however you want
			serverRouter.addRequestResponseHandler(payload -> {
				// require password in the metadata
				return payload.getMetadataUtf8().contains("password");
			}, passSuccessFunction);
			Function<Payload, Mono<Payload>> passFailFallbackFunction = null; // implement however you want
			serverRouter.addRequestResponseHandler(payload -> {
				// accept everything
				return true;
			}, passFailFallbackFunction);
		}

		if (true) {
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
		}

	}

	private static CloseableChannel createServer(RSocketServerRouter serverRouter) {
		return RSocketServer.create(SocketAcceptor.with(serverRouter)).errorConsumer(t -> t.printStackTrace())
				.bind(TcpServerTransport.create("localhost", 7000)).block();

	}

	private static RSocket createClient() {
		return RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
	}
}
