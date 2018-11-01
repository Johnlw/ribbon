package com.netflix.loadbalancer;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.weightedload.FetchLoadTask;
import com.netflix.loadbalancer.weightedload.WebSocketClientWithServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WeightedLoadRule extends AbstractLoadBalancerRule{

    private static Logger log = LoggerFactory.getLogger(WeightedLoadRule.class);

    private HashMap<Server,WebSocketClientWithServer> scMap = new HashMap<>();
    private ExecutorService pool = Executors.newCachedThreadPool();

    private boolean inited = false;
    //@PostConstruct
    /*public void initWebSocketClient() throws URISyntaxException {

        int count =0;
        int size = 0;

        while(lb == null){
            System.out.println(" lb is null");
            lb = getLoadBalancer();
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        while(lb != null && count<5){
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int reachable = lb.getReachableServers().size();
            if(reachable>0&&reachable == size){
                count++;
            }
            size = reachable;
        }


        for(Server s:lb.getReachableServers()){
            WebSocketClientWithServer client = new WebSocketClientWithServer(new URI("ws://"+s.getHost()));
            FetchLoadTask task = new FetchLoadTask(client);
            pool.submit(task);
            scMap.put(s,client);
        }
    }*/


    private Server choose(ILoadBalancer lb) throws URISyntaxException {

        if (lb == null) {
            log.warn("no load balancer");
            return null;
        }

        if(!inited){
            int count =0;
            int size = 0;
            while(count<5){
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                int reachable = lb.getReachableServers().size();
                if(reachable>0&&reachable == size){
                    count++;
                }
                size = reachable;
            }

            for(Server s:lb.getReachableServers()){
                WebSocketClientWithServer client = new WebSocketClientWithServer(new URI("ws://"+s.getHost()));
                FetchLoadTask task = new FetchLoadTask(client);
                pool.submit(task);
                scMap.put(s,client);
            }

            boolean loadinfoGeted = false;
            while(!loadinfoGeted){
                for(Server s:lb.getReachableServers()){
                    if(scMap.get(s).getLoad()<101){
                        loadinfoGeted = true;
                        break;
                    }
                }
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            inited = true;
        }


        double minLoad = 101;
        Server best = null;
        for(Server s:lb.getReachableServers()){
            WebSocketClientWithServer client = scMap.get(s);
            if(client == null){
                client = new WebSocketClientWithServer(new URI("ws://"+s.getHost()));
                FetchLoadTask task = new FetchLoadTask(client);
                pool.submit(task);
                scMap.put(s,client);
            }else{
                double load = client.getLoad();
                //System.out.println(count+" time starting choosing server..."+"current:"+s.getHost()+" load:"+load);
                log.info("starting choosing server..."+"current:"+s.getHost()+" load:"+load);
                if(load<minLoad){
                    minLoad = load;
                    best = s;
                }
            }
        }
        //System.out.println(count+" time server choosed:"+best.getHost());
        log.info("server choosed:"+best.getHost());
        return best;
    }

    @Override
    public Server choose(Object key) {
        Server best = null;
        try {
            best = choose(getLoadBalancer());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return best;
    }

    @Override
    public void initWithNiwsConfig(IClientConfig clientConfig) {

    }
}
