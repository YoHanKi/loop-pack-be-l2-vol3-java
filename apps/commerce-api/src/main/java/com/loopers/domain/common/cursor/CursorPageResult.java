package com.loopers.domain.common.cursor;

import java.util.List;
import java.util.function.Function;

public record CursorPageResult<T>(
        List<T> items,
        String nextCursor,
        boolean hasNext,
        int size
) {
    public <R> CursorPageResult<R> map(Function<T, R> mapper) {
        return new CursorPageResult<>(
                items.stream().map(mapper).toList(),
                nextCursor,
                hasNext,
                size
        );
    }
}