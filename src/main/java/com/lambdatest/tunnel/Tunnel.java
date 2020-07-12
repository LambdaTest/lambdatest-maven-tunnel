package com.lambdatest.tunnel;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Creates and manages a secure tunnel connection to LambdaTest.
 */
public class Tunnel {

    private static final List<String> IGNORE_KEYS = Arrays.asList("user", "key", "infoAPIPort", "binarypath");

    List<String> command;
    private Map<String, String> startOptions;
    private String binaryPath;
    private int stackCount=0;
    private boolean isOSWindows, tunnelFlag=true, controlFlag=false;
    private static int infoAPIPortValue=0;

    static Queue<String> Q = new LinkedList<String>();

    private TunnelProcess proc = null;

    private final Map<String, String> parameters;

    public Tunnel() {

        parameters = new HashMap<String, String>();
        parameters.put("config", "-config");
        parameters.put("controller", "-controller");
        parameters.put("cui", "-cui");
        parameters.put("customSSHHost", "-customSSHHost");
        parameters.put("customSSHPort", "-customSSHPort");
        parameters.put("customSSHPrivateKey", "-customSSHPrivateKey");
        parameters.put("customSSHUser", "-customSSHUser");
        parameters.put("dir", "-dir");
        parameters.put("dns", "-dns");
        parameters.put("emulateChrome", "-emulateChrome");
        parameters.put("env", "-env");
        parameters.put("infoAPIPort", "-infoAPIPort");
        parameters.put("key", "-key");
        parameters.put("localDomains", "-local-domains");
        parameters.put("logFile", "-logFile");
        parameters.put("mode", "-mode");
        parameters.put("nows", "-nows");
        parameters.put("outputConfig", "-outputConfig");
        parameters.put("pac", "-pac");
        parameters.put("pidfile", "-pidfile");
        parameters.put("port", "-port");
        parameters.put("proxyHost", "-proxy-host");
        parameters.put("proxyPass", "-proxy-pass");
        parameters.put("proxyPort", "-proxy-port");
        parameters.put("proxyUser", "-proxy-user");
        parameters.put("remoteDebug", "-remote-debug");
        parameters.put("server", "-server");
        parameters.put("sharedTunnel", "-shared-tunnel");
        parameters.put("tunnelName", "-tunnelName");
        parameters.put("user", "-user");
        parameters.put("v", "-v");
        parameters.put("version", "-version");

    }

    /**
     * Starts Tunnel instance with options
     *
     * @param options Options for the Tunnel instance
     * @throws Exception
     */
    public void start(Map<String, String> options) throws Exception {
        startOptions = options;
        //Get path of downloaded tunnel in project directory
        TunnelBinary tunnelBinary = new TunnelBinary();
        binaryPath = tunnelBinary.getBinaryPath();
        if(options.containsKey("infoAPIPort") && options.get("infoAPIPort").matches("^[0-9]+"))
            infoAPIPortValue = Integer.parseInt(options.get("infoAPIPort"));
        else
            infoAPIPortValue = findAvailablePort();

        clearTheFile();
        passParametersToTunnel(startOptions, "start");

        proc = runCommand(command);
        BufferedReader stdoutbr = new BufferedReader(new InputStreamReader(proc.getInputStream()));
        BufferedReader stderrbr = new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        //allow time to generate logs
        Thread.sleep(5000);
        //verify the tunnel 
        verifyTunnelStarted(options);
        
        //capture infoAPIPort of running tunnel and store in queue
        Q.add(String.valueOf(infoAPIPortValue));
    }
    public void verifyTunnelStarted(Map<String, String> options) {
    	try {
    		if(options.get("user")==null ||  options.get("user")=="" || options.get("key")==null || options.get("key")=="") {
            	controlFlag = true;
                throw new TunnelException("Username/AccessKey Cannot Be Empty");
            }
    		
            List<String> lines = Files.readAllLines(Paths.get("tunnel.log"));
            for (String line : lines) {
                if (line.contains("Err: Unable to authenticate user")) {
                    tunnelFlag = false;
                    controlFlag = true;
                    throw new TunnelException("Invalid Username/AccessKey");
                }
                else if(line.contains("Tunnel ID")) {
                    System.out.println("Tunnel Started Successfully");
                    controlFlag = true;
                    break;
                }
            }
            if(!controlFlag)
            	throw new TunnelException("Unable to Start Tunnel");
        } catch (IOException | TunnelException e) {
            e.printStackTrace();
        }
    }

    public void stop() throws Exception {
        //Return the control if the tunnel is not even started
        if(!tunnelFlag)
            return;
        stackCount = Q.size();

        while(stackCount!=0) {
            stopTunnel();
            stackCount = stackCount-1;
        }
        stackCount=0;
        passParametersToTunnel(startOptions, "stop");
        proc.waitFor();
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
    public static void stopTunnel() throws TunnelException {
        try {
        	URL urlForDeleteRequest = new URL("http://127.0.0.1:"+Q.poll()+"/api/v1.0/stop");
        	HttpURLConnection connection=(HttpURLConnection) urlForDeleteRequest.openConnection();
        	connection.setRequestMethod("DELETE");
        	connection.connect();
        	InputStream inputStream = connection.getInputStream();
        	if(connection.getResponseCode()==200) {
        		System.out.println("Tunnel Closed Successfully");
        	}
        }
        catch (IOException e) {
    	    throw new TunnelException("Unable to Close Tunnel");
        }
    }

    // Give parameters to the tunnel for starting it in runCommand.
    private void passParametersToTunnel(Map<String, String> options, String opCode) throws IOException, TunnelException {
        command = new ArrayList<String>();
        command.add(binaryPath);
        
        command.add("-user");
        if(options.get("user") != null)
            command.add(options.get("user"));
        
        command.add("-key");
        if(options.get("key") != null)
            command.add(options.get("key"));
        
        command.add("-infoAPIPort");
	command.add(String.valueOf(infoAPIPortValue));

        for (Map.Entry<String, String> opt : options.entrySet()) {
            String parameter = opt.getKey().trim();
            if (IGNORE_KEYS.contains(parameter)) {
                continue;
            }

                if (parameters.get(parameter) != null) {
                    command.add(parameters.get(parameter));
                } else {
                    command.add("-" + parameter);
                }
                if (opt.getValue() != null) {
                    command.add(opt.getValue().trim());
                }
            }
        }


    protected TunnelProcess runCommand(List<String> command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        final Process process = processBuilder.start();
        //Check if Tunnel Cache is successfully created
        String ltcbin = System.getProperty("user.dir");

        String osname = System.getProperty("os.name").toLowerCase();
        isOSWindows = osname.contains("windows");
        if (isOSWindows) {
            ltcbin += "/ltcbin.exe";
        }
        else
            ltcbin += "/.ltcbin";

        File fileExist = new File(ltcbin);
        if(fileExist.exists())
        {
            System.out.println("Found Cached Tunnel Component");
            Thread.sleep(3000);
        }
        else {
            System.out.println("Creating Cached Tunnel Component");
        }
        while(!fileExist.renameTo(fileExist)) {
            // To wait until ltcbin cache is fully created.
            Thread.sleep(3000);
        }
        process.destroy();

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
