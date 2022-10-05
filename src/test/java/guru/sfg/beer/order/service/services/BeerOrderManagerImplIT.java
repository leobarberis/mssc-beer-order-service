package guru.sfg.beer.order.service.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import guru.sfg.beer.order.service.config.JmsConfig;
import guru.sfg.beer.order.service.domain.*;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.brewery.model.BeerDto;
import guru.sfg.brewery.model.events.AllocationFailureEvent;
import guru.sfg.brewery.model.events.DeallocateOrderRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.annotation.Rollback;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.jenspiegsa.wiremockextension.ManagedWireMockServer.with;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Slf4j
@ExtendWith(WireMockExtension.class)
@SpringBootTest
public class BeerOrderManagerImplIT {

    @Autowired
    BeerOrderManager beerOrderManager;

    @Autowired
    BeerOrderRepository beerOrderRepository;

    @Autowired
    CustomerRepository customerRepository;

    @Autowired
    WireMockServer wireMockServer;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JmsTemplate jmsTemplate;

    @Value("${sfg.brewery.beer-service-path-by-upc}")
    String beerServicePathByUpc;

    Customer customer;

    UUID beerId = UUID.randomUUID();

    @TestConfiguration
    static class RestTemplateBuilderProvider {
        @Bean(destroyMethod = "stop")
        public WireMockServer wireMockServer() {
            WireMockServer server = with(wireMockConfig().port(8083));
            server.start();
            return server;
        }
    }

    @BeforeEach
    void setUp() {
        customer = customerRepository.save(Customer.builder()
                .customerName("Test Customer")
                .build());
    }

    @Rollback(false)
    @Test
    void newToAllocate() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            final Optional<BeerOrder> optionalBeerOrder = beerOrderRepository.findById(beerOrder.getId());
            optionalBeerOrder.ifPresentOrElse((foundOrder) -> {
                assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
            }, () -> log.error("Order not found, id: " + beerOrder.getId()));
        });

    }

    @Rollback(value = false)
    @Test
    void testNewToPickedUp() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();

        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            final Optional<BeerOrder> optionalBeerOrder = beerOrderRepository.findById(beerOrder.getId());
            optionalBeerOrder.ifPresentOrElse((foundOrder) -> {
                assertEquals(BeerOrderStatusEnum.ALLOCATED, foundOrder.getOrderStatus());
            }, () -> log.error("Order not found, id: " + beerOrder.getId()));
        });

        beerOrderManager.beerOrderPickedUp(savedBeerOrder.getId());

        await().untilAsserted(() -> {
            final Optional<BeerOrder> optionalBeerOrder = beerOrderRepository.findById(beerOrder.getId());
            optionalBeerOrder.ifPresentOrElse((foundOrder) -> {
                assertEquals(BeerOrderStatusEnum.PICKED_UP, foundOrder.getOrderStatus());
            }, () -> log.error("Order not found, id: " + beerOrder.getId()));
        });

        final Optional<BeerOrder> pickedUpOrderOptional = beerOrderRepository.findById(savedBeerOrder.getId());
        pickedUpOrderOptional.ifPresentOrElse((pickedUpOrder) -> {
            assertEquals(BeerOrderStatusEnum.PICKED_UP, pickedUpOrder.getOrderStatus());
        }, () -> {
            log.error("Order Not Found, id: " + savedBeerOrder.getId());
        });
    }

    @Test
    void testFailedValidation() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
         wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                 .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("fail-validation");

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrder.getId());
            beerOrderOptional.ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.VALIDATION_EXCEPTION, foundedOrder.getOrderStatus());
            }, () -> {
                log.error("Order not found, id: " + beerOrder.getId());
            });
        });
    }

    @Test
    void testFailedAllocation() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("fail-allocation");

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrder.getId());
            beerOrderOptional.ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.ALLOCATION_EXCEPTION, foundedOrder.getOrderStatus());
            }, () -> {
                log.error("Order not found, id: " + beerOrder.getId());
            });
        });

        AllocationFailureEvent allocationFailureEvent = (AllocationFailureEvent) jmsTemplate.receiveAndConvert(JmsConfig.ALLOCATE_FAILURE_QUEUE);
        assertNotNull(allocationFailureEvent);
        assertThat(allocationFailureEvent.getOrderId()).isEqualTo(savedBeerOrder.getId());
    }

    @Test
    void testFailedAllocationPending() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("fail-allocation-no-inventory");

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            Optional<BeerOrder> beerOrderOptional = beerOrderRepository.findById(beerOrder.getId());
            beerOrderOptional.ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.PENDING_INVENTORY, foundedOrder.getOrderStatus());
            }, () -> {
                log.error("Order not found, id: " + beerOrder.getId());
            });
        });
    }

    @Test
    void testValidationPendingToCancel() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("validation-networking-error");

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.VALIDATION_PENDING, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

        beerOrderManager.handleCancelResult(beerOrder.getId());

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.CANCELLED, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

    }

    @Test
    void testAllocationPendingToCancel() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();
        beerOrder.setCustomerRef("allocation-networking-error");

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.ALLOCATION_PENDING, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

        beerOrderManager.handleCancelResult(beerOrder.getId());

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.CANCELLED, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

    }

    @Test
    void testAllocatedToCancel() throws JsonProcessingException {
        BeerDto beerDto = BeerDto.builder().id(beerId).upc("12345").build();
        wireMockServer.stubFor(get(beerServicePathByUpc.replace("{upc}", "12345"))
                .willReturn(okJson(objectMapper.writeValueAsString(beerDto))));

        BeerOrder beerOrder = createBeerOrder();

        BeerOrder savedBeerOrder = beerOrderManager.newBeerOrder(beerOrder);

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.ALLOCATED, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

        beerOrderManager.handleCancelResult(beerOrder.getId());

        await().untilAsserted(() -> {
            beerOrderRepository.findById(beerOrder.getId()).ifPresentOrElse((foundedOrder) -> {
                assertEquals(BeerOrderStatusEnum.CANCELLED, foundedOrder.getOrderStatus());
            }, () -> log.error("Order not found :" + beerOrder.getId()));
        });

        DeallocateOrderRequest deallocateOrderRequest = (DeallocateOrderRequest) jmsTemplate.receiveAndConvert(JmsConfig.DEALLOCATE_ORDER_QUEUE);
        assertNotNull(deallocateOrderRequest);
        assertThat(deallocateOrderRequest.getBeerOrderDto().getId()).isEqualTo(savedBeerOrder.getId());

    }

    public BeerOrder createBeerOrder() {
        BeerOrder beerOrder = BeerOrder.builder()
                .customer(customer)
                .build();

        Set<BeerOrderLine> lines = new HashSet<>();
        lines.add(BeerOrderLine.builder()
                .beerId(beerId)
                .upc("12345")
                .orderQuantity(1)
                .beerOrder(beerOrder)
                .build());

        beerOrder.setBeerOrderLines(lines);

        return beerOrder;
    }
}
