package com.wuji.assistant.server;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ReactorTest {
    public static void main(String[] args) {
       test4();
    }

    public static void test1() {
        Flux<Integer> flux = Flux.just(1, 2, 3)
                .map(i -> i * 2);

        flux.subscribe(System.out::println);
    }
    public static void test2() {
        Mono<Integer> flux = Mono.just(1)
                .map(i -> i * 2);

        flux.subscribe(System.out::println);
    }
    public static void test3() {
        Flux.range(1, 1000)
                .subscribe(
                        System.out::println,
                        Throwable::printStackTrace,
                        () -> System.out.println("完成"),
                        subscription -> subscription.request(10)
                );
    }
    public static void test4() {
        Flux<Integer> flux = Flux.just(1, 2, 3)
                .flatMap(i -> Mono.just(i * 2));

        flux.subscribe(System.out::println);
    }
}
