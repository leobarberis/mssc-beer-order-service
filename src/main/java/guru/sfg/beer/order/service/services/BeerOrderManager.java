package guru.sfg.beer.order.service.services;

import guru.sfg.beer.order.service.domain.BeerOrder;
import guru.sfg.brewery.model.BeerOrderDto;

import java.util.UUID;

public interface BeerOrderManager {
    BeerOrder newBeerOrder(BeerOrder beerOrder);

    void handleValidationResult(UUID orderId, boolean valid);

    void handleAllocationResult(BeerOrderDto beerOrderDto, boolean allocationError, boolean pendingInventory);

    void beerOrderPickedUp(UUID id);
}
