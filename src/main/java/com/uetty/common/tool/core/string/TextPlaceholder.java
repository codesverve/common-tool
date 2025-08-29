package com.uetty.common.tool.core.string;

import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 文本占位符工具
 */
@Slf4j
public class TextPlaceholder {

    /**
     * 允许的消息占位符集合
     */
    private Set<String> allowPlaceholders;
    /**
     * 消息字符串模版
     */
    private final String msgTemplate;
    /**
     * 当前消息字符串模版包含的占位符列表
     */
    private Set<String> containPlaceholders;

    private static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
    private static final String DEFAULT_PLACEHOLDER_SUFFIX = "}";

    private final String placeholderPrefix;

    private final String placeholderSuffix;

    private List<Node> nodes;

    /**
     * 不能作为占位符的字符集：大小写字母、数值、横线、下划线、小括号
     */
    private final Set<Character> INVALID_PLACEHOLDER_CHARSET;

    private Set<Character> buildInvalidPlaceholderCharset() {
        Set<Character> invalidPlaceholderCharset = new HashSet<>();
        invalidPlaceholderCharset.add(' ');
        invalidPlaceholderCharset.add('\t');
        invalidPlaceholderCharset.add('\n');
        invalidPlaceholderCharset.add('\r');
        invalidPlaceholderCharset.add('\f');
        invalidPlaceholderCharset.add('\u0000');
        this.placeholderPrefix.chars()
                .mapToObj(c -> (char) c)
                .forEach(invalidPlaceholderCharset::add);
        this.placeholderSuffix.chars()
                .mapToObj(c -> (char) c)
                .forEach(invalidPlaceholderCharset::add);
        return Collections.unmodifiableSet(invalidPlaceholderCharset);
    }

    public TextPlaceholder(String msgTemplate, String placeholderPrefix, String placeholderSuffix, Set<String> allowPlaceholders) {
        this.msgTemplate = msgTemplate;
        this.placeholderPrefix = placeholderPrefix;
        this.placeholderSuffix = placeholderSuffix;
        this.INVALID_PLACEHOLDER_CHARSET = buildInvalidPlaceholderCharset();

        updateAllowPlaceholders(allowPlaceholders);
        analyzeContainPlaceholders();
    }

    public TextPlaceholder(String msgTemplate, Set<String> allowPlaceholders) {
        this(msgTemplate, DEFAULT_PLACEHOLDER_PREFIX, DEFAULT_PLACEHOLDER_SUFFIX, allowPlaceholders);
    }

    public TextPlaceholder(String msgTemplate, String... allowPlaceholders) {
        this(msgTemplate, DEFAULT_PLACEHOLDER_PREFIX, DEFAULT_PLACEHOLDER_SUFFIX, new HashSet<>(Arrays.asList(allowPlaceholders)));
    }

    public boolean isContainPlaceholder(String placeholder) {
        if (placeholder.length() > placeholderPrefix.length() + placeholderSuffix.length() && placeholder.startsWith(placeholderPrefix) && placeholder.endsWith(placeholderSuffix)) {
            placeholder = placeholder.substring(placeholderPrefix.length(), placeholder.length() - placeholderSuffix.length());
        }
        return containPlaceholders.contains(placeholder);
    }

    public Set<String> getPlaceholders() {
        return new HashSet<>(containPlaceholders);
    }

    public String processPlaceholder(Map<String, String> placeholderValues) {
        Map<String, String> context = new HashMap<>();
        placeholderValues.forEach((k, v) -> {
            if (k.length() > placeholderPrefix.length() + placeholderSuffix.length() && k.startsWith(placeholderPrefix) && k.endsWith(placeholderSuffix)) {
                k = k.substring(placeholderPrefix.length(), k.length() - placeholderSuffix.length());
            }
            context.put(k, v);
        });

        StringBuilder sb = new StringBuilder();
        for (Node node : nodes) {
            if (node.isLiteral()) {
                sb.append(node.getLiteral());
            } else if (node.isVar()) {
                String placeholder = node.getVarName();
                String value = context.get(placeholder);
                if (value == null) {
                    throw new IllegalArgumentException("未找到占位符对应的值：" + placeholder);
                }
                sb.append(value);
            }
        }
        return sb.toString();
    }

    private String resolvePlaceholder(String placeholder) {
        if (placeholder.startsWith(placeholderPrefix)) {
            placeholder = placeholder.substring(placeholderPrefix.length());
        }
        if (placeholder.endsWith(placeholderSuffix)) {
            placeholder = placeholder.substring(0, placeholder.length() - placeholderSuffix.length());
        }
        return placeholder;
    }

