package org.linlinjava.litemall.goods.interfaces.rest.admin.vo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Time-series stat payload, ported from litemall-admin-api ({@code admin.vo.StatVo}):
 * {@code columns} names the row keys in display order, {@code rows} carries the
 * per-day maps produced by {@code StatService}.
 */
public class StatVo {
    private String[] columns = new String[0];
    private List<Map> rows = new ArrayList<>();

    public String[] getColumns() {
        return columns;
    }

    public void setColumns(String[] columns) {
        this.columns = columns;
    }

    public List<Map> getRows() {
        return rows;
    }

    public void setRows(List<Map> rows) {
        this.rows = rows;
    }

    public void add(Map... r) {
        rows.addAll(Arrays.asList(r));
    }
}
