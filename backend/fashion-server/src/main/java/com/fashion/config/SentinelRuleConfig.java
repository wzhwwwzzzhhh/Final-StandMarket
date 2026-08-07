package com.fashion.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;

/** Loads a local default so rate limiting works without a Sentinel dashboard. */
@Configuration
public class SentinelRuleConfig implements InitializingBean {

    @Value("${fashion.sentinel.seckill-qps:500}")
    private double seckillQps;

    @Override
    public void afterPropertiesSet() {
        FlowRule rule = new FlowRule();
        rule.setResource("seckill");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(seckillQps);
        rule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);
        FlowRuleManager.loadRules(Collections.singletonList(rule));
    }
}
