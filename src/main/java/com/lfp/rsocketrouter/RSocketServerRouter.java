package com.lfp.rsocketrouter;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;

import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.frame.FrameType;
import io.rsocket.util.ByteBufPayload;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface RSocketServerRouter extends RSocket {

	public static RSocketServerRouter create() {
		return new RSocketServerRouter.Impl();
	}

	// FIRE AND FORGET

	default Disposable addFireAndForgetHandler(Predicate<Payload> predicate, Function<Payload, Mono<Void>> function) {
		return addFireAndForgetHandler(-1, predicate, function);
	}

	default Disposable addFireAndForgetHandler(int index, Predicate<Payload> predicate,
			Function<Payload, Mono<Void>> handler) {
		return addFireAndForgetHandler(-1, Utils.asOptionalFunction(predicate, handler));
	}

	default Disposable addFireAndForgetHandler(Function<Payload, Optional<Mono<Void>>> handler) {
		return addFireAndForgetHandler(-1, handler);
	}

	Disposable addFireAndForgetHandler(int index, Function<Payload, Optional<Mono<Void>>> handler);

	// REQUEST RESPONSE

	default Disposable addRequestResponseHandler(Predicate<? extends Payload> predicate,
			Function<? extends Payload, Mono<Payload>> function) {
		return addRequestResponseHandler(-1, predicate, function);
	}

	default Disposable addRequestResponseHandler(int index, Predicate<? extends Payload> predicate,
			Function<? extends Payload, Mono<Payload>> function) {
		return addRequestResponseHandler(-1, Utils.asOptionalFunction(predicate, function));
	}

	default Disposable addRequestResponseHandler(Function<Payload, Optional<Mono<Payload>>> handler) {
		return addRequestResponseHandler(-1, handler);
	}

	Disposable addRequestResponseHandler(int index, Function<Payload, Optional<Mono<Payload>>> handler);

	// REQUEST STREAM

	default Disposable addRequestStreamHandler(Predicate<Payload> predicate,
			Function<Payload, Flux<Payload>> function) {
		return addRequestStreamHandler(-1, predicate, function);
	}

	default Disposable addRequestStreamHandler(int index, Predicate<Payload> predicate,
			Function<Payload, Flux<Payload>> function) {
		return addRequestStreamHandler(-1, Utils.asOptionalFunction(predicate, function));
	}

	default Disposable addRequestStreamHandler(Function<Payload, Optional<Flux<Payload>>> handler) {
		return addRequestStreamHandler(-1, handler);
	}

	Disposable addRequestStreamHandler(int index, Function<Payload, Optional<Flux<Payload>>> handler);

	// REQUEST CHANNEL

	default Disposable addRequestChannelHandler(Predicate<Publisher<Payload>> predicate,
			Function<Publisher<Payload>, Flux<Payload>> function) {
		return addRequestChannelHandler(-1, predicate, function);
	}

	default Disposable addRequestChannelHandler(int index, Predicate<Publisher<Payload>> predicate,
			Function<Publisher<Payload>, Flux<Payload>> function) {
		return addRequestChannelHandler(-1, Utils.asOptionalFunction(predicate, function));
	}

	default Disposable addRequestChannelHandler(Function<Publisher<Payload>, Optional<Flux<Payload>>> handler) {
		return addRequestChannelHandler(-1, handler);
	}

	Disposable addRequestChannelHandler(int index, Function<Publisher<Payload>, Optional<Flux<Payload>>> handler);

	// META DATA PUSH

	default Disposable addMetadataPushHandler(Predicate<Payload> predicate, Function<Payload, Mono<Void>> function) {
		return addMetadataPushHandler(-1, predicate, function);
	}

	default Disposable addMetadataPushHandler(int index, Predicate<Payload> predicate,
			Function<Payload, Mono<Void>> function) {
		return addMetadataPushHandler(-1, Utils.asOptionalFunction(predicate, function));
	}

	default Disposable addMetadataPushHandler(Function<Payload, Optional<Mono<Void>>> handler) {
		return addMetadataPushHandler(-1, handler);
	}

	Disposable addMetadataPushHandler(int index, Function<Payload, Optional<Mono<Void>>> handler);

	static interface Default extends RSocketServerRouter {

		List<Function<Payload, Optional<Mono<Void>>>> getFireAndForgetHandlers();

		List<Function<Payload, Optional<Mono<Payload>>>> getRequestResponseHandlers();

		List<Function<Payload, Optional<Flux<Payload>>>> getRequestStreamHandlers();

		List<Function<Publisher<Payload>, Optional<Flux<Payload>>>> getRequestChannelHandlers();

		List<Function<Payload, Optional<Mono<Void>>>> getMetadataPushHandlers();

		@Override
		default Mono<Void> fireAndForget(Payload payload) {
			return Utils.handleServerRouting(getFireAndForgetHandlers(), payload,
					Utils.errorMono("server route not found", FrameType.REQUEST_FNF, payload, true));
		}

		@Override
		default Mono<Payload> requestResponse(Payload payload) {
			return Utils.handleServerRouting(getRequestResponseHandlers(), payload,
					Utils.errorMono("server route not found", FrameType.REQUEST_RESPONSE, payload, true));
		}

		@Override
		default Flux<Payload> requestStream(Payload payload) {
			return Utils.handleServerRouting(getRequestStreamHandlers(), payload,
					Utils.errorFlux("server route not found", FrameType.REQUEST_STREAM, payload, true));
		}

		@Override
		default Flux<Payload> requestChannel(Publisher<Payload> payloads) {
			return Utils.handleServerRouting(getRequestChannelHandlers(), payloads,
					Utils.errorFlux("server route not found", FrameType.REQUEST_STREAM, payloads, true));
		}

		@Override
		default Mono<Void> metadataPush(Payload payload) {
			return Utils.handleServerRouting(getMetadataPushHandlers(), payload,
					Utils.errorMono("server route not found", FrameType.REQUEST_STREAM, payload, true));
		}

		@Override
		default Disposable addFireAndForgetHandler(int index, Function<Payload, Optional<Mono<Void>>> handler) {
			return Utils.listAdd(getFireAndForgetHandlers(), index, handler);
		}

		@Override
		default Disposable addRequestResponseHandler(int index, Function<Payload, Optional<Mono<Payload>>> handler) {
			return Utils.listAdd(getRequestResponseHandlers(), index, handler);
		}

		@Override
		default Disposable addRequestStreamHandler(int index, Function<Payload, Optional<Flux<Payload>>> handler) {
			return Utils.listAdd(getRequestStreamHandlers(), index, handler);
		}

		@Override
		default Disposable addRequestChannelHandler(int index,
				Function<Publisher<Payload>, Optional<Flux<Payload>>> handler) {
			return Utils.listAdd(getRequestChannelHandlers(), index, handler);
		}

		@Override
		default Disposable addMetadataPushHandler(int index, Function<Payload, Optional<Mono<Void>>> handler) {
			return Utils.listAdd(getMetadataPushHandlers(), index, handler);
		}
	}

	static class Impl implements RSocketServerRouter.Default {

		// this caching is to make the implementation as light weight as possible
		private static final Class<?> THIS_CLASS = new Object() {
		}.getClass().getEnclosingClass();
		private static int _LIST_CACHE_SIZE = -1;

		private static int getListCacheSize() {
			if (_LIST_CACHE_SIZE < 0)
				synchronized (THIS_CLASS) {
					if (_LIST_CACHE_SIZE < 0)
						_LIST_CACHE_SIZE = (int) (Arrays.asList(THIS_CLASS.getDeclaredMethods()).stream()
								.filter(m -> !Modifier.isStatic(m.getModifiers())).count() - 1);
				}
			return _LIST_CACHE_SIZE;
		}

		private Map<FrameType, CopyOnWriteArrayList<?>> _listCache;

		@SuppressWarnings("unchecked")
		private <X> CopyOnWriteArrayList<X> getList(FrameType frameType) {
			Objects.requireNonNull(frameType);
			if (_listCache == null)
				synchronized (this) {
					if (_listCache == null)
						_listCache = new ConcurrentHashMap<>(getListCacheSize());
				}
			return (CopyOnWriteArrayList<X>) _listCache.computeIfAbsent(frameType, nil -> new CopyOnWriteArrayList<>());
		}

		@Override
		public List<Function<Payload, Optional<Mono<Void>>>> getFireAndForgetHandlers() {
			return getList(FrameType.REQUEST_FNF);
		}

		@Override
		public List<Function<Payload, Optional<Mono<Payload>>>> getRequestResponseHandlers() {
			return getList(FrameType.REQUEST_RESPONSE);
		}

		@Override
		public List<Function<Payload, Optional<Flux<Payload>>>> getRequestStreamHandlers() {
			return getList(FrameType.REQUEST_RESPONSE);
		}

		@Override
		public List<Function<Publisher<Payload>, Optional<Flux<Payload>>>> getRequestChannelHandlers() {
			return getList(FrameType.REQUEST_RESPONSE);
		}

		@Override
		public List<Function<Payload, Optional<Mono<Void>>>> getMetadataPushHandlers() {
			return getList(FrameType.REQUEST_FNF);
		}

	}

	public static void main(String[] args) {
		RSocketServerRouter r = new RSocketServerRouter.Impl();
		Function<String, String> func = s -> {
			if (s.length() > 10)
				return String.format("what a good question:'%s'", s);
			return null;
		};
		Function<String, Optional<Mono<Payload>>> responseFunc = func.andThen(s -> Optional.ofNullable(s))
				.andThen(op -> op.map(s -> ByteBufPayload.create(s))).andThen(op -> op.map(v -> Mono.just(v)));
		Function<Payload, Optional<Mono<Payload>>> completed = responseFunc.compose(p -> p.getDataUtf8());
		r.addRequestResponseHandler(completed);
		String result = r.requestResponse(ByteBufPayload.create("how do you do brah?")).map(p -> p.getDataUtf8())
				.block();
		System.out.println(result);
		result = r.requestResponse(ByteBufPayload.create("how do?")).map(p -> p.getDataUtf8()).block();
		System.out.println(result);

	}
}
