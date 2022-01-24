package com.lambdatest;

public class Utils {
    public void logger(String s) {
        String traceEnabled = System.getenv("isTraceEnabled");
        if(traceEnabled != null && traceEnabled != "") {
            System.out.println(s);
        }
    }
}
