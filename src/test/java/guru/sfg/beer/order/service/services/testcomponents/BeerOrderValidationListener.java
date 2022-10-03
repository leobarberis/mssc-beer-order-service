package guru.sfg.beer.order.service.services.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
import guru.sfg.brewery.model.events.ValidationBeerOrderResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderValidationListener {

    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.VALIDATE_ORDER_QUEUE)
    public void listen(Message message) {

        ValidateBeerOrderRequest orderRequest = (ValidateBeerOrderRequest) message.getPayload();
        jmsTemplate.convertAndSend(JmsConfig.VALIDATE_ORDER_RESULT_QUEUE,
                ValidationBeerOrderResultEvent.builder()
                        .isValid((orderRequest.getBeerOrderDto().getCustomerRef() == null)
                                || !orderRequest.getBeerOrderDto().getCustomerRef().equals("fail-validation"))
                        .orderId(orderRequest.getBeerOrderDto().getId())
                        .build());
    }
}
