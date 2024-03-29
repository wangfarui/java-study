package com.itwray.study.rocketmq.consumer;

import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.SelectorType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 消息消费者监听器
 *
 * @author Wray
 * @since 2023/10/25
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface MQConsumerListener {

    String NAME_SERVER_PLACEHOLDER = "${rocketmq.name-server:}";
    String TOPIC_PLACEHOLDER = "${rocketmq.consumer.topic:}";
    String GROUP_PLACEHOLDER = "${rocketmq.consumer.group:}";

    /**
     * 消费者名称，即Bean名称
     */
    String value() default "";

    /**
     * 消费监听模式
     */
    ConsumeListeningMode consumeListeningMode() default ConsumeListeningMode.PUSH;

    /**
     * 消息监听规则
     * <p>仅在 ConsumeListeningMode.PUSH 模式下生效</p>
     */
    MessageListeningRule messageListeningRule() default MessageListeningRule.ORDERLY;

    String nameServer() default NAME_SERVER_PLACEHOLDER;

    String topic() default TOPIC_PLACEHOLDER;

    String group() default GROUP_PLACEHOLDER;

    MessageModel messageModel() default MessageModel.CLUSTERING;

    SelectorType selectorType() default SelectorType.TAG;

    String selectorExpression() default "*";

}
