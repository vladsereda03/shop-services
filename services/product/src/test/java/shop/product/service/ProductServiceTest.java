package shop.product.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import shop.product.model.Good;
import shop.product.model.Manufacturer;
import shop.product.model.dto.CreateGoodRequest;
import shop.product.repository.GoodRepository;
import shop.product.repository.ManufacturerRepository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private GoodRepository goodRepository;

    @Mock
    private ManufacturerRepository manufacturerRepository;

    @InjectMocks
    private ProductService productService;

    // --- createGood ---

    @Test
    void blankOrMissingNameIsRejectedWith400() {
        for (String name : new String[]{null, "", "   "}) {
            CreateGoodRequest request = validRequest();
            request.setName(name);

            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> productService.createGood(request))
                    .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        verifyNoInteractions(goodRepository);
    }

    @Test
    void negativePriceOrQuantityIsRejectedWith400() {
        CreateGoodRequest negativePrice = validRequest();
        negativePrice.setPriceKopeck(-1);
        CreateGoodRequest negativeQuantity = validRequest();
        negativeQuantity.setQuantity(-1);

        for (CreateGoodRequest request : new CreateGoodRequest[]{negativePrice, negativeQuantity}) {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> productService.createGood(request))
                    .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        verifyNoInteractions(goodRepository);
    }

    @Test
    void brokenBase64ImageIsRejectedWith400() {
        CreateGoodRequest request = validRequest();
        request.setImageBase64("!!!not-base64!!!");

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> productService.createGood(request))
                .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        verifyNoInteractions(goodRepository);
    }

    @Test
    void createGoodDecodesImageAndResolvesManufacturers() {
        byte[] imageBytes = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);
        CreateGoodRequest request = validRequest();
        request.setImageBase64(Base64.getEncoder().encodeToString(imageBytes));
        Manufacturer manufacturer = new Manufacturer("Lego", "lego.com", "bricks");
        when(manufacturerRepository.findAllById(List.of(3L))).thenReturn(List.of(manufacturer));
        when(goodRepository.saveAndFlush(any(Good.class))).thenAnswer(inv -> inv.getArgument(0));

        Good saved = productService.createGood(request);

        assertThat(saved.getName()).isEqualTo("Конструктор");
        assertThat(saved.getPriceKopeck()).isEqualTo(129900L);
        assertThat(saved.getDescription()).isEqualTo("description");
        assertThat(saved.getCategory()).isEqualTo("toys");
        assertThat(saved.getQuantity()).isEqualTo(7);
        assertThat(saved.getImage()).isEqualTo(imageBytes);
        assertThat(saved.getManufacturers()).containsExactly(manufacturer);
    }

    @Test
    void createGoodWithoutImageStoresNull() {
        CreateGoodRequest request = validRequest();
        request.setImageBase64("   ");
        when(manufacturerRepository.findAllById(List.of(3L))).thenReturn(List.of());
        when(goodRepository.saveAndFlush(any(Good.class))).thenAnswer(inv -> inv.getArgument(0));

        Good saved = productService.createGood(request);

        assertThat(saved.getImage()).isNull();
    }

    // --- reserve / release ---

    @Test
    void reserveDecreasesStock() {
        Good good = goodWithQuantity(10);
        when(goodRepository.findWithLockById(1L)).thenReturn(Optional.of(good));

        productService.reserve(1L, 3);

        assertThat(good.getQuantity()).isEqualTo(7);
    }

    @Test
    void reserveMoreThanInStockIsRejectedWith409AndStockIsUntouched() {
        Good good = goodWithQuantity(2);
        when(goodRepository.findWithLockById(1L)).thenReturn(Optional.of(good));

        assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> productService.reserve(1L, 3))
                .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(good.getQuantity()).isEqualTo(2);
    }

    @Test
    void nonPositiveQuantityIsRejectedWith400BeforeTouchingTheDatabase() {
        for (int quantity : new int[]{0, -5}) {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> productService.reserve(1L, quantity))
                    .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> productService.release(1L, quantity))
                    .satisfies(e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        }

        verifyNoInteractions(goodRepository);
    }

    @Test
    void unknownGoodThrowsEntityNotFound() {
        when(goodRepository.findWithLockById(99L)).thenReturn(Optional.empty());

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> productService.reserve(99L, 1));
    }

    @Test
    void releaseReturnsStock() {
        Good good = goodWithQuantity(2);
        when(goodRepository.findWithLockById(1L)).thenReturn(Optional.of(good));

        productService.release(1L, 3);

        assertThat(good.getQuantity()).isEqualTo(5);
    }

    // --- helpers ---

    private static CreateGoodRequest validRequest() {
        return new CreateGoodRequest("Конструктор", 129900L, "description", "toys",
                7, null, List.of(3L));
    }

    private static Good goodWithQuantity(int quantity) {
        Good good = new Good("Конструктор", 129900L, "description", "toys", null, List.of());
        good.setQuantity(quantity);
        return good;
    }
}
