package guru.sfg.beer.order.service.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.jenspiegsa.wiremockextension.WireMockExtension;
import com.github.tomakehurst.wiremock.WireMockServer;
import guru.sfg.beer.order.service.domain.*;
import guru.sfg.beer.order.service.repositories.BeerOrderRepository;
import guru.sfg.beer.order.service.repositories.CustomerRepository;
import guru.sfg.brewery.model.BeerDto;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.Rollback;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.github.jenspiegsa.wiremockextension.ManagedWireMockServer.with;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
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
