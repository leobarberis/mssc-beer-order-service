package guru.sfg.beer.order.service.services.listeners;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.ValidationBeerOrderResultEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationOrderListener {

    private final BeerOrderManager beerOrderManager;

    @JmsListener(destination = JmsConfig.VALIDATE_ORDER_RESULT_QUEUE)
    public void listenValidationOrderResult(ValidationBeerOrderResultEvent orderResultEvent) {
        log.debug(String.format("Validation for order %s: %s", orderResultEvent.getOrderId(), orderResultEvent.isValid()));
        beerOrderManager.handleValidationResult(orderResultEvent.getOrderId(), orderResultEvent.isValid());
    }

}
