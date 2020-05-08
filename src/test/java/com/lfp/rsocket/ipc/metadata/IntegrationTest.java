package com.lfp.rsocket.ipc.metadata;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.junit.Test;

import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.ipc.Client;
import io.rsocket.ipc.IPCRSocket;
import io.rsocket.ipc.RequestHandlingRSocket;
import io.rsocket.ipc.Server;
import io.rsocket.ipc.marshallers.Primitives;
import io.rsocket.ipc.marshallers.Strings;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class IntegrationTest {

	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();

	@SuppressWarnings("deprecation")
	@Test
	public void test() {
		MetadataDecoderLFP decoder = new MetadataDecoderLFP();
		RequestHandlingRSocket requestHandler = new RequestHandlingRSocket(decoder);
		{// start server
			SocketAcceptor socketAcceptor = (setup, client) -> Mono.just(requestHandler);
			RSocketServer.create(socketAcceptor).interceptors(ir -> {
			}).errorConsumer(t -> {
				java.util.logging.Logger.getLogger("[server]").log(Level.SEVERE, "uncaught error", t);
			}).bind(TcpServerTransport.create("localhost", 7000)).block();
		}
		MetadataEncoderLFP encoder = new MetadataEncoderLFP();
		RSocket rsocket;
		{// start client
			rsocket = RSocketConnector.create().connect(TcpClientTransport.create("localhost", 7000)).block();
		}
		AtomicBoolean ff = new AtomicBoolean();
		IPCRSocket service = Server.service("HelloService").noMeterRegistry().noTracer().marshall(Strings.marshaller())
				.unmarshall(Strings.unmarshaller()).requestResponse("hello", (s, byteBuf) -> Mono.just("Hello -> " + s))
				.requestResponse("goodbye", (s, byteBuf) -> Mono.just("Goodbye -> " + s))
				.requestResponse("count", Primitives.intMarshaller(),
						(charSequence, byteBuf) -> Mono.just(charSequence.length()))
				.requestResponse("increment", Primitives.intUnmarshaller(), Primitives.intMarshaller(),
						(integer, byteBuf) -> Mono.just(integer + 1))
				.requestStream("helloStream", (s, byteBuf) -> Flux.range(1, 10).map(i -> i + " - Hello -> " + s))
				.requestStream("toString", Primitives.longUnmarshaller(),
						(aLong, byteBuf) -> Flux.just(String.valueOf(aLong)))
				.fireAndForget("ff", (s, byteBuf) -> {
					ff.set(true);
					return Mono.empty();
				}).requestChannel("helloChannel", (s, publisher, byteBuf) -> Flux.just("Hello -> " + s)).toIPCRSocket();
		requestHandler.withEndpoint(service);

		Client<CharSequence, String> helloService = Client.service("HelloService").rsocket(rsocket)
				.customMetadataEncoder(encoder).noMeterRegistry().noTracer().marshall(Strings.marshaller())
				.unmarshall(Strings.unmarshaller());

		String r1 = helloService.requestResponse("hello").apply("Alice").block();
		Assert.assertEquals("Hello -> Alice", r1);

		String r2 = helloService.requestResponse("goodbye").apply("Bob").block();
		Assert.assertEquals("Goodbye -> Bob", r2);

		StepVerifier.create(helloService.requestStream("helloStream").apply("Carol")).expectNextCount(10)
				.expectComplete().verify();

		helloService.fireAndForget("ff").apply("boom").block();

		{// add polling bc of network delay
			Date stopAt = new Date(new Date().getTime() + Duration.ofSeconds(5).toMillis());
			while (!ff.get() && new Date().before(stopAt)) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					throw java.lang.RuntimeException.class.isAssignableFrom(e.getClass())
							? java.lang.RuntimeException.class.cast(e)
							: new java.lang.RuntimeException(e);
				}
			}
		}
		Assert.assertTrue(ff.get());

		String r3 = helloService.requestChannel("helloChannel").apply(Mono.just("Eve")).blockLast();
		Assert.assertEquals("Hello -> Eve", r3);

		int count = helloService.requestResponse("count", Primitives.intUnmarshaller()).apply("hello").block();
		Assert.assertEquals(5, count);

		long l = System.currentTimeMillis();
		String toString = helloService.requestStream("toString", Primitives.longMarshaller()).apply(l).blockLast();
		Assert.assertEquals(String.valueOf(l), toString);
		Disposable encoderPasswordDisposable = null;
		for (int i = 0; i < 3; i++) {
			if (i == 1) {
				encoderPasswordDisposable = encoder.addInterceptor(
						writer -> writer.writeString(MimeTypes.create("password"), "thisIsACoolPassWord!"));
				decoder.addInterceptor(reader -> {
					boolean match = reader.containsString(MimeTypes.create("password"), "thisIsACoolPassWord!");
					if (!match)
						throw new IllegalArgumentException("not authorized");
				});
			}
			if (i == 2)
				encoderPasswordDisposable.dispose();
			Runnable runTest = () -> {
				Integer increment = helloService
						.requestResponse("increment", Primitives.intMarshaller(), Primitives.intUnmarshaller()).apply(1)
						.block();
				Assert.assertEquals(2, increment.intValue());
			};
			if (i == 2)
				Assert.assertThrows(Throwable.class, () -> runTest.run());
			else
				runTest.run();
		}
	}
}