package com.lfp.rsocketrouter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import org.reactivestreams.Publisher;

import io.netty.util.ReferenceCounted;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.frame.FrameType;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

class Utils {

	private static Map<Optional<Void>, Boolean> LOG_REQUEST_DETAILS = new ConcurrentHashMap<>(1);

	private static boolean isLogRequestDetailsEnabled() {
		return LOG_REQUEST_DETAILS.getOrDefault(Optional.empty(), false);
	}

	static void setLogRequestDetailsEnabled(boolean enabled) {
		LOG_REQUEST_DETAILS.compute(Optional.empty(), (nil, current) -> {
			if (current == null || Objects.equals(enabled, current))
				return enabled;
			throw new IllegalAccessError("log request details has already been set to:" + current);
		});
	}

	static final RSocket errorRSocket(String message) {
		return new RSocket() {

			public Mono<Void> fireAndForget(Payload payload) {
				return errorMono(message, FrameType.REQUEST_FNF, payload, true);
			}

			public Mono<Payload> requestResponse(Payload payload) {
				return errorMono(message, FrameType.REQUEST_RESPONSE, payload, true);
			}

			public Flux<Payload> requestStream(Payload payload) {
				return errorFlux(message, FrameType.REQUEST_STREAM, payload, true);
			}

			public Flux<Payload> requestChannel(Publisher<Payload> payloads) {
				return errorFlux(message, FrameType.REQUEST_CHANNEL, payloads, true);
			}

			public Mono<Void> metadataPush(Payload payload) {
				return errorMono(message, FrameType.METADATA_PUSH, payload, true);
			}
		};
	}

	static <X> Flux<X> errorFlux(String message, FrameType frameType, Object request, boolean closeReferenceCounted) {
		return Flux.error(() -> {
			String data;
			String metaData;
			if (request == null) {
				data = null;
				metaData = null;
			} else if (isLogRequestDetailsEnabled() && request instanceof Payload) {
				try {
					data = ((Payload) request).getDataUtf8();
				} catch (Exception e) {
					data = "[data read error]";
				}
				try {
					metaData = ((Payload) request).getMetadataUtf8();
				} catch (Exception e) {
					metaData = "[metaData read error]";
				}

			} else if (isLogRequestDetailsEnabled()) {
				data = "[stream request data]";
				metaData = "[stream request metaData]";
			} else {
				data = "[omitted]";
				metaData = "[omitted]";
			}
			if (closeReferenceCounted && request instanceof ReferenceCounted)
				((ReferenceCounted) request).release();
			return new IllegalArgumentException(
					String.format("%s - frameType:%s requestMetaData:%s requestData:%s requestType:%s", message,
							frameType, metaData, data, request == null ? null : request.getClass().getName()));
		});
	}

	static <X> Mono<X> errorMono(String message, FrameType frameType, Object request, boolean closeReferenceCounted) {
		Flux<X> flux = errorFlux(message, frameType, request, closeReferenceCounted);
		return flux.next();
	}

	static <REQ, RESP_VAL, RESP_PUB extends Publisher<RESP_VAL>> RESP_PUB handleServerRouting(
			Iterable<? extends Function<REQ, Optional<RESP_PUB>>> functions, REQ request, RESP_PUB onNoRouteFound) {
		Objects.requireNonNull(functions);
		Objects.requireNonNull(request);
		Objects.requireNonNull(onNoRouteFound);
		for (Function<REQ, Optional<RESP_PUB>> func : functions) {
			Optional<RESP_PUB> op = func.apply(request);
			if (!op.isPresent())
				continue;
			return op.get();
		}
		return onNoRouteFound;
	}

	static <REQ, RESP_VAL, RESP_PUB extends Publisher<RESP_VAL>> Function<REQ, Flux<Tuple2<RSocket, RESP_PUB>>> handleClientRouting(
			Iterable<RSocket> attachedRSockets, BiFunction<RSocket, REQ, RESP_PUB> invokeFunction) {
		return req -> Flux.fromIterable(attachedRSockets).map(rs -> Tuples.of(rs, invokeFunction.apply(rs, req)));
	}

	static <X> Disposable listAdd(List<X> list, int index, X value) {
		if (index == -1)
			list.add(value);
		else
			list.add(index, value);
		return Disposables.composite(() -> list.removeIf(v -> Objects.equals(v, value)));
	}

	static <X, Y> Function<X, Optional<Y>> asOptionalFunction(Predicate<X> predicate, Function<X, Y> function) {
		Objects.requireNonNull(predicate);
		Objects.requireNonNull(function);
		return input -> {
			if (!predicate.test(input))
				return Optional.empty();
			return Optional.of(function.apply(input));
		};
	}

	static <X, Y, Z> Function<X, Y> asSingleFunction(Function<X, Flux<Tuple2<RSocket, Z>>> multiFunction,
			Function<Mono<Z>, Y> resultMap) {
		return request -> {
			Flux<Tuple2<RSocket, Z>> result = multiFunction.apply(request);
			Mono<Z> next = result.map(t -> t.getT2()).next();
			return resultMap.apply(next);
		};
	}

}
