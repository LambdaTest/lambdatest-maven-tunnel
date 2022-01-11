package com.lambdatest.tunnel;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
/**
 * Creates and manages a secure tunnel connection to LambdaTest.
 */
public class Tunnel {

    private static final List<String> IGNORE_KEYS = Arrays.asList("user", "key", "infoAPIPort", "binarypath");

    List<String> command;
    private Map<String, String> startOptions;
    private String binaryPath;
    private int stackCount=0;
    private boolean tunnelFlag=true;
    private static FileWriter fwOb;

    static Queue<String> Q = new LinkedList<String>();

    private TunnelProcess proc = null;
    private int infoAPIPortValue;

    private  Map<String, String> parameters;

    public Tunnel() {
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
        parameters.put("mitm", "--mitm");
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
        parameters.put("v", "--v");
        parameters.put("version", "--version");
    }

    /**
     * Starts Tunnel instance with options
     *
     * @param options Options for the Tunnel instance
     * @throws Exception
     */
    public void start(Map<String, String> options) throws Exception {
        try {
            startOptions = options;
            //Get path of downloaded tunnel in project directory
            TunnelBinary tunnelBinary = new TunnelBinary();
            binaryPath = tunnelBinary.getBinaryPath();
            if (options.containsKey("infoAPIPort") && options.get("infoAPIPort").matches("^[0-9]+"))
                infoAPIPortValue = Integer.parseInt(options.get("infoAPIPort"));
            else
                infoAPIPortValue = findAvailablePort();

            System.out.println("infoAPIPortValue;"+infoAPIPortValue);
            clearTheFile();
            verifyTunnelStarted(startOptions);
            passParametersToTunnel(startOptions, infoAPIPortValue);

            proc = runCommand(command);
//            Q.add(String.valueOf(infoAPIPortValue));
        }catch (Exception e){
            throw new TunnelException("Unable to start tunnel");
        }
    }
    public void verifyTunnelStarted(Map<String, String> options) throws TunnelException {
        if(options.get("user")==null ||  options.get("user")=="" || options.get("key")==null || options.get("key")=="") {
            tunnelFlag = false;
            throw new TunnelException("Username/AccessKey Cannot Be Empty");
        }
    }

    public void stop() throws Exception {
        //Return the control if the tunnel is not even started
        if(!tunnelFlag)
            return;
        stopTunnel();
        proc.waitFor();
    }
    private static Integer findAvailablePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        s.close();
        return s.getLocalPort();
    }
    public static void clearTheFile() throws IOException {
        fwOb = new FileWriter("tunnel.log", false);
        PrintWriter pwOb = new PrintWriter(fwOb, false);
        pwOb.flush();
        pwOb.close();
        fwOb.close();
    }
    public void stopTunnel() throws TunnelException {
        try {
            String deleteEndpoint = "http://127.0.0.1:"+String.valueOf(infoAPIPortValue)+"/api/v1.0/stop";
            CloseableHttpClient httpclient = HttpClients.createDefault();

            HttpDelete httpDelete = new HttpDelete(deleteEndpoint);
//            System.out.println("Executing request " + httpDelete.getRequestLine());

            HttpResponse response = httpclient.execute(httpDelete);
            BufferedReader br = new BufferedReader(new InputStreamReader((response.getEntity().getContent())));

            //Throw runtime exception if status code isn't 200
            if (response.getStatusLine().getStatusCode() != 200) {
                throw new RuntimeException("Failed : HTTP error code : " + response.getStatusLine().getStatusCode());
            }
            System.out.println("Tunnel closed successfully");
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new TunnelException("Unable to Close Tunnel");
        }
    }

    // Give parameters to the tunnel for starting it in runCommand.
    private void passParametersToTunnel(Map<String, String> options, int infoAPIPortValue){
        command = new ArrayList<String>();
        command.add(binaryPath);

        command.add("--user");
        if(options.get("user") != null)
            command.add(options.get("user"));

        command.add("--key");
        if(options.get("key") != null)
            command.add(options.get("key"));

        command.add("--infoAPIPort");
        command.add(String.valueOf(infoAPIPortValue));

        for (Map.Entry<String, String> opt : options.entrySet()) {
            String parameter = opt.getKey().trim();
            if (IGNORE_KEYS.contains(parameter)) {
                continue;
            }

            if (parameters.get(parameter) != null) {
                command.add(parameters.get(parameter));
                if (opt.getValue() != null) {
                    command.add(opt.getValue().trim());
                }
            }
        }
    }
    //Creating Cached Tunnel Component
    protected TunnelProcess runCommand(List<String> command) throws IOException{
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Process process = processBuilder.start();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = null;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                if (line.contains("Err: Unable to authenticate user")) {
                    tunnelFlag = false;
                    throw new TunnelException("Invalid Username/AccessKey");
                }
                else if(line.contains("Tunnel ID")) {
                    System.out.println("Tunnel Started Successfully");
                    break;
                }
            }
        } catch (IOException | TunnelException ex) {
            System.out.println(ex.getMessage());
        }

        return new TunnelProcess() {
            public InputStream getInputStream() {
                return process.getInputStream();
            }
            public InputStream getErrorStream() {
                return process.getErrorStream();
            }
            public int waitFor() throws Exception {
                return process.waitFor();
            }
        };
    }

    public interface TunnelProcess {
        InputStream getInputStream();

        InputStream getErrorStream();

        int waitFor() throws Exception;

    }
}
