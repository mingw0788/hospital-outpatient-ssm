package com.example.hospital.common;
public record Api<T>(T data) {
    public static <T> Api<T> ok(T data) { return new Api<>(data); }
}
