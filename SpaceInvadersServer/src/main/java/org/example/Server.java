package org.example;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private static final int PORT = 4444;

    private static final List<ClientHandler> clients = new LinkedList<>();
    private static final ExecutorService pool = Executors.newFixedThreadPool(2);

    public static void main(String[] args)  {
        try (ServerSocket listener = new ServerSocket(PORT)) {
            while (true) {
                System.out.println("[SERVER] Waiting for client connection...");
                Socket client = listener.accept();
                System.out.println("[SERVER] Connected to client");

                ClientHandler clientThread = new ClientHandler(client, clients);
                clients.add(clientThread);

                pool.execute(clientThread);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}