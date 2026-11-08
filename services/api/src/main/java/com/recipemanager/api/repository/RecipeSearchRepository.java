package com.recipemanager.api.repository;

import com.recipemanager.api.recipe.RecipeDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

// ElasticsearchRepository is Spring Data's Elasticsearch equivalent of JpaRepository —
// same derived-query-method mechanism, backed by Elasticsearch instead of a JDBC driver.
// C# equivalent: a repository wrapping ElasticsearchClient.SearchAsync(...), the way
// JpaRepository wraps a DbContext.
public interface RecipeSearchRepository extends ElasticsearchRepository<RecipeDocument, String> {

    // Derived query method — identical mechanism to RecipeRepository.findByHouseholdId,
    // just resolved against the Elasticsearch index instead of the recipes table.
    Page<RecipeDocument> findByHouseholdId(String householdId, Pageable pageable);

    // Unlike a JPA @Query (JPQL — a SQL-like language), this @Query takes a raw Elasticsearch
    // query DSL JSON string. ?0 / ?1 are positional placeholders bound to the method's
    // arguments in declaration order — there's no JPQL-style named-parameter binding here.
    // This replaces the old Postgres ILIKE-based keyword search entirely (see EP2-06).
    //
    // "must": term filter on household_id — an exact match because it's mapped as a keyword
    // field (RecipeDocument.householdId), not analyzed/tokenized the way text fields are.
    // "should": multi_match searches title + description + ingredient_names together, with
    // "fuzziness": "AUTO" so small typos and singular/plural mismatches still match — this is
    // the fuzzy ingredient matching docs/schema.md calls for, which ILIKE in Postgres has no
    // equivalent for and is exactly why this story moves search here.
    // C# equivalent: building the same query with the Elasticsearch .NET client's query DSL
    // (a BoolQuery combining a TermQuery and a MultiMatchQuery), rather than LINQ-to-Entities.
    @Query("""
            {
              "bool": {
                "must": [
                  { "term": { "household_id": "?0" } }
                ],
                "should": [
                  { "multi_match": {
                      "query": "?1",
                      "fields": ["title", "description", "ingredient_names"],
                      "fuzziness": "AUTO"
                  } }
                ]
              }
            }
            """)
    Page<RecipeDocument> searchByKeyword(String householdId, String keyword, Pageable pageable);
}
