package com.lambdatest.tunnel;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.regex.Pattern;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.exception.ZipException;

class TunnelBinary {

    private static final String BIN_URL = "https://downloads.lambdatest.com/tunnel/";

    private String httpPath;

    private String binaryPath;
    private String store;
    private String binFileName;
    private String downloadFileName;
    String destParentDir;

    private boolean isOSWindows;

    private final String orderedPaths[] = {
            System.getProperty("user.dir") + "/.lambdatest",
            System.getProperty("user.home") + "/.lambdatest",
            System.getProperty("java.io.tmpdir")
    };

    TunnelBinary() throws TunnelException {
        initialize();
        getBinary();
        checkBinary();
    }

    private static void downloadZipFile(String urlStr, String file) throws IOException {
        try {
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection();
            InputStream in = conn.getInputStream();
            FileOutputStream out = new FileOutputStream(file);
            byte[] b = new byte[1024];
            int count;
            while ((count = in.read(b)) >= 0) {
                out.write(b, 0, count);
            }
            out.close();
            out.flush(); out.close(); in.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initialize() throws TunnelException {
        String osname = System.getProperty("os.name").toLowerCase();
        isOSWindows = osname.contains("windows");

        if (isOSWindows) {
            String arch = System.getProperty("os.arch");
            binFileName = "windows/" + (arch.contains("64") ? "64bit/LT_Windows.zip" : "32bit/LT_Windows.zip");
            downloadFileName = (arch.contains("64") ? "/LT_Windows.zip" : "/LT_Windows.zip");
        } else if (osname.contains("mac") || osname.contains("darwin")) {
            String arch = System.getProperty("os.arch");
            binFileName = "mac/" + (arch.contains("64") ? "64bit/LT_Mac.zip" : "32bit/LT_Mac.zip");
            downloadFileName = (arch.contains("64") ? "/LT_Mac.zip" : "/LT_Mac.zip");
        } else if (osname.contains("linux")) {
            String arch = System.getProperty("os.arch");
            binFileName = "linux/" + (arch.contains("64") ? "64bit/LT_Linux.zip" : "32bit/LT_Linux.zip");
            downloadFileName = (arch.contains("64") ? "/LT_Linux.zip" : "/LT_Linux.zip");

        } else {
            throw new TunnelException("Failed to detect OS type");
        }

        httpPath = BIN_URL + binFileName;
    }

    private void checkBinary() throws TunnelException{
        boolean binaryWorking = validateBinary();

        //if binary not working, delete it and get new binary
        if(!binaryWorking){
            File binary_file = new File(binaryPath);
            if (binary_file.exists()) {
                binary_file.delete();
            }
            getBinary();
            if(!validateBinary()){
                throw new TunnelException("LambdaTestTunnel binary is corrupt");
            }
        }
    }

    private boolean validateBinary() throws TunnelException{
        Process process;
        String url = httpPath;
        try {
            File tunnelZip = new File(store+downloadFileName);
            if(!tunnelZip.exists())
                downloadZipFile(url, store+downloadFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }        
        System.out.println("Extracting the Zipped Tunnel");
        unzip(destParentDir+downloadFileName, destParentDir);
        try {
            changePermissions(binaryPath);
            ProcessBuilder pb = new ProcessBuilder(binaryPath,"--version");
            process = pb.start();
            BufferedReader stdoutbr = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String stdout="",line="";

            while ((line = stdoutbr.readLine()) != null) {
                stdout += line;
            }
            process.waitFor();
            return true;

        }catch(IOException ex){
            throw new TunnelException(ex.toString());
        }
        catch(InterruptedException ex){
            throw new TunnelException(ex.toString());
        }
    }

    public static void unzip(String source, String destination){
        try {
            ZipFile zipFile = new ZipFile(source);
            zipFile.extractAll(destination);
        } catch (ZipException e) {
            e.printStackTrace();
        }
    }

    private void getBinary() throws TunnelException {
        destParentDir = getAvailableDirectory();
        store = destParentDir;
        binaryPath = destParentDir + "/LT";
        if (isOSWindows) {
            binaryPath += ".exe";
        }

        if (!new File(binaryPath).exists()) {
            System.out.println("Downloading Fresh Tunnel Package");
            downloadBinary(destParentDir);
        }
        else {
            System.out.println("Found Existing Tunnel Package");
        }
    }

    private String getAvailableDirectory() throws TunnelException {
        int i = 0;
        while (i < orderedPaths.length) {
            String path = orderedPaths[i];
            if (makePath(path))
                return path;
            else
                i++;
        }

        throw new TunnelException("No Available Directory To Store Tunnel Package");
    }

    private boolean makePath(String path) {
        try {
            if (!new File(path).exists())
                new File(path).mkdirs();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void downloadBinary(String destParentDir) throws TunnelException {
        try {
            if (!new File(destParentDir).exists())
                new File(destParentDir).mkdirs();

            URL url = new URL(httpPath);
            String source = destParentDir + "/LT";
            if (isOSWindows) {
                source += ".exe";
            }

            File f = new File(source);
            FileUtils.copyURLToFile(url, f);

            changePermissions(binaryPath);
        } catch (Exception e) {
            throw new TunnelException("Error While Downloading Tunnel Package");
        }
    }

    //allow executable permissions to tunnel binary, if in read only mode
    public void changePermissions(String path) {
        File f = new File(path);
        f.setExecutable(true, true);
        f.setReadable(true, true);
        f.setWritable(true, true);
    }

    public String getBinaryPath() {
        return binaryPath;
    }
}
