package com.uetty.common.tool.core;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 四元组
 */
@AllArgsConstructor
@Data
public class Tuple4<T1, T2, T3, T4> {

    private T1 val1;
    private T2 val2;
    private T3 val3;
    private T4 val4;
}
