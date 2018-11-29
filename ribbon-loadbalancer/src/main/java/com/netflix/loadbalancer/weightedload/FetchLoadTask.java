package com.netflix.loadbalancer.weightedload;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FetchLoadTask implements Runnable {

    private WebSocketClientWithServer client;
    private static Logger log = LoggerFactory.getLogger(FetchLoadTask.class);
    public FetchLoadTask(WebSocketClientWithServer client){
        this.client = client;
    }

    @Override
    public void run() {
        client.connect();
        while(!client.getReadyState().equals(WebSocket.READYSTATE.OPEN)){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        while (true){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            client.send("loadinfo");
        }
    }
}

