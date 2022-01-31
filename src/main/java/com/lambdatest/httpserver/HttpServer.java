package com.lambdatest.httpserver;
import java.net.*;
import java.io.*;

/**
 * Runs a HTTP server
 */
public class HttpServer{

    public static Integer port = null;

    /**
     * finds available port
     */
    public static Integer findAvailablePort() throws IOException {
        ServerSocket s = new ServerSocket(0);
        s.close();
        return s.getLocalPort();
    }

    /**
     * Runs main command
     */
    public static void main(int[] args) throws IOException {
        port = args[0];
        final ServerSocket server = new ServerSocket(port);

        System.out.println("Listening for connection on port " + port  +  "  ....");
        int length = 0;
        StringBuilder body = new StringBuilder();
        while (true){
            // spin forever
            Socket clientSocket = server.accept();
            InputStreamReader isr = new InputStreamReader(clientSocket.getInputStream());
            BufferedReader reader = new BufferedReader(isr);
            String line = reader.readLine();

            while (!line.isEmpty()) {
                if (line.startsWith("Content-Length: ")) { // get the
                    // content-length
                    int index = line.indexOf(':') + 1;
                    String len = line.substring(index).trim();
                    length = Integer.parseInt(len);
                }
                System.out.println(line);
                line = reader.readLine();
            }
            if (length > 0) {
                int read;
                while ((read = reader.read()) != -1) {
                    body.append((char) read);
                    if (body.length() == length)
                        break;
                }
            }
            try {
                File myObj = new File(port + ".txt");
                if (myObj.createNewFile()) {
                    System.out.println("File created: " + myObj.getName());
                    FileWriter myWriter = new FileWriter(port + ".txt");
                    myWriter.write(body.toString());
                    myWriter.close();
                    System.out.println("Writing to file done");
                } else {
                    FileWriter myWriter = new FileWriter(port + ".txt");
                    myWriter.write(body.toString());
                    myWriter.close();
                    System.out.println("Writing to file done");
                }
                break;
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        }
    }
}
