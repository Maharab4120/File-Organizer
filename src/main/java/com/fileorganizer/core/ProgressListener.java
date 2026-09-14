package com.fileorganizer.core;

public interface ProgressListener {
    void update(int current, int total, String message);
}