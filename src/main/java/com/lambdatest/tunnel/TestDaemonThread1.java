package com.lambdatest.tunnel;

import com.lambdatest.httpserver.HttpServer;

import java.io.IOException;
import java.util.ArrayList;
import com.lambdatest.Utils;

public class TestDaemonThread1 extends Thread {

    public Integer port = null;
    Utils utils = new Utils();

    public void run() {
        if (Thread.currentThread().isDaemon()) {//checking for daemon thread
            HttpServer httpServer = new HttpServer();
            try {
                port = HttpServer.findAvailablePort();
                utils.logger(port.toString());
//                System.out.println(port);
                int[] myIntArray = {port};
                httpServer.main(myIntArray);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("user thread work");
        }
    }
}
