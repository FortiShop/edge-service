package org.fortishop.edgeservice.domain;

public enum PointSourceService {

    MEMBER_TRANSFER("api-member-service:transfer"),
    MEMBER_ADJUST("api-member-service:adjust"),
    ORDER_REWARD("order-payment-service:reward"),
    ORDER_REFUND("order-payment-service:refund"),
    DELIVERY_COMPENSATION("delivery-service:compensation");

    private final String value;

    PointSourceService(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
