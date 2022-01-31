package com.lambdatest;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

/**
 * kills the port active.
 */
public class KillPort {

    /**
     * Executes kill process
     */
    public static void killProcess(int port) {

        int pid = getPid(port);

        if (pid == 0) {
            return;
        }

        String[] command = { "taskkill", "/F", "/T", "/PID", Integer.toString(pid) };
        if (System.getProperty("os.name").startsWith("Linux")) {
            String[] cmd = { "kill", "-9", Integer.toString(pid) };
            command = cmd;
        }

        try {
            Process killer = Runtime.getRuntime().exec(command);
            int result = killer.waitFor();
            System.out.println();

        } catch (IOException e1) {
            e1.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * gets the pid of port running
     */
    public static int getPid(int port) {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return getPidWin(port);
        } else {
            return getPidLinux(port);
        }
    }

    /**
     * gets PID on Windows
     */
    public static int getPidWin(int port) {
        String[] command = { "netstat", "-on" };
        try {
            Process netstat = Runtime.getRuntime().exec(command);

            StringBuilder conectionList = new StringBuilder();
            Reader reader = new InputStreamReader(netstat.getInputStream());
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                conectionList.append(buffer, 0, n);
            }
            reader.close();
            String[] conections = conectionList.toString().split("\n");
            int portIdx = 10000;
            String pid = null;
            for (String conection : conections) {
                int idx = conection.indexOf(":" + port);
                if (idx == -1 || idx > portIdx) {
                    continue;
                }
                String state = "ESTABLISHED";
                int stateIdx = conection.indexOf(state);
                if (stateIdx == -1) {
                    continue;
                }
                portIdx = idx;
                idx = stateIdx + state.length();
                pid = conection.substring(idx).trim();
            }
            if (pid != null) {
                return Integer.valueOf(pid);
            }

        } catch (Exception e) {
        }

        return 0;

    }

    /**
     * Gets PID on Linux
     */
    public static int getPidLinux(int port) {
        String[] command = { "netstat", "-anp" };
        try {
            Process netstat = Runtime.getRuntime().exec(command);

            StringBuilder conectionList = new StringBuilder();
            Reader reader = new InputStreamReader(netstat.getInputStream());
            char[] buffer = new char[1024];
            for (int n = reader.read(buffer); n != -1; n = reader.read(buffer)) {
                conectionList.append(buffer, 0, n);
            }
            reader.close();
            String[] conections = conectionList.toString().split("\n");
            String pid = null;
            for (String conection : conections) {
                if (conection.contains(":" + port) && conection.contains("/soffice.bin")) {
                    int idx = conection.indexOf("/soffice.bin");
                    int idx2 = idx;
                    while (Character.isDigit(conection.charAt(--idx2)))
                        ;
                    pid = conection.substring(idx2 + 1, idx);
                }
            }
            if (pid != null) {
                return Integer.valueOf(pid);
            }

        } catch (Exception e) {
        }
        return 0;
    }
}
