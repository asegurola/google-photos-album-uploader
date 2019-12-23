package com.google.photos.library.sample.helpers;

public class LogUtil {

    public static final void log(String logMessage) {
        System.out.println(logMessage);
    }

    public static final void logError(String errorMessage) {
        System.err.println(errorMessage);
    }

    public static final void logError(Exception exception) {
        exception.printStackTrace();
    }
}
