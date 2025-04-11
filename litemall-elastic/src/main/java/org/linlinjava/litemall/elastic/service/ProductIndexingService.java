package org.linlinjava.litemall.elastic.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.collections.map.HashedMap;
import org.linlinjava.litemall.db.dao.LitemallBrandMapper;
import org.linlinjava.litemall.db.dao.LitemallCategoryMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsAttributeMapper;
import org.linlinjava.litemall.db.dao.LitemallGoodsMapper;
import org.linlinjava.litemall.db.domain.*;
import org.linlinjava.litemall.db.service.LitemallBrandService;
import org.linlinjava.litemall.db.service.LitemallCategoryService;
import org.linlinjava.litemall.db.service.LitemallGoodsService;
import org.linlinjava.litemall.elastic.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ProductIndexingService {

    private final ElasticsearchOperations elasticsearchOperations;
    private final LitemallGoodsMapper goodsMapper;
    private final LitemallCategoryMapper categoryMapper;
    private final LitemallCategoryService categoryService;
    private final LitemallBrandService brandService;
    private final LitemallGoodsService goodsService;
    private final LitemallBrandMapper brandMapper;
    private final LitemallGoodsAttributeMapper attributeMapper;

    private final ObjectMapper objectMapper;

    @Value("classpath:index-settings.json")
    private Resource indexSettingsResource;
    @Value("classpath:index-mapping.json")
    private Resource indexMappingResource;



    public ProductIndexingService(ElasticsearchOperations elasticsearchOperations,
                                  LitemallGoodsMapper goodsMapper,
                                  LitemallCategoryMapper categoryMapper,
                                  LitemallBrandMapper brandMapper, LitemallGoodsAttributeMapper attributeMapper,
                                  LitemallCategoryService catService,
                                  LitemallBrandService brandService,
                                  LitemallGoodsService goodsService,
                                  final ObjectMapper objectMapper) {
        this.elasticsearchOperations = elasticsearchOperations;
        this.goodsMapper = goodsMapper;
        this.categoryMapper = categoryMapper;
        this.brandMapper = brandMapper;
        this.attributeMapper = attributeMapper;
        this.categoryService = catService;
        this.brandService = brandService;
        this.goodsService = goodsService;
        this.objectMapper = objectMapper;
    }


    /***
     * Create helper methods to create various parts of ProductDocument
     *
     */
    public ProductDocument createProductDocumentByBrand(LitemallBrand brand){
          ProductDocument productDocument = new ProductDocument();


        //Get all the brands goods from the LitemallBrand
        List<LitemallGoods> goodsList = goodsService.queryByBrand(brand.getId(), 2, 5);

        if(!goodsList.isEmpty()){
            productDocument.setSearchData(createSearchDataList(goodsList));

            for(LitemallGoods goods: goodsList) {

                CategoryDoc categoryDoc = createCategoryDoc(goods);
                Map<String, Integer> categoryScores = createCategoryScores(goods);
                List<String> completion = createCompletionTerms(goods);


                productDocument.setCategoryDoc(categoryDoc);
                productDocument.setCategoryScores(categoryScores);
                productDocument.setCompletionTerms(completion);
                productDocument.setSearchResultData(createSearchResultData(brand, goods));
                productDocument.setNumberSort(createNumberScore(goods));
            }

        }
        return productDocument;
    }

    // Creation of the SearchData
    private List<SearchData> createSearchDataList(List<LitemallGoods> listGoods){

       SearchData searchData = new SearchData();
       List<SearchData> listSearchData = new ArrayList<>();

       if(!listGoods.isEmpty()){
           for(LitemallGoods goods: listGoods){
               searchData.setFullTextSearch(createFullText(goods));
               searchData.setFulltextBoosted(createFullTextBoosted(goods));
               searchData.setStringFacets(createStringFacets(goods));
               searchData.setNumberFacets(createNumberFacets(goods));
               listSearchData.add(searchData);
           }
       }
       return listSearchData;
    }

    private SearchResultData createSearchResultData(LitemallBrand brand, LitemallGoods goods){

        LitemallCategory category = categoryMapper.selectByPrimaryKey(goods.getCategoryId());

        if(category == null){
            return null;
        }
        SearchResultData searchResultData = new SearchResultData();

        searchResultData.setName(goods.getName());
        searchResultData.setBrand(brand.getName());
        searchResultData.setCategory(category.getName());
        searchResultData.setGoodsSn(goods.getGoodsSn());
        searchResultData.setName(goods.getName());
        searchResultData.setPicUrl(goods.getPicUrl());
        searchResultData.setRetailPrice(goods.getRetailPrice());
        searchResultData.setCounterPrice(goods.getCounterPrice());
        searchResultData.setOnSale(goods.getIsOnSale());
        return searchResultData;
    }

    private NumberSort createNumberScore(LitemallGoods goods){
        NumberSort numberSort = new NumberSort();
        numberSort.setRetailPrice(goods.getRetailPrice());
        return numberSort;
    }

    private CategoryDoc createCategoryDoc(LitemallGoods goods){
        CategoryDoc categoryDoc = new CategoryDoc();
        LitemallCategory category = categoryService.findById(goods.getCategoryId());

        if(category != null){
            categoryDoc.setDirectParents(Collections.singletonList(category.getName()));

            List<String> allParents = new ArrayList<>();
            while(category != null){
                allParents.add(category.getName());
                category = categoryService.findById(category.getPid());
            }

            Collections.reverse(allParents);
            categoryDoc.setAllParents(allParents);
            categoryDoc.setPaths(Collections.singletonList(String.join("-", allParents)));

        } else {
            return null;
        }
        return categoryDoc;
    }

    private Map<String, Integer> createCategoryScores(LitemallGoods goods){
        Map<String, Integer> scores = new HashMap<>();

        scores.put("number_of_impressions", 265);
        scores.put("number_of_orders", 23);
        return  scores;
    }


    private List<String> createCompletionTerms(LitemallGoods goods){
        List<String> completionsTerms = new ArrayList<>();

        // Add product name to as completion terms
        completionsTerms.add(goods.getName());

        LitemallBrand brand = brandMapper.selectByPrimaryKey(goods.getBrandId());
        if(brand != null){
            completionsTerms.add(brand.getName());
        }

        //Add category name as a completion term
      /*  LitemallCategory category = categoryMapper.selectByPrimaryKey(goods.getCategoryId());
        if(category!= null){
            completionsTerms.add(category.getName());
        }*/

        // Add other attributes as completion terms
        LitemallGoodsAttributeExample attrExample = new LitemallGoodsAttributeExample();
        attrExample.or().andGoodsIdEqualTo(goods.getId());
        List<LitemallGoodsAttribute> attributes = attributeMapper.selectByExample(attrExample);
        for(LitemallGoodsAttribute attribute: attributes){
            completionsTerms.add(attribute.getValue());
        }
        return completionsTerms;
    }



    private Map<String, Double> createScores(LitemallGoods goods) {
        Map<String, Double> scores = new HashMap<>();
        scores.put("top_seller", 0.91); // You may want to calculate this based on sales data
        scores.put("pdp_impressions", 0.38); // You may want to calculate this based on view data
        scores.put("sale_impressions_rate", 0.8); // You may want to calculate this based on sales/views ratio
        scores.put("data_quality", 0.87); // You may want to calculate this based on data completeness
        scores.put("delivery_speed", 0.85); // You may want to calculate this based on shipping data
        scores.put("random", Math.random()); // Generate a random score between 0 and 1
        //scores.put("stock", goods.() > 0 ? 1.0 : 0.0);
        return scores;
    }



    private String createFullText(LitemallGoods goods){
        return String.format("%s %s", goods.getGoodsSn(), goods.getName());
    }
    private String createFullTextBoosted(LitemallGoods goods){
        return String.format("%s %s", goods.getName(), goods.getBrief());
    }
    private List<StringFacet> createStringFacets(LitemallGoods goods){
        List<StringFacet> facets = new ArrayList<>();
        LitemallBrand brand = brandMapper.selectByPrimaryKey(goods.getBrandId());
        if(brand != null) {
            facets.add(StringFacet.builder().name("brand").values(brand.getName()).build());
        }

        LitemallCategory category = categoryMapper.selectByPrimaryKey(goods.getCategoryId());
        if(category!= null) {
            facets.add(StringFacet.builder().name("category").values(category.getName()).build());
        }
        // Let add the material facet as a facet string
        LitemallGoodsAttributeExample attrExample = new LitemallGoodsAttributeExample();
        attrExample.or().andGoodsIdEqualTo(goods.getId());
        List<LitemallGoodsAttribute> attributes = attributeMapper.selectByExample(attrExample);
        for(LitemallGoodsAttribute attribute: attributes){
            facets.add(StringFacet.builder()
                    .name("material")
                    .values(attribute.getValue())
                    .build());
        }
        // We can add more string facets
        return facets;
    }
    private List<NumberFacet> createNumberFacets(LitemallGoods goods){
        List<NumberFacet> facets = new ArrayList<>();
        facets.add(NumberFacet.builder().name("price").value(goods.getRetailPrice().intValue()).build());
        // We can add more number facets
        return facets;
    }
}
