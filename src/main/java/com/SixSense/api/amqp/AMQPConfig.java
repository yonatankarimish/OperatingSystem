package com.SixSense.api.amqp;


import com.SixSense.config.HostConfig;
import com.SixSense.config.ThreadingConfig;
import com.SixSense.threading.ThreadingManager;
import com.rabbitmq.client.ShutdownSignalException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

//https://www.rabbitmq.com/tutorials/tutorial-four-spring-amqp.html
//Should explain the whole class, read the article through
@Configuration
@EnableConfigurationProperties({HostConfig.class, ThreadingConfig.class})
public class AMQPConfig {
    private static final Logger logger = LogManager.getLogger(AMQPConfig.class);
    static final String OperationResultBindingKey = "operation";
    static final String TerminationResultBindingKey = "terminate";

    private final ThreadingManager threadingManager;
    private final HostConfig.RabbitHost rabbitHost;
    private final ThreadingConfig.ThreadingProperties amqpThreadingProperties;

    @Autowired
    public AMQPConfig(ThreadingManager threadingManager, HostConfig hostConfig, ThreadingConfig threadingConfig){
        this.threadingManager = threadingManager;
        this.rabbitHost = hostConfig.getRabbit();
        this.amqpThreadingProperties = threadingConfig.getAmqp();
    }


    @Bean
    public ConnectionFactory rabbitConnectionFactory(){
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setHost(rabbitHost.getHost());
        connectionFactory.setPort(rabbitHost.getPort());
        connectionFactory.setUsername(rabbitHost.getUsername());
        connectionFactory.setPassword(rabbitHost.getPassword());
        connectionFactory.setVirtualHost(rabbitHost.getVhost());

        connectionFactory.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connectionFactory.setPublisherReturns(true);
        connectionFactory.addConnectionListener(new ConnectionListener() {
            @Override
            public void onCreate(Connection connection) {
                logger.debug("AMQP CachingConnectionFactory is now open");
            }

            @Override
            public void onShutDown(ShutdownSignalException signal) {
                logger.debug("AMQP CachingConnectionFactory is now closed (" + signal.getMessage() + ")");
            }
        });

        return connectionFactory;
    }

    @Bean
    public RabbitTemplate rabbitTemplate(){
        RabbitTemplate template = new RabbitTemplate(rabbitConnectionFactory());
        template.setMandatory(true);
        template.setConfirmCallback((CorrelationData correlationData, boolean ack, String cause) -> {
            if(!ack){
                logger.error("Failed to publish message to rabbitmq broker. Caused by: " + cause);
            }
        });
        template.setReturnCallback((Message message, int replyCode, String replyText, String exchange, String routingKey) -> {
            if(replyCode != 200){
                logger.error("Failed to route message through exchange " + exchange + " with routing key " + routingKey + ". Caused by: " + replyText);
            }
        });
        return template;
    }

    @Bean
    public DirectExchange resultsExchange(){
        return new DirectExchange("engine.results");
    }

    @Bean
    public Queue operationResultsQueue(){
        //Queue(String name, boolean durable)
        return new Queue("engine.results.operation", true);
    }

    @Bean
    public Queue terminationResultsQueue(){
        //Queue(String name, boolean durable)
        return new Queue("engine.results.terminate", true);
    }

    @Bean
    public Binding bindOperationResults(DirectExchange resultsExchange, Queue operationResultsQueue){
        return BindingBuilder.bind(operationResultsQueue).to(resultsExchange).with("operation");
    }

    @Bean
    public Binding bindTerminationResults(DirectExchange resultsExchange, Queue terminationResultsQueue){
        return BindingBuilder.bind(terminationResultsQueue).to(resultsExchange).with("terminate");
    }
}
