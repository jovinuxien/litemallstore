package org.linlinjava.litemall.order.interfaces.dtos.order;

/**
 * Body for {@code POST /srv/private/admin/order/{orderId}/ship} — the courier and
 * tracking number recorded when an admin ships a paid order.
 */
public class ShipActionRequest {

    private String shipChannel;
    private String shipSn;

    public String getShipChannel() { return shipChannel; }
    public void setShipChannel(String shipChannel) { this.shipChannel = shipChannel; }

    public String getShipSn() { return shipSn; }
    public void setShipSn(String shipSn) { this.shipSn = shipSn; }
}
