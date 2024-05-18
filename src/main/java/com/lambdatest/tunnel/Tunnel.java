package com.lambdatest.tunnel;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.net.ServerSocket;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;

import com.lambdatest.KillPort;

/**
 * Creates and manages a secure tunnel connection to LambdaTest.
 */
public class Tunnel {

    private static final List<String> IGNORE_KEYS = Arrays.asList("user", "key", "infoAPIPort", "binarypath",
            "load-balanced", "mitm", "pacfile", "mTLSHosts", "clientKey",
            "clientCert", "allowHosts", "verbose", "serverDomain", "usePrivateIp", "retry-proxy-error",
            "retry-proxy-error-count", "ntlm", "ntlmPassword", "ntlmUsername", "maxSSHConnections");

    private boolean tunnelFlag = false;
    private final int MAX_TUNNEL_STARTUP_RETRY_COUNT = 20;
    private final int TUNNEL_INITIAL_WAIT_AND_RETRY_BACKOFF = 3000;
    private final int HTTP_TIMEOUT = 5000;
    private int infoAPIPortValue;
    private int tunnelID;

    private Map<String, String> parameters;

    private String userName;
    private String accessKey;

    private Integer tunnelCount = 0;

    TunnelBinary tunnelBinary;

    private Process process = null;

    private ReentrantLock mutex = new ReentrantLock();

