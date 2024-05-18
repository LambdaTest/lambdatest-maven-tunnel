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
                File folder = new File(".lambdatest/tunnelprocs");
                if (!folder.exists()) {
                    folder.mkdir();
                }
                File myObj = new File(folder,port + ".txt");
                if (myObj.createNewFile()) {
                    FileWriter myWriter = new FileWriter(myObj);
                    myWriter.write(body.toString());
                    myWriter.close();

                } else {
                    FileWriter myWriter = new FileWriter(myObj);
                    myWriter.write(body.toString());
                    myWriter.close();
                }
                // write 200 OK response
                String response = "HTTP/1.1 200 OK\r\n" + "Content-Type: text/html\r\n" + "Content-Length: 0\r\n" + "\r\n";
                OutputStream os = clientSocket.getOutputStream();
                os.write(response.getBytes());
                break;
            } catch (IOException e) {
                System.out.println("An error occurred.");
                e.printStackTrace();
            }
        }

    }
}
