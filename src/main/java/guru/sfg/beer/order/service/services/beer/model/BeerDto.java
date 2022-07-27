package guru.sfg.beer.order.service.services.beer.model;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class BeerDto implements Serializable {
    static final long serialVersionUID = 9158063420208483710L;

    private String beerStyle;
    private BigDecimal price;
    private String beerName;
    private UUID id;

}
