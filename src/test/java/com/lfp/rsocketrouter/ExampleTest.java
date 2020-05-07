package com.lfp.rsocketrouter;

import static org.junit.Assert.assertThrows;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.ByteBufPayload;
import junit.framework.TestCase;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@RunWith(JUnit4.class)
public class ExampleTest extends TestCase {
	private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd/yy");
	private static long authValue;

	private static RSocketServerRouter serverRouter;
	private static CloseableChannel server;
	private static RSocket client;
	private static RSocketClientRouter clientRouter;

	@BeforeClass
	public static void beforeClass() {
		Utils.setLogRequestDetailsEnabled(true);
		authValue = UUID.randomUUID().getMostSignificantBits();
		serverRouter = RSocketServerRouter.create();
		server = RSocketServer.create(SocketAcceptor.with(serverRouter)).errorConsumer(t -> t.printStackTrace())
				.bind(TcpServerTransport.create("localhost", 7000)).block();
		client = RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
		clientRouter = RSocketClientRouter.createAttached(client);
	}

	@Test
	public void testDateParse() {
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
		parser = parser.andThen(d -> {
			if (d == null)
				throw new IllegalArgumentException("could not parse date");
			return d;
		});
		Function<String, Mono<Payload>> responseFunc = parser.andThen(d -> ByteBufPayload.create(d.getTime() + ""))
				.andThen(p -> Mono.just(p));
		Function<Payload, Mono<Payload>> requestAndResponseFunc = responseFunc.compose(p -> p.getDataUtf8());
		Disposable funcHandle = serverRouter
				.addRequestResponseHandler(p -> p.getMetadataUtf8().contains(authValue + ""), requestAndResponseFunc);
		Function<Payload, Mono<Payload>> requestResponseFunction = clientRouter.getRequestResponseFunction();
		Function<Tuple2<String, Long>, Mono<Payload>> inputFunction = requestResponseFunction
				.compose(tup -> ByteBufPayload.create(tup.getT1(), tup.getT2() + ""));
		Function<Tuple2<String, Long>, Mono<Date>> inputOutputFunction = inputFunction
				.andThen(m -> m.map(p -> new Date(Long.valueOf(p.getDataUtf8()))));
		Mono<Date> resp = inputOutputFunction.apply(Tuples.of("123456789", authValue));
		System.out.println(resp.block());
		Date now = new Date();
		resp = inputOutputFunction.apply(Tuples.of(now.getTime() + "", authValue));
		System.out.println(resp.block());
		resp = inputOutputFunction.apply(Tuples.of(DATE_FORMAT.format(now), authValue));
		System.out.println(resp.block());
		funcHandle.dispose();
		try {
			assertThrows(Throwable.class, () -> {
				inputOutputFunction.apply(Tuples.of(DATE_FORMAT.format(now), authValue)).block();
			});
		} catch (Throwable t) {
			// TODO: handle exception
		}
		try {
			assertThrows(Throwable.class, () -> {
				clientRouter.detachAll();
				try {
					inputOutputFunction.apply(Tuples.of(DATE_FORMAT.format(now), authValue)).block();
				} catch (Throwable t) {
					t.printStackTrace();
				}
			});
		} catch (

		Throwable t) {
			// TODO: handle exception
		}

	}

	@AfterClass
	public static void afterClass() {
		server.dispose();
	}

}