    TestDaemonThread1 t1 = new TestDaemonThread1();// creating thread

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
        parameters.put("skip-upgrade", "--skip-upgrade");
        parameters.put("pacfile", "--pacfile");
        parameters.put("mTLSHosts", "--mTLSHosts");
        parameters.put("clientKey", "--clientKey");
        parameters.put("clientCert", "--clientCert");
        parameters.put("allowHosts", "--allowHosts");
        parameters.put("verbose", "--verbose");
        parameters.put("serverDomain", "--server-domain");
        parameters.put("usePrivateIp", "--use-private-ip");
        parameters.put("retry-proxy-error", "--retry-proxy-error");
        parameters.put("retry-proxy-error-count", "--retry-proxy-error-count");
        parameters.put("ntlm", "--ntlm");
        parameters.put("ntlmUsername", "--ntlm-username");
        parameters.put("ntlmPassword", "--ntlm-password");
        parameters.put("maxSSHConnections", "--maxSSHConnections");
    }

    /**
     * Starts Tunnel instance with options
     *
     * @param options Options for the Tunnel instance
     */
    public synchronized Boolean start(Map<String, String> options) {

        try {

            tunnelBinary = new TunnelBinary(options.get("binary"));
            // Get path of downloaded tunnel in project directory
            mutex.lock();
            if (options.containsKey("infoAPIPort") && options.get("infoAPIPort").matches("^[0-9]+"))
                infoAPIPortValue = Integer.parseInt(options.get("infoAPIPort"));
            else
                infoAPIPortValue = findAvailablePort();

            t1.setDaemon(true);// now t1 is daemon thread
            t1.start();// starting threads
            clearTheFile();
            verifyTunnelCredentials(options);

            System.out.println("tunnel Verified");
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

            this.userName = options.get("user");
            this.accessKey = options.get("key");

            // Check if tunnel binary path contains spaces
            String tunnelBinaryPath = "";
            if (options.get("binary") != null) {
                tunnelBinaryPath += options.get("binary");
            } else {
                String path = tunnelBinary.getBinaryPath();
                tunnelBinaryPath += path;
            }
            boolean isWhiteSpaceInBinaryPath = tunnelBinaryPath.contains(" ");
            if (isWhiteSpaceInBinaryPath) {
                String[] command = passParametersToTunnelV2(options);
                runCommandV2(command);

            } else {

                String command = passParametersToTunnel(options);
                runCommand(command);

            }

            mutex.unlock();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void verifyTunnelCredentials(Map<String, String> options) throws TunnelException {
        if (options.get("user") == null || options.get("user") == "" || options.get("key") == null
                || options.get("key") == "") {
            tunnelFlag = false;
            throw new TunnelException("Username/AccessKey Cannot Be Empty");
        }
    }

    public synchronized void stop() throws Exception {
        // Return the control if the tunnel is not even started
        if (!tunnelFlag)
            return;
        try {
            mutex.lock();
            stopTunnel();

            boolean hasTerminated = process.waitFor(30, TimeUnit.SECONDS);
            if (!hasTerminated) {
                System.err.println("The tunnel process did not terminate within timeout");
                process.destroyForcibly(); // Forcefully kill the process if it doesn't stop
            } else {
                System.out.println("Tunnel process terminated successfully.");
            }
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
            boolean stopTunnelAPI = Boolean.parseBoolean(System.getenv("STOP_TUNNEL_API"));

            String deleteEndpoint = stopTunnelAPI ?
                    "https://ts.lambdatest.com/v1.0/stop/" + tunnelID :
                    "http://127.0.0.1:" + String.valueOf(infoAPIPortValue) + "/api/v1.0/stop";

            HttpDelete httpDelete = new HttpDelete(deleteEndpoint);

            if (!stopTunnelAPI) {

                CloseableHttpClient httpclient = HttpClients.createDefault();
                HttpResponse response = httpclient.execute(httpDelete);
                BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

                // Throw runtime exception if status code isn't 200
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                }

            } else {

                String auth = userName + ":" + accessKey;
                String encodedAuth = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

                    httpDelete.setHeader("Authorization", encodedAuth);

                    int attempt = 0;
                    CloseableHttpResponse response = null;
                    while (attempt < 3) {
                        response = httpClient.execute(httpDelete);
                        if (response.getStatusLine().getStatusCode() == 200) {
                            System.out.println("Tunnel stopped successfully");
                            break;
                        } else {
                            attempt++;
                            if (response != null) response.close();

                            if (attempt == 3) {
                                System.out.println("Max retries reached, could not stop tunnel");
                                throw new TunnelException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
                            } else {
                                Thread.sleep(3000);
                            }
                        }
                    }
                }
            }
            String filePath = ".lambdatest/tunnelprocs/" + t1.port + ".txt";
            Path pathOfFile = Paths.get(filePath);

            if (!Files.exists(pathOfFile)) {
                System.out.println("File does not exist, cannot stop tunnel");
                return; // Exit the method if the file does not exist
            }
            boolean result = Files.deleteIfExists(pathOfFile);
            if (result)
                System.out.println("File is deleted");
            else
                System.out.println("File does not exists");
            KillPort killPort = new KillPort();
            killPort.killProcess(t1.port);
            System.out.println("Tunnel closed successfully && Port process killed");
        } catch (Exception e) {
            throw new TunnelException("Tunnel with ID: " + tunnelID + " has been closed!");
        }
    }

    // Give parameters to the tunnel for starting it in runCommand.
    public String passParametersToTunnel(Map<String, String> options) {
        String command = "";

        if (options.get("binary") != null) {
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

        if (options.get("load-balanced") != "" && options.get("load-balanced") != null) {
            command += " --load-balanced ";
        }
        if (options.get("skip-upgrade") != "" && options.get("skip-upgrade") != null) {
            command += " --skip-upgrade ";
        }

        if (options.get("basicAuth") != "" && options.get("basicAuth") != null) {
            command += " --basic-auth ";
            command += options.get("basicAuth");
        }

        if (options.get("mitm") != "" && options.get("mitm") != null) {
            command += " --mitm ";
        }

        if (options.get("pacfile") != "" && options.get("pacfile") != null) {
            command += " --pacfile ";
            command += options.get("pacfile");
        }

        if (options.get("mTLSHosts") != "" && options.get("mTLSHosts") != null) {
            command += " --mTLSHosts ";
            command += options.get("mTLSHosts");
        }

        if (options.get("clientKey") != "" && options.get("clientKey") != null) {
            command += " --clientKey ";
            command += options.get("clientKey");
        }

        if (options.get("clientCert") != "" && options.get("clientCert") != null) {
            command += " --clientCert ";
            command += options.get("clientCert");
        }

        if (options.get("allowHosts") != "" && options.get("allowHosts") != null) {
            command += " --allowHosts ";
            command += options.get("allowHosts");
        }

        if (options.get("serverDomain") != "" && options.get("serverDomain") != null) {
            command += " --server-domain ";
            command += options.get("serverDomain");
        }

        if (options.get("verbose") != "" && options.get("verbose") != null) {
            command += " --verbose ";
        }

        if (options.get("usePrivateIp") != "" && options.get("usePrivateIp") != null) {
            command += " --use-private-ip ";
        }

        if (options.get("retry-proxy-error") != "" && options.get("retry-proxy-error") != null) {
            command += " --retry-proxy-error ";
        }

        if (options.get("retry-proxy-error-count") != "" && options.get("retry-proxy-error-count") != null) {
            command += " --retry-proxy-error-count ";
            command += options.get("retry-proxy-error-count");
        }

        if (options.get("ntlm") != "" && options.get("ntlm") != null) {
            command += " --ntlm ";
        }

        if (options.get("ntlmUsername") != "" && options.get("ntlmUsername") != null) {
            command += " --ntlm-username ";
            command += options.get("ntlmUsername");
        }

        if (options.get("ntlmPassword") != "" && options.get("ntlmPassword") != null) {
            command += " --ntlm-password ";
            command += options.get("ntlmPassword");
        }

        if (options.get("maxSSHConnections") != "" && options.get("maxSSHConnections") != null) {
            command += " --maxSSHConnections ";
            command += options.get("maxSSHConnections");
        }

        if (t1.port != null) {
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

    // Give parameters to the tunnel for starting it in runCommand.
    public String[] passParametersToTunnelV2(Map<String, String> options) {

        ArrayList<String> commandArray = new ArrayList<String>();

        if (options.get("binary") != null) {
            commandArray.add(options.get("binary"));
        } else {
            commandArray.add(tunnelBinary.getBinaryPath());
        }
        commandArray.add("--user");
        if (options.get("user") != null)
            commandArray.add(options.get("user"));

        commandArray.add("--key");
        if (options.get("key") != null)
            commandArray.add(options.get("key"));

        commandArray.add("--infoAPIPort");
        commandArray.add(String.valueOf(infoAPIPortValue));

        if (options.get("load-balanced") != "" && options.get("load-balanced") != null) {
            commandArray.add("--load-balanced");
        }
        if (options.get("skip-upgrade") != "" && options.get("skip-upgrade") != null) {
            commandArray.add("--skip-upgrade");
        }

        if (options.get("basicAuth") != "" && options.get("basicAuth") != null) {
            commandArray.add("--basic-auth");
            commandArray.add(options.get("basicAuth"));
        }

        if (options.get("mitm") != "" && options.get("mitm") != null) {
            commandArray.add("--mitm");
        }

        if (options.get("pacfile") != "" && options.get("pacfile") != null) {
            commandArray.add("--pacfile");
            commandArray.add(options.get("pacfile"));
        }

        if (options.get("mTLSHosts") != "" && options.get("mTLSHosts") != null) {
            commandArray.add("--mTLSHosts");
            commandArray.add(options.get("mTLSHosts"));
        }

        if (options.get("clientKey") != "" && options.get("clientKey") != null) {
            commandArray.add("--clientKey");
            commandArray.add(options.get("clientKey"));
        }

        if (options.get("clientCert") != "" && options.get("clientCert") != null) {
            commandArray.add("--clientCert");
            commandArray.add(options.get("clientCert"));
        }

        if (options.get("allowHosts") != "" && options.get("allowHosts") != null) {
            commandArray.add("--allowHosts");
            commandArray.add(options.get("allowHosts"));
        }

        if (options.get("serverDomain") != "" && options.get("serverDomain") != null) {
            commandArray.add("--server-domain");
            commandArray.add(options.get("serverDomain"));
        }

        if (options.get("verbose") != "" && options.get("verbose") != null) {
            commandArray.add("--verbose");
        }

        if (options.get("usePrivateIp") != "" && options.get("usePrivateIp") != null) {
            commandArray.add("--use-private-ip");
        }

        if (options.get("retry-proxy-error") != "" && options.get("retry-proxy-error") != null) {
            commandArray.add("--retry-proxy-error");
        }

        if (options.get("retry-proxy-error-count") != "" && options.get("retry-proxy-error-count") != null) {
            commandArray.add("--retry-proxy-error-count");
            commandArray.add(options.get("retry-proxy-error-count"));
        }

        if (options.get("ntlm") != "" && options.get("ntlm") != null) {
            commandArray.add("--ntlm");
        }

        if (options.get("ntlmUsername") != "" && options.get("ntlmUsername") != null) {
            commandArray.add("--ntlm-username");
            commandArray.add(options.get("ntlmUsername"));
        }

        if (options.get("ntlmPassword") != "" && options.get("ntlmPassword") != null) {
            commandArray.add("--ntlm-password");
            commandArray.add(options.get("ntlmPassword"));
        }

        if (options.get("maxSSHConnections") != "" && options.get("maxSSHConnections") != null) {
            commandArray.add("--maxSSHConnections");
            commandArray.add(options.get("maxSSHConnections"));
        }

        if (t1.port != null) {
            commandArray.add("--callbackURL");
            commandArray.add("http://127.0.0.1:" + String.valueOf(t1.port));
        }

        for (Map.Entry<String, String> opt : options.entrySet()) {
            String parameter = opt.getKey().trim();
            if (IGNORE_KEYS.contains(parameter)) {
                continue;
            }

            if (parameters.get(parameter) != null) {
                commandArray.add(parameters.get(parameter));
                if (opt.getValue() != null) {
                    commandArray.add(opt.getValue().trim());
                }
            }
        }
        String[] commandV2 = commandArray.toArray(new String[commandArray.size()]);
        return commandV2;
    }

    public void runCommand(String command) throws IOException {
        try {
            Runtime run = Runtime.getRuntime();
            process = run.exec(command);
            Boolean update = false;
            long start = System.currentTimeMillis();
            long end = start;
            waitForTunnelToStart();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return;
    }

    public void runCommandV2(String[] command) throws IOException {
        try {
            Runtime run = Runtime.getRuntime();
            process = run.exec(command);
            Boolean update = false;
            waitForTunnelToStart();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void waitForTunnelToStart() throws IOException {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(HTTP_TIMEOUT)
                .setSocketTimeout(HTTP_TIMEOUT)
                .setConnectionRequestTimeout(HTTP_TIMEOUT)
                .build();
        CloseableHttpClient httpClient = HttpClientBuilder.create().setDefaultRequestConfig(config).build();

        for (int retryCount = 0; retryCount < MAX_TUNNEL_STARTUP_RETRY_COUNT; retryCount++) {
            try {
                Thread.sleep(TUNNEL_INITIAL_WAIT_AND_RETRY_BACKOFF);
                System.out.println("Checking Tunnel Status");
                String infoAPIGetEndpoint = "http://127.0.0.1:" + Integer.toString(infoAPIPortValue) + "/api/v1.0/info";
                HttpGet httpGet = new HttpGet(infoAPIGetEndpoint);
                CloseableHttpResponse execute = httpClient.execute(httpGet);
                if (execute.getStatusLine().getStatusCode() == 200) {
                    System.out.println("Received Tunnel Status Response");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(execute.getEntity().getContent()));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    JSONObject tunnelInfo = new JSONObject(response.toString());
                    if (tunnelInfo.getString("status").equals("SUCCESS")) {
                        tunnelFlag = true;
                        System.out.println("Tunnel Started Successfully");
                        if (tunnelInfo.getJSONObject("data") != null && tunnelInfo.getJSONObject("data").has("id")) {
                            tunnelID = tunnelInfo.getJSONObject("data").getInt("id");
                        }
                        System.out.println("Tunnel ID: " + tunnelID);
                        break;
                    } else {
                        System.out.println("Tunnel Status: " + tunnelInfo.getString("status"));
                    }
                } else {
                    System.out.println("Tunnel Status Response: " + execute.getStatusLine().getStatusCode());
                }
            } catch (Exception e) {
                System.out.println("Tunnel not yet started. Retrying");
            }
        }
    }
}
