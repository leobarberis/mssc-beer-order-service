package guru.sfg.beer.order.service.sm;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.state.State;
import org.springframework.statemachine.support.StateMachineInterceptorAdapter;
import org.springframework.statemachine.transition.Transition;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static guru.sfg.beer.order.service.sm.BeerOrderStateMachineConfig.BEER_ORDER_ID_HEADER;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeerOrderStateChangeInterceptor extends StateMachineInterceptorAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {

    private final BeerOrderRepository beerOrderRepository;

    @Transactional
    @Override
    public void preStateChange(State<BeerOrderStatusEnum, BeerOrderEventEnum> state, Message<BeerOrderEventEnum> message, Transition<BeerOrderStatusEnum, BeerOrderEventEnum> transition, StateMachine<BeerOrderStatusEnum, BeerOrderEventEnum> stateMachine) {
        Optional.ofNullable(message).flatMap(msg ->
                        Optional.ofNullable((String) message.getHeaders()
                                .getOrDefault(BEER_ORDER_ID_HEADER, -1L)))
                .ifPresent(orderId -> {
                    log.debug(String.format("Saving state for order id: %s, status: %s", orderId, state.getId()));
                    final Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(UUID.fromString(orderId));
                    beerOrderOptional.ifPresentOrElse((beerOrder) -> {
                        beerOrder.setOrderStatus(state.getId());
                        beerOrderRepository.saveAndFlush(beerOrder);
                    }, () -> new RuntimeException("Could not save beer order " + orderId));
                });
    }
}
