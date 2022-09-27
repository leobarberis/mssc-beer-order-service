package guru.sfg.beer.order.service.services.listeners;

import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.services.BeerOrderManager;
import guru.sfg.brewery.model.events.AllocationOrderResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AllocationOrderListener {
    private final BeerOrderManager beerOrderManager;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE)
    public void listen(AllocationOrderResult result) {
        log.debug(String.format("Allocation for order %s: ", result.getBeerOrderDto().getId()));
        beerOrderManager.handleAllocationResult(result.getBeerOrderDto(), result.isAllocationError(), result.isPendingInventory());
    }
}
