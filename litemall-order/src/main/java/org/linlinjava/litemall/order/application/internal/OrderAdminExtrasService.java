package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.OrderMapper;
import org.linlinjava.litemall.db.domain.OrderExportVo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin order extras (Wave 4, Task C): streamed CSV export and the channel stat.
 * Queries live in litemall-db {@code db.dao.OrderMapper} (the OrderMapper/StatMapper
 * precedent — order's @MapperScan is litemall-db's; no module-local mapper pattern
 * exists) with a LEAN {@link OrderExportVo} projection — never 10k aggregate
 * hydrations.
 *
 * <p>Export: keyset pagination id-asc (stable under concurrent inserts, no OFFSET
 * scans), RFC-4180 quoting, capped at {@code litemall.order.export.max-rows} with a
 * {@code # TRUNCATED} trailer (deviation from crmeb's xlsx-temp-file two-hop: we
 * stream text straight to the response — no temp file, no second download request).
 */
@Service
public class OrderAdminExtrasService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int BATCH = 500;

    static final String[] HEADER = {
            "id", "orderSn", "addTime", "orderStatus", "aftersaleStatus", "source",
            "deliveryType", "consignee", "mobile", "address", "countryCode",
            "goodsPrice", "freightPrice", "couponPrice", "orderPrice", "actualPrice",
            "payId", "payTime", "shipChannel", "shipSn", "userId"};

    private final OrderMapper orderMapper;

    /** Hard row cap per export — over it, the file ends with a "# TRUNCATED" trailer. */
    @Value("${litemall.order.export.max-rows:10000}")
    private int maxRows;

    public OrderAdminExtrasService(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /**
     * Stream the filtered orders as CSV rows (header included, no BOM — the controller
     * writes that on the raw byte stream). Returns the number of DATA rows written.
     */
    public int streamExportCsv(Writer out, Integer userId, String orderSn, List<Short> orderStatuses,
                               LocalDateTime start, LocalDateTime end) throws IOException {
        writeRow(out, (Object[]) HEADER);
        int written = 0;
        int lastId = 0;
        boolean truncated = false;
        while (true) {
            // Fetch one row beyond the remaining budget so we can tell "exactly at the
            // cap" from "over the cap" without a COUNT query.
            int budget = maxRows - written;
            List<OrderExportVo> batch = orderMapper.selectExportRows(
                    userId, orderSn, orderStatuses, start, end, lastId, Math.min(BATCH, budget + 1));
            if (batch.isEmpty()) {
                break;
            }
            for (OrderExportVo row : batch) {
                if (written >= maxRows) {
                    truncated = true;
                    break;
                }
                writeRow(out,
                        row.getId(), row.getOrderSn(), fmt(row.getAddTime()), row.getOrderStatus(),
                        row.getAftersaleStatus(), row.getSource(), row.getDeliveryType(),
                        row.getConsignee(), row.getMobile(), row.getAddress(), row.getCountryCode(),
                        fmt(row.getGoodsPrice()), fmt(row.getFreightPrice()), fmt(row.getCouponPrice()),
                        fmt(row.getOrderPrice()), fmt(row.getActualPrice()),
                        row.getPayId(), fmt(row.getPayTime()), row.getShipChannel(), row.getShipSn(),
                        row.getUserId());
                written++;
                lastId = row.getId();
            }
            if (truncated || batch.size() < Math.min(BATCH, budget + 1)) {
                break;
            }
        }
        if (truncated) {
            out.write("# TRUNCATED — row cap (" + maxRows + ") reached; narrow the filters\r\n");
        }
        return written;
    }

    /** {@code {bySource[], byTender[]}} over the optional placement-time window. */
    public Map<String, Object> statChannel(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bySource", orderMapper.statBySource(start, end));
        data.put("byTender", orderMapper.statByTender(start, end));
        return data;
    }

    // ---- CSV mechanics (RFC 4180: CRLF rows, double-quote escaping) --------------------

    private static void writeRow(Writer out, Object... fields) throws IOException {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                out.write(',');
            }
            out.write(quote(fields[i]));
        }
        out.write("\r\n");
    }

    private static String quote(Object value) {
        if (value == null) {
            return "";
        }
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private static String fmt(LocalDateTime value) {
        return value == null ? null : DT.format(value);
    }

    private static String fmt(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
