package com.lfp.rsocketrouter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import org.reactivestreams.Publisher;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

public interface RSocketClientRouter extends RSocket {

	public static RSocketClientRouter create() {
		return new RSocketClientRouter.Impl();
	}

	public static RSocketClientRouter createAttached(RSocket rSocket) {
		RSocketClientRouter rSocketClientRouter = create();
		rSocketClientRouter.attach(rSocket);
		return rSocketClientRouter;
	}

	default Disposable attach(RSocket rSocket) {
		return attach(-1, rSocket);
	}

	Disposable attach(int index, RSocket rSocket);

	List<RSocket> detachAll();

	// FIRE AND FORGET

	default Function<Payload, Mono<Void>> getFireAndForgetFunction() {
		return Utils.asSingleFunction(getFireAndForgetMultiFunction(), mono -> mono.flatMap(m -> m.flux().next()));
	}

	Function<Payload, Flux<Tuple2<RSocket, Mono<Void>>>> getFireAndForgetMultiFunction();

	default Mono<Void> fireAndForget(Payload payload) {
		return getFireAndForgetFunction().apply(payload);
	}

	// REQUEST RESPONSE

	default Function<Payload, Mono<Payload>> getRequestResponseFunction() {
		return Utils.asSingleFunction(getRequestResponseMultiFunction(), mono -> mono.flatMap(m -> m.flux().next()));
	}

	Function<Payload, Flux<Tuple2<RSocket, Mono<Payload>>>> getRequestResponseMultiFunction();

	default Mono<Payload> requestResponse(Payload payload) {
		return getRequestResponseFunction().apply(payload);
	}

	// REQUEST STREAM

	default Function<Payload, Flux<Payload>> getRequestStreamFunction() {
		return Utils.asSingleFunction(getRequestStreamMultiFunction(),
				mono -> mono.flux().flatMap(Function.identity()));
	}

	Function<Payload, Flux<Tuple2<RSocket, Flux<Payload>>>> getRequestStreamMultiFunction();

	default Flux<Payload> requestStream(Payload payload) {
		return getRequestStreamFunction().apply(payload);
	}

	// REQUEST CHANNEL

	default Function<Publisher<Payload>, Flux<Payload>> getRequestChannelFunction() {
		return Utils.asSingleFunction(getRequestChannelMultiFunction(),
				mono -> mono.flux().flatMap(Function.identity()));
	}

	Function<Publisher<Payload>, Flux<Tuple2<RSocket, Flux<Payload>>>> getRequestChannelMultiFunction();

	default Flux<Payload> requestChannel(Publisher<Payload> payloads) {
		return getRequestChannelFunction().apply(payloads);
	}
	// META DATA PUSH

	default Function<Payload, Mono<Void>> getMetadataPushFunction() {
		return Utils.asSingleFunction(getMetadataPushMultiFunction(), mono -> mono.flatMap(m -> m.flux().next()));
	}

	Function<Payload, Flux<Tuple2<RSocket, Mono<Void>>>> getMetadataPushMultiFunction();

	default Mono<Void> metadataPush(Payload payload) {
		return getMetadataPushFunction().apply(payload);
	}

	static interface Default extends RSocketClientRouter {

		Iterable<RSocket> getAttachedRSockets();

		@Override
		default Function<Payload, Flux<Tuple2<RSocket, Mono<Void>>>> getFireAndForgetMultiFunction() {
			return Utils.handleClientRouting(getAttachedRSockets(), (rs, req) -> rs.fireAndForget(req));
		}

		// REQUEST RESPONSE
		@Override
		default Function<Payload, Flux<Tuple2<RSocket, Mono<Payload>>>> getRequestResponseMultiFunction() {
			return Utils.handleClientRouting(getAttachedRSockets(), (rs, req) -> rs.requestResponse(req));
		}

		// REQUEST STREAM
		@Override
		default Function<Payload, Flux<Tuple2<RSocket, Flux<Payload>>>> getRequestStreamMultiFunction() {
			return Utils.handleClientRouting(getAttachedRSockets(), (rs, req) -> rs.requestStream(req));
		}

		// REQUEST CHANNEL

		@Override
		default Function<Publisher<Payload>, Flux<Tuple2<RSocket, Flux<Payload>>>> getRequestChannelMultiFunction() {
			return Utils.handleClientRouting(getAttachedRSockets(), (rs, req) -> rs.requestChannel(req));
		}

		// META DATA PUSH

		@Override
		default Function<Payload, Flux<Tuple2<RSocket, Mono<Void>>>> getMetadataPushMultiFunction() {
			return Utils.handleClientRouting(getAttachedRSockets(), (rs, req) -> rs.metadataPush(req));
		}

	}

	static class Impl implements RSocketClientRouter.Default {

		private static final RSocket NOT_ATTACHED_RSOCKET = Utils.errorRSocket("client router is not attached");
		private List<RSocket> _rSockets;

		private List<RSocket> getAttachedRSocketsInternal() {
			if (_rSockets == null)
				synchronized (this) {
					if (_rSockets == null)
						_rSockets = new CopyOnWriteArrayList<>();
				}
			return _rSockets;
		}

		@Override
		public Iterable<RSocket> getAttachedRSockets() {
			return () -> {
				if (_rSockets == null || _rSockets.isEmpty())
					return Arrays.asList(NOT_ATTACHED_RSOCKET).iterator();
				return _rSockets.iterator();
			};
		}

		@Override
		public Disposable attach(int index, RSocket rSocket) {
			return Utils.listAdd(getAttachedRSocketsInternal(), index, rSocket);
		}

		@Override
		public List<RSocket> detachAll() {
			if (_rSockets == null || _rSockets.isEmpty())
				return Collections.emptyList();
			List<RSocket> removed = new ArrayList<>();
			_rSockets.removeIf(v -> {
				removed.add(v);
				return true;
			});
			return Collections.unmodifiableList(removed);
		}

	}

	public static void main(String[] args) {
		RSocketClientRouter router = new RSocketClientRouter.Impl();
		Function<Payload, Mono<Payload>> connectFunc = router.getRequestResponseFunction();
		Function<Payload, String> responseFunc = connectFunc.andThen(m -> m.block()).andThen(p -> p.getDataUtf8());
		Function<String, String> func = responseFunc.compose(s -> ByteBufPayload.create(s));
		Disposable disp = router.attach(new RSocket() {
			@Override
			public Mono<Payload> requestResponse(Payload payload) {
				return Mono.just(ByteBufPayload.create("response - " + payload.getDataUtf8()));
			}
		});
		System.out.println(func.apply("wow this is mapped"));
		disp.dispose();
		System.out.println(func.apply("wow this is mapped"));
	}
}
