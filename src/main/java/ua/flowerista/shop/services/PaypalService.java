package ua.flowerista.shop.services;


import com.paypal.core.PayPalHttpClient;
import com.paypal.http.HttpResponse;
import com.paypal.orders.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import ua.flowerista.shop.exceptions.AppException;
import ua.flowerista.shop.models.order.OrderStatus;
import ua.flowerista.shop.models.paypal.CompletedOrder;
import ua.flowerista.shop.models.paypal.PaymentOrder;
import ua.flowerista.shop.repositories.paypal.PaymentOrderRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaypalService {
    private static final Logger logger = LoggerFactory.getLogger(PaypalService.class);
    public static final String SUCCESS_URL = "/capture";
    public static final String CANCEL_URL = "/cancel";
    @Value("${frontend.server.url}")
    private String frontendUrl;
    private final PayPalHttpClient payPalHttpClient;
    private final OrderService orderService;
    private final PaymentOrderRepository paymentOrderRepository;

    public PaymentOrder createPayment(Integer orderId) {
        ua.flowerista.shop.models.order.Order order = orderService.getById(orderId).get(); //check for null made in controller
        BigDecimal fee = BigDecimal.valueOf(order.getSum().longValue());
        String currencyCode = order.getCurrency();
        OrderRequest orderRequest = new OrderRequest();
        orderRequest.checkoutPaymentIntent("CAPTURE");
        AmountWithBreakdown amountBreakdown = new AmountWithBreakdown().currencyCode(currencyCode).value(fee.toString());
        PurchaseUnitRequest purchaseUnitRequest = new PurchaseUnitRequest().amountWithBreakdown(amountBreakdown);
        orderRequest.purchaseUnits(List.of(purchaseUnitRequest));
        ApplicationContext applicationContext = new ApplicationContext()
                .returnUrl(frontendUrl + SUCCESS_URL)
                .cancelUrl(frontendUrl + CANCEL_URL);
        orderRequest.applicationContext(applicationContext);
        OrdersCreateRequest ordersCreateRequest = new OrdersCreateRequest().requestBody(orderRequest);

        try {
            HttpResponse<Order> orderHttpResponse = payPalHttpClient.execute(ordersCreateRequest);
            Order paymentOrder = orderHttpResponse.result();

            String redirectUrl = paymentOrder.links().stream()
                    .filter(link -> "approve".equals(link.rel()))
                    .findFirst()
                    .orElseThrow(NoSuchElementException::new)
                    .href();
            orderService.updatePayId(orderId, paymentOrder.id());
            orderService.updateStatus(orderId, OrderStatus.PENDING);
            PaymentOrder paymentOrderEntity = new PaymentOrder("success", paymentOrder.id(), redirectUrl);
            paymentOrderRepository.save(paymentOrderEntity);
            return paymentOrderEntity;
        } catch (IOException e) {
            log.error(e.getMessage());
            return new PaymentOrder("Error");
        }
    }


    public CompletedOrder completePayment(String token) {
        OrdersCaptureRequest ordersCaptureRequest = new OrdersCaptureRequest(token);
        try {
            HttpResponse<Order> httpResponse = payPalHttpClient.execute(ordersCaptureRequest);
            if (httpResponse.result().status() != null) {
                orderService.updateStatusByPayId(token, OrderStatus.IN_PROCESS);
                paymentOrderRepository.updateStatusAndDateOfPaymentByPayId(token, "payed",
                        httpResponse.result().updateTime());
                return new CompletedOrder("success", token);
            }
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return new CompletedOrder("error");
    }

    public PaymentOrder getPaymentForOrder(Integer orderId) {
        Optional<ua.flowerista.shop.models.order.Order> order = orderService.getById(orderId);
        if (order.isEmpty()) {
            logger.error("Order with id {} not found", orderId);
            throw new AppException("Order with id " + orderId + " not found", HttpStatus.BAD_REQUEST);
        }

        if (order.get().getPayId() == null) {
            return createPayment(orderId);
        }
        return paymentOrderRepository.findByPayId(order.get().getPayId());
    }
}
