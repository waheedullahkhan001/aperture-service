package com.aperture.apertureservice.ddd;

import java.util.List;

public record PageOf<T>(List<T> content, int page, int size, long totalElements) {
    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }
}
