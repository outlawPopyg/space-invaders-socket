package org.example;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ClientHandler implements Runnable {
    private final Socket client;
    private final InputStream in;
    private final OutputStream out;
    private final List<ClientHandler> clients;

    public ClientHandler(Socket clientSocket, List<ClientHandler> clients) throws IOException {
        this.client = clientSocket;
        this.clients = clients;

        this.in = clientSocket.getInputStream();
        this.out = clientSocket.getOutputStream();
    }

    @Override
    public void run() {
        try {
            if (clients.size() == 2) {
                SuperPacket startPacket = SuperPacket.create(1);
                outToAll(startPacket.toByteArray());
            }
            while (true) {
                byte[] request = readInput(in);

                outToAll(request);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } finally {
            try {
                in.close();
                out.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
    private void outToAll(byte[] request) {
        for (ClientHandler aClient : clients) {
            try {
                aClient.out.write(request);
                aClient.out.flush();
            } catch (IOException e) {
                throw new IllegalArgumentException(e);
            }
        }
    }

    private byte[] extendArray(byte[] oldArray) {
        int oldSize = oldArray.length;
        byte[] newArray = new byte[oldSize * 2];
        System.arraycopy(oldArray, 0, newArray, 0, oldSize);
        return newArray;
    }

    private byte[] readInput(InputStream stream) throws IOException {
        int b;
        byte[] buffer = new byte[10];
        int counter = 0;
        while ((b = stream.read()) > -1) {
            buffer[counter++] = (byte) b;
            if (counter >= buffer.length) {
                buffer = extendArray(buffer);
            }
            if (counter > 2 && SuperPacket.compareEndOfPacket(buffer, counter - 1)) {
                break;
            }
        }
        byte[] data = new byte[counter];
        System.arraycopy(buffer, 0, data, 0, counter);
        return data;
    }
}
