package com.aliexpress.paymentservice.service;

import com.aliexpress.paymentservice.dto.ChargeRequest;
import com.aliexpress.paymentservice.dto.OrderResponse;
import com.aliexpress.paymentservice.dto.PayoutRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class PaymentService {
    Logger logger = LoggerFactory.getLogger(PaymentService.class);
    @Value("${STRIPE_SECRET_KEY}")
    private String secretKey;
    @Value("${rabbitmq.exchangePayToInv.name}")
    private String exchangeNamePayToInv;
    @Value("${rabbitmq.jsonBindingPayToInv.routingKey}")
    private String jsonRoutingKeyPayToInv;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    private void setSecretKey() {
        Stripe.apiKey = secretKey;
    }

    public Customer createCustomer(String token, String email) throws Exception {
        Map<String, Object> customerParams = new HashMap<String, Object>();
        customerParams.put("email", email);
        customerParams.put("source", token);
        return Customer.create(customerParams);
    }

    private Customer getCustomer(String id) throws StripeException {
        return Customer.retrieve(id);
    }

    public Charge chargeNewCard(ChargeRequest chargeRequest) throws StripeException {
        Map<String, Object> card = new HashMap<>();
        card.put("number", "4242424242424242");
        card.put("exp_month", 5);
        card.put("exp_year", 2024);
        card.put("cvc", "314");
        Map<String, Object> params = new HashMap<>();
        params.put("card", card);

        Token token = Token.create(params);

        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", (int) (chargeRequest.getAmount() * 100));
        chargeParams.put("currency", "USD");
        chargeParams.put("source", token.getId());
        Charge charge = Charge.create(chargeParams);
        return charge;
    }

    public Charge chargeCustomerCard(String customerId, int amount) throws StripeException {
        String sourceCard = getCustomer(customerId).getDefaultSource();
        Map<String, Object> chargeParams = new HashMap<>();
        chargeParams.put("amount", amount);
        chargeParams.put("currency", "USD");
        chargeParams.put("customer", customerId);
        chargeParams.put("source", sourceCard);
        Charge charge = Charge.create(chargeParams);
        return charge;
    }

    public Refund refund(String chargeId) throws StripeException {
        Map<String, Object> params = new HashMap<>();
        params.put("charge", chargeId);
        Refund refund = Refund.create(params);
        return refund;
    }

    // todo: add valid test bank account
    public Payout payout(PayoutRequest payoutRequest) throws StripeException {
        // get our stripe account

        Map<String, Object> payoutParams = new HashMap<>();
        payoutParams.put("amount", (int) (payoutRequest.getAmount() * 100));
        payoutParams.put("currency", "USD");
        payoutParams.put("destination", payoutRequest.getDestinationCustomerId());
        payoutParams.put("description", "Test Payout");
        Payout payout = Payout.create(payoutParams);
        return payout;
    }


    @RabbitListener(queues = {"${rabbitmq.jsonQueueInvToPay.name}"})
    public void consumeOrder(OrderResponse orderResponse) throws JsonProcessingException, StripeException {
        logger.info(String.format("Received Json message => %s", orderResponse.toString()));
        try {
            ChargeRequest chargeRequest = new ChargeRequest();
            chargeRequest.setAmount(orderResponse.getTotal_price());
            chargeNewCard(chargeRequest);
        }
        catch (Exception e){
            logger.info("Payment Exception "+e.getMessage());
            orderRollback(orderResponse, exchangeNamePayToInv, jsonRoutingKeyPayToInv);
            return;
        }
//        todo - payout??
//        try{
//            //payout
//        }
//        catch (Exception e){
//            //refund
//            orderRollback(orderResponse, exchangeNamePayToInv, jsonRoutingKeyPayToInv);
//            return;
//        }
        logger.info("Payment successful!");
    }

    public void orderRollback(OrderResponse orderResponse, String exchangeName, String jsonRoutingKey) {
        logger.info(String.format("Sent JSON message => %s", orderResponse.toString()));
        rabbitTemplate.convertAndSend(exchangeName, jsonRoutingKey, orderResponse);
    }
}