package org.linlinjava.litemall.order.infrastructure.acl.express;

import org.linlinjava.litemall.core.express.ExpressService;
import org.linlinjava.litemall.core.express.dao.ExpressInfo;
import org.linlinjava.litemall.core.express.dao.Traces;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.ExpressQueryPort;
import org.linlinjava.litemall.order.infrastructure.services.acl.facades.fulfillment.ExpressTrackingSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ExpressQueryPort} over litemall-core's kdniao (快递鸟) {@code ExpressService} bean
 * (provider {@code kdniao}). The bean is constructor-injected — {@code ExpressAutoConfiguration}
 * always provides it; whether it actually CALLS kdniao is governed by core's OWN properties.
 *
 * <p><b>CONFIG-PRECEDENCE TRAP</b> (why OnePass is the practical provider — see
 * docs/adr-fulfillment-seams.md): {@code ExpressService.getExpressInfo} returns null
 * unless {@code litemall.express.enable=true} PLUS appId/appKey — and those keys are bound
 * from litemall-core's profile yml ({@code application-core.yml}), which OUTRANKS this
 * module's {@code config/application.yml} in the property-source order. Setting
 * {@code litemall.express.*} here does NOT take effect; enabling kdniao requires env vars
 * ({@code LITEMALL_EXPRESS_ENABLE=true}, {@code LITEMALL_EXPRESS_APP_ID=...},
 * {@code LITEMALL_EXPRESS_APP_KEY=...}) or editing core's profile yml. On top of that,
 * core's vendor table maps CHINESE carrier names to kdniao codes, so a de-Chinesed DB
 * usually carries ship_channel values the table can't resolve.
 *
 * <p>{@code query(carrier, ...)} accepts either a kdniao vendor CODE (e.g. "ZTO") or a
 * vendor NAME from core's table, and never throws — core already catches its own
 * exceptions and returns null, which maps to {@code Optional.empty()}.
 */
public class KdniaoExpressQueryAdapter implements ExpressQueryPort {

    private static final Logger log = LoggerFactory.getLogger(KdniaoExpressQueryAdapter.class);

    private final ExpressService expressService;

    public KdniaoExpressQueryAdapter(ExpressService expressService) {
        this.expressService = expressService;
    }

    @Override
    public Optional<ExpressTrackingSnapshot> query(String carrier, String trackNumber) {
        if (trackNumber == null || trackNumber.isBlank()) {
            return Optional.empty();
        }
        try {
            ExpressInfo info = expressService.getExpressInfo(vendorCodeFrom(carrier), trackNumber.trim());
            if (info == null || !info.getSuccess()) {
                return Optional.empty();
            }
            List<ExpressTrackingSnapshot.Event> events = new ArrayList<>();
            if (info.getTraces() != null) {
                for (Traces trace : info.getTraces()) {
                    events.add(new ExpressTrackingSnapshot.Event(
                            trace.getAcceptTime(), null, trace.getAcceptStation()));
                }
            }
            String carrierName = info.getShipperName() != null ? info.getShipperName()
                    : (info.getShipperCode() != null ? info.getShipperCode() : carrier);
            return Optional.of(new ExpressTrackingSnapshot(
                    carrierName,
                    info.getLogisticCode() != null ? info.getLogisticCode() : trackNumber,
                    stateText(info.getState()),
                    events));
        } catch (RuntimeException e) {
            log.warn("kdniao query failed for {} / {}: {}", carrier, trackNumber, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public boolean enabled() {
        return true;
    }

    /** Accept a vendor NAME from core's table or pass a CODE through unchanged. */
    private String vendorCodeFrom(String carrier) {
        if (carrier == null || carrier.isBlank()) {
            return "";
        }
        String c = carrier.trim();
        for (Map<String, String> vendor : expressService.getVendors()) {
            if (c.equalsIgnoreCase(vendor.get("name"))) {
                return vendor.get("code");
            }
        }
        return c;
    }

    /** kdniao State: 2 = in transit, 3 = delivered, 4 = problem. English (DB is de-Chinesed). */
    private static String stateText(String state) {
        if (state == null) {
            return null;
        }
        switch (state) {
            case "2":
                return "In transit";
            case "3":
                return "Delivered";
            case "4":
                return "Problem";
            default:
                return state;
        }
    }
}
