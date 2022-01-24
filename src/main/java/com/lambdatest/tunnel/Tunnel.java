package com.lambdatest.tunnel;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import com.lambdatest.Utils;
import com.sun.org.apache.xpath.internal.operations.Bool;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import java.util.HashMap;
import com.lambdatest.httpserver.HttpServer;
import com.lambdatest.tunnel.TestDaemonThread1;
import com.lambdatest.KillPort;

/**
 * Creates and manages a secure tunnel connection to LambdaTest.
 */
public class Tunnel {

    private static final List<String> IGNORE_KEYS = Arrays.asList("user", "key", "infoAPIPort", "binarypath", "load-balanced", "mitm");

    private boolean tunnelFlag = false;

    private int infoAPIPortValue;

    private Map<String, String> parameters;

    private String TunnelID;

    private Integer tunnelCount = 0;

    TunnelBinary tunnelBinary = new TunnelBinary();

    private Process process = null;

    private ReentrantLock mutex = new ReentrantLock();

    TestDaemonThread1 t1=new TestDaemonThread1();//creating thread

    Utils utils = new Utils();


    public Tunnel() throws TunnelException {
        parameters = new HashMap<String, String>();
        parameters.put("bypassHosts", "--bypassHosts");
        parameters.put("callbackURL", "--callbackURL");
        parameters.put("config", "--config");
        parameters.put("controller", "--controller");
        parameters.put("egress-only ", "--egress-only ");
        parameters.put("dir", "--dir");
        parameters.put("dns", "--dns");
        parameters.put("emulateChrome", "--emulateChrome");
        parameters.put("env", "--env");
        parameters.put("help", "--help");
        parameters.put("infoAPIPort", "--infoAPIPort");
        parameters.put("ingress-only", "--ingress-only");
        parameters.put("key", "--key");
        parameters.put("localDomains", "--local-domains");
        parameters.put("logFile", "--logFile");
        parameters.put("mode", "--mode");
        parameters.put("noProxy", "--no-proxy");
        parameters.put("pidfile", "--pidfile");
        parameters.put("port", "--port");
        parameters.put("proxyHost", "--proxy-host");
        parameters.put("proxyPass", "--proxy-pass");
        parameters.put("proxyPort", "--proxy-port");
        parameters.put("proxyUser", "--proxy-user");
        parameters.put("sharedTunnel", "--shared-tunnel");
        parameters.put("sshConnType", "--sshConnType");
        parameters.put("tunnelName", "--tunnelName");
        parameters.put("user", "--user");
        parameters.put("load-balanced", "--load-balanced");
        parameters.put("v", "--v");
        parameters.put("version", "--version");
        parameters.put("basicAuth", "--basic-auth");
        parameters.put("mitm", "--mitm");
    }

