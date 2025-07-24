package com.uetty.common.tool.core;

import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CodeCounter {

    /**
     * 文件内容读取为多行字符串
     * @param file 文件
     * @throws IOException io exception
     */
    public static List<String> readLines(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            return readLines(fis, StandardCharsets.UTF_8.name());
        }
    }

    /**
     * 文件内容读取为多行字符串
     * @param inputStream 输入流
     * @param charset 字符编码格式
     * @throws IOException io exception
     */
    public static List<String> readLines(InputStream inputStream, String charset) throws IOException {
        List<String> list = new ArrayList<>();
        try (InputStreamReader reader = new InputStreamReader(inputStream, charset);
             BufferedReader br = new BufferedReader(reader)) {

            String line;
            while ((line = br.readLine()) != null) {
                list.add(line);
            }
        }
        return list;
    }

    public static Node<Pair<Integer, Integer>> countCode(File file, Set<String> extNames, Set<String> ignoreFolders) {
        Node<Pair<Integer, Integer>> node = new Node<>();
        node.path = file.getAbsolutePath();
        if (!file.exists()) {
            System.out.println("该文件不存在！");
            node.value = Pair.of(0, 0);
            return node;
        }
        File[] fs = file.listFiles();
        Pair<Integer, Integer> pair = Pair.of(0, 0);
        if (fs == null) {
            node.value = Pair.of(0, 0);
            return node;
        }
        List<Node<Pair<Integer, Integer>>> nodes = new ArrayList<>();
        for (File file2 : fs) {
            final String name = file2.getName();

            if(file2.isDirectory()){
                boolean isIgnore = false;
                for (String ignoreExt : ignoreFolders) {
                    if (name.equalsIgnoreCase(ignoreExt)) {
                        isIgnore = true;
                        break;
                    }
                }

                if (isIgnore) {
                    continue;
                }

                Node<Pair<Integer, Integer>> pairNode = countCode(file2, extNames, ignoreFolders);
                nodes.add(pairNode);
            }else{

                boolean isTarget = false;
                for (String extName : extNames) {
                    if(name.endsWith(extName)){
                        isTarget = true;
                        break;
                    }
                }
                if (isTarget) {

                    List<String> strings;
                    List<String> removeComments = new ArrayList<>();
                    try {
                        strings = readLines(file2);
                        strings = strings.stream()
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .filter(s -> !s.startsWith("//"))
                                .collect(Collectors.toList());
                        if (name.endsWith(".java") || name.endsWith(".js") || name.endsWith(".ts")
                                || name.endsWith(".tsx")) {
                            boolean commentOpened = false;

                            for (String string : strings) {
                                if (!commentOpened && (string.startsWith("/* ") || string.startsWith("/**"))) {
                                    commentOpened = true;
                                } else if (commentOpened && string.endsWith("*/")) {
                                    commentOpened = false;
                                } else if (commentOpened) {
                                    continue;
                                } else {
                                    // 剔除空行
                                    removeComments.add(string);
                                }
                            }
                        } else {
                            removeComments = strings;
                        }

                        if (!removeComments.isEmpty()) {
                            pair = Pair.of(pair.getLeft() + 1, pair.getRight() + removeComments.size());
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        int countFile = 0;
        int countLine = 0;
        for (Node<Pair<Integer, Integer>> node2 : nodes) {
            countFile += node2.value.getLeft();
            countLine += node2.value.getRight();
            node.nexts.add(node2);
        }
        node.value = Pair.of(countFile + pair.getLeft(), countLine + pair.getRight());
        return node;
    }

    public static void main(String[] args) {
        final File file = new File("/Users/");
        String absolutePath = file.getAbsolutePath();
        if (!absolutePath.endsWith("/")) {
            absolutePath = absolutePath + "/";
        }

        Set<String> extNames = new HashSet<>();
        extNames.add("java");
        extNames.add("xml");
        extNames.add("groovy");


//        extNames.add("js");
//        extNames.add("ts");
//        extNames.add("tsx");
//        extNames.add("less");
//        extNames.add("vue");
//        extNames.add("html");

        Set<String> ignoreFolders = new HashSet<>();
        ignoreFolders.add(".git");
        ignoreFolders.add("node_modules");
        ignoreFolders.add("target");
        ignoreFolders.add(".idea");
        ignoreFolders.add(".flowclasses");

        Node<Pair<Integer, Integer>> pairNode = countCode(file, extNames, ignoreFolders);


        System.out.printf("%s\t%d%n",
                pairNode.path.replace(absolutePath, ""),
                pairNode.value.getRight());


        List<Node<Pair<Integer, Integer>>> nodes = new ArrayList<>();
        nodes.add(pairNode);

        int depth = 4;
        while (depth > 0) {

            nodes = nodes.stream()
                    .map(Node::getNexts)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
            System.out.println();
            for (Node<Pair<Integer, Integer>> node : nodes) {
                System.out.printf("%s\t%d%n",
                        node.path.replace(absolutePath, ""),
                        node.value.getRight());
            }

            depth--;
        }

    }



    public static class Node<T> {
        private String path;
        private T value;
        @Getter
        private List<Node<T>> nexts = new ArrayList<>();

    }

    @Setter
    @Getter
    public static class Pair<L, R> {

        private L left;
        private R right;

        public Pair(L left, R right) {
            this.left = left;
            this.right = right;
        }
        public static <L, R> Pair<L, R> of(L left, R right) {
            return new Pair<>(left, right);
        }

    }
}
