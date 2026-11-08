package com.recipemanager.api.recipe;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

// Maps this class to the "recipes" Elasticsearch index, the same way @Entity maps a class
// to a database table. The index itself (and its mappings) is created by the Python scraper
// service (scraper/indexing.py ensure_elasticsearch_index) when a recipe is first indexed —
// this class only needs to describe the same shape so Spring Data can read/write it.
// C# equivalent: a POCO mapped to an Elasticsearch index via NEST/Elastic.Clients.Elasticsearch
// attributes, analogous to an EF Core entity mapped to a table.
@Document(indexName = "recipes")
public class RecipeDocument {

    // Elasticsearch document IDs are strings — this holds the recipe's UUID as text, the
    // same value used as the Qdrant point ID by the scraper.
    @Id
    private String id;

    @Field(type = FieldType.Keyword)
    private String householdId;

    @Field(type = FieldType.Text, analyzer = "english")
    private String title;

    @Field(type = FieldType.Text, analyzer = "english")
    private String description;

    @Field(name = "ingredient_names", type = FieldType.Text, analyzer = "english")
    private String ingredientNames;

    @Field(type = FieldType.Keyword)
    private String[] occasions;

    @Field(type = FieldType.Keyword)
    private String complexity;

    @Field(type = FieldType.Keyword)
    private String cuisine;

    @Field(name = "diet_tags", type = FieldType.Keyword)
    private String[] dietTags;

    @Field(name = "cook_time_minutes", type = FieldType.Integer)
    private Integer cookTimeMinutes;

    @Field(name = "created_at", type = FieldType.Date)
    private String createdAt;

    public String getId() { return id; }
    public String getHouseholdId() { return householdId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getIngredientNames() { return ingredientNames; }
    public String[] getOccasions() { return occasions; }
    public String getComplexity() { return complexity; }
    public String getCuisine() { return cuisine; }
    public String[] getDietTags() { return dietTags; }
    public Integer getCookTimeMinutes() { return cookTimeMinutes; }
    public String getCreatedAt() { return createdAt; }
}
