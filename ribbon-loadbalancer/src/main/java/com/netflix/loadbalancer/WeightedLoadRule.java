package com.netflix.loadbalancer;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.weightedload.FetchLoadTask;
import com.netflix.loadbalancer.weightedload.WebSocketClientWithServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeightedLoadRule extends AbstractLoadBalancerRule{

    private static Logger log = LoggerFactory.getLogger(WeightedLoadRule.class);

    private HashMap<Server,WebSocketClientWithServer> scMap = new HashMap<>();
    private ExecutorService pool = Executors.newCachedThreadPool();
    private ScheduledExecutorService chooser = Executors.newSingleThreadScheduledExecutor();
    private boolean inited = false;

    private ILoadBalancer lb = null;
    private volatile Server best = null;

    private Server choose(ILoadBalancer lb) throws URISyntaxException {
        if (lb == null) {
            log.warn("no load balancer");
            return null;
        }

        this.lb = lb;

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

            chooser.scheduleAtFixedRate(new ChooseTask(),0,500,TimeUnit.MILLISECONDS);

            while(best == null){
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            inited = true;
        }

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

    class ChooseTask implements Runnable{

        private List<String> list = new LinkedList<>();
        StringBuilder sb = new StringBuilder();
        @Override
        public void run() {
            double minLoad = 101;
            for(Server s:lb.getReachableServers()){
                WebSocketClientWithServer client = scMap.get(s);
                if(client == null){
                    try {
                        client = new WebSocketClientWithServer(new URI("ws://"+s.getHost()));
                    } catch (URISyntaxException e) {
                        e.printStackTrace();
                    }
                    FetchLoadTask task = new FetchLoadTask(client);
                    pool.submit(task);
                    scMap.put(s,client);
                }else{
                    double load = client.getLoad();
                    //System.out.println(count+" time starting choosing server..."+"current:"+s.getHost()+" load:"+load);
                    list.add("["+s.getHost()+":"+load+"]");
                    //log.info("starting choosing server..."+"current:"+s.getHost()+" load:"+load);
                    if(load<minLoad){
                        minLoad = load;
                        best = s;
                    }
                }
            }

            for(String s:list){
                sb.append(s);
                sb.append("    ");
            }

            log.info(sb.toString()+"best: "+best.getHost());

            list.clear();
            sb.delete(0,sb.length());
        }
    }

}
