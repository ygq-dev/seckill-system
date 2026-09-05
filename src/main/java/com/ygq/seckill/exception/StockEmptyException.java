package com.ygq.seckill.exception;

public class StockEmptyException extends RuntimeException {
    public StockEmptyException(String message) {
        super(message);
    }
}