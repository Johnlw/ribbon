package com.netflix.loadbalancer;

import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.weightedload.CpuAndRam;
import com.netflix.loadbalancer.weightedload.FetchLoadTask;
import com.netflix.loadbalancer.weightedload.Range;
import com.netflix.loadbalancer.weightedload.WebSocketClientWithServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class WeightedLoadRule extends AbstractLoadBalancerRule {

    private static Logger log = LoggerFactory.getLogger(WeightedLoadRule.class);

    private ExecutorService pool = Executors.newCachedThreadPool();
    private ScheduledExecutorService rangeHandler = Executors.newSingleThreadScheduledExecutor();

    private HashMap<Server, WebSocketClientWithServer> scMap = new HashMap<>();
    HashMap<Server, Range> rangeMap = new HashMap<>();
    HashMap<Server, CpuAndRam> loadMap = new HashMap<>();

    private Object mutex = new Object();
    private volatile boolean rangeUpdated = false;
    private boolean rangeCache = true;


    private boolean inited = false;
    private volatile int randomBound = 1000;
    private ILoadBalancer lb = null;

    private Random rand = new Random();


    private void init(ILoadBalancer lb) {

        log.info("weighted load balancer initialing...");

        this.lb = lb;
        int count = 0;
        int size = 0;
        while (count < 5) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            int reachable = lb.getReachableServers().size();
            if (reachable > 0 && reachable == size) {
                count++;
            }
            size = reachable;
        }

        for (Server s : lb.getReachableServers()) {
            log.info("Server  " + s.getHost() + " is online");
            WebSocketClientWithServer client = null;
            try {
                client = new WebSocketClientWithServer(new URI("ws://" + s.getHost()));
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            scMap.put(s, client);
        }
        log.info("fetch load task start working...");


        FetchLoadTask task = new FetchLoadTask(scMap.values());
        pool.submit(task);


        //设置负载均衡器上的数据存储
        for (Server s : lb.getReachableServers()) {
            if (loadMap.get(s) == null) {
                loadMap.put(s, new CpuAndRam());
            }
        }

        log.info("rangehandler starting...");
        rangeHandler.scheduleAtFixedRate(new RangeTask(), 0, 2000, TimeUnit.MILLISECONDS);


        while (randomBound == 1000) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        log.info("weighted load balancer initialized");
        inited = true;
    }

    private Server choose(ILoadBalancer lb) throws URISyntaxException {

        if (lb == null) {
            log.warn("no load balancer");
            return null;
        }

        if(!inited){
            init(lb);
        }

        int value = rand.nextInt(randomBound);

        for (Server s : lb.getReachableServers()) {
            if (rangeCheck(value, s)) {
                //log.info("server choosed:" + s.getHost());
                return s;
            }
        }

        return null;
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


    class RangeTask implements Runnable {

        HashMap<Server, Integer> weightMap = new HashMap<>();

        @Override
        public void run() {
            log.info("rangehandler start working...");

            //将数据填充至数据存储
            try {
                int cpuSum = 0;
                int ramSum = 0;

                for (Server s : lb.getReachableServers()) {
                    WebSocketClientWithServer client = scMap.get(s);

                    String load = client.getLoad();
                    String[] msg = load.split("_");
                    CpuAndRam tmp = loadMap.get(s);

                    tmp.setCpu(Integer.parseInt(msg[0]));
                    tmp.setRam(Integer.parseInt(msg[1]));

                    cpuSum += tmp.getCpu();
                    ramSum += tmp.getRam();

                }

                for (Server s : loadMap.keySet()) {
                    log.info("server " + s + " : " + loadMap.get(s));
                }
                log.info("cpuSum:" + cpuSum);
                log.info("ramSum:" + ramSum);

                //计算各服务器剩余性能的权重
                for (Server s : lb.getReachableServers()) {
                    CpuAndRam load = loadMap.get(s);
                    int weight = (int) ((load.getCpu() * 600 / cpuSum) + (load.getRam() * 400 / ramSum));
                    weightMap.put(s, weight);
                }

                for (Server s : weightMap.keySet()) {
                    log.info("weight: server " + s.getHost() + " " + weightMap.get(s));
                }

                //将权重反应为区间，便于选择

                Server former = null;
                for (Server s : lb.getReachableServers()) {
                    Range range = rangeMap.get(s);
                    if (range == null) {
                        range = new Range();
                        rangeMap.put(s, range);
                    }
                    if (former == null) {
                        range.setLeft(0);
                        range.setRight(weightMap.get(s));
                    } else {
                        int formerRight = rangeMap.get(former).getRight();
                        range.setLeft(formerRight);
                        range.setRight(formerRight + weightMap.get(s));
                    }
                    former = s;
                }

                randomBound = rangeMap.get(former).getRight();
                for (Server s : weightMap.keySet()) {
                    log.info("range: server " + s.getHost() + " " + rangeMap.get(s));
                }
                log.info("randomBound:" + randomBound);
            } catch (Exception e) {
                log.error(" eeeeeee", e);
            }

        }
    }


    private boolean rangeCheck(int value, Server s) {

        Range range = rangeMap.get(s);
        if ((range.getLeft() <= value) && (value < range.getRight())) {
            return true;
        }
        return false;
    }


}
