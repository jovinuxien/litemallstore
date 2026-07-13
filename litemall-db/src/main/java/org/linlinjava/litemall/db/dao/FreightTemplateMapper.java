package org.linlinjava.litemall.db.dao;

import org.apache.ibatis.annotations.Param;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplate;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateFree;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateRegion;

import java.util.List;

/**
 * Hand-written mapper for the freight-template tables
 * ({@code litemall_shipping_templates} + {@code _region} + {@code _free}; V8 base,
 * V34 international columns). Mirrors {@link CjDisputeMapper}: co-located with the
 * generated DAOs so the existing {@code @MapperScan} + {@code dao/*.xml}
 * mapper-location pattern picks it up.
 */
public interface FreightTemplateMapper {

    List<LitemallShippingTemplate> selectAllTemplates();

    LitemallShippingTemplate selectTemplateById(@Param("id") Integer id);

    /** The single default template (is_default=1), or null if none is set. */
    LitemallShippingTemplate selectDefaultTemplate();

    /** Insert a new template; populates the generated id back onto the record. */
    int insertTemplate(LitemallShippingTemplate record);

    /** Update name/type/appoint/sort by id; is_default is managed separately. */
    int updateTemplate(LitemallShippingTemplate record);

    int logicalDeleteTemplate(@Param("id") Integer id);

    /** Clear the current default flag (run before setDefaultTemplate for at-most-one). */
    int clearDefaultTemplate();

    int setDefaultTemplate(@Param("id") Integer id);

    List<LitemallShippingTemplateRegion> selectRegionsByTempId(@Param("tempId") Integer tempId);

    List<LitemallShippingTemplateFree> selectFreeByTempId(@Param("tempId") Integer tempId);

    int insertRegion(LitemallShippingTemplateRegion record);

    int insertFree(LitemallShippingTemplateFree record);

    int logicalDeleteRegionsByTempId(@Param("tempId") Integer tempId);

    int logicalDeleteFreeByTempId(@Param("tempId") Integer tempId);

    /** Live (on-sale, non-deleted) goods bound to a template — guards template deletion. */
    int countLiveGoodsByTempId(@Param("tempId") Integer tempId);
}
