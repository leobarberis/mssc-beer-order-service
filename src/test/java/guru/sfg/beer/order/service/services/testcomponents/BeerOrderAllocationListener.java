package guru.sfg.beer.order.service.services.testcomponents;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
@Component
public class BeerOrderAllocationListener {

    private final JmsTemplate jmsTemplate;
    private final ObjectMapper mapper;

    @JmsListener(destination = JmsConfig.ALLOCATE_ORDER_QUEUE)
    public void listen(Message msg) {
        AllocateOrderRequest request = (AllocateOrderRequest) msg.getPayload();

        for (BeerOrderLineDto beerOrderLineDto : request.getBeerOrderDto().getBeerOrderLines()) {
            beerOrderLineDto.setQuantityAllocated(beerOrderLineDto.getOrderQuantity());
        }

        String customerRef = request.getBeerOrderDto().getCustomerRef();

        final boolean allocationError = customerRef != null && customerRef.equals("fail-allocation");
        final boolean allocationPartialError = customerRef != null && customerRef.equals("fail-allocation-no-inventory");
        final boolean networkError = customerRef != null && customerRef.equals("allocation-networking-error");

        //Allocation Compensation logic
        List<BeerOrderLineDto> updatedBeerOrderLines = request.getBeerOrderDto().getBeerOrderLines()
                .stream()
                .map(beerOrderLineDto -> allocationPartialError ?
                        cloneBeerOrderLineDto(beerOrderLineDto)
                        : beerOrderLineDto)
                .collect(Collectors.toList());

        request.getBeerOrderDto().setBeerOrderLines(updatedBeerOrderLines);

        if(!networkError) {
            jmsTemplate.convertAndSend(JmsConfig.ALLOCATE_ORDER_RESULT_QUEUE, AllocationOrderResult.builder()
                    .beerOrderDto(request.getBeerOrderDto())
                    .pendingInventory(allocationPartialError)
                    .allocationError(allocationError)
                    .build());
        }
    }

    private BeerOrderLineDto cloneBeerOrderLineDto(BeerOrderLineDto dto) {
        return BeerOrderLineDto.builder()
                .id(dto.getId())
                .version(dto.getVersion())
                .createdDate(dto.getCreatedDate())
                .lastModifiedDate(dto.getLastModifiedDate())
                .upc(dto.getUpc())
                .beerName(dto.getBeerName())
                .beerId(dto.getBeerId())
                .beerStyle(dto.getBeerStyle())
                .price(dto.getPrice())
                .orderQuantity(dto.getOrderQuantity() - 1)
                .build();
    }

}
