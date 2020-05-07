package com.lfp.rsocketrouter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;
import java.util.function.Function;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

public class Example {
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
		Function<Payload, Mono<Payload>> mapped = parser.andThen(Date::getTime).andThen(Object::toString)
				.andThen(ByteBufPayload::create).andThen(Mono::just).compose(p -> p.getDataUtf8());
		return mapped;
	}

	private static CloseableChannel createServer(RSocketServerRouter serverRouter) {
		return RSocketServer.create(SocketAcceptor.with(serverRouter)).errorConsumer(t -> t.printStackTrace())
				.bind(TcpServerTransport.create("localhost", 7000)).block();

	}

	private static RSocket createClient() {
		return RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
	}
}
