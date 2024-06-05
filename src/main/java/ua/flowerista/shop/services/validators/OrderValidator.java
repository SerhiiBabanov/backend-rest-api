package ua.flowerista.shop.services.validators;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ua.flowerista.shop.dto.order.OrderDto;
import ua.flowerista.shop.dto.order.OrderItemDto;
import ua.flowerista.shop.services.BouquetService;
import ua.flowerista.shop.services.BouquetSizeService;
import ua.flowerista.shop.services.ColorService;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderValidator {
    private final BouquetService bouquetService;
    private final ColorService colorService;
    private final BouquetSizeService bouquetSizeService;

    public List<String> validateOrder(OrderDto order) {
        List<String> errors = new ArrayList<>();

        isOrderItemsEmpty(order, errors);
        isProductIdEmpty(order, errors);
        isSizeEmpty(order, errors);
        isPriceEmpty(order, errors);

        isQuantityPositive(order, errors);

        isProductIdExist(order, errors);
        isSizeIdExist(order, errors);

        isProductIdAvailableForSale(order, errors);
        isSumCalculatedOnFrontEqualToCalculatedOnServer(order, errors);

        return errors;
    }

    private void isPriceEmpty(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (orderItem.getSizeId() == null) {
                errors.add("Price is empty");
            }
        });
    }

    private void isQuantityPositive(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (orderItem.getQuantity() <= 0) {
                errors.add("Quantity should be positive");
            }
        });
    }

    private void isProductIdAvailableForSale(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (!bouquetService.isAvailableForSale(orderItem.getProductId())) {
                errors.add("Product with id " + orderItem.getProductId() + " is not available for sale - " +
                        "out of stock or not active");
            }
        });
    }

    private void isSumCalculatedOnFrontEqualToCalculatedOnServer(OrderDto order, List<String> errors) {
        BigInteger sum = BigInteger.ZERO;
        for (OrderItemDto orderItem : order.getOrderItems()) {
            BigInteger price = bouquetSizeService.getPriceById(orderItem.getSizeId());
            sum = sum.add(price.multiply(BigInteger.valueOf(orderItem.getQuantity())));
        }
        if (sum.compareTo(order.getSum()) != 0) {
            errors.add("Sum calculated on front is not equal to sum calculated on server side. " +
                    "Front sum: " + order.getSum() + ", server sum: " + sum);
        }
    }

    private void isSizeIdExist(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (!bouquetSizeService.isExistById(orderItem.getSizeId())) {
                errors.add("Size with id " + orderItem.getSizeId() + " does not exist");
            }
        });
    }

    private void isProductIdExist(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (!bouquetService.isExist(orderItem.getProductId())) {
                errors.add("Product with id " + orderItem.getProductId() + " does not exist");
            }
        });
    }

    private void isSizeEmpty(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (orderItem.getSizeId() == null) {
                errors.add("Size is empty");
            }
        });
    }

    private void isProductIdEmpty(OrderDto order, List<String> errors) {
        order.getOrderItems().forEach(orderItem -> {
            if (orderItem.getProductId() == null) {
                errors.add("Product id is empty");
            }
        });
    }

    private void isOrderItemsEmpty(OrderDto order, List<String> errors) {
        if (order.getOrderItems().isEmpty()) {
            errors.add("Order items are empty");
        }
    }
}
