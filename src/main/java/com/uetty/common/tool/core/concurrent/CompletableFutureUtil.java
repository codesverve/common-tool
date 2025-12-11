package com.uetty.common.tool.core.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.function.Supplier;

public class CompletableFutureUtil {


    private static <T> CompletableFuture<T> anyOf(ThreadPoolExecutor threadPoolExecutor, Predicate<T> completePredicate, Supplier<T>... suppliers) {

        CompletableFuture<T> resultFuture = new CompletableFuture<>();

        // 用于记录是否已经完成（避免重复 complete）
        AtomicBoolean completed = new AtomicBoolean(false);
        List<Throwable> throwables = new ArrayList<>();

        List<CompletableFuture<T>> cfs = new ArrayList<>();
        for (Supplier<T> supplier : suppliers) {
            Supplier<T> wrapSupplier = () -> {
                if (completed.get()) {
                    // 如果已经完成，忽略这个代码的执行，即取消执行
                    return null;
                }
                return supplier.get();
            };
            CompletableFuture<T> cf = CompletableFuture.supplyAsync(wrapSupplier, threadPoolExecutor);
            cfs.add(cf);
        }

        for (CompletableFuture<T> cf : cfs) {
            cf.whenComplete((result, throwable) -> {
                synchronized (resultFuture) {
                    if (throwable != null) {
                        throwables.add(throwable);
                        boolean allDone = cfs.stream()
                                .allMatch(CompletableFuture::isDone);
                        if (allDone) {
                            resultFuture.completeExceptionally(new MultipleCompletableFutureException(throwables));
                            completed.set(true);
                        }
                        return;
                    }

                    if (completePredicate.test(result)) {
                        resultFuture.complete(result);
                        completed.set(true);
                        return;
                    }
                    boolean allDone = cfs.stream()
                            .allMatch(CompletableFuture::isDone);
                    if (allDone) {
                        if (!throwables.isEmpty()) {
                            resultFuture.completeExceptionally(new MultipleCompletableFutureException(throwables));
                            completed.set(true);
                            return;
                        }

                        resultFuture.complete(result);
                        completed.set(true);
                    }
                }
            });
        }

        return resultFuture;
    }



    public static class MultipleCompletableFutureException extends Exception {

        private List<Throwable> throwables;

        public MultipleCompletableFutureException(List<Throwable> throwables) {
            super(throwables.size() + " exception occurs when multiple completable future execute");
            this.throwables = throwables;
        }

        public List<Throwable> getThrowables() {
            return throwables;
        }
    }

}
