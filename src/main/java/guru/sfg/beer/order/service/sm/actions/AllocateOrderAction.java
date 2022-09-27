package guru.sfg.beer.order.service.sm.actions;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.web.mappers.BeerOrderMapper;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.statemachine.StateContext;
import org.springframework.statemachine.action.Action;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static guru.sfg.beer.order.service.config.JmsConfig.ALLOCATE_ORDER_QUEUE;
import static guru.sfg.beer.order.service.sm.BeerOrderStateMachineConfig.BEER_ORDER_ID_HEADER;

@Component
@RequiredArgsConstructor
@Slf4j
public class AllocateOrderAction implements Action<BeerOrderStatusEnum, BeerOrderEventEnum> {

    private final JmsTemplate jmsTemplate;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderMapper beerOrderMapper;

    @Override
    public void execute(StateContext<BeerOrderStatusEnum, BeerOrderEventEnum> stateContext) {
        final String beerOrderId = (String) stateContext.getMessageHeader(BEER_ORDER_ID_HEADER);
        jmsTemplate.convertAndSend(ALLOCATE_ORDER_QUEUE,
                AllocateOrderRequest.builder()
                        .beerOrderDto(beerOrderMapper
                                .beerOrderToDto(getBeerOrderById(UUID.fromString(beerOrderId))))
                        .build());
        log.debug(String.format("Send order allocation request for order %s", beerOrderId));
    }

    private BeerOrder getBeerOrderById(UUID beerOrderId) {
        return beerOrderRepository.findById(beerOrderId).orElseThrow(() -> new RuntimeException("Order not found, id: " + beerOrderId));
    }
}
