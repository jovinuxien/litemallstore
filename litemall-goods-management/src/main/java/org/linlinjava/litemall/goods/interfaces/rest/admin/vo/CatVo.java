package org.linlinjava.litemall.goods.interfaces.rest.admin.vo;

import java.util.List;

/**
 * Cascader option (value/label/children) for the admin goods "category + brand" picker.
 * Ported verbatim from litemall-admin-api ({@code admin.vo.CatVo}).
 */
public class CatVo {
    private Integer value = null;
    private String label = null;
    private List children = null;

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public List getChildren() {
        return children;
    }

    public void setChildren(List children) {
        this.children = children;
    }
}
