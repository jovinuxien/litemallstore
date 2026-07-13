package org.linlinjava.litemall.order.application.internal;

import org.linlinjava.litemall.db.dao.FreightTemplateMapper;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplate;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateFree;
import org.linlinjava.litemall.db.domain.LitemallShippingTemplateRegion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Admin CRUD over the freight-template tables (Wave 4, Task A). Child rows (region
 * pricing + free rules) are replaced wholesale on update — templates are small and the
 * admin SPA edits them as one form. Every mutation invalidates the calculator's 5-min
 * template cache so edits price immediately.
 *
 * <p>Delete is refused while a live (on-sale, not deleted) goods still references the
 * template — the referencing goods would silently fall back to flat pricing otherwise.
 */
@Service
public class FreightTemplateAdminService {

    /** Outcome of a delete/set-default attempt (controller maps to envelope/status). */
    public enum MutationResult {
        OK, NOT_FOUND, REFERENCED
    }

    private final FreightTemplateMapper mapper;
    private final FreightCalculationService calculationService;

    public FreightTemplateAdminService(FreightTemplateMapper mapper,
                                       FreightCalculationService calculationService) {
        this.mapper = mapper;
        this.calculationService = calculationService;
    }

    public List<LitemallShippingTemplate> list() {
        return mapper.selectAllTemplates();
    }

    public LitemallShippingTemplate template(Integer id) {
        return mapper.selectTemplateById(id);
    }

    public List<LitemallShippingTemplateRegion> regions(Integer tempId) {
        return mapper.selectRegionsByTempId(tempId);
    }

    public List<LitemallShippingTemplateFree> freeRules(Integer tempId) {
        return mapper.selectFreeByTempId(tempId);
    }

    @Transactional
    public Integer create(LitemallShippingTemplate template,
                          List<LitemallShippingTemplateRegion> regions,
                          List<LitemallShippingTemplateFree> freeRules) {
        mapper.insertTemplate(template);
        replaceChildren(template.getId(), regions, freeRules, false);
        calculationService.invalidateTemplateCache();
        return template.getId();
    }

    @Transactional
    public MutationResult update(LitemallShippingTemplate template,
                                 List<LitemallShippingTemplateRegion> regions,
                                 List<LitemallShippingTemplateFree> freeRules) {
        if (mapper.selectTemplateById(template.getId()) == null) {
            return MutationResult.NOT_FOUND;
        }
        mapper.updateTemplate(template);
        replaceChildren(template.getId(), regions, freeRules, true);
        calculationService.invalidateTemplateCache();
        return MutationResult.OK;
    }

    @Transactional
    public MutationResult delete(Integer id) {
        if (mapper.selectTemplateById(id) == null) {
            return MutationResult.NOT_FOUND;
        }
        if (mapper.countLiveGoodsByTempId(id) > 0) {
            return MutationResult.REFERENCED;
        }
        mapper.logicalDeleteTemplate(id);
        mapper.logicalDeleteRegionsByTempId(id);
        mapper.logicalDeleteFreeByTempId(id);
        calculationService.invalidateTemplateCache();
        return MutationResult.OK;
    }

    /** Make {@code id} the default template for unbound goods; {@code id=0} clears the default. */
    @Transactional
    public MutationResult setDefault(Integer id) {
        if (id != null && id > 0 && mapper.selectTemplateById(id) == null) {
            return MutationResult.NOT_FOUND;
        }
        mapper.clearDefaultTemplate();
        if (id != null && id > 0) {
            mapper.setDefaultTemplate(id);
        }
        calculationService.invalidateTemplateCache();
        return MutationResult.OK;
    }

    private void replaceChildren(Integer tempId,
                                 List<LitemallShippingTemplateRegion> regions,
                                 List<LitemallShippingTemplateFree> freeRules,
                                 boolean clearExisting) {
        if (clearExisting) {
            mapper.logicalDeleteRegionsByTempId(tempId);
            mapper.logicalDeleteFreeByTempId(tempId);
        }
        if (regions != null) {
            for (LitemallShippingTemplateRegion region : regions) {
                region.setId(null);
                region.setTempId(tempId);
                mapper.insertRegion(region);
            }
        }
        if (freeRules != null) {
            for (LitemallShippingTemplateFree free : freeRules) {
                free.setId(null);
                free.setTempId(tempId);
                mapper.insertFree(free);
            }
        }
    }
}
