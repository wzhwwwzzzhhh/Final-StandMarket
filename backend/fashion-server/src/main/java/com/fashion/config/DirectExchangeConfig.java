package com.fashion.config;


import org.springframework.amqp.core.Binding;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DirectExchangeConfig {
    public static  final String SeckillQueue = "market.mq";
    public static  final String SeckillExchange = "market.direct";
    public static  final String SeckillRoutingKey = "seckillOrder";

    //延迟队列和延迟交换机
    public static  final String delayQueue = "delay.queue";
    public static  final String delayExchange = "delay.exchange";
    public static  final String delayRoutingKey = "delay.routingKey";

    //死信队列和死信交换机
    public static  final String deadQueue = "dead.queue";
    public static  final String deadExchange = "dead.exchange";
    public static  final String deadRoutingKey = "dead.routingKey";


    @Bean
    public DirectExchange SeckillExchange(){
        return new DirectExchange(SeckillExchange);
    }

    @Bean
    public Queue SeckillQueue(){
        return new Queue(SeckillQueue);
    }

    @Bean
    public Binding bindingQueue(){
        return BindingBuilder.bind(SeckillQueue()).to(SeckillExchange()).with(SeckillRoutingKey);
    }

    @Bean
    public DirectExchange delayExchange(){
        return new DirectExchange(delayExchange);
    }
    @Bean
    public Queue delayQueue(){
        return new Queue(delayQueue);
    }
    @Bean
    public Binding bindingDelayQueue(){
        return BindingBuilder.bind(delayQueue()).to(delayExchange()).with(delayRoutingKey);
    }

    @Bean
    public DirectExchange deadExchange(){
        return new DirectExchange(deadExchange);
    }
    @Bean
    public Queue deadQueue(){
        return new Queue(deadQueue);
    }
    @Bean
    public Binding bindingDeadQueue(){
        return BindingBuilder.bind(deadQueue()).to(deadExchange()).with(deadRoutingKey);
    }


}
