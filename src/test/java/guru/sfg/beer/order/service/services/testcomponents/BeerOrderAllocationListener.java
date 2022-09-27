package guru.sfg.beer.order.service.services.testcomponents;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.brewery.model.BeerOrderLineDto;
import guru.sfg.brewery.model.events.AllocateOrderRequest;
import guru.sfg.brewery.model.events.AllocationOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {

    private final JmsTemplate jmsTemplate;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(Message msg) {
        AllocateOrderRequest request = (AllocateOrderRequest) msg.getPayload();

        for (BeerOrderLineDto beerOrderLineDto : request.getBeerOrderDto().getBeerOrderLines()) {
            beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
        }

        jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE,
                AllocationOrderResult.builder()
                        .beerOrderDto(request.getBeerOrderDto())
                        .pendingInventory(false)
                        .allocationError(false)
                        .build());
    }

}
