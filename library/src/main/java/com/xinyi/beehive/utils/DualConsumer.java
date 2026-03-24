package com.xinyi.beehive.utils;

import java.util.Objects;

/**
 * <p>
 * 这是一个双参数的消费者接口，可以接收两个参数并执行操作，支持组合回调。
 * </p>
 *
 * 本接口模仿 JDK8 的 {@link java.util.function.BiConsumer} 自定义回调接口设计，兼容 Android SDK 19。
 *
 * @param <F> 第一个参数的类型
 * @param <S> 第二个参数的类型
 *
 * @author 新一
 * @date 2025/8/14 9:22
 */
@FunctionalInterface
public interface DualConsumer<F, S>  {

    /**
     * 对给定参数执行此操作
     *
     * @param first 作的第一个参数的类型
     * @param second 作的第二个参数的类型
     */
    void accept(F first, S second);

    /**
     * 返回一个组合回调接口，将此回调接口与另一个回调接口组合在一起
     *
     * @param after 前一个回调接口
     *
     * @return 组合后的回调接口
     * @throws NullPointerException if {@code after} is null
     */
    default DualConsumer<F, S> andThen(DualConsumer<? super F, ? super S> after) {
        Objects.requireNonNull(after);

        return (first, second) -> {
            accept(first, second);
            after.accept(first, second);
        };
    }
}