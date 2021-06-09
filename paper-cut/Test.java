package com.wangyifei;

/**
 * @author WangYifei
 * @date 2021-06-08 9:56
 * @describe
 */
public class Test {
    public static void main(String[] args) {
        Boxing merge = Boxing.merge(1080, 780, 445,195);
        // Boxing merge = Boxing.merge(1000, 1000, 100, 100);
        System.out.println(merge.getOptimals());
        System.out.println(merge.getCount());
    }
}