    /**
     * Starts Tunnel instance with options
     *
     * @param options Options for the Tunnel instance
     * @throws Exception
     */
    public synchronized Boolean start(Map<String, String> options) {
        try {
            //Get path of downloaded tunnel in project directory
            mutex.lock();
            if (options.containsKey("infoAPIPort") && options.get("infoAPIPort").matches("^[0-9]+"))
                infoAPIPortValue = Integer.parseInt(options.get("infoAPIPort"));
            else
                infoAPIPortValue = findAvailablePort();

            t1.setDaemon(true);//now t1 is daemon thread
            t1.start();//starting threads

            utils.logger("infoAPIPortValue: " + infoAPIPortValue);
//            System.out.println("infoAPIPortValue: " + infoAPIPortValue);
            clearTheFile();
            verifyTunnelStarted(options, infoAPIPortValue);

            utils.logger("tunnel Verified");
//            System.out.println("tunnel Verified");
            if (options.get("load-balanced") != "" && options.get("load-balanced") != null) {
                if (options.get("tunnelName") == "" || options.get("tunnelName") == null) {
                    options.put("tunnelName", "Maven_Tunnel_LambdaTest_" + options.get("key"));
                }
            }

            try {
                for (int i = 0; i < 5; i++) {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println(e);
            }

            String command = passParametersToTunnel(options);
            runCommand(command);

            mutex.unlock();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void verifyTunnelStarted(Map<String, String> options, int infoAPIPortValue) throws TunnelException {
        if (options.get("user") == null || options.get("user") == "" || options.get("key") == null || options.get("key") == "") {
            tunnelFlag = false;
            throw new TunnelException("Username/AccessKey Cannot Be Empty");
        }
    }

    public synchronized void stop() throws Exception {
        //Return the control if the tunnel is not even started
        if (!tunnelFlag)
            return;
        try {
            mutex.lock();
            stopTunnel();
            process.waitFor();
            mutex.unlock();
        } catch (Exception e) {
            throw e;
        }
    }

    private static Integer findAvailablePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        s.close();
        return s.getLocalPort();
    }

    public static void clearTheFile() throws IOException {
        FileWriter fwOb = new FileWriter("tunnel.log", false);
        PrintWriter pwOb = new PrintWriter(fwOb, false);
        pwOb.flush();
        pwOb.close();
        fwOb.close();
    }

    public void stopTunnel() throws TunnelException {
        try {
            String deleteEndpoint = "http://127.0.0.1:" + String.valueOf(infoAPIPortValue) + "/api/v1.0/stop";
            CloseableHttpClient httpclient = HttpClients.createDefault();

            HttpDelete httpDelete = new HttpDelete(deleteEndpoint);
//            System.out.println("Executing request " + httpDelete.getRequestLine());

            HttpResponse response = httpclient.execute(httpDelete);
            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

            //Throw runtime exception if status code isn't 200
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            }
            KillPort killPort = new KillPort();
            killPort.killProcess(t1.port);
            utils.logger("Tunnel closed successfully && Port process killed");
//            System.out.println("Tunnel closed successfully && Port process killed");
        } catch (Exception e) {
            throw new TunnelException("Tunnel with ID: " + TunnelID + " has been closed!");
        }
    }

    // Give parameters to the tunnel for starting it in runCommand.
    public String passParametersToTunnel(Map<String, String> options) {
        String command = "";

        if(options.get("binary") != null) {
            command += options.get("binary");
        } else {
            String binaryPath = tunnelBinary.getBinaryPath();
            command += binaryPath;
        }

        command += " --user ";
        if (options.get("user") != null)
            command += options.get("user");

        command += " --key ";
        if (options.get("key") != null)
            command += options.get("key");

        command += " --infoAPIPort ";
        command += String.valueOf(infoAPIPortValue);

        utils.logger("options load " + options.get("load-balanced"));
//        System.out.println("options load " + options.get("load-balanced"));
        if (options.get("load-balanced") != "" && options.get("load-balanced") != null) {
            command += " --load-balanced ";
        }

        if(options.get("basicAuth") != "" && options.get("basicAuth") != null ) {
            command += " --basic-auth ";
            command += options.get("basicAuth");
        }

        if(options.get("mitm") != "" && options.get("mitm") !=null ) {
            command += " --mitm ";
        }

        if(t1.port!=null) {
            command += " --callbackURL http://127.0.0.1:" + String.valueOf(t1.port);
        }

        for (Map.Entry<String, String> opt : options.entrySet()) {
            String parameter = opt.getKey().trim();
            if (IGNORE_KEYS.contains(parameter)) {
                continue;
            }

            if (parameters.get(parameter) != null) {
                command += " " + parameters.get(parameter) + " ";
                if (opt.getValue() != null) {
                    command += opt.getValue().trim();
                }
            }
        }
        return command;
    }

    public void runCommand(String command) throws IOException {
        try {
//          ProcessBuilder processBuilder = new ProcessBuilder(command);
            utils.logger("Command String: " + command);
//            System.out.println("Command String: " + command);
            Runtime run = Runtime.getRuntime();
            process = run.exec(command);
            Boolean update = false;
            long start = System.currentTimeMillis();
            long end = start;

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            try {
                while ((line = reader.readLine()) != null) {
//                    System.out.println(line);
//                    if (line.contains("Err: Unable to authenticate user")) {
//                        throw new TunnelException("Invalid Username/AccessKey");
//                    } else if (line.contains("Tunnel ID")) {
//                        tunnelFlag = true;
//                        String[] arrOfStr = line.split(":", 2);
//                        if (arrOfStr.length == 2) {
//                            TunnelID = arrOfStr[1].trim();
//                        }
//                        System.out.println("Tunnel Started Successfully");
//                        break;
//                    } else if (line.contains("Downloading update")) {
//                        update = true;
//                    } else if (((end - start) / 1000F) > 30) {
//                        process.destroy();
//                        System.out.println("Unable to start the tunnel. timeout exceeds");
//                        break;
//                    }
//                    end = System.currentTimeMillis();
//                }
//                if (update) {
//                    System.out.println("Tunnel is updated. restarting...");
//                    runCommand(command);
//                }
//              } catch (IOException e) {
//                e.printStackTrace();
//              }
                    if(line.contains("Downloading update")) {
                        update = true;
                    } else {
                        if(update) {
                            utils.logger("Tunnel is updated. restarting...");
//                            System.out.println("Tunnel is updated. restarting...");
                            runCommand(command);
                        }
                        try {
                            if(t1.port != null) {
                                BufferedReader br = new BufferedReader(new FileReader(String.valueOf(t1.port) + ".txt"));
                                if(br.readLine()!=null) {
                                    tunnelFlag=true;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            System.out.println("Not found any file");
                        }
                    }

                    utils.logger(line);
//                    System.out.println(line);

                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }
}