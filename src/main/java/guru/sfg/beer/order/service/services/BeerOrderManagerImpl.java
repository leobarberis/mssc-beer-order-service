package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.sm.BeerOrderStateChangeInterceptor;
import guru.sfg.brewery.model.BeerOrderDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static guru.sfg.beer.order.service.sm.BeerOrderStateMachineConfig.BEER_ORDER_ID_HEADER;

@Slf4j
@Service
@RequiredArgsConstructor
public class BeerOrderManagerImpl implements BeerOrderManager {

    private final StateMachineFactory<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachineFactory;
    private final BeerOrderRepository beerOrderRepository;
    private final BeerOrderStateChangeInterceptor beerOrderStateChangeInterceptor;

    @Transactional
    @Override
    public BeerOrder newBeerOrder(BeerOrder beerOrder) {
        beerOrder.setId(null);
        beerOrder.setOrderStatus(BeerOrderStatusEnum.NEW);
        //No need for rehydrate state machine, cause brand-new order
        final BeerOrder savedBeerOrder = beerOrderRepository.saveAndFlush(beerOrder);
        sendBeerOrderEvent(savedBeerOrder, BeerOrderEventEnum.VALIDATE_ORDER);
        return savedBeerOrder;
    }

    @Transactional
    @Override
    public void handleValidationResult(UUID orderId, boolean valid) {
        final Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(orderId);

        beerOrderOptional.ifPresentOrElse((beerOrder) -> {
            BeerOrderEventEnum eventEnum = valid ?
                    BeerOrderEventEnum.VALIDATION_PASSED :
                    BeerOrderEventEnum.VALIDATION_FAILED;
            sendBeerOrderEvent(beerOrder, eventEnum);

            if (valid) {
                //After sending VALIDATION_PASSED event, hibernate will persist that order
                //So we need to refresh and get the updated one out of the DB
                final Optional<BeerOrder> beerOrderOptionalValidated = beerOrderRepository.findById(orderId);
                beerOrderOptionalValidated.ifPresentOrElse((validatedBeerOrder) -> {
                    awaitForStatus(orderId, BeerOrderStatusEnum.VALIDATED);
                    sendBeerOrderEvent(validatedBeerOrder, BeerOrderEventEnum.ALLOCATE_ORDER);
                }, () -> log.error("Validated Order Not Found, Id: " + orderId));
            }
        }, () -> log.error("Handled Validation Result; Order Not found, Id: " + orderId));
    }

    @Transactional
    @Override
    public void handleAllocationResult(BeerOrderDto beerOrderDto, boolean allocationError, boolean pendingInventory) {
        final Optional<BeerOrder> optionalBeerOrder = beerOrderRepository.findById(beerOrderDto.getId());
        optionalBeerOrder.ifPresentOrElse((beerOrder) -> {
            BeerOrderEventEnum eventEnum = allocationError ?
                    BeerOrderEventEnum.ALLOCATION_FAILED :
                    pendingInventory ?
                            BeerOrderEventEnum.ALLOCATION_NO_INVENTORY : BeerOrderEventEnum.ALLOCATION_SUCCESS;
            sendBeerOrderEvent(beerOrder, eventEnum);
            awaitForStatus(beerOrder.getId(), BeerOrderStatusEnum.ALLOCATED);
            updateAllocatedQty(beerOrderDto, beerOrder);
        }, () -> log.error("Order Not found, id: " + beerOrderDto.getId()));
    }

    @Override
    public void beerOrderPickedUp(UUID id) {
        final Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(id);
        beerOrderOptional.ifPresentOrElse((beerOrder) -> sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.BEER_ORDER_PICKED_UP),
                () -> log.error("Order Not Found, ID: " + id));
    }

    @Override
    public void handleCancelResult(UUID orderId) {
        beerOrderRepository.findById(orderId).ifPresentOrElse((beerOrder -> sendBeerOrderEvent(beerOrder, BeerOrderEventEnum.CANCEL_ORDER)),
                () -> log.error("Order not found: " + orderId));
    }

    private void updateAllocatedQty(BeerOrderDto beerOrderDto, BeerOrder beerOrder) {
        final BeerOrder allocatedOrder = beerOrderRepository.findById(beerOrderDto.getId()).get();

        allocatedOrder.getBeerOrderLines().forEach(beerOrderLine ->
                beerOrderDto.getBeerOrderLines().forEach(beerOrderLineDto -> {
                    if (beerOrderLine.getId().equals(beerOrderLineDto.getId())) {
                        beerOrderLine.setQuantityAllocated(beerOrderLineDto.getQuantityAllocated());
                    }
                }));

        beerOrderRepository.saveAndFlush(beerOrder);
    }

    private void sendBeerOrderEvent(BeerOrder beerOrder, BeerOrderEventEnum eventEnum) {
        final StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> sm = build(beerOrder);
        final Message<BeerOrderEventEnum> message = MessageBuilder
                .withPayload(eventEnum)
                .setHeader(BEER_ORDER_ID_HEADER, beerOrder.getId().toString())
                .build();
        log.debug(String.format("Sending Event %s for order id %s", eventEnum, beerOrder.getId()));
        sm.sendEvent(message);
    }

    private void awaitForStatus(UUID beerOrderId, BeerOrderStatusEnum statusEnum) {

        AtomicBoolean found = new AtomicBoolean(false);
        AtomicInteger loopCount = new AtomicInteger(0);

        while (!found.get()) {
            if (loopCount.incrementAndGet() > 10) {
                found.set(true);
                log.debug("Loop Retries exceeded");
            }

            beerOrderRepository.findById(beerOrderId).ifPresentOrElse(beerOrder -> {
                if (beerOrder.getOrderStatus().equals(statusEnum)) {
                    found.set(true);
                    log.debug("Order Found");
                } else {
                    log.debug("Order Status Not Equal. Expected: " + statusEnum.name() + " Found: " + beerOrder.getOrderStatus().name());
                }
            }, () -> {
                log.debug("Order Id Not Found");
            });

            if (!found.get()) {
                try {
                    log.debug("Sleeping for retry");
                    Thread.sleep(100);
                } catch (Exception e) {
                    // do nothing
                }
            }
        }
    }

    private StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> build(BeerOrder beerOrder) {
        //Rehydrate sm
        final StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine =
                stateMachineFactory.getStateMachine(beerOrder.getId());
        stateMachine.stop();
        stateMachine.getStateMachineAccessor()
                .doWithAllRegions(sma -> {
                    sma.addStateMachineInterceptor(beerOrderStateChangeInterceptor);
                    sma.resetStateMachine(new DefaultStateMachineContext<>(beerOrder.getOrderStatus()
                            , null
                            , null
                            , null));
                });
        stateMachine.start();
        return stateMachine;
    }
}
