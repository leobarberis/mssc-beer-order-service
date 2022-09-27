package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.BeerOrderDto;
import guru.sfg.brewery.model.events.ValidateBeerOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static guru.sfg.beer.order.service.config.JmsConfig.VALIDATE_ORDER_QUEUE;
import static guru.sfg.beer.order.service.sm.BeerOrderStateMachineConfig.BEER_ORDER_ID_HEADER;

@Component
@RequiredArgsConstructor
@Slf4j
public class ValidateOrderAction implements Action<BeerOrderStatusEnum, BeerOrderEventEnum> {

    private final JmsTemplate jmsTemplate;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderMapper beerOrderMapper;

    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        final String beerOrderId = (String) stateContext.getMessageHeader(BEER_ORDER_ID_HEADER);
        final BeerOrderDto beerOrderDto = beerOrderMapper
                .beerOrderToDto(getBeerOrderById(UUID.fromString(beerOrderId)));
        jmsTemplate.convertAndSend(VALIDATE_ORDER_QUEUE,
                ValidateBeerOrderRequest.builder()
                        .beerOrderDto(beerOrderDto)
                        .build());
        log.debug("Sent validation request to queue for order id " + beerOrderId);
    }

    private BeerOrder getBeerOrderById(UUID beerOrderId) {
        return beerOrderRepository.findById(beerOrderId).orElseThrow(() -> new RuntimeException("Not found beer order id: " + beerOrderId ));
    }
}
