package com.netflix.loadbalancer.weightedload;

import org.java_websocket.WebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

public class FetchLoadTask implements Runnable {

    private Collection<WebSocketClientWithServer> clients;
    private static Logger log = LoggerFactory.getLogger(FetchLoadTask.class);
    public FetchLoadTask(Collection<WebSocketClientWithServer> clients){
        this.clients = clients;
    }

    @Override
    public void run() {
        for(WebSocketClientWithServer client:clients){
            client.connect();
        }
        for(WebSocketClientWithServer client:clients){
            while(!client.getReadyState().equals(WebSocket.READYSTATE.OPEN)){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        while (true){
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            for(WebSocketClientWithServer client:clients){
                client.send("loadinfo");
            }
        }
    }
}

