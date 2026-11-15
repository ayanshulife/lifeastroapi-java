package com.lifeastroapi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * One generated sample call (see {@link Samples}): the blocking and the asynchronous form of the
 * same method with the same inputs.
 *
 * @param key     {@code group.method}, e.g. {@code panchang.basic}
 * @param path    the path template
 * @param shape   the manifest shape
 * @param hasLang whether the method takes {@code lang}
 * @param sync    calls the blocking method
 * @param async   calls the asynchronous method
 */
record Sample(
        String key,
        String path,
        String shape,
        boolean hasLang,
        BiFunction<LifeAstro, Lang, JsonNode> sync,
        BiFunction<LifeAstroAsync, Lang, CompletableFuture<JsonNode>> async) {
}
