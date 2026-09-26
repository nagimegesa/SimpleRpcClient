package com.rpcclient.rpc.interceptor;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import com.rpcclient.rpc.RpcConfig;
import com.rpcclient.rpc.RpcContext;
import com.rpcclient.rpc.exception.RpcTooManyCallException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CallerLimitInterceptor implements RpcInterceptor {

    @Resource
    RpcConfig config;

    @Override
    public Object process(RpcContext context, RpcInterceptorChain chain) {
        try (Entry entry = SphU.entry("RpcCallLimit")) {
            return chain.process(context);
        } catch (BlockException e) {
            throw new RpcTooManyCallException("too many call", e);
        }
    }

    @Override
    public int getOrder() {
        return 2;
    }

    @PostConstruct
    private void init() {
        FlowRule rule = new FlowRule();
        rule.setRefResource("RpcCallLimit");
        rule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        rule.setCount(config.callMax);
        List<FlowRule> rules = new ArrayList<>();
        rules.add(rule);
        FlowRuleManager.loadRules(rules);
    }
}
