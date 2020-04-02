package com.SixSense.threading;

import com.SixSense.api.http.overrides.HTTPThreadExecutor;
import com.SixSense.config.ThreadingConfig;
import org.apache.catalina.connector.Connector;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatWebServer;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

/*Manages all multi-threaded executables generated by the engine*/
@Component
@EnableConfigurationProperties(ThreadingConfig.class)
public class ThreadingManager implements Closeable {
    private static final Logger logger = LogManager.getLogger(ThreadingManager.class);
    private final ThreadPoolExecutor enginePool; //Executes all tasks originating from the engine itself (com.SixSense.*)
    private final HTTPThreadExecutor httpConnectionPool; //Executes all threads intercepting web requests (org.apache.catalina.*) [NOT all tomcat threads]
    private boolean isClosed = false;

    @Autowired
    private ThreadingManager(ThreadingConfig threadingConfig){
        ThreadingConfig.ThreadingProperties engineProperties = threadingConfig.getEngine();
        ThreadingConfig.ThreadingProperties httpProperties = threadingConfig.getHttp();

        this.enginePool = generateThreadPool(engineProperties);
        this.httpConnectionPool = new HTTPThreadExecutor(httpProperties);
    }

    public ThreadPoolExecutor generateThreadPool(ThreadingConfig.ThreadingProperties threadingProperties){
        return new ThreadPoolExecutor(
                threadingProperties.getMinimumThreads(),
                threadingProperties.getMaximumThreads(),
                threadingProperties.getAllowedIdleTime().toMillis(),
                TimeUnit.MILLISECONDS,
                new SynchronousQueue<>(), //To allow for dynamic size increase, we must provide a synchronousQueue (https://stackoverflow.com/a/8591681/1658288, https://stackoverflow.com/a/47691139/1658288)
                new EngineThreadFactory(threadingProperties)
        );
    }

    public <V> CompletableFuture<V> submit(Supplier<V> worker){
        if(this.isClosed){
            return CompletableFuture.failedFuture(new Exception("Cannot submit supplier to work queue - worker pool has been closed"));
        }else{
            return CompletableFuture.supplyAsync(worker, enginePool);
        }
    }

    public <V> void acceptAsync(CompletableFuture<V> future, Consumer<? super V> action){
        if(this.isClosed){
            future.completeExceptionally(new Exception("Cannot apply action to future task - worker pool has been closed"));
        }else{
            future.thenAcceptAsync(action, enginePool);
        }
    }

    public void submit(Runnable worker) throws Exception{
        if(this.isClosed){
            throw new Exception("Cannot submit runnable to work queue - worker pool has been closed");
        }
        enginePool.submit(worker);
    }

    public void injectServletWithPool(TomcatWebServer embeddedServer){
        embeddedServer.getTomcat().getService().addExecutor(this.httpConnectionPool);
    }

    public void injectConnectorWithPool(Connector connector){
        connector.getProtocolHandler().setExecutor(this.httpConnectionPool);
    }

    public boolean isShutdown(){
        return enginePool.isShutdown();
    }

    @Override
    public void close() {
        this.enginePool.shutdownNow();
        this.isClosed = true;
        logger.info("WorkerQueue closed");
    }
}