package com.uetty.common.tool.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 三元组
 */
@AllArgsConstructor
@Data
public class Tuple3<T1, T2, T3> {

    private T1 val1;
    private T2 val2;
    private T3 val3;

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 val1, T2 val2, T3 val3) {
        return new Tuple3<>(val1, val2, val3);
    }
}
