package com.netflix.loadbalancer;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class WeightedLoadRuleTest {
    static BaseLoadBalancer lb;
    static Map<String, Boolean> isAliveMap = new ConcurrentHashMap<String, Boolean>();

    @BeforeClass
    public static void setup(){
        LogManager.getRootLogger().setLevel((Level)Level.DEBUG);
        isAliveMap.put("192.168.44.131:8085", Boolean.TRUE);
        isAliveMap.put("192.168.44.132:8085", Boolean.TRUE);
        isAliveMap.put("192.168.44.133:8085", Boolean.TRUE);

        IPing ping = new WeightedLoadRuleTest.PingFake();
        IRule rule = new WeightedLoadRule();
        lb = new BaseLoadBalancer(ping,rule);
        lb.setPingInterval(1);
        lb.setMaxTotalPingTime(2);

        // the setting of servers is done by a call to DiscoveryService
        List<Server> servers = new ArrayList<Server>();
        for (String svc: isAliveMap.keySet()){
            servers.add(new Server(svc));
        }
        lb.addServers(servers);

        System.out.println(lb);

        rule.setLoadBalancer(lb);
        try {
            ((WeightedLoadRule) rule).initWebSocketClient();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        // make sure the ping cycle has kicked in and all servers are set to alive
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }
    }

    @Test
    public void test() throws InterruptedException {
        for(int i = 0;i<30;i++){
            Server s = lb.chooseServer();
            Thread.sleep(10000);
        }
    }


    static class PingFake implements IPing {
        public boolean isAlive(Server server) {
            Boolean res = isAliveMap.get(server.getId());
            return ((res != null) && (res.booleanValue()));
        }
    }
}
