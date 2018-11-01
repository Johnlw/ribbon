package com.netflix.loadbalancer.weightedload;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class WebSocketClientWithServer extends WebSocketClient {

    private volatile double load = 101;
    private static Logger log = LoggerFactory.getLogger(WebSocketClientWithServer.class);

    public WebSocketClientWithServer(URI serverUri) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        //System.out.println("websocket connection established for "+uri);
        log.info("websocket connection established for "+uri);
    }

    @Override
    public void onMessage(String message) {
        //System.out.println("msg received from "+uri+" : "+message);
        log.info("msg received from "+uri+" :  load is "+message);
        load = Double.parseDouble(message);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        //System.out.println("websocket connection closed for "+uri);
        log.info("websocket connection closed for "+uri);
    }

    @Override
    public void onError(Exception ex) {
        //System.out.println("websocket connection error for "+uri);
        log.error("websocket connection error for "+uri);
    }

    public double getLoad() {
        return load;
    }
}
