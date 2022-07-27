package guru.sfg.beer.order.service.services.beer;

import guru.sfg.beer.order.service.services.beer.model.BeerDto;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@ConfigurationProperties(prefix = "sfg.brewery", ignoreUnknownFields = false)
public class BeerServiceRestTemplateImpl implements BeerService {

    private final RestTemplate restTemplate;
    private String beerServiceHost;
    private String beerServicePathById;
    private String beerServicePathByUpc;

    public void setBeerServiceHost(String beerServiceHost) {
        this.beerServiceHost = beerServiceHost;
    }

    public void setBeerServicePathById(String beerServicePathById) {
        this.beerServicePathById = beerServicePathById;
    }

    public void setBeerServicePathByUpc(String beerServicePathByUpc) {
        this.beerServicePathByUpc = beerServicePathByUpc;
    }

    public BeerServiceRestTemplateImpl(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    @Override
    public Optional<BeerDto> getById(UUID beerId) {
        ResponseEntity<BeerDto> responseEntity = restTemplate
                .exchange(beerServiceHost + beerServicePathById, HttpMethod.GET, null,
                        new ParameterizedTypeReference<>() {}, beerId);
        return Objects.isNull(responseEntity.getBody()) ? Optional.empty() :
                Optional.of(responseEntity.getBody());
    }

    @Override
    public Optional<BeerDto> getByUpc(String upc) {
        ResponseEntity<BeerDto> responseEntity = restTemplate
                .exchange(beerServiceHost + beerServicePathByUpc, HttpMethod.GET, null,
                        new ParameterizedTypeReference<>() {}, upc);
        return Objects.isNull(responseEntity.getBody()) ? Optional.empty() :
                Optional.of(responseEntity.getBody());
    }
}
