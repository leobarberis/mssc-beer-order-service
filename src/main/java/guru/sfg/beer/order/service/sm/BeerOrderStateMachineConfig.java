package guru.sfg.beer.order.service.sm;

import guru.sfg.beer.order.service.domain.BeerOrderEventEnum;
import guru.sfg.beer.order.service.domain.BeerOrderStatusEnum;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.action.Action;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;

import java.util.EnumSet;

@Configuration
@EnableStateMachineFactory
public class BeerOrderStateMachineConfig extends StateMachineConfigurerAdapter<BeerOrderStatusEnum, BeerOrderEventEnum> {

    public static final String BEER_ID_HEADER = "beer_id";

    @Override
    public void configure(StateMachineStateConfigurer<BeerOrderStatusEnum, BeerOrderEventEnum> states) throws Exception {
        states.withStates()
                .initial(BeerOrderStatusEnum.NEW)
                .states(EnumSet.allOf(BeerOrderStatusEnum.class))
                .end(BeerOrderStatusEnum.DELIVERED)
                .end(BeerOrderStatusEnum.DELIVERY_EXCEPTION)
                .end(BeerOrderStatusEnum.VALIDATION_EXCEPTION)
                .end(BeerOrderStatusEnum.ALLOCATION_EXCEPTION)
                .end(BeerOrderStatusEnum.PICKED_UP);
    }

    @Override
    public void configure(StateMachineTransitionConfigurer<BeerOrderStatusEnum, BeerOrderEventEnum> transitions) throws Exception {
        transitions.withExternal().source(BeerOrderStatusEnum.NEW).target(BeerOrderStatusEnum.NEW)
                .event(BeerOrderEventEnum.VALIDATE_ORDER)
                .action(validateOrder())
                .and()
                .withExternal().source(BeerOrderStatusEnum.NEW).target(BeerOrderStatusEnum.VALIDATED).event(BeerOrderEventEnum.VALIDATION_PASSED)
                .and()
                .withExternal().source(BeerOrderStatusEnum.NEW).target(BeerOrderStatusEnum.VALIDATION_EXCEPTION).event(BeerOrderEventEnum.VALIDATION_FAILED);
    }

    private Action<BeerOrderStatusEnum, BeerOrderEventEnum> validateOrder() {
        return stateContext -> {
            //Call to beerService so we can establish validation
            stateContext.getStateMachine().sendEvent(
                    MessageBuilder.withPayload(BeerOrderEventEnum.VALIDATION_PASSED)
                            .setHeader(BEER_ID_HEADER, stateContext.getMessageHeader(BEER_ID_HEADER))
                            .build());
        };
    }
}
