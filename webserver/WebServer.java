


import java.net.*;
import java.io.*;


public class WebServer {

    public static void main(String[] args) {
        if (args.length != 2) {
            throw new IllegalArgumentException("Use: <port> <list directories (1/0)>");
        }

        int port = Integer.parseInt(args[0]);
        boolean isListingDirectory = false;
        if (args[1].equals("1")) isListingDirectory = true;
        ServerSocket serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            serverSocket.setSoTimeout(300000);
            while (true) {
                Socket socket = serverSocket.accept(); //Abrimos un socket con el cliente que procesa la solicitud
                ServerThread serverThread = new ServerThread(socket, isListingDirectory); //Creamos el hilo que procesa la petición
                serverThread.start(); //Iniciamos la ejecución del hilo
            }

        } catch (SocketTimeoutException e) {
            System.err.println("Nothing received in 300 secs");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            try{
                serverSocket.close();
            }catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
