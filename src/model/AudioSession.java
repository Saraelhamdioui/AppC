package model;

import java.net.Socket;

public class AudioSession {

    private Socket client1;
    private Socket client2;

    public AudioSession(Socket client1, Socket client2) {
        this.client1 = client1;
        this.client2 = client2;
    }

    public Socket getClient1() {
        return client1;
    }

    public Socket getClient2() {
        return client2;
    }
}