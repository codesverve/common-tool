package com.uetty.common.tool.core;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CollUtils {

    /**
     * 通用转换方法
     * @param input 输入对象
     * @return 字符串列表
     */
    public static List<String> toStringList(Object input) {
        if (input == null) return new ArrayList<>();

        List<String> result = new ArrayList<>();

        if (input.getClass().isArray()) {
            int length = Array.getLength(input);
            for (int i = 0; i < length; i++) {
                Object element = Array.get(input, i);
                if (element == null) {
                    continue;
                }
                result.add(String.valueOf(element));
            }
        } else if (input instanceof Collection) {
            for (Object element : (Collection<?>) input) {
                if (element == null) {
                    continue;
                }
                result.add(String.valueOf(element));
            }
        } else if (input instanceof Iterable) {
            for (Object element : (Iterable<?>) input) {
                if (element == null) {
                    continue;
                }
                result.add(String.valueOf(element));
            }
        } else {
            result.add(String.valueOf(input));
        }

        return result;
    }

    public static List<String> toStringList(String str, String separator) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] split = str.split(separator);
        return Arrays.stream(split).filter(s -> !s.trim().isEmpty()).collect(Collectors.toList());
    }

    public static List<Long> toLongList(String str, String separator) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] split = str.split(separator);
        return Arrays.stream(split).map(s -> {
            Long val = null;
            try {
                val = Long.parseLong(s);
            } catch (Exception ignore) {}
            return val;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static List<Integer> toIntList(String str, String separator) {
        if (str == null || str.trim().isEmpty()) {
            return new ArrayList<>();
        }

        String[] split = str.split(separator);
        return Arrays.stream(split).map(s -> {
            Integer val = null;
            try {
                val = Integer.parseInt(s);
            } catch (Exception ignore) {}
            return val;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public static <T> String join(String separator, Collection<T> collection) {
        StringBuilder sb = new StringBuilder();
        if (collection == null) {
            return sb.toString();
        }
        for (Object o : collection) {
            if (o != null) {
                sb.append(separator).append(o);
            }
        }
        int start = 0;
        if (sb.length() > 0) {
            start = separator.length();
        }
        return sb.substring(start);
    }

    public static <T> String join(String separator, T[] array) {
        StringBuilder sb = new StringBuilder();
        if (array == null) {
            return sb.toString();
        }
        for (T t : array) {
            if (t != null) {
                sb.append(separator).append(t);
            }
        }
        int start = 0;
        if (sb.length() > 0) {
            start = separator.length();
        }
        return sb.substring(start);
    }

    public static String join(String separator, String... array) {
        StringBuilder sb = new StringBuilder();
        if (array == null) {
            return sb.toString();
        }
        for (String str : array) {
            if (str != null) {
                sb.append(separator).append(str);
            }
        }
        int start = 0;
        if (sb.length() > 0) {
            start = separator.length();
        }
        return sb.substring(start);
    }

    public static String join(String separator, int... array) {
        StringBuilder sb = new StringBuilder();
        if (array == null) {
            return sb.toString();
        }
        for (int n : array) {
            sb.append(separator).append(n);
        }
        int start = 0;
        if (sb.length() > 0) {
            start = separator.length();
        }
        return sb.substring(start);
    }

    public static String join(String separator, long... array) {
        StringBuilder sb = new StringBuilder();
        if (array == null) {
            return sb.toString();
        }
        for (long n : array) {
            sb.append(separator).append(n);
        }
        int start = 0;
        if (sb.length() > 0) {
            start = separator.length();
        }
        return sb.substring(start);
    }

    public static boolean isEqualsDistinctNullable(Collection<?> c1, Collection<?> c2) {
        Set<Object> set1 = new HashSet<>();
        if (c1 != null) {
            set1.addAll(c1);
        }
        Set<Object> set2 = new HashSet<>();
        if (c2 != null) {
            set2.addAll(c2);
        }
        return isEqualList(set1, set2);
    }

    public static boolean isEqualList(Iterable<?> list1, Iterable<?> list2) {
        if (list1 == list2) {
            return true;
        }
        if  (list1 == null || list2 == null) {
            return false;
        }

        final Iterator<?> it1 = list1.iterator();
        final Iterator<?> it2 = list2.iterator();
        Object obj1;
        Object obj2;
        while (it1.hasNext() && it2.hasNext()) {
            obj1 = it1.next();
            obj2 = it2.next();

            if (!Objects.equals(obj1, obj2)) {
                return false;
            }
        }

        // 当两个Iterable长度不一致时返回false
        return !(it1.hasNext() || it2.hasNext());
    }

    public static <T, K> List<K> pickFieldList(Collection<T> collection, Function<T, K> getter) {
        return collection.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