    public void updateAllowPlaceholders(Set<String> allowPlaceholders) {
        this.allowPlaceholders = allowPlaceholders.stream()
                .filter(Objects::nonNull)
                .map(this::resolvePlaceholder)
                .peek(holder -> {
                    if (isValidPlaceholder(holder)) {
                        return;
                    }
                    throw new IllegalArgumentException("不支持的占位符：" + holder);
                })
                .collect(Collectors.toSet());
    }

    public void updateAllowPlaceholders(String... allowPlaceholders) {
        this.allowPlaceholders = Arrays.stream(allowPlaceholders)
                .filter(Objects::nonNull)
                .map(this::resolvePlaceholder)
                .collect(Collectors.toSet());
    }

    private int findPlaceholderStart(int start, char[] charArray, char[] placeholderPrefix) {
        if (start + placeholderPrefix.length > charArray.length) {
            return charArray.length;
        }
        int i = start;
        for (; i < charArray.length; i++) {
            boolean match = true;
            for (int j = 0; j < placeholderPrefix.length; j++) {
                if (charArray[i + j] != placeholderPrefix[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return charArray.length;
    }

    private int findPlaceholderClose(int start, char[] charArray, char[] placeholderSuffix) {
        if (start + placeholderSuffix.length > charArray.length) {
            return charArray.length;
        }
        int i = start;
        for (; i < charArray.length; i++) {
            boolean match = true;
            for (int j = 0; j < placeholderSuffix.length; j++) {
                if (charArray[i + j] != placeholderSuffix[j]) {
                    match = false;
                    break;
                }
            }
            if (match) {
                return i;
            }
        }
        return charArray.length;
    }

    /**
     * 校验占位符格式是否符合规范（允许大小写字母、数字、横线、下划线、小括号，数字、括号与横线不能为第一个字符）
     */
    private boolean isValidPlaceholder(String placeholder) {
        char[] charArray = placeholder.toCharArray();
        if (charArray.length == 0) {
            return false;
        }
        for (char c : charArray) {
            if (INVALID_PLACEHOLDER_CHARSET.contains(c)) {
                return false;
            }
        }
        return true;
    }

    public void analyzeContainPlaceholders() {
        Set<String> containPlaceholders = new HashSet<>();

        List<Node> nodes = new ArrayList<>();
        char[] charArray = msgTemplate.toCharArray();

        char[] prefix = placeholderPrefix.toCharArray();
        char[] suffix = placeholderSuffix.toCharArray();

        int prev = 0;
        for (int i = 0; i < charArray.length; i++) {
            i = findPlaceholderStart(i, charArray, prefix);
            if (i < charArray.length) {
                int close = findPlaceholderClose(i + prefix.length + 1, charArray, suffix);
                if (close < charArray.length) {
                    String placeholder = msgTemplate.substring(i + prefix.length, close);
                    if (!allowPlaceholders.isEmpty()) {
                        if (!allowPlaceholders.contains(placeholder)) {
                            log.debug("忽略不允许的占位符：{}", placeholder);
                            continue;
                        }
                    }
                    if (allowPlaceholders.isEmpty() && !isValidPlaceholder(placeholder)) {
                        log.warn("忽略不规范的占位符：{}", placeholder);
                        continue;
                    }

                    // 已找到占位符
                    containPlaceholders.add(placeholder);
                    if (i > prev) {
                        nodes.add(Node.ofLiteral(msgTemplate.substring(prev, i)));
                    }
                    String literal = msgTemplate.substring(i, close + suffix.length);
                    nodes.add(Node.ofVar(literal, placeholder));

                    prev = close + suffix.length;
                }
            }
        }
        if (prev < charArray.length) {
            nodes.add(Node.ofLiteral(msgTemplate.substring(prev)));
        }

        this.containPlaceholders = containPlaceholders;
        this.nodes = nodes;
    }

    @ToString
    @Getter
    private static class Node {
        // 变量占位符
        static final int TYPE_VAR = 1;
        // 字面量类型
        static final int TYPE_LITERAL = 2;

        /**
         * 节点类型
         */
        private Integer type;
        /**
         * 字面量
         */
        private String literal;
        /**
         * 变量名
         */
        private String varName;

        public static Node ofLiteral(String literal) {
            Node node = new Node();
            node.type = Node.TYPE_LITERAL;
            node.literal = literal;
            return node;
        }

        public static Node ofVar(String literal, String varName) {
            Node node = new Node();
            node.type = Node.TYPE_VAR;
            node.literal = literal;
            node.varName = varName;
            return node;
        }

        public boolean isLiteral() {
            return type == Node.TYPE_LITERAL;
        }

        public boolean isVar() {
            return type == Node.TYPE_VAR;
        }

    }

}